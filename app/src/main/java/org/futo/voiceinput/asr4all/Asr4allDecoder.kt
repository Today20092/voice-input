package org.futo.voiceinput.asr4all

import org.json.JSONArray
import org.json.JSONObject
import org.pytorch.executorch.EValue
import org.pytorch.executorch.Module
import org.pytorch.executorch.Tensor
import java.io.File
import org.futo.voiceinput.nemotron.SherpaStreamingDecoder
import java.util.Locale

internal data class TensorSpec(val dtype: Int, val shape: LongArray) {
    val elements: Int = shape.fold(1L) { n, dimension -> Math.multiplyExact(n, dimension) }
        .also { require(it in 0..Int.MAX_VALUE.toLong()) }.toInt()

    fun zeros(): EValue = when (dtype) {
        4 -> EValue.from(Tensor.fromBlob(LongArray(elements), shape))
        6 -> EValue.from(Tensor.fromBlob(FloatArray(elements), shape))
        else -> error("Unsupported ASR4ALL tensor type $dtype")
    }
}

internal fun JSONArray.tensorSpecs() = List(length()) { i ->
    val tensor = getJSONObject(i)
    val shape = tensor.getJSONArray("shape")
    TensorSpec(tensor.getInt("dtype"), LongArray(shape.length()) { shape.getLong(it) })
}

internal fun copyTensor(value: EValue, spec: TensorSpec): EValue = when (spec.dtype) {
    4 -> EValue.from(Tensor.fromBlob(value.toTensor().dataAsLongArray, spec.shape))
    6 -> EValue.from(Tensor.fromBlob(value.toTensor().dataAsFloatArray, spec.shape))
    else -> error("Unsupported tensor type")
}

internal fun pauseBucket(blanks: Int): Int = intArrayOf(1, 2, 4, 8, 16, 32, 64, 128)
    .count { blanks >= it }

internal fun renderPcec(vocab: List<String>, blank: Int, controls: Map<String, Int>, slots: List<Int>): String {
    data class Word(val pieces: StringBuilder, val casing: String, var punctuation: String = "")
    val words = mutableListOf<Word>()
    var pending = ""
    var previous = -1
    slots.forEach { id ->
        if (id == previous || id == blank) { previous = id; return@forEach }
        previous = id
        when (id) {
            controls["cap"] -> pending = "cap"
            controls["allcaps"] -> pending = "upper"
            controls["period"], controls["comma"], controls["question"] -> {
                words.lastOrNull()?.punctuation = when (id) {
                    controls["period"] -> "."
                    controls["comma"] -> ","
                    else -> "?"
                }
            }
            else -> vocab.getOrNull(id)?.takeIf { it.isNotEmpty() }?.let { piece ->
                if (piece.startsWith(' ') || words.isEmpty()) {
                    words.add(Word(StringBuilder(piece), pending))
                    pending = ""
                } else words.last().pieces.append(piece)
            }
        }
    }
    var sentenceStart = true
    return words.mapNotNull { word ->
        var text = word.pieces.toString().trim()
        if (text.isEmpty()) return@mapNotNull null
        text = when {
            word.casing == "upper" -> text.uppercase(Locale.ROOT)
            word.casing == "cap" || sentenceStart -> text.replaceFirstChar { it.uppercaseChar() }
            else -> text
        }
        sentenceStart = word.punctuation == "." || word.punctuation == "?"
        text + word.punctuation
    }.joinToString(" ")
}

internal class Asr4allDecoder(directory: File, geometry: JSONObject) : SherpaStreamingDecoder {
    private val meta = JSONObject(File(directory, "metadata.json").readText())
    private val methods = geometry.getJSONObject("methods")
    private val tierName = meta.getString("default_tier")
    private val tier = meta.getJSONArray("tiers").let { tiers ->
        (0 until tiers.length()).map { tiers.getJSONObject(it) }.first { it.getString("name") == tierName }
    }
    private val specs = methods.getJSONObject(tierName)
    private val inputs = specs.getJSONArray("inputs").tensorSpecs()
    private val outputs = specs.getJSONArray("outputs").tensorSpecs()
    private val window = inputs[0].elements
    private val commit = outputs[0].shape[1].toInt()
    private val spf = meta.getInt("samples_per_frame")
    private val blank = meta.getInt("blank_id")
    private val hiddenDim = outputs[3].shape.last().toInt()
    private val module = Module.load(File(directory, "asr_encoder.pte").absolutePath, Module.LOAD_MODE_MMAP, 1)
    private var caches = inputs.drop(1).map { it.zeros() }
    private var pcm = FloatArray(0)
    private var readOffset = 0
    private val pcec = Pcec()

    init {
        try {
            require(meta.getInt("schema_version") == 1 && meta.getInt("sample_rate") == 16000 && spf == 640)
            require(tierName == "stream_c16r4" && inputs.size == 3 && outputs.size == 4)
            listOf(tierName, pcec.name, pcec.flushName).forEach {
                check(module.loadMethod(it) == 0) { "Unable to load ASR4ALL method $it" }
            }
        } catch (failure: Throwable) { module.destroy(); throw failure }
    }

    override fun acceptAudio(samples: FloatArray): String {
        pcm = pcm.copyOfRange(readOffset, pcm.size) + samples
        readOffset = 0
        while (pcm.size - readOffset >= window) step()
        return pcec.text(true)
    }

    override fun finish(): String {
        while (readOffset < pcm.size) step()
        pcec.emitOpen()
        pcec.drain()
        return pcec.text(true)
    }

    private fun step() {
        val remaining = pcm.size - readOffset
        val frameCount = minOf(commit, remaining / spf)
        val audio = FloatArray(window)
        pcm.copyInto(audio, 0, readOffset, minOf(pcm.size, readOffset + window))
        val result = module.execute(tierName,
            EValue.from(Tensor.fromBlob(audio, inputs[0].shape)), *caches.toTypedArray())
        check(result.size == outputs.size) { "ASR4ALL returned invalid acoustic outputs" }
        // The method arena is reused, so copy every retained cache before another call.
        caches = (1..2).map { copyTensor(result[it], outputs[it]) }
        val logits = result[0].toTensor().dataAsFloatArray
        val hidden = result[3].toTensor().dataAsFloatArray
        val classes = outputs[0].shape.last().toInt()
        repeat(frameCount) { frame ->
            val offset = frame * classes
            val id = (0 until classes).maxBy { logits[offset + it] }
            pcec.pool(id, hidden, frame * hiddenDim)
        }
        readOffset += minOf(remaining, commit * spf)
        pcec.drain()
    }

    override fun close() = module.destroy()

    private inner class Pcec {
        private val config = meta.getJSONObject("pcec")
        private val pcecVocab = config.getJSONArray("vocab").let { v -> List(v.length()) { v.getString(it) } }
        private val controls = config.getJSONObject("ctrl").let { control ->
            listOf("cap", "allcaps", "period", "comma", "question").associateWith { control.getInt(it) }
        }
        val name = tier.getJSONObject("pcec").getString("name")
        val flushName = tier.getJSONObject("pcec").getString("flush")
        private val inputSpecs = methods.getJSONObject(name).getJSONArray("inputs").tensorSpecs()
        private val outputSpecs = methods.getJSONObject(name).getJSONArray("outputs").tensorSpecs()
        private val flushSpecs = methods.getJSONObject(flushName).getJSONArray("outputs").tensorSpecs()
        private val k = inputSpecs[0].shape[1].toInt()
        private val lookahead = (flushSpecs[0].shape[1].toInt() - 1) / 2 - k
        private var cache = inputSpecs.takeLast(2).map { it.zeros() }
        private val ids = mutableListOf<Int>()
        private val pauses = mutableListOf<Int>()
        private val acoustic = mutableListOf<FloatArray>()
        private val slots = mutableListOf<Int>()
        private var consumed = 0
        private var openId: Int? = null
        private var sum = DoubleArray(hiddenDim)
        private var frames = 0
        private var leadingBlanks = 0
        private var blanks = 0
        private var prev = -1

        init { require(inputSpecs.size == 6 && inputSpecs.last().dtype == 4 && lookahead >= 0) }

        fun pool(id: Int, hidden: FloatArray, offset: Int) {
            if (id != prev && id != blank) {
                emitOpen()
                openId = id
                sum = DoubleArray(hiddenDim) { hidden[offset + it].toDouble() }
                frames = 1
                leadingBlanks = blanks
                blanks = 0
            } else if (openId != null) {
                repeat(hiddenDim) { sum[it] += hidden[offset + it] }
                frames++
            }
            prev = id
            if (id == blank) blanks++
        }

        fun emitOpen() {
            val id = openId ?: return
            ids.add(id)
            pauses.add(pauseBucket(leadingBlanks))
            acoustic.add(FloatArray(hiddenDim) { (sum[it] / frames).toFloat() })
            openId = null
        }

        private fun features(n: Int): List<EValue> {
            val tokenIds = LongArray(k) { config.getLong("pad_in") }
            val pauseIds = LongArray(k) { config.getLong("pause_blackout") }
            val hidden = FloatArray(k * hiddenDim)
            repeat(n) { i ->
                tokenIds[i] = ids[i].toLong()
                if (config.optBoolean("real_pause")) pauseIds[i] = pauses[i].toLong()
                acoustic[i].copyInto(hidden, i * hiddenDim)
            }
            return listOf(
                EValue.from(Tensor.fromBlob(tokenIds, inputSpecs[0].shape)),
                EValue.from(Tensor.fromBlob(pauseIds, inputSpecs[1].shape)),
                EValue.from(Tensor.fromBlob(hidden, inputSpecs[2].shape)),
                inputSpecs[3].zeros()
            )
        }

        fun drain() {
            while (ids.size >= k) {
                val result = module.execute(name, *(features(k) + cache).toTypedArray())
                check(result.size == 3) { "Invalid PCEC outputs" }
                val skip = minOf(2 * k, maxOf(0, 2 * lookahead - 2 * consumed))
                slots.addAll(result[0].toTensor().dataAsLongArray.slice(skip until 2 * k).map { it.toInt() })
                cache = result.takeLast(2).mapIndexed { i, value -> copyTensor(value, outputSpecs[i + 1]) }
                ids.subList(0, k).clear()
                pauses.subList(0, k).clear()
                acoustic.subList(0, k).clear()
                consumed += k
            }
        }

        fun text(tail: Boolean): String {
            val rendered = slots.toMutableList()
            if (tail) {
                val n = ids.size
                val result = module.execute(flushName, *(features(n) +
                    EValue.from(Tensor.fromBlob(longArrayOf(n.toLong()), longArrayOf())) + cache).toTypedArray())
                check(result.size == 1) { "Invalid PCEC flush outputs" }
                val valid = 2 * lookahead + 2 * n + 1
                val skip = maxOf(0, 2 * lookahead - 2 * consumed)
                rendered.addAll(result[0].toTensor().dataAsLongArray.slice(skip until valid).map { it.toInt() })
            }
            return renderPcec(pcecVocab, config.getInt("blank_id"), controls, rendered)
        }
    }
}

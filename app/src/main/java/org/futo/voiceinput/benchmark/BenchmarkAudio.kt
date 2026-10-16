package org.futo.voiceinput.benchmark

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

const val BENCHMARK_MAX_SECONDS = 180
const val BENCHMARK_MAX_FILE_BYTES = 64L * 1024 * 1024

object BenchmarkAudio {
    suspend fun import(context: Context, uri: Uri, original: File, metadata: (String) -> Unit = {}): FloatArray {
        require(!original.exists()) { "Import destination already exists" }
        try {
            copyOriginal(context, uri, original)
            return decode(original, metadata)
        } catch (failure: Throwable) {
            original.delete()
            throw failure
        }
    }

    private suspend fun copyOriginal(context: Context, uri: Uri, original: File) {
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open recording" }
            original.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= BENCHMARK_MAX_FILE_BYTES) { "Recording exceeds 64 MiB; use a shorter recording" }
                    output.write(buffer, 0, count)
                }
                require(total > 0) { "Recording is empty" }
            }
        }
    }

    suspend fun decode(file: File, metadata: (String) -> Unit = {}): FloatArray {
        require(file.length() in 1..BENCHMARK_MAX_FILE_BYTES) { "Recording is empty or exceeds 64 MiB" }
        require(file.length() >= 4) { "Recording is too short or corrupt" }
        val signature = DataInputStream(file.inputStream()).use { input -> ByteArray(4).also { input.readFully(it) } }.toString(Charsets.US_ASCII)
        if (signature == "RIFF") return decodeWav(file.readBytes(), metadata)
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME) == "audio/mp4a-latm"
            } ?: error("Use an AAC M4A file or a 16-bit PCM WAV file")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            validateFormat(rate, channels)
            metadata("AAC M4A, $rate Hz, $channels channels; stereo mean; windowed-sinc 32 taps/1024 phases; 16 kHz mono float32 LE")
            if (format.containsKey(MediaFormat.KEY_DURATION)) require(format.getLong(MediaFormat.KEY_DURATION) <= BENCHMARK_MAX_SECONDS * 1_000_000L) {
                "Recording exceeds 180 seconds; use a shorter recording"
            }
            val decoder = MediaCodec.createDecoderByType("audio/mp4a-latm")
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()
            val mono = FloatArray(rate * BENCHMARK_MAX_SECONDS)
            var count = 0
            var inputDone = false
            var outputDone = false
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            val info = MediaCodec.BufferInfo()
            val deadline = System.nanoTime() + 120_000_000_000L
            while (!outputDone) {
                currentCoroutineContext().ensureActive()
                check(System.nanoTime() < deadline) { "Audio conversion timed out; try another file" }
                if (!inputDone) {
                    val index = decoder.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        val buffer = requireNotNull(decoder.getInputBuffer(index))
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val index = decoder.dequeueOutputBuffer(info, 10_000)
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val output = decoder.outputFormat
                    require(output.getInteger(MediaFormat.KEY_SAMPLE_RATE) == rate && output.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == channels) {
                        "Changing audio format is unsupported; export a PCM WAV"
                    }
                    if (output.containsKey(MediaFormat.KEY_PCM_ENCODING)) encoding = output.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    require(encoding == AudioFormat.ENCODING_PCM_16BIT || encoding == AudioFormat.ENCODING_PCM_FLOAT)
                } else if (index >= 0) {
                    try {
                        val buffer = requireNotNull(decoder.getOutputBuffer(index)).order(ByteOrder.LITTLE_ENDIAN)
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        val stride = channels * if (encoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
                        require(buffer.remaining() % stride == 0) { "Corrupt PCM output" }
                        while (buffer.remaining() >= stride) {
                            require(count < mono.size) { "Recording exceeds 180 seconds" }
                            var sum = 0f
                            repeat(channels) { sum += if (encoding == AudioFormat.ENCODING_PCM_FLOAT) buffer.float else buffer.short / 32768f }
                            val value = sum / channels
                            require(value.isFinite()) { "Corrupt audio samples" }
                            mono[count++] = value.coerceIn(-1f, 1f)
                        }
                        outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    } finally { decoder.releaseOutputBuffer(index, false) }
                }
            }
            require(count > 0) { "Recording contains no audio" }
            return resample(mono.copyOf(count), rate)
        } finally {
            try { codec?.release() } finally { extractor.release() }
        }
    }

    internal fun decodeWav(bytes: ByteArray, metadata: (String) -> Unit = {}): FloatArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(bytes.size >= 44 && String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE") { "Invalid WAV file" }
        var rate = 0
        var channels = 0
        var audio: ByteArray? = null
        buffer.position(12)
        while (buffer.remaining() >= 8) {
            val tagBytes = ByteArray(4).also { buffer.get(it) }
            val tag = tagBytes.toString(Charsets.US_ASCII)
            val size = buffer.int
            require(size >= 0 && size <= buffer.remaining()) { "Truncated WAV file" }
            val end = buffer.position() + size
            if (tag == "fmt ") {
                require(size >= 16 && buffer.short.toInt() == 1) { "Use a 16-bit PCM WAV file" }
                channels = buffer.short.toInt()
                rate = buffer.int
                buffer.int
                val alignment = buffer.short.toInt()
                require(buffer.short.toInt() == 16 && alignment == channels * 2) { "Use a 16-bit PCM WAV file" }
                validateFormat(rate, channels)
            } else if (tag == "data") {
                require(audio == null) { "Multiple WAV data chunks are unsupported" }
                audio = ByteArray(size).also { buffer.get(it) }
            }
            buffer.position(end)
            if (size % 2 == 1 && buffer.hasRemaining()) buffer.get()
        }
        validateFormat(rate, channels)
        metadata("PCM16 WAV, $rate Hz, $channels channels; stereo mean; windowed-sinc 32 taps/1024 phases; 16 kHz mono float32 LE")
        val pcm = requireNotNull(audio) { "WAV has no audio" }
        require(pcm.isNotEmpty() && pcm.size % (channels * 2) == 0) { "Empty or truncated WAV" }
        val count = pcm.size / (channels * 2)
        require(count <= rate * BENCHMARK_MAX_SECONDS) { "Recording exceeds 180 seconds" }
        val input = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN)
        return resample(FloatArray(count) {
            var sum = 0f
            repeat(channels) { sum += input.short / 32768f }
            sum / channels
        }, rate)
    }

    private fun validateFormat(rate: Int, channels: Int) {
        require(rate in 8000..48000 && channels in 1..2) { "Use mono or stereo audio at 8 to 48 kHz" }
    }

    internal fun resample(mono: FloatArray, rate: Int): FloatArray {
        if (rate == 16000) return mono
        // Windowed-sinc low-pass conversion prevents aliasing when reducing the sample rate.
        val cutoff = minOf(1.0, 16000.0 / rate) * 0.95
        val taps = 32
        val kernels = Array(1024) { phase ->
            DoubleArray(taps) { tap ->
                val x = tap - 15 - phase / 1024.0
                val sinc = if (x == 0.0) cutoff else sin(PI * cutoff * x) / (PI * x)
                sinc * (0.5 + 0.5 * cos(PI * x / 16.0))
            }.also { weights -> val sum = weights.sum(); weights.indices.forEach { weights[it] /= sum } }
        }
        return FloatArray((mono.size.toLong() * 16000 / rate).toInt()) { index ->
            val position = index.toDouble() * rate / 16000
            val base = position.toInt()
            val weights = kernels[((position - base) * 1024).toInt().coerceIn(0, 1023)]
            var sum = 0.0
            repeat(taps) { tap -> sum += mono[(base + tap - 15).coerceIn(mono.indices)] * weights[tap] }
            sum.toFloat().coerceIn(-1f, 1f)
        }.also { require(it.isNotEmpty()) { "Recording is too short" } }
    }
}

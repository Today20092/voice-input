package org.futo.voiceinput.whistle

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal data class WhistleResult(val text: String, val language: String?)

internal fun parseWhistleResult(output: String): WhistleResult = try {
    val json = Json.parseToJsonElement(output).jsonObject
    val text = json.getValue("text").jsonPrimitive
    val language = json.getValue("language").jsonPrimitive
    check(text.isString && language.isString)
    check(language.content in setOf("", "en", "de", "fr", "es", "it", "nl", "pl"))
    check(text.content.isBlank() || language.content.isNotEmpty())
    WhistleResult(text.content, language.content.takeIf { it.isNotEmpty() })
} catch (_: Exception) {
    // Parser failures can contain dictated text; never propagate them to diagnostics.
    throw IllegalStateException("Whistle returned an invalid result")
}

private val processMutex = Mutex()

internal suspend fun runWhistle(runner: File, model: File, directory: File, samples: FloatArray): WhistleResult =
    withContext(Dispatchers.IO) {
        processMutex.withLock {
            require(samples.size <= (Int.MAX_VALUE - 36) / 4) { "Whistle recording is too long" }
            require(samples.all { it.isFinite() && it in -1f..1f }) { "Invalid Whistle audio" }
            if (samples.isEmpty()) return@withLock WhistleResult("", null)
            check(directory.isDirectory || directory.mkdirs()) { "Cannot create Whistle workspace" }
            // One worker at a time; also remove private scratch files left by an interrupted app process.
            directory.listFiles()?.forEach { check(it.delete()) { "Cannot clear Whistle workspace" } }
            val audio = File(directory, "audio.wav")
            val output = File(directory, "result.json")
            var process: Process? = null
            try {
                audio.outputStream().buffered().use { stream ->
                    val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
                    header.put("RIFF".toByteArray()).putInt(36 + samples.size * 4)
                    header.put("WAVEfmt ".toByteArray()).putInt(16).putShort(3).putShort(1)
                    header.putInt(16000).putInt(64000).putShort(4).putShort(32)
                    header.put("data".toByteArray()).putInt(samples.size * 4)
                    stream.write(header.array())
                    val block = ByteBuffer.allocate(16384).order(ByteOrder.LITTLE_ENDIAN)
                    var offset = 0
                    while (offset < samples.size) {
                        currentCoroutineContext().ensureActive()
                        block.clear()
                        repeat(minOf(block.capacity() / 4, samples.size - offset)) { block.putFloat(samples[offset++]) }
                        stream.write(block.array(), 0, block.position())
                    }
                }
                process = ProcessBuilder(runner.absolutePath, "--model", model.absolutePath,
                    "--audio", audio.absolutePath, "--threads", "2")
                    .redirectOutput(output).redirectError(File("/dev/null")).start()
                while (process.isAlive) delay(25)
                check(process.exitValue() == 0) { "Whistle could not transcribe this recording" }
                check(output.length() in 1..1_048_576) { "Whistle result is missing or too large" }
                parseWhistleResult(output.readText(Charsets.UTF_8))
            } finally {
                process?.let {
                    if (it.isAlive) it.destroyForcibly()
                    it.waitFor()
                }
                audio.delete()
                output.delete()
                directory.delete()
            }
        }
    }

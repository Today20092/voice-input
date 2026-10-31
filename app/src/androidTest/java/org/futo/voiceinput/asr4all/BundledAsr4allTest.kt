package org.futo.voiceinput.asr4all

import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.futo.voiceinput.ENGLISH_MODELS
import org.futo.voiceinput.recognition.RecognitionModelLifecycle
import org.futo.voiceinput.recognition.RecognitionModelSelection
import org.futo.voiceinput.settings.SPEECH_BACKEND
import org.futo.voiceinput.sha256
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BundledAsr4allTest {
    private fun withFreshStorage(test: (ContextWrapper) -> Unit) {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.cacheDir, "bundled-asr4all-test-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getCodeCacheDir() = File(root, "code-cache").apply { mkdirs() }
        }
        try { test(context) } finally { root.deleteRecursively() }
    }

    @Test fun freshInstallTranscribesOfflineWithoutDownloadingOrWhisperAssets() = withFreshStorage { context ->
        runBlocking {
            val lifecycle = RecognitionModelLifecycle.create(context.filesDir, false)
            val selection = RecognitionModelSelection(SPEECH_BACKEND.default)
            assertEquals("asr4all-m", lifecycle.readiness(selection)?.model?.id)
            assertTrue(requireNotNull(lifecycle.readiness(selection)).isReady)
            assertFalse(ENGLISH_MODELS.first().ggml.is_builtin_asset)
            assertFalse(context.assets.list("")!!.contains("tiny_en_acft_q8_0.bin.not.tflite"))
            val bytes = InstrumentationRegistry.getInstrumentation().context.assets.open("jfk.wav").use { it.readBytes() }
            val pcm = ByteBuffer.wrap(bytes, 78, bytes.size - 78).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            val backend = asr4allBackend(context, "m")
            try {
                backend.load(context)
                val transcript = backend.transcribe(FloatArray(pcm.remaining()) { pcm.get() / 32768f }).lowercase()
                assertTrue(transcript.contains("ask not what your country can do for you"))
                assertTrue(transcript.contains("what you can do for your country"))
            } finally { backend.close() }
            assertTrue(context.filesDir.listFiles()!!.isEmpty())
        }
    }

    @Test fun reclaimedAndCorruptCachesAreRestoredFromThePinnedAssets() = withFreshStorage { context ->
        val model = Asr4allModels.bundled
        var directory = BundledAsr4all.directory(context)
        File(directory, model.artifacts.first().name).writeText("corrupt")
        directory = BundledAsr4all.directory(context)
        model.artifacts.forEach { assertEquals(it.sha256, sha256(File(directory, it.name))) }
        assertTrue(context.codeCacheDir.deleteRecursively())
        directory = BundledAsr4all.directory(context)
        model.artifacts.forEach { assertEquals(it.sha256, sha256(File(directory, it.name))) }
    }
}

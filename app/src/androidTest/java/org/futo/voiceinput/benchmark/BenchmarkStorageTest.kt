package org.futo.voiceinput.benchmark

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BenchmarkStorageTest {
    @Test fun failedImportRemovesItsPrivateCopyAndPreservesTheSource() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val root = File(context.cacheDir, "benchmark-import-test-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        try {
            val source = File(root, "source.wav").apply { writeBytes("RIFF".toByteArray()) }
            val destination = File(root, "private-copy.original")
            try {
                BenchmarkAudio.import(context, android.net.Uri.fromFile(source), destination)
                fail("Corrupt recording accepted")
            } catch (_: IllegalArgumentException) { }
            assertFalse(destination.exists())
            assertEquals("RIFF", source.readText())
        } finally { root.deleteRecursively() }
    }

    @Test fun canonicalAudioAndReportsSurviveRestartAndInterruption() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val root = File(context.cacheDir, "benchmark-test-${java.util.UUID.randomUUID()}")
        try {
            val store = BenchmarkStore(root)
            val report = BenchmarkReport(clips = emptyList(), selectedModelIds = listOf("test"), installedBefore = emptyList())
            val samples = floatArrayOf(-1f, 0f, 0.5f)
            val hash = store.saveAudio(report.runId, "test", samples)
            val clip = BenchmarkClip("test", "source", hash, samples.size, "Hello", "reference")
            val withClip = report.copy(clips = listOf(clip), status = "running")
            withClip.cases.add(BenchmarkCaseResult("test"))
            store.save(withClip)
            val reopened = BenchmarkStore(root)
            val recovered = reopened.recoverInterrupted().single()
            assertEquals("interrupted", recovered.status)
            assertEquals("interrupted", recovered.cases.single().status)
            assertArrayEquals(samples, reopened.readAudio(report.runId, clip), 0f)
            assertTrue(reopened.markdown(recovered).contains("Hello"))
            val file = reopened.audioFile(report.runId, "test")
            file.writeBytes(ByteArray(samples.size * 4))
            try { reopened.readAudio(report.runId, clip); fail("Modified audio must be rejected") } catch (_: IllegalStateException) { }
        } finally { root.deleteRecursively() }
    }

    @Test fun wavStereoDownmixAndResamplingAreBounded() = runBlocking {
        val data = ByteBuffer.allocate(44 + 8000 * 4).order(ByteOrder.LITTLE_ENDIAN)
        data.put("RIFF".toByteArray()).putInt(data.capacity() - 8).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(2).putInt(8000).putInt(32000).putShort(4).putShort(16)
            .put("data".toByteArray()).putInt(8000 * 4)
        repeat(8000) { data.putShort(16384).putShort(0) }
        val samples = BenchmarkAudio.decodeWav(data.array())
        assertEquals(16000, samples.size)
        assertEquals(0.25f, samples[8000], 0.0001f)
        try { BenchmarkAudio.decodeWav(byteArrayOf(1, 2)); fail("Corrupt WAV accepted") } catch (_: IllegalArgumentException) { }
    }
}

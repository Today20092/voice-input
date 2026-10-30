package org.futo.voiceinput.downloader

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NestedDownloadTest {
    @Test
    fun nemotronNestedArtifactsCompleteTheDownload() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.filesDir, "nested-download-regression")
        directory.deleteRecursively()
        // Reproduce the nested paths from the 1.4.5 manifest; current Nemotron needs no test audio.
        val names = listOf("tokens.txt", "test_wavs/en.wav", "test_wavs/ja.wav")
        val hash = "ec654fac9599f62e79e2706abef23dfb7c07c08185aa86db4d8695f0b718d1b3"
        val intent = Intent(context, DownloadActivity::class.java).apply {
            putStringArrayListExtra(EXTRA_DOWNLOAD_FILE_NAMES, ArrayList(names))
            putStringArrayListExtra(EXTRA_DOWNLOAD_FILE_URLS,
                ArrayList(names.map { "https://example.com/$it" }))
            putStringArrayListExtra(EXTRA_DOWNLOAD_FILE_HASHES, ArrayList(names.map { hash }))
            putExtra(EXTRA_DOWNLOAD_FILE_SIZES, LongArray(names.size) { 5L })
            putExtra(EXTRA_TARGET_SUBDIR, directory.name)
            putExtra(EXTRA_COMPLETION_MARKER, ".download_complete")
            putExtra(EXTRA_MODEL_ID, "nested-download-regression")
            putExtra(EXTRA_MODEL_VERSION, "1")
        }
        val responses = CountDownLatch(names.size)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body("valid".toResponseBody()).build()
                .also { responses.countDown() }
        }.build()
        try {
            ActivityScenario.launch<DownloadActivity>(intent).use { scenario ->
                // Wait for onCreate's asynchronous scan before starting the real downloader.
                val pending = DownloadActivity::class.java.getDeclaredField("modelsToDownload")
                    .apply { isAccessible = true }
                val ready = CountDownLatch(1)
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                while (ready.count > 0 && System.nanoTime() < deadline) {
                    scenario.onActivity {
                        if ((pending.get(it) as List<*>).size == names.size) ready.countDown()
                    }
                    ready.await(20, TimeUnit.MILLISECONDS)
                }
                assertEquals("Downloader did not initialize", 0L, ready.count)
                scenario.onActivity { activity ->
                    DownloadActivity::class.java.getDeclaredField("httpClient")
                        .apply { isAccessible = true }.set(activity, client)
                    DownloadActivity::class.java.getDeclaredMethod("startDownload")
                        .apply { isAccessible = true }.invoke(activity)
                }
                assertTrue("Not all artifacts were requested", responses.await(5, TimeUnit.SECONDS))
                val marker = File(directory, ".download_complete")
                val finished = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                while (!marker.exists() && System.nanoTime() < finished) Thread.sleep(20)
                names.forEach { name ->
                    val file = File(directory, name)
                    assertTrue("Missing artifact: $name", file.isFile)
                    assertEquals("valid", file.readText())
                }
                assertEquals("nested-download-regression@1", marker.readText())
            }
        } finally {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
            directory.deleteRecursively()
        }
    }
}

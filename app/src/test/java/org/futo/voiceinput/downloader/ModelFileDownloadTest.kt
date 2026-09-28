package org.futo.voiceinput.downloader

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.security.MessageDigest
import java.util.Collections

class ModelFileDownloadTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun interruptedLargeFileResumesSavedRangesAndSkipsValidatedTarget() = runBlocking {
        val bytes = ByteArray(33 * 1024 * 1024) { (it % 251).toByte() }
        val requests = Collections.synchronizedList(mutableListOf<String>())
        var interrupt = true
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val range = requireNotNull(chain.request().header("Range"))
            requests += range
            val (start, end) = range.removePrefix("bytes=").split('-').map { it.toInt() }
            val buffer = Buffer().write(bytes, start, if (interrupt) 256 * 1024 else end - start + 1)
            val fail = interrupt
            val source = object : ForwardingSource(buffer) {
                override fun read(sink: Buffer, byteCount: Long): Long {
                    if (fail && buffer.size == 0L) throw IOException("Interrupted")
                    return super.read(sink, byteCount)
                }
            }.buffer()
            val body = object : ResponseBody() {
                override fun contentType() = null
                override fun contentLength() = (end - start + 1).toLong()
                override fun source() = source
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(206).message("OK").header("Content-Range", "bytes $start-$end/${bytes.size}")
                .body(body).build()
        }.build()
        val target = temporary.root.resolve("encoder.onnx")
        val hash = hash(bytes)
        try {
            downloadModelFile(client, "https://example.com/model", target, bytes.size.toLong(), hash)
            fail("Expected interrupted transfer")
        } catch (_: IOException) { }
        assertFalse(target.exists())
        val checkpoints = downloadRanges(bytes.size.toLong()).mapIndexed { index, range ->
            val checkpoint = temporary.root.resolve("encoder.onnx.download.range$index")
            val saved = if (checkpoint.exists()) checkpoint.readText().toLong() else 0L
            "bytes=${range.start + saved}-${range.endInclusive}"
        }
        assertTrue("At least one range must retain transferred data", checkpoints.any {
            it !in downloadRanges(bytes.size.toLong()).map { range -> "bytes=${range.start}-${range.endInclusive}" }
        })
        requests.clear()
        interrupt = false
        downloadModelFile(client, "https://example.com/model", target, bytes.size.toLong(), hash)
        assertEquals(checkpoints.toSet(), requests.toSet())
        assertArrayEquals(bytes, target.readBytes())
        requests.clear()
        downloadModelFile(client, "https://example.com/model", target, bytes.size.toLong(), hash)
        assertTrue("Validated files must not be requested again", requests.isEmpty())
    }

    @Test fun ignoredRangeExplainsRestartAndNeverAppendsFullResponse() = runBlocking {
        val bytes = "complete model".toByteArray()
        val target = temporary.root.resolve("model.bin")
        temporary.root.resolve("model.bin.download").writeBytes(bytes.copyOfRange(0, 4))
        temporary.root.resolve("model.bin.download.range0").writeText("4")
        val requests = mutableListOf<String?>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request().header("Range")
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(bytes.toResponseBody()).build()
        }.build()
        var restarts = 0
        downloadModelFile(client, "https://example.com/model", target, bytes.size.toLong(), hash(bytes),
            onRestart = { restarts++ })
        assertEquals(1, restarts)
        assertEquals(listOf("bytes=4-13", null), requests)
        assertArrayEquals(bytes, target.readBytes())
    }

    @Test fun interruptedFallbackResumesItsContiguousPartialFile() = runBlocking {
        val bytes = "complete model".toByteArray()
        val target = temporary.root.resolve("model.bin")
        val requests = mutableListOf<String?>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val range = chain.request().header("Range")
            requests += range
            val response = Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).message("OK")
            when (requests.size) {
                1 -> response.code(200).body(bytes.toResponseBody()).build()
                2 -> {
                    val buffer = Buffer().write(bytes, 0, 4)
                    val source = object : ForwardingSource(buffer) {
                        override fun read(sink: Buffer, byteCount: Long): Long {
                            if (buffer.size == 0L) throw IOException("Disconnected")
                            return super.read(sink, byteCount)
                        }
                    }.buffer()
                    response.code(200).body(object : ResponseBody() {
                        override fun contentType() = null
                        override fun contentLength() = bytes.size.toLong()
                        override fun source() = source
                    }).build()
                }
                else -> response.code(206).header("Content-Range", "bytes 4-13/14")
                    .body(bytes.copyOfRange(4, bytes.size).toResponseBody()).build()
            }
        }.build()
        try {
            downloadModelFile(client, "https://example.com/model", target, 14, hash(bytes))
            fail("Expected disconnect")
        } catch (_: IOException) { }
        assertEquals(4L, temporary.root.resolve("model.bin.download.sequential").length())
        downloadModelFile(client, "https://example.com/model", target, 14, hash(bytes))
        assertEquals(listOf("bytes=0-13", null, "bytes=4-"), requests)
        assertArrayEquals(bytes, target.readBytes())
    }

    @Test fun rejectsInvalidResponsesWithoutReplacingExistingTarget() = runBlocking {
        val bytes = "good".toByteArray()
        for (failure in listOf("range", "length", "short", "oversized", "hash")) {
            val target = temporary.root.resolve("$failure.bin").apply { writeText("previous model") }
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                val bodyBytes = when (failure) {
                    "short" -> "go".toByteArray()
                    "oversized" -> "goodbad".toByteArray()
                    "hash" -> "evil".toByteArray()
                    else -> bytes
                }
                val buffer = Buffer().write(bodyBytes)
                val body = object : ResponseBody() {
                    override fun contentType() = null
                    override fun contentLength() = if (failure == "length") 5L else -1L
                    override fun source() = buffer
                }
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(206).message("OK")
                    .header("Content-Range", if (failure == "range") "bytes 1-3/4" else "bytes 0-3/4")
                    .body(body).build()
            }.build()
            try {
                downloadModelFile(client, "https://example.com/model", target, 4, hash(bytes))
                fail("Expected rejection: $failure")
            } catch (_: IOException) { }
            assertEquals("previous model", target.readText())
        }
    }

    @Test fun changedArtifactCannotReuseSavedOffsets() = runBlocking {
        val target = temporary.root.resolve("model.bin")
        temporary.root.resolve("model.bin.download").writeText("old!")
        temporary.root.resolve("model.bin.download.range0").writeText("4")
        temporary.root.resolve("model.bin.download.identity").writeText("old identity")
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            assertEquals("bytes=0-3", chain.request().header("Range"))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(206).message("OK")
                .header("Content-Range", "bytes 0-3/4").body("new!".toResponseBody()).build()
        }.build()
        downloadModelFile(client, "https://example.com/new", target, 4, hash("new!".toByteArray()))
        assertEquals("new!", target.readText())
    }

    @Test fun simultaneousRetriesUseOneWriterAndOneRequest() = runBlocking {
        val requests = Collections.synchronizedList(mutableListOf<String?>())
        val target = temporary.root.resolve("model.bin")
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request().header("Range")
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(206).message("OK")
                .header("Content-Range", "bytes 0-3/4").body("good".toResponseBody()).build()
        }.build()
        val first = async(Dispatchers.IO) {
            downloadModelFile(client, "https://example.com/model", target, 4, hash("good".toByteArray()))
        }
        val second = async(Dispatchers.IO) {
            downloadModelFile(client, "https://example.com/model", target, 4, hash("good".toByteArray()))
        }
        first.await()
        second.await()
        assertEquals(listOf("bytes=0-3"), requests)
        assertEquals("good", target.readText())
    }

    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}

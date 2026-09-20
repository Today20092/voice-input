package org.futo.voiceinput.downloader

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.futo.voiceinput.recognition.RecognitionModelArtifact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.futo.voiceinput.parakeet.OrukeetModel
import org.futo.voiceinput.recognition.RecognitionModelStore
import java.io.File
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.Protocol
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.assertTrue

class ModelArchiveTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun resumesInterruptedArchiveWithoutDownloadingSavedBytesAgain() {
        val contents = ByteArray(65536).also { java.util.Random(42).nextBytes(it) }
        val bytes = archive("model/model.bin", contents)
        val halfway = bytes.size / 2
        val requests = mutableListOf<String?>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val range = chain.request().header("Range")
            requests += range
            val start = range?.removePrefix("bytes=")?.removeSuffix("-")?.toInt() ?: 0
            val body = if (requests.size == 1) {
                val buffer = Buffer().write(bytes, 0, halfway)
                val interrupted = object : ForwardingSource(buffer) {
                    override fun read(sink: Buffer, byteCount: Long): Long {
                        if (buffer.size == 0L) throw IOException("Connection interrupted")
                        return super.read(sink, byteCount)
                    }
                }.buffer()
                object : ResponseBody() {
                    override fun contentType() = null
                    override fun contentLength() = bytes.size.toLong()
                    override fun source() = interrupted
                }
            } else bytes.copyOfRange(start, bytes.size).toResponseBody()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(if (range == null) 200 else 206).message("OK")
                .header("Content-Range", "bytes $start-${bytes.lastIndex}/${bytes.size}")
                .body(body).build()
        }.build()
        val url = "https://example.com/model"
        val artifact = RecognitionModelArtifact("model.bin", url, contents.size.toLong(), sha256(contents))
        val archive = RecognitionModelArtifact("model.tar.bz2", url, bytes.size.toLong(), sha256(bytes))
        val saved = temporaryFolder.root.resolve("archive.download")
        val target = temporaryFolder.root.resolve("model")
        assertThrows(IOException::class.java) {
            downloadModelArchive(client, archive, saved, target, "model", listOf(artifact))
        }
        assertTrue("Interrupted downloads must retain compressed bytes", saved.length() > 0)
        val retained = saved.length()
        downloadModelArchive(client, archive, saved, target, "model", listOf(artifact))
        assertEquals(listOf(null, "bytes=$retained-"), requests)
        org.junit.Assert.assertArrayEquals(contents, target.resolve("model.bin").readBytes())
        assertTrue("Successful installation removes the saved archive", !saved.exists())
    }

    @Test
    fun rejectsWrongRangesAndSafelyRestartsWhenRangeIsIgnored() {
        val bytes = archive("model/model.bin", "valid".toByteArray())
        val saved = temporaryFolder.root.resolve("archive.download")
        saved.writeBytes(bytes.copyOfRange(0, 10))
        val artifact = RecognitionModelArtifact("model.bin", "https://example.com/model", 5, sha256("valid".toByteArray()))
        val archive = artifact.copy(name = "model.tar.bz2", sizeBytes = bytes.size.toLong(), sha256 = sha256(bytes))
        val target = temporaryFolder.root.resolve("model")
        for (code in listOf(206, 200)) {
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                assertEquals("bytes=10-", chain.request().header("Range"))
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(code).message("OK").body(bytes.toResponseBody())
                    .header("Content-Range", "bytes 0-${bytes.lastIndex}/${bytes.size}").build()
            }.build()
            if (code == 206) {
                assertThrows(IOException::class.java) {
                    downloadModelArchive(client, archive, saved, target, "model", listOf(artifact))
                }
                assertEquals(10L, saved.length())
            } else {
                downloadModelArchive(client, archive, saved, target, "model", listOf(artifact))
                assertEquals("valid", target.resolve("model.bin").readText())
            }
        }
    }

    @Test
    fun retainsCompleteArchiveAfterInstallFailureAndRetriesOffline() {
        val bytes = archive("model/model.bin", "valid".toByteArray())
        val saved = temporaryFolder.root.resolve("archive.download").apply { writeBytes(bytes) }
        val target = temporaryFolder.root.resolve("model")
        val artifact = RecognitionModelArtifact("model.bin", "https://example.com/model", 5, sha256("valid".toByteArray()))
        val archive = artifact.copy(name = "model.tar.bz2", sizeBytes = bytes.size.toLong(), sha256 = sha256(bytes))
        val client = OkHttpClient.Builder().addInterceptor { error("Saved archive must not be downloaded again") }.build()
        assertThrows(IllegalArgumentException::class.java) {
            downloadModelArchive(client, archive, saved, target, "model", listOf(artifact.copy(sha256 = "0".repeat(64))))
        }
        assertEquals(bytes.size.toLong(), saved.length())
        downloadModelArchive(client, archive, saved, target, "model", listOf(artifact))
        assertEquals("valid", target.resolve("model.bin").readText())
        assertTrue(!saved.exists())

        saved.writeBytes(bytes.copyOf().apply { this[0] = (this[0].toInt() xor 1).toByte() })
        assertThrows(IOException::class.java) {
            downloadModelArchive(client, archive, saved, target, "model", listOf(artifact))
        }
        assertTrue("Corrupt archives must not be reused", !saved.exists())
        assertEquals("valid", target.resolve("model.bin").readText())
    }

    @Test
    fun buffersCompressedReadsInsteadOfHashingOneByteAtATime() {
        val contents = ByteArray(65536).also { java.util.Random(42).nextBytes(it) }
        val bytes = archive("model/model.bin", contents)
        var singleByteReads = 0
        val input = object : ByteArrayInputStream(bytes) {
            override fun read(): Int {
                singleByteReads++
                return super.read()
            }
        }
        extractModelArchive(input, temporaryFolder.root.resolve("buffered"), "model",
            listOf(RecognitionModelArtifact("model.bin", "https://example.com/model",
                contents.size.toLong(), sha256(contents))), sha256(bytes))
        assertEquals("Compressed input should be read and hashed in blocks", 0, singleByteReads)
    }

    @Test
    fun installsPublishedOrukeetArchive() {
        val path = System.getenv("ORUKEET_TEST_ARCHIVE")
        assumeTrue("Set ORUKEET_TEST_ARCHIVE to the pinned download", path != null)
        val model = OrukeetModel.recognitionModel
        val archive = File(requireNotNull(path))
        val store = RecognitionModelStore(temporaryFolder.root)
        archive.inputStream().buffered().use { input ->
            extractModelArchive(input, store.modelDirectory(model), requireNotNull(model.archiveRoot),
                model.artifacts, requireNotNull(model.archive).sha256)
        }
        org.junit.Assert.assertTrue(store.completeInstall(model))
    }

    @Test
    fun validatesArchiveBeforeReplacingInstalledFiles() {
        val archive = archive("model/model.bin", "valid".toByteArray())
        val target = temporaryFolder.newFolder("model")
        target.resolve("model.bin").writeText("working")
        val artifact = RecognitionModelArtifact(
            name = "model.bin",
            url = "https://example.com/model.tar.bz2",
            sizeBytes = 5,
            sha256 = sha256("valid".toByteArray())
        )

        assertThrows(IllegalArgumentException::class.java) {
            extractModelArchive(
                ByteArrayInputStream(archive),
                target,
                "model",
                listOf(artifact),
                "0".repeat(64)
            )
        }
        assertEquals("working", target.resolve("model.bin").readText())

        extractModelArchive(
            ByteArrayInputStream(archive),
            target,
            "model",
            listOf(artifact),
            sha256(archive)
        )
        assertEquals("valid", target.resolve("model.bin").readText())
    }

    @Test
    fun validatesPaddedTarArchivesThroughTheCompressedTrailer() {
        val bytes = archive("model/model.bin", "valid".toByteArray(), blockSize = 10240)
        val target = temporaryFolder.newFolder("padded")
        extractModelArchive(
            ByteArrayInputStream(bytes), target, "model",
            listOf(RecognitionModelArtifact("model.bin", "https://example.com/model.tar.bz2",
                5, sha256("valid".toByteArray()))), sha256(bytes)
        )
        assertEquals("valid", target.resolve("model.bin").readText())
    }

    private fun archive(name: String, contents: ByteArray, blockSize: Int = 512): ByteArray {
        val output = ByteArrayOutputStream()
        BZip2CompressorOutputStream(output).use { compressed ->
            TarArchiveOutputStream(compressed, blockSize).use { tar ->
                val entry = TarArchiveEntry(name).apply { size = contents.size.toLong() }
                tar.putArchiveEntry(entry)
                tar.write(contents)
                tar.closeArchiveEntry()
            }
        }
        return output.toByteArray()
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}

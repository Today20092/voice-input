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

class ModelArchiveTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

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

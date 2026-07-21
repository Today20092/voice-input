package org.futo.voiceinput.downloader

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.futo.voiceinput.sha256
import org.futo.voiceinput.recognition.RecognitionModelArtifact
import java.io.File
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest

internal fun extractModelArchive(
    input: InputStream,
    targetDirectory: File,
    archiveRoot: String,
    artifacts: List<RecognitionModelArtifact>,
    expectedArchiveSha256: String
) {
    val digest = MessageDigest.getInstance("SHA-256")
    val parent = requireNotNull(targetDirectory.parentFile)
    val stagingDirectory = File(parent, ".${targetDirectory.name}.download")
    val previousDirectory = File(parent, ".${targetDirectory.name}.previous")
    val temporaryFiles = artifacts.associateWith {
        File(stagingDirectory, it.name)
    }
    val extracted = mutableSetOf<RecognitionModelArtifact>()

    if (previousDirectory.exists() && !targetDirectory.exists()) {
        check(previousDirectory.renameTo(targetDirectory)) { "Failed to restore interrupted model update" }
    }
    stagingDirectory.deleteRecursively()
    check(stagingDirectory.mkdirs()) { "Failed to create model staging directory" }
    try {
        DigestInputStream(input, digest).use { verifiedInput ->
            TarArchiveInputStream(BZip2CompressorInputStream(verifiedInput)).use { archive ->
                while (true) {
                    val entry = archive.nextEntry ?: break
                    val relativeName = entry.name.removePrefix("$archiveRoot/")
                    val artifact = artifacts.firstOrNull { it.name == relativeName } ?: continue
                    require(!entry.isDirectory) { "Expected ${artifact.name} to be a file" }
                    temporaryFiles.getValue(artifact).outputStream().use { output ->
                        archive.copyTo(output)
                    }
                    extracted += artifact
                }
            }
        }

        require(digest.hex() == expectedArchiveSha256) { "Downloaded archive checksum mismatch" }
        require(extracted.size == artifacts.size) { "Downloaded archive is missing model files" }
        artifacts.forEach { artifact ->
            val file = temporaryFiles.getValue(artifact)
            require(file.length() == artifact.sizeBytes) { "Unexpected size for ${artifact.name}" }
            require(sha256(file) == artifact.sha256) { "Checksum mismatch for ${artifact.name}" }
        }
        previousDirectory.deleteRecursively()
        val hadPreviousInstall = targetDirectory.exists()
        if (hadPreviousInstall) {
            check(targetDirectory.renameTo(previousDirectory)) { "Failed to preserve installed model" }
        }
        try {
            check(stagingDirectory.renameTo(targetDirectory)) { "Failed to install model" }
        } catch (error: Throwable) {
            if (hadPreviousInstall) {
                targetDirectory.deleteRecursively()
                check(previousDirectory.renameTo(targetDirectory)) { "Failed to restore installed model" }
            }
            throw error
        }
        previousDirectory.deleteRecursively()
    } catch (error: Throwable) {
        stagingDirectory.deleteRecursively()
        throw error
    }
}

private fun MessageDigest.hex() = digest().joinToString("") { "%02x".format(it) }

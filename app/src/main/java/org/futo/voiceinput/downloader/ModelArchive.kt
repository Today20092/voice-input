package org.futo.voiceinput.downloader

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.CompressorStreamFactory
import org.futo.voiceinput.sha256
import org.futo.voiceinput.recognition.RecognitionModelArtifact
import java.io.File
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import okhttp3.OkHttpClient

internal fun downloadModelArchive(
    client: OkHttpClient,
    archive: RecognitionModelArtifact,
    savedArchive: File,
    targetDirectory: File,
    archiveRoot: String,
    artifacts: List<RecognitionModelArtifact>,
    onRestart: () -> Unit = {},
    onInstallationProgress: (Float) -> Unit = {},
    onProgress: (Long) -> Unit = {}
) {
    require(archive.sizeBytes > 0)
    downloadSequentialFile(client, archive.url, savedArchive, archive.sizeBytes, archive.sha256,
        onRestart, onProgress)
    savedArchive.inputStream().use { input ->
        extractModelArchive(input, targetDirectory, archiveRoot, artifacts, archive.sha256, onInstallationProgress)
    }
    savedArchive.delete()
}

internal fun extractModelArchive(
    input: InputStream,
    targetDirectory: File,
    archiveRoot: String,
    artifacts: List<RecognitionModelArtifact>,
    expectedArchiveSha256: String,
    onInstallationProgress: (Float) -> Unit = {}
) {
    val digest = MessageDigest.getInstance("SHA-256")
    val parent = requireNotNull(targetDirectory.parentFile)
    val stagingDirectory = File(parent, ".${targetDirectory.name}.download")
    val previousDirectory = File(parent, ".${targetDirectory.name}.previous")
    val temporaryFiles = artifacts.associateWith {
        File(stagingDirectory, it.name)
    }
    val extracted = mutableSetOf<RecognitionModelArtifact>()
    val totalBytes = artifacts.sumOf { it.sizeBytes }
    require(totalBytes > 0)
    var installedBytes = 0L

    if (previousDirectory.exists() && !targetDirectory.exists()) {
        check(previousDirectory.renameTo(targetDirectory)) { "Failed to restore interrupted model update" }
    }
    stagingDirectory.deleteRecursively()
    check(stagingDirectory.mkdirs()) { "Failed to create model staging directory" }
    try {
        onInstallationProgress(0f)
        // Buffer outside the digest so format detection and byte reads hash each block once.
        DigestInputStream(input, digest).buffered(128 * 1024).use { verifiedInput ->
            TarArchiveInputStream(CompressorStreamFactory().createCompressorInputStream(verifiedInput)).use { archive ->
                while (true) {
                    val entry = archive.nextEntry ?: break
                    val relativeName = entry.name.removePrefix("$archiveRoot/")
                    val artifact = artifacts.firstOrNull { it.name == relativeName } ?: continue
                    require(!entry.isDirectory) { "Expected ${artifact.name} to be a file" }
                    require(entry.size == artifact.sizeBytes) { "Unexpected size for ${artifact.name}" }
                    require(artifact !in extracted) { "Duplicate model file in archive" }
                    temporaryFiles.getValue(artifact).outputStream().use { output ->
                        val buffer = ByteArray(128 * 1024)
                        while (true) {
                            val read = archive.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            installedBytes += read
                            onInstallationProgress(installedBytes.toFloat() / totalBytes / 2)
                        }
                    }
                    extracted += artifact
                }
                // Tar EOF can precede the compressed trailer and padding. Hash the whole download.
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (verifiedInput.read(buffer) != -1) { }
            }
        }

        require(digest.hex() == expectedArchiveSha256) { "Downloaded archive checksum mismatch" }
        require(extracted.size == artifacts.size) { "Downloaded archive is missing model files" }
        artifacts.forEach { artifact ->
            val file = temporaryFiles.getValue(artifact)
            require(file.length() == artifact.sizeBytes) { "Unexpected size for ${artifact.name}" }
            require(sha256(file) == artifact.sha256) { "Checksum mismatch for ${artifact.name}" }
            installedBytes += artifact.sizeBytes
            onInstallationProgress((installedBytes.toFloat() / totalBytes / 2).coerceAtMost(0.99f))
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
        onInstallationProgress(1f)
    } catch (error: Throwable) {
        stagingDirectory.deleteRecursively()
        throw error
    }
}

private fun MessageDigest.hex() = digest().joinToString("") { "%02x".format(it) }

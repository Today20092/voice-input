package org.futo.voiceinput.downloader

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.futo.voiceinput.sha256
import org.futo.voiceinput.recognition.RecognitionModelArtifact
import java.io.File
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

internal fun downloadModelArchive(
    client: OkHttpClient,
    archive: RecognitionModelArtifact,
    savedArchive: File,
    targetDirectory: File,
    archiveRoot: String,
    artifacts: List<RecognitionModelArtifact>,
    onProgress: (Long) -> Unit = {}
) {
    require(archive.sizeBytes > 0)
    savedArchive.parentFile?.mkdirs()
    if (savedArchive.length() > archive.sizeBytes) {
        check(savedArchive.delete()) { "Failed to discard oversized archive" }
    }
    var downloaded = savedArchive.length()
    onProgress(downloaded)
    if (downloaded < archive.sizeBytes) {
        val request = Request.Builder().url(archive.url).header("Accept-Encoding", "identity")
        if (downloaded > 0) request.header("Range", "bytes=$downloaded-")
        client.newCall(request.build()).execute().use { response ->
            val append = response.code == 206
            if (append) {
                val expectedRange = "bytes $downloaded-${archive.sizeBytes - 1}/${archive.sizeBytes}"
                if (response.header("Content-Range") != expectedRange) {
                    throw IOException("Server returned an unexpected download range")
                }
            } else if (response.code == 200) {
                // Servers may ignore Range. Replace the partial file rather than append a full response.
                downloaded = 0
            } else {
                throw IOException("HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("Empty response")
            val expectedBytes = archive.sizeBytes - downloaded
            if (body.contentLength() >= 0 && body.contentLength() != expectedBytes) {
                throw IOException("Downloaded archive size mismatch")
            }
            body.byteStream().use { input ->
                java.io.FileOutputStream(savedArchive, append).use { output ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        if (read > archive.sizeBytes - downloaded) {
                            throw IOException("Downloaded archive exceeds expected size")
                        }
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded)
                    }
                }
            }
        }
    }
    if (savedArchive.length() != archive.sizeBytes) throw IOException("Archive download ended early")
    if (sha256(savedArchive) != archive.sha256) {
        check(savedArchive.delete()) { "Failed to discard corrupt archive" }
        throw IOException("Downloaded archive checksum mismatch; retry to download a fresh copy")
    }
    savedArchive.inputStream().use { input ->
        extractModelArchive(input, targetDirectory, archiveRoot, artifacts, archive.sha256)
    }
    savedArchive.delete()
}

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
        // Buffer outside the digest so bzip2's byte reads hash whole blocks, not individual bytes.
        DigestInputStream(input, digest).buffered(128 * 1024).use { verifiedInput ->
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

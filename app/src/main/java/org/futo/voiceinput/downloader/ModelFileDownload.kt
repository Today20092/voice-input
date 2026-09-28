package org.futo.voiceinput.downloader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import org.futo.voiceinput.sha256
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

// ponytail: serialize installations; use per-target locks if concurrent model installs are needed.
internal val modelDownloadMutex = Mutex()

private class RangeUnsupported : IOException("Server does not support download ranges")

internal fun validModelFile(file: File, size: Long?, hash: String?): Boolean =
    file.isFile && file.length() > 0 && (size == null || file.length() == size) &&
        (hash == null || sha256(file) == hash)

internal suspend fun downloadModelFile(
    client: OkHttpClient,
    url: String,
    target: File,
    size: Long,
    hash: String,
    onRestart: () -> Unit = {},
    onProgress: (Long) -> Unit = {}
) = modelDownloadMutex.withLock {
    require(size > 0 && hash.isNotBlank())
    coroutineContext.ensureActive()
    if (validModelFile(target, size, hash)) return@withLock
    val file = File(target.path + ".download")
    val identity = File(file.path + ".identity")
    val sequential = File(file.path + ".sequential")
    val expectedIdentity = "$url\n$size\n$hash"
    val ranges = downloadRanges(size, if (size >= 32L * 1024 * 1024) 4 else 1)
    val checkpoints = ranges.indices.map { File(file.path + ".range$it") }
    file.parentFile?.mkdirs()
    // Older releases have no identity sidecar. Their checkpoints remain usable because
    // the complete artifact must still pass the current manifest's size and hash.
    if (file.length() > size ||
        (identity.exists() && identity.readText() != expectedIdentity)) {
        onRestart()
        if (file.exists() && !file.delete()) throw IOException("Cannot reset partial download")
        if (sequential.exists() && !sequential.delete()) throw IOException("Cannot reset partial download")
        checkpoints.forEach { if (it.exists() && !it.delete()) throw IOException("Cannot reset download progress") }
    }
    if (!file.exists()) checkpoints.forEach { it.delete() }
    identity.writeText(expectedIdentity)
    val saved = checkpoints.mapIndexed { index, checkpoint ->
        val count = if (checkpoint.isFile) checkpoint.readText().toLongOrNull() ?: 0L else 0L
        count.takeIf { it in 0..ranges[index].size &&
            (it == 0L || ranges[index].start + it <= file.length()) } ?: 0L
    }
    val completed = AtomicLong(saved.sum())
    onProgress(completed.get())
    try {
      if (sequential.exists()) throw RangeUnsupported()
      coroutineScope {
        ranges.mapIndexed { index, range ->
            async(Dispatchers.IO) {
                var downloaded = saved[index]
                if (downloaded == range.size) return@async
                ensureActive()
                val start = range.start + downloaded
                val request = Request.Builder().url(url).header("Accept-Encoding", "identity")
                    .header("Range", "bytes=$start-${range.endInclusive}").build()
                client.newCall(request).execute().use { response ->
                    if (response.code == 200) throw RangeUnsupported()
                    if (response.code != 206 ||
                        response.header("Content-Range") != "bytes $start-${range.endInclusive}/$size") {
                        throw IOException("Server returned an unexpected download range")
                    }
                    val body = response.body ?: throw IOException("Empty download response")
                    val remaining = range.size - downloaded
                    if (body.contentLength() >= 0 && body.contentLength() != remaining) {
                        throw IOException("Download response length mismatch")
                    }
                    RandomAccessFile(file, "rw").use { output ->
                        output.seek(start)
                        try {
                            body.byteStream().use { input ->
                                val buffer = ByteArray(128 * 1024)
                                var lastSaved = downloaded
                                while (downloaded < range.size) {
                                    ensureActive()
                                    val read = input.read(buffer, 0, minOf(buffer.size.toLong(), range.size - downloaded).toInt())
                                    if (read < 0) throw IOException("Download ended early")
                                    output.write(buffer, 0, read)
                                    downloaded += read
                                    onProgress(completed.addAndGet(read.toLong()))
                                    if (downloaded - lastSaved >= 1024 * 1024) {
                                        output.fd.sync()
                                        checkpoints[index].writeText(downloaded.toString())
                                        lastSaved = downloaded
                                    }
                                }
                                if (input.read() != -1) throw IOException("Download exceeded expected size")
                            }
                        } finally {
                            output.fd.sync()
                            checkpoints[index].writeText(downloaded.toString())
                        }
                    }
                }
            }
        }.awaitAll()
      }
    } catch (_: RangeUnsupported) {
        coroutineContext.ensureActive()
        if (!sequential.exists()) onRestart()
        val context = coroutineContext
        downloadSequentialFile(client, url, sequential, size, hash, onRestart) {
            context.ensureActive()
            onProgress(it)
        }
        if (!sequential.renameTo(file)) throw IOException("Cannot promote completed download")
    }
    coroutineContext.ensureActive()
    if (!validModelFile(file, size, hash)) {
        if (!file.delete()) throw IOException("Cannot discard invalid download")
        checkpoints.forEach { it.delete() }
        throw IOException("Downloaded file failed size or checksum validation")
    }
    // Adjacent staging allows rename without a partial copy over an installed file.
    if (!file.renameTo(target)) throw IOException("Failed to install downloaded file")
    checkpoints.forEach { it.delete() }
    identity.delete()
}

// Used by archives and servers without multipart range support. The file length is
// the checkpoint, and only pinned, hash-checked artifacts can reuse a partial body.
internal fun downloadSequentialFile(
    client: OkHttpClient,
    url: String,
    file: File,
    size: Long?,
    hash: String?,
    onRestart: () -> Unit = {},
    onProgress: (Long) -> Unit = {}
) {
    file.parentFile?.mkdirs()
    if (file.exists() && (hash == null || size == null || file.length() > size)) {
        onRestart()
        if (!file.delete()) throw IOException("Cannot restart partial download")
    }
    var downloaded = file.length()
    onProgress(downloaded)
    if (size == null || downloaded < size) {
        val request = Request.Builder().url(url).header("Accept-Encoding", "identity")
        if (downloaded > 0) request.header("Range", "bytes=$downloaded-")
        client.newCall(request.build()).execute().use { response ->
            val append = response.code == 206
            if (append) {
                if (size == null || response.header("Content-Range") != "bytes $downloaded-${size - 1}/$size") {
                    throw IOException("Server returned an unexpected download range")
                }
            } else if (response.code == 200) {
                if (downloaded > 0) onRestart()
                downloaded = 0
            } else throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty download response")
            val expectedBytes = size?.minus(downloaded)
            if (expectedBytes != null && body.contentLength() >= 0 && body.contentLength() != expectedBytes) {
                throw IOException("Download response length mismatch")
            }
            val total = size ?: body.contentLength().takeIf { it >= 0 }
            body.byteStream().use { input ->
                java.io.FileOutputStream(file, append).use { output ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (total != null && read > total - downloaded) throw IOException("Download exceeded expected size")
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded)
                    }
                }
            }
            if (total != null && downloaded != total) throw IOException("Download ended early")
        }
    }
    if (size != null && file.length() != size) throw IOException("Download ended early")
    if (!validModelFile(file, size, hash)) {
        if (!file.delete()) throw IOException("Cannot discard invalid download")
        throw IOException("Downloaded file failed size or checksum validation")
    }
}

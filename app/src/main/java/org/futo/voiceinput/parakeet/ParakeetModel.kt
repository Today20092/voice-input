package org.futo.voiceinput.parakeet

import android.app.Activity
import android.content.Context
import android.content.Intent
import org.futo.voiceinput.BuildConfig
import org.futo.voiceinput.downloader.DownloadActivity
import org.futo.voiceinput.downloader.EXTRA_COMPLETION_MARKER
import org.futo.voiceinput.downloader.EXTRA_DOWNLOAD_FILE_HASHES
import org.futo.voiceinput.downloader.EXTRA_DOWNLOAD_FILE_NAMES
import org.futo.voiceinput.downloader.EXTRA_DOWNLOAD_FILE_URLS
import org.futo.voiceinput.downloader.EXTRA_TARGET_SUBDIR
import java.io.File
import java.security.MessageDigest

data class ParakeetModelFile(
    val name: String,
    val url: String,
    val sha256: String?,
    val required: Boolean = true
)

object ParakeetModel {
    const val directoryName = "parakeet-unified-en-0.6b-onnx"
    const val completionMarker = ".download_complete"
    private const val exportedAssetBaseUrl =
        "https://huggingface.co/futo-org/parakeet-unified-en-0.6b-onnx/resolve/main"

    // Fill these hashes from tools/parakeet_export/checksums.sha256 after the
    // unified ONNX export is validated and hosted. Missing hashes are accepted
    // for local/unhosted exports.
    val files = listOf(
        ParakeetModelFile(
            name = "config.json",
            url = "$exportedAssetBaseUrl/config.json?download=true",
            sha256 = null
        ),
        ParakeetModelFile(
            name = "vocab.txt",
            url = "$exportedAssetBaseUrl/vocab.txt?download=true",
            sha256 = null
        ),
        ParakeetModelFile(
            name = "encoder-model.int8.onnx",
            url = "$exportedAssetBaseUrl/encoder-model.int8.onnx?download=true",
            sha256 = null
        ),
        ParakeetModelFile(
            name = "decoder_joint-model.int8.onnx",
            url = "$exportedAssetBaseUrl/decoder_joint-model.int8.onnx?download=true",
            sha256 = null
        ),
        ParakeetModelFile(
            name = "preprocessor.onnx",
            url = "$exportedAssetBaseUrl/preprocessor.onnx?download=true",
            sha256 = null
        )
    )
}

fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            digest.update(buffer, 0, read)
        }
    }

    return digest.digest().joinToString("") { "%02x".format(it) }
}

fun Context.parakeetModelDir(): File = File(filesDir, ParakeetModel.directoryName)

fun Context.parakeetModelMarker(): File =
    File(parakeetModelDir(), ParakeetModel.completionMarker)

fun Context.isParakeetModelDownloaded(verifyHashes: Boolean = false): Boolean {
    if (BuildConfig.BUNDLE_PARAKEET_MODEL) return true

    val marker = parakeetModelMarker()
    if (!marker.exists()) return false

    val modelDir = parakeetModelDir()
    val isValid = ParakeetModel.files
        .filter { it.required }
        .all { model ->
            val file = File(modelDir, model.name)
            file.exists() && (!verifyHashes || model.sha256 == null || sha256(file) == model.sha256)
        }

    if (!isValid) {
        marker.delete()
    }

    return isValid
}

fun Context.deleteIncompleteParakeetModel() {
    runCatching { ParakeetNative.close() }
    if (!isParakeetModelDownloaded()) {
        parakeetModelDir().deleteRecursively()
    }
}

fun Context.parakeetModelDownloadIntent(): Intent {
    runCatching { ParakeetNative.close() }
    return Intent(this, DownloadActivity::class.java).apply {
        putStringArrayListExtra(
            EXTRA_DOWNLOAD_FILE_NAMES,
            ArrayList(ParakeetModel.files.map { it.name })
        )
        putStringArrayListExtra(
            EXTRA_DOWNLOAD_FILE_URLS,
            ArrayList(ParakeetModel.files.map { it.url })
        )
        putStringArrayListExtra(
            EXTRA_DOWNLOAD_FILE_HASHES,
            ArrayList(ParakeetModel.files.map { it.sha256 ?: "" })
        )
        putExtra(EXTRA_TARGET_SUBDIR, ParakeetModel.directoryName)
        putExtra(EXTRA_COMPLETION_MARKER, ParakeetModel.completionMarker)
    }
}

fun Context.startParakeetModelDownloadActivity() {
    val intent = parakeetModelDownloadIntent()
    if (this !is Activity) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    startActivity(intent)
}

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
    const val directoryName = "parakeet-tdt-0.6b-v3-int8"
    const val completionMarker = ".download_complete"

    val files = listOf(
        ParakeetModelFile(
            name = "config.json",
            url = "https://huggingface.co/istupakov/parakeet-tdt-0.6b-v3-onnx/resolve/main/config.json?download=true",
            sha256 = null
        ),
        ParakeetModelFile(
            name = "vocab.txt",
            url = "https://huggingface.co/istupakov/parakeet-tdt-0.6b-v3-onnx/resolve/main/vocab.txt?download=true",
            sha256 = null
        ),
        ParakeetModelFile(
            name = "encoder-model.int8.onnx",
            url = "https://huggingface.co/istupakov/parakeet-tdt-0.6b-v3-onnx/resolve/main/encoder-model.int8.onnx?download=true",
            sha256 = "6139d2fa7e1b086097b277c7149725edbab89cc7c7ae64b23c741be4055aff09"
        ),
        ParakeetModelFile(
            name = "decoder_joint-model.int8.onnx",
            url = "https://huggingface.co/istupakov/parakeet-tdt-0.6b-v3-onnx/resolve/main/decoder_joint-model.int8.onnx?download=true",
            sha256 = "eea7483ee3d1a30375daedc8ed83e3960c91b098812127a0d99d1c8977667a70"
        ),
        ParakeetModelFile(
            name = "nemo128.onnx",
            url = "https://huggingface.co/istupakov/parakeet-tdt-0.6b-v3-onnx/resolve/main/nemo128.onnx?download=true",
            sha256 = "a9fde1486ebfcc08f328d75ad4610c67835fea58c73ba57e3209a6f6cf019e9f"
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

fun Context.isParakeetModelDownloaded(): Boolean {
    if (BuildConfig.BUNDLE_PARAKEET_MODEL) return true

    val marker = parakeetModelMarker()
    if (!marker.exists()) return false

    val modelDir = parakeetModelDir()
    val isValid = ParakeetModel.files
        .filter { it.required }
        .all { model ->
            val file = File(modelDir, model.name)
            file.exists() && (model.sha256 == null || sha256(file) == model.sha256)
        }

    if (!isValid) {
        marker.delete()
    }

    return isValid
}

fun Context.deleteIncompleteParakeetModel() {
    if (!isParakeetModelDownloaded()) {
        parakeetModelDir().deleteRecursively()
    }
}

fun Context.parakeetModelDownloadIntent(): Intent {
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

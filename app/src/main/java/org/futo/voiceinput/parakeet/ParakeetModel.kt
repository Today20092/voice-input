package org.futo.voiceinput.parakeet

import android.app.Activity
import android.content.Context
import android.content.Intent
import org.futo.voiceinput.BuildConfig
import org.futo.voiceinput.downloader.DownloadActivity
import org.futo.voiceinput.downloader.putRecognitionModel
import org.futo.voiceinput.recognition.PerformanceClass
import org.futo.voiceinput.recognition.RecognitionModelArtifact
import org.futo.voiceinput.recognition.RecognitionModel
import org.futo.voiceinput.recognition.RecognitionModelStore
import org.futo.voiceinput.recognition.TranscriptionBehavior
import java.io.File
import java.security.MessageDigest

object ParakeetModel {
    private const val revision = "8f23f0c03c8761650bdb5b40aaf3e40d2c15f1ce"
    private const val source = "istupakov/parakeet-tdt-0.6b-v3-onnx"
    val recognitionModel = RecognitionModel(
        id = "parakeet-tdt-0.6b-v3",
        version = revision,
        runtimeId = "parakeet",
        variantId = null,
        directoryName = "parakeet-unified-en-0.6b-onnx",
        source = source,
        sourceUrl = "https://huggingface.co/$source/tree/$revision",
        displayName = "Parakeet TDT 0.6B V3",
        description = "High-accuracy NVIDIA recognition that returns text after recording stops.",
        transcription = TranscriptionBehavior.FINAL_ONLY,
        recognitionLanguages = "English",
        performanceClass = PerformanceClass.DEMANDING,
        artifacts = listOf(
            artifact("config.json", "config.json", 97, "5ee4d84eeb13e7a90bf76a2af8b8eb0a536f8e985a28816beae69c1dce2d4cf9"),
            artifact("vocab.txt", "vocab.txt", 93_939, "15811f575ed0c421c68e46af904d8c435d9bededfd4203e22333efe39a77dca5"),
            artifact("encoder-model.int8.onnx", "encoder-model.int8.onnx", 652_183_999, "6139d2fa7e1b086097b277c7149725edbab89cc7c7ae64b23c741be4055aff09"),
            artifact("decoder_joint-model.int8.onnx", "decoder_joint-model.int8.onnx", 18_202_004, "eea7483ee3d1a30375daedc8ed83e3960c91b098812127a0d99d1c8977667a70"),
            artifact("preprocessor.onnx", "nemo128.onnx", 139_764, "a9fde1486ebfcc08f328d75ad4610c67835fea58c73ba57e3209a6f6cf019e9f")
        )
    )
    val directoryName = recognitionModel.directoryName

    private fun artifact(localName: String, remoteName: String, size: Long, hash: String) =
        RecognitionModelArtifact(
            name = localName,
            url = "https://huggingface.co/$source/resolve/$revision/$remoteName?download=true",
            sizeBytes = size,
            sha256 = hash
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
    File(parakeetModelDir(), ParakeetModel.recognitionModel.completionMarker)

fun Context.isParakeetModelDownloaded(verifyHashes: Boolean = false): Boolean {
    if (BuildConfig.BUNDLE_PARAKEET_MODEL) return true
    return if (verifyHashes) {
        RecognitionModelStore(filesDir).isInstalled(
            ParakeetModel.recognitionModel,
            verifyHashes = true
        )
    } else {
        RecognitionModelStore(filesDir).isInstalled(ParakeetModel.recognitionModel)
    }
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
        putRecognitionModel(ParakeetModel.recognitionModel)
    }
}

fun Context.startParakeetModelDownloadActivity() {
    val intent = parakeetModelDownloadIntent()
    if (this !is Activity) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    startActivity(intent)
}

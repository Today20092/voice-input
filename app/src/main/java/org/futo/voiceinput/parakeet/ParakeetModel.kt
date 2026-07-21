package org.futo.voiceinput.parakeet

import android.app.Activity
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.runBlocking
import org.futo.voiceinput.BuildConfig
import org.futo.voiceinput.downloader.DownloadActivity
import org.futo.voiceinput.downloader.putRecognitionModel
import org.futo.voiceinput.recognition.PerformanceClass
import org.futo.voiceinput.recognition.RecognitionModel
import org.futo.voiceinput.recognition.RecognitionModelArtifact
import org.futo.voiceinput.recognition.RecognitionModelStore
import org.futo.voiceinput.recognition.TranscriptionBehavior
import java.io.File

object ParakeetModel {
    private const val revision = "1247204e1cc87d84abf1c9a5e45c1caee15b314a"
    private const val repository = "twmht/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8"
    val recognitionModel = RecognitionModel(
        id = "parakeet-tdt-0.6b-v3",
        version = revision,
        runtimeId = "parakeet",
        variantId = null,
        directoryName = "sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8",
        source = "NVIDIA Parakeet TDT 0.6B V3 (CC BY 4.0), Sherpa-ONNX export by twmht",
        displayName = "Parakeet TDT 0.6B V3",
        description = "High-accuracy NVIDIA recognition that returns text after recording stops.",
        transcription = TranscriptionBehavior.FINAL_ONLY,
        recognitionLanguages = "25 European languages",
        performanceClass = PerformanceClass.DEMANDING,
        artifacts = listOf(
            artifact("encoder.int8.onnx", 652_184_281, "acfc2b4456377e15d04f0243af540b7fe7c992f8d898d751cf134c3a55fd2247"),
            artifact("decoder.int8.onnx", 11_845_275, "179e50c43d1a9de79c8a24149a2f9bac6eb5981823f2a2ed88d655b24248db4e"),
            artifact("joiner.int8.onnx", 6_355_277, "3164c13fc2821009440d20fcb5fdc78bff28b4db2f8d0f0b329101719c0948b3"),
            artifact("tokens.txt", 93_939, "d58544679ea4bc6ac563d1f545eb7d474bd6cfa467f0a6e2c1dc1c7d37e3c35d")
        )
    )
    val directoryName = recognitionModel.directoryName

    private fun artifact(name: String, size: Long, hash: String) =
        RecognitionModelArtifact(
            name = name,
            url = "https://huggingface.co/$repository/resolve/$revision/$name?download=true",
            sizeBytes = size,
            sha256 = hash
        )
}

fun Context.parakeetModelDir(): File =
    RecognitionModelStore(filesDir).modelDirectory(ParakeetModel.recognitionModel)

suspend fun RecognitionModel.releaseRuntime() {
    if (runtimeId == ParakeetModel.recognitionModel.runtimeId) {
        ParakeetEngineManager.forceClose()
    }
}

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
    runBlocking { ParakeetEngineManager.forceClose() }
    if (!isParakeetModelDownloaded()) {
        parakeetModelDir().deleteRecursively()
    }
}

fun Context.parakeetModelDownloadIntent(): Intent {
    runBlocking { ParakeetEngineManager.forceClose() }
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

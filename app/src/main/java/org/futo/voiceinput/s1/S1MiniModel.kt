package org.futo.voiceinput.s1

import android.app.Activity
import android.content.Context
import android.content.Intent
import org.futo.voiceinput.downloader.DownloadActivity
import org.futo.voiceinput.downloader.putRecognitionModel
import org.futo.voiceinput.recognition.PerformanceClass
import org.futo.voiceinput.recognition.RecognitionModel
import org.futo.voiceinput.recognition.RecognitionModelArtifact
import org.futo.voiceinput.recognition.RecognitionModelStore
import org.futo.voiceinput.recognition.TranscriptionBehavior
import java.io.File

const val EXTRA_ENABLE_S1_MINI_AFTER_DOWNLOAD = "enable_s1_mini_after_download"

object S1MiniModel {
    const val ID = "s1-mini-superwhisper"
    const val VERSION = "v1-ee2c0f56e56345f475749a44ff2893e21c3cb292"
    const val DIRECTORY = "s1-mini-superwhisper-v1"
    const val FILE_NAME = "s1-mini-q4_k_m.gguf"
    const val FILE_SIZE = 484_219_808L
    const val SHA256 = "3b41ebe2502cbd03e811d5d16b022f5ab551eda58d62597d152f89535003c634"
    private const val REVISION = "ee2c0f56e56345f475749a44ff2893e21c3cb292"

    val model = RecognitionModel(
        id = ID,
        version = VERSION,
        runtimeId = "s1_mini_cleanup",
        variantId = "q4_k_m",
        directoryName = DIRECTORY,
        source = "S1-mini by Superwhisper (Hugging Face, pinned v1 GGUF)",
        displayName = "S1-mini by Superwhisper",
        description = "On-device cleanup for final English transcripts.",
        transcription = TranscriptionBehavior.FINAL_ONLY,
        recognitionLanguages = "English only",
        performanceClass = PerformanceClass.DEMANDING,
        artifacts = listOf(
            RecognitionModelArtifact(
                name = FILE_NAME,
                url = "https://huggingface.co/superwhisper/s1-mini-GGUF/resolve/$REVISION/$FILE_NAME?download=true",
                sizeBytes = FILE_SIZE,
                sha256 = SHA256
            )
        )
    )

    fun directory(context: Context) = RecognitionModelStore(context.filesDir).modelDirectory(model)
    fun modelFile(context: Context) = File(directory(context), FILE_NAME)

    fun isInstalled(context: Context, verifyHash: Boolean = false): Boolean =
        RecognitionModelStore(context.filesDir).isInstalled(model, verifyHash)

    fun downloadIntent(context: Context, enableAfterDownload: Boolean = false) =
        Intent(context, DownloadActivity::class.java).apply {
            putRecognitionModel(model)
            putExtra(EXTRA_ENABLE_S1_MINI_AFTER_DOWNLOAD, enableAfterDownload)
        }

    fun startDownload(context: Context, enableAfterDownload: Boolean = false) {
        context.startActivity(downloadIntent(context, enableAfterDownload).apply {
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    suspend fun delete(context: Context) {
        S1MiniClient.unload(context)
        RecognitionModelStore(context.filesDir).delete(model, selectedModelId = null) { }
    }
}

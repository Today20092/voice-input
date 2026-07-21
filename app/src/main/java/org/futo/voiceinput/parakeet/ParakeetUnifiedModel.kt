package org.futo.voiceinput.parakeet

import android.app.Activity
import android.content.Context
import android.content.Intent
import org.futo.voiceinput.downloader.DownloadActivity
import org.futo.voiceinput.downloader.putRecognitionModel
import org.futo.voiceinput.recognition.RecognitionModelCatalog
import org.futo.voiceinput.recognition.RecognitionModelStore
import java.io.File

fun Context.parakeetUnifiedModelDirectory(): File =
    RecognitionModelStore(filesDir).modelDirectory(RecognitionModelCatalog.parakeetUnified)

fun Context.isParakeetUnifiedModelDownloaded(verifyHashes: Boolean = false): Boolean =
    RecognitionModelStore(filesDir).isInstalled(
        RecognitionModelCatalog.parakeetUnified,
        verifyHashes
    )

fun Context.parakeetUnifiedModelDownloadIntent() = Intent(this, DownloadActivity::class.java).apply {
    putRecognitionModel(RecognitionModelCatalog.parakeetUnified)
}

fun Context.startParakeetUnifiedModelDownloadActivity() {
    val intent = parakeetUnifiedModelDownloadIntent()
    if (this !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}

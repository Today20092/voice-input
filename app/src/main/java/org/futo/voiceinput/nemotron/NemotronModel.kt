package org.futo.voiceinput.nemotron

import android.app.Activity
import android.content.Context
import android.content.Intent
import org.futo.voiceinput.downloader.DownloadActivity
import org.futo.voiceinput.downloader.putRecognitionModel
import org.futo.voiceinput.recognition.RecognitionModelCatalog
import org.futo.voiceinput.recognition.RecognitionModelStore
import java.io.File

val nemotronEnglishBalanced = RecognitionModelCatalog.nemotronEnglishBalanced

fun Context.nemotronModelDirectory(): File = File(filesDir, nemotronEnglishBalanced.directoryName)

fun Context.isNemotronModelDownloaded(verifyHashes: Boolean = false): Boolean =
    RecognitionModelStore(filesDir).isInstalled(nemotronEnglishBalanced, verifyHashes)

fun Context.nemotronModelDownloadIntent() = Intent(this, DownloadActivity::class.java).apply {
    putRecognitionModel(nemotronEnglishBalanced)
}

fun Context.startNemotronModelDownloadActivity() {
    val intent = nemotronModelDownloadIntent()
    if (this !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}

package org.futo.voiceinput.nemotron

import android.app.Activity
import android.content.Context
import android.content.Intent
import org.futo.voiceinput.downloader.DownloadActivity
import org.futo.voiceinput.downloader.putRecognitionModel
import org.futo.voiceinput.recognition.RecognitionModelCatalog
import org.futo.voiceinput.recognition.RecognitionModel
import org.futo.voiceinput.recognition.RecognitionModelStore
import org.futo.voiceinput.settings.NEMOTRON_PROFILE
import org.futo.voiceinput.settings.getSettingBlocking
import java.io.File

enum class NemotronProfile(val id: String) {
    LowLatency("low_latency"),
    Balanced("balanced"),
    Accuracy("accuracy")
}

fun String.toNemotronProfile(): NemotronProfile =
    NemotronProfile.entries.firstOrNull { it.id == this } ?: NemotronProfile.Balanced

fun NemotronProfile.recognitionModel(): RecognitionModel =
    requireNotNull(RecognitionModelCatalog.modelFor("nemotron", id))

private fun Context.selectedNemotronProfile() =
    getSettingBlocking(NEMOTRON_PROFILE.key, NEMOTRON_PROFILE.default).toNemotronProfile()

fun Context.nemotronModelDirectory(
    profile: NemotronProfile = selectedNemotronProfile()
): File = RecognitionModelStore(filesDir).modelDirectory(profile.recognitionModel())

fun Context.isNemotronModelDownloaded(
    profile: NemotronProfile = selectedNemotronProfile(),
    verifyHashes: Boolean = false
): Boolean = RecognitionModelStore(filesDir).isInstalled(profile.recognitionModel(), verifyHashes)

fun Context.nemotronModelDownloadIntent(
    profile: NemotronProfile = selectedNemotronProfile()
) = Intent(this, DownloadActivity::class.java).apply {
    putRecognitionModel(profile.recognitionModel())
}

fun Context.startNemotronModelDownloadActivity(
    profile: NemotronProfile = selectedNemotronProfile()
) {
    val intent = nemotronModelDownloadIntent(profile)
    if (this !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}

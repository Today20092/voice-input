package org.futo.voiceinput.moonshine

import android.content.Context
import android.content.Intent
import org.futo.voiceinput.downloader.DownloadActivity
import org.futo.voiceinput.downloader.putRecognitionModel
import org.futo.voiceinput.recognition.RecognitionModelCatalog
import org.futo.voiceinput.recognition.RecognitionModel
import org.futo.voiceinput.recognition.RecognitionModelStore
import org.futo.voiceinput.settings.MOONSHINE_MODEL_VARIANT
import org.futo.voiceinput.settings.getSetting
import org.futo.voiceinput.settings.getSettingBlocking
import java.io.File

fun MoonshineModelVariant.recognitionModel(): RecognitionModel =
    requireNotNull(RecognitionModelCatalog.modelFor("moonshine", id))

fun Context.moonshineModelDir(variant: MoonshineModelVariant): File =
    RecognitionModelStore(filesDir).modelDirectory(variant.recognitionModel())

fun Context.isMoonshineModelDownloaded(variant: MoonshineModelVariant): Boolean {
    return RecognitionModelStore(filesDir).isInstalled(variant.recognitionModel())
}

private fun Context.selectedMoonshineModelVariant() =
    getSettingBlocking(MOONSHINE_MODEL_VARIANT.key, MOONSHINE_MODEL_VARIANT.default)
        .toMoonshineModelVariant()

suspend fun Context.getSelectedMoonshineModelVariant() =
    getSetting(MOONSHINE_MODEL_VARIANT).toMoonshineModelVariant()

fun Context.isMoonshineModelDownloaded(): Boolean =
    isMoonshineModelDownloaded(selectedMoonshineModelVariant())

fun Context.moonshineModelDownloadIntent(
    variant: MoonshineModelVariant = selectedMoonshineModelVariant()
): Intent =
    Intent(this, DownloadActivity::class.java).apply {
        putRecognitionModel(variant.recognitionModel())
    }

fun Context.startMoonshineModelDownloadActivity(
    variant: MoonshineModelVariant = selectedMoonshineModelVariant()
) {
    startActivity(moonshineModelDownloadIntent(variant))
}

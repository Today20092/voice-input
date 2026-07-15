package org.futo.voiceinput.moonshine

import android.content.Context
import android.content.Intent
import org.futo.voiceinput.downloader.DownloadActivity
import org.futo.voiceinput.downloader.EXTRA_COMPLETION_MARKER
import org.futo.voiceinput.downloader.EXTRA_DOWNLOAD_FILE_HASHES
import org.futo.voiceinput.downloader.EXTRA_DOWNLOAD_FILE_NAMES
import org.futo.voiceinput.downloader.EXTRA_DOWNLOAD_FILE_URLS
import org.futo.voiceinput.downloader.EXTRA_TARGET_SUBDIR
import org.futo.voiceinput.settings.MOONSHINE_MODEL_VARIANT
import org.futo.voiceinput.settings.getSetting
import org.futo.voiceinput.settings.getSettingBlocking
import java.io.File

object MoonshineModel {
    const val completionMarker = ".download_complete"

    val files = listOf(
        "adapter.ort",
        "cross_kv.ort",
        "decoder_kv.ort",
        "decoder_kv_with_attention.ort",
        "encoder.ort",
        "frontend.ort",
        "streaming_config.json",
        "tokenizer.bin"
    )

}

fun Context.moonshineModelDir(variant: MoonshineModelVariant): File =
    File(filesDir, variant.directoryName)

fun Context.isMoonshineModelDownloaded(variant: MoonshineModelVariant): Boolean {
    val directory = moonshineModelDir(variant)
    return File(directory, MoonshineModel.completionMarker).exists() &&
        MoonshineModel.files.all { File(directory, it).exists() }
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
        putExtra(EXTRA_TARGET_SUBDIR, variant.directoryName)
        putExtra(EXTRA_COMPLETION_MARKER, MoonshineModel.completionMarker)
        putStringArrayListExtra(
            EXTRA_DOWNLOAD_FILE_NAMES,
            ArrayList(MoonshineModel.files)
        )
        putStringArrayListExtra(
            EXTRA_DOWNLOAD_FILE_URLS,
            ArrayList(MoonshineModel.files.map { "${variant.baseUrl}/$it" })
        )
        putStringArrayListExtra(
            EXTRA_DOWNLOAD_FILE_HASHES,
            ArrayList(List(MoonshineModel.files.size) { "" })
        )
    }

fun Context.startMoonshineModelDownloadActivity(
    variant: MoonshineModelVariant = selectedMoonshineModelVariant()
) {
    startActivity(moonshineModelDownloadIntent(variant))
}

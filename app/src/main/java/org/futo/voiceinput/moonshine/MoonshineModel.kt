package org.futo.voiceinput.moonshine

import android.content.Context
import android.content.Intent
import org.futo.voiceinput.downloader.DownloadActivity
import org.futo.voiceinput.downloader.EXTRA_COMPLETION_MARKER
import org.futo.voiceinput.downloader.EXTRA_DOWNLOAD_FILE_HASHES
import org.futo.voiceinput.downloader.EXTRA_DOWNLOAD_FILE_NAMES
import org.futo.voiceinput.downloader.EXTRA_DOWNLOAD_FILE_URLS
import org.futo.voiceinput.downloader.EXTRA_TARGET_SUBDIR
import java.io.File

object MoonshineModel {
    const val directoryName = "moonshine-small-streaming-en"
    const val completionMarker = ".download_complete"
    private const val baseUrl =
        "https://download.moonshine.ai/model/small-streaming-en/quantized"

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

    fun url(file: String) = "$baseUrl/$file"
}

fun Context.moonshineModelDir(): File = File(filesDir, MoonshineModel.directoryName)

fun Context.isMoonshineModelDownloaded(): Boolean {
    val directory = moonshineModelDir()
    return File(directory, MoonshineModel.completionMarker).exists() &&
        MoonshineModel.files.all { File(directory, it).exists() }
}

fun Context.moonshineModelDownloadIntent(): Intent =
    Intent(this, DownloadActivity::class.java).apply {
        putExtra(EXTRA_TARGET_SUBDIR, MoonshineModel.directoryName)
        putExtra(EXTRA_COMPLETION_MARKER, MoonshineModel.completionMarker)
        putStringArrayListExtra(
            EXTRA_DOWNLOAD_FILE_NAMES,
            ArrayList(MoonshineModel.files)
        )
        putStringArrayListExtra(
            EXTRA_DOWNLOAD_FILE_URLS,
            ArrayList(MoonshineModel.files.map(MoonshineModel::url))
        )
        putStringArrayListExtra(
            EXTRA_DOWNLOAD_FILE_HASHES,
            ArrayList(List(MoonshineModel.files.size) { "" })
        )
    }

fun Context.startMoonshineModelDownloadActivity() {
    startActivity(moonshineModelDownloadIntent())
}

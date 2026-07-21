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
import org.futo.voiceinput.settings.NEMOTRON_MULTILINGUAL_LANGUAGE
import org.futo.voiceinput.settings.getSettingBlocking
import java.io.File

enum class NemotronProfile(val id: String, val supportsLanguageSelection: Boolean = false) {
    LowLatency("low_latency"),
    Balanced("balanced"),
    Accuracy("accuracy"),
    Multilingual("multilingual", supportsLanguageSelection = true)
}

data class NemotronLanguage(val id: String, val displayName: String)

val NEMOTRON_MULTILINGUAL_LANGUAGES = listOf(
    NemotronLanguage("auto", "Auto-detect"),
    NemotronLanguage("en", "English"),
    NemotronLanguage("es", "Spanish"),
    NemotronLanguage("fr", "French"),
    NemotronLanguage("it", "Italian"),
    NemotronLanguage("pt", "Portuguese"),
    NemotronLanguage("nl", "Dutch"),
    NemotronLanguage("de", "German"),
    NemotronLanguage("tr", "Turkish"),
    NemotronLanguage("ru", "Russian"),
    NemotronLanguage("ar", "Arabic"),
    NemotronLanguage("hi", "Hindi"),
    NemotronLanguage("ja", "Japanese"),
    NemotronLanguage("ko", "Korean"),
    NemotronLanguage("vi", "Vietnamese"),
    NemotronLanguage("uk", "Ukrainian"),
    NemotronLanguage("pl", "Polish"),
    NemotronLanguage("sv", "Swedish"),
    NemotronLanguage("cs", "Czech"),
    NemotronLanguage("nb", "Norwegian Bokmal"),
    NemotronLanguage("da", "Danish"),
    NemotronLanguage("bg", "Bulgarian"),
    NemotronLanguage("fi", "Finnish"),
    NemotronLanguage("hr", "Croatian"),
    NemotronLanguage("sk", "Slovak"),
    NemotronLanguage("zh", "Mandarin Chinese"),
    NemotronLanguage("hu", "Hungarian"),
    NemotronLanguage("ro", "Romanian"),
    NemotronLanguage("et", "Estonian")
)

fun String.toNemotronLanguageCode(): String =
    NEMOTRON_MULTILINGUAL_LANGUAGES.firstOrNull { it.id == this }?.id ?: "en"

fun String.toNemotronProfile(): NemotronProfile =
    NemotronProfile.entries.firstOrNull { it.id == this } ?: NemotronProfile.Balanced

fun NemotronProfile.recognitionModel(): RecognitionModel =
    requireNotNull(RecognitionModelCatalog.modelFor("nemotron", id))

private fun Context.selectedNemotronProfile() =
    getSettingBlocking(NEMOTRON_PROFILE.key, NEMOTRON_PROFILE.default).toNemotronProfile()

internal fun Context.selectedNemotronLanguageCode(): String? =
    if (selectedNemotronProfile().supportsLanguageSelection) {
        getSettingBlocking(
            NEMOTRON_MULTILINGUAL_LANGUAGE.key,
            NEMOTRON_MULTILINGUAL_LANGUAGE.default
        ).toNemotronLanguageCode()
    } else {
        null
    }

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

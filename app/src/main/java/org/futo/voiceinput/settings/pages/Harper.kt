package org.futo.voiceinput.settings.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.futo.voiceinput.harper.HarperEnglishGate
import org.futo.voiceinput.settings.COHERE_LANGUAGE
import org.futo.voiceinput.settings.HARPER_ENABLED
import org.futo.voiceinput.settings.HARPER_EXPLICIT_ENGLISH
import org.futo.voiceinput.settings.LANGUAGE_TOGGLES
import org.futo.voiceinput.settings.NEMOTRON_MULTILINGUAL_LANGUAGE
import org.futo.voiceinput.settings.NEMOTRON_PROFILE
import org.futo.voiceinput.settings.SPEECH_BACKEND
import org.futo.voiceinput.settings.SpeechBackendType
import org.futo.voiceinput.settings.toSpeechBackendType
import org.futo.voiceinput.settings.useDataStore

@Composable
fun HarperOptions() {
    val enabled = useDataStore(HARPER_ENABLED)
    val explicitEnglish = useDataStore(HARPER_EXPLICIT_ENGLISH)
    val backend = useDataStore(SPEECH_BACKEND).value.toSpeechBackendType()
    val profile = useDataStore(NEMOTRON_PROFILE).value
    val nemotronLanguage = useDataStore(NEMOTRON_MULTILINGUAL_LANGUAGE).value
    val whisperLanguages = useDataStore(LANGUAGE_TOGGLES).value
    val cohereLanguage = useDataStore(COHERE_LANGUAGE).value
    fun establishesEnglish(declared: Boolean) = HarperEnglishGate.isEstablishedEnglish(
        backend, detectedLanguage = null,
        forcedLanguage = cohereLanguage.takeIf { backend == SpeechBackendType.Cohere },
        nemotronProfile = profile, nemotronLanguage = nemotronLanguage,
        enabledWhisperLanguages = whisperLanguages, explicitlyEnglish = declared
    )
    val confirmedEnglish = establishesEnglish(false)
    val canDeclareEnglish = establishesEnglish(true)

    CleanupCard {
        CleanupToggle("Basic text cleanup", enabled.value) { enabled.setValue(it) }
        CleanupText("Tidies spacing and capitalization while keeping your words.")
        Text("Harper · English only · No download", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(
            Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Before", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("i  am here .", style = MaterialTheme.typography.bodyMedium)
                Text("After", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("I am here.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        CleanupHeading("Language")
        when {
            confirmedEnglish -> CleanupText("Your selected model is set to English. Cleanup can run without extra confirmation.")
            !canDeclareEnglish -> CleanupText("Your selected language is not English. Basic text cleanup will be skipped.")
            else -> {
                CleanupToggle("I dictate in English", explicitEnglish.value) { explicitEnglish.setValue(it) }
                CleanupText(if (explicitEnglish.value) {
                    "Cleanup can run when no language is reported. Known non-English results are still skipped."
                } else {
                    "Confirm only if you dictate in English. Without confirmation, results with no reported language are skipped."
                })
            }
        }
        CleanupDetails("What changes?") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CleanupText("Fixes extra spaces, spacing around punctuation, lowercase 'i', and sentence capitalization when a sentence is recognized.")
                CleanupText("Keeps spelling, word choice, and repeated words. It does not rewrite grammar or add every missing comma.")
                CleanupText("Known non-English results and mixed text containing non-Latin letters are skipped, even with English confirmation. Personal vocabulary corrections are applied last.")
            }
        }
    }
}

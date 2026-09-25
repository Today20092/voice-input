package org.futo.voiceinput.harper

import org.futo.voiceinput.settings.SpeechBackendType

/** Does not assume English just because an English cleanup feature is enabled. */
object HarperEnglishGate {
    fun isEstablishedEnglish(
        backend: SpeechBackendType,
        detectedLanguage: String?,
        forcedLanguage: String?,
        nemotronProfile: String,
        nemotronLanguage: String,
        enabledWhisperLanguages: Set<String>,
        explicitlyEnglish: Boolean = false
    ): Boolean {
        fun String.isEnglish() = lowercase().replace('_', '-').substringBefore('-') == "en"
        // Known non-English recognition always wins over a user fallback declaration.
        if (!detectedLanguage.isNullOrBlank()) return detectedLanguage.isEnglish()
        if (!forcedLanguage.isNullOrBlank() && forcedLanguage != "auto") return forcedLanguage.isEnglish()
        return when (backend) {
            SpeechBackendType.Moonshine, SpeechBackendType.ParakeetUnified -> true
            SpeechBackendType.Nemotron -> if (nemotronProfile != "multilingual") true
                else if (nemotronLanguage != "auto") nemotronLanguage.isEnglish() else explicitlyEnglish
            SpeechBackendType.WhisperGGML -> if (enabledWhisperLanguages.size == 1)
                enabledWhisperLanguages.single().isEnglish() else explicitlyEnglish
            // Orukeet and Parakeet TDT v3 are multilingual and do not expose detection.
            SpeechBackendType.Orukeet, SpeechBackendType.Parakeet -> explicitlyEnglish
            SpeechBackendType.Cohere -> false // Its backend supplies the selected language.
        }
    }

    fun containsNonLatinLetters(text: String): Boolean = text.codePoints().anyMatch {
        Character.isLetter(it) && Character.UnicodeScript.of(it) != Character.UnicodeScript.LATIN
    }
}

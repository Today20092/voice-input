package org.futo.voiceinput.s1

import org.futo.voiceinput.settings.SpeechBackendType

object S1MiniEnglishGate {
    fun isEstablishedEnglish(
        backend: SpeechBackendType,
        detectedLanguage: String?,
        forcedLanguage: String?,
        nemotronProfile: String,
        nemotronLanguage: String,
        enabledWhisperLanguages: Set<String>
    ): Boolean {
        if (!detectedLanguage.isNullOrBlank()) return detectedLanguage.equals("en", ignoreCase = true)
        if (!forcedLanguage.isNullOrBlank()) return forcedLanguage.equals("en", ignoreCase = true)

        // Enabling English cleanup also covers recognizers that cannot report a language.
        return when (backend) {
            SpeechBackendType.Moonshine,
            SpeechBackendType.Orukeet,
            SpeechBackendType.Parakeet,
            SpeechBackendType.ParakeetUnified -> true

            SpeechBackendType.Nemotron -> nemotronProfile != "multilingual" ||
                nemotronLanguage == "auto" || nemotronLanguage.equals("en", ignoreCase = true)

            SpeechBackendType.WhisperGGML ->
                enabledWhisperLanguages.isEmpty() || "en" in enabledWhisperLanguages
        }
    }
}

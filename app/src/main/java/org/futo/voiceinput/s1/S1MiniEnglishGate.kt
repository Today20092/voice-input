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
    ): Boolean = when (backend) {
        SpeechBackendType.Moonshine,
        SpeechBackendType.Parakeet,
        SpeechBackendType.ParakeetUnified -> true

        SpeechBackendType.Nemotron -> {
            if (nemotronProfile != "multilingual") {
                true
            } else if (nemotronLanguage == "auto") {
                detectedLanguage.equals("en", ignoreCase = true)
            } else {
                nemotronLanguage.equals("en", ignoreCase = true)
            }
        }

        SpeechBackendType.WhisperGGML -> when {
            forcedLanguage != null -> forcedLanguage.equals("en", ignoreCase = true)
            detectedLanguage != null -> detectedLanguage.equals("en", ignoreCase = true)
            else -> enabledWhisperLanguages == setOf("en")
        }
    }
}

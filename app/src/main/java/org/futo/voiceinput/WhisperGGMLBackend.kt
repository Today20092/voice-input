package org.futo.voiceinput

import android.content.Context
import org.futo.voiceinput.ggml.DecodingMode
import org.futo.voiceinput.ml.RunState
import org.futo.voiceinput.ml.WhisperModelWrapper
import org.futo.voiceinput.parakeet.SpeechBackend
import org.futo.voiceinput.settings.BEAM_SEARCH
import org.futo.voiceinput.settings.DISALLOW_SYMBOLS
import org.futo.voiceinput.settings.ENGLISH_MODEL_INDEX
import org.futo.voiceinput.settings.LANGUAGE_TOGGLES
import org.futo.voiceinput.settings.MULTILINGUAL_MODEL_INDEX
import org.futo.voiceinput.settings.PERSONAL_DICTIONARY
import org.futo.voiceinput.settings.USE_LANGUAGE_SPECIFIC_MODELS
import org.futo.voiceinput.settings.getSetting

class WhisperGGMLBackend(
    private val onStatusUpdate: (RunState) -> Unit,
    private val onPartialDecode: (String) -> Unit,
    private val forceLanguageProvider: () -> String?
) : SpeechBackend {
    private var whisperModelWrapper: WhisperModelWrapper? = null
    private var personalDictionary: String = ""
    private var beamSearch: Boolean = true

    override suspend fun load(context: Context) {
        personalDictionary = context.getSetting(PERSONAL_DICTIONARY)
        val disallowSymbols = context.getSetting(DISALLOW_SYMBOLS)
        beamSearch = context.getSetting(BEAM_SEARCH)

        val englishModelIdx = context.getSetting(ENGLISH_MODEL_INDEX)
        val multilingualModelIdx = context.getSetting(MULTILINGUAL_MODEL_INDEX)
        val languages = context.getSetting(LANGUAGE_TOGGLES)
        val useLanguageSpecificModels = context.getSetting(USE_LANGUAGE_SPECIFIC_MODELS)
        val forceLanguage = forceLanguageProvider()
        val modelMap = context.getLanguageModelMap()

        val primaryModel = if (forceLanguage != null) {
            modelMap[forceLanguage] ?: if (forceLanguage == "en") {
                ENGLISH_MODELS[englishModelIdx]
            } else {
                MULTILINGUAL_MODELS[multilingualModelIdx]
            }
        } else {
            val selectedModels = modelMap.values.distinct()
            selectedModels.firstOrNull { it in MULTILINGUAL_MODELS }
                ?: selectedModels.firstOrNull()
                ?: ENGLISH_MODELS[englishModelIdx]
        }

        val fallbackEnglishModel = if (
            forceLanguage == null &&
            languages.contains("en") &&
            useLanguageSpecificModels &&
            primaryModel in MULTILINGUAL_MODELS
        ) {
            ENGLISH_MODELS[englishModelIdx]
        } else {
            null
        }

        whisperModelWrapper = WhisperModelWrapper(
            context = context,
            primaryModel = primaryModel,
            fallbackEnglishModel = fallbackEnglishModel,
            suppressNonSpeech = disallowSymbols,
            languages = languages,
            onStatusUpdate = onStatusUpdate,
            onPartialDecode = onPartialDecode
        )
    }

    override suspend fun transcribe(samples: FloatArray): String {
        return whisperModelWrapper?.run(
            samples = samples,
            glossary = personalDictionary,
            forceLanguage = forceLanguageProvider(),
            decodingMode = if (beamSearch) DecodingMode.BeamSearch5 else DecodingMode.Greedy
        ) ?: throw IllegalStateException("Whisper/GGML backend is not loaded")
    }

    override suspend fun close() {
        whisperModelWrapper?.close()
        whisperModelWrapper = null
    }
}

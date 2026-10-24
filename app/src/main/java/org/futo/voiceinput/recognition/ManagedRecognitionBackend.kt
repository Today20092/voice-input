package org.futo.voiceinput.recognition

import android.content.Context
import org.futo.voiceinput.asr4all.asr4allBackend
import org.futo.voiceinput.backend.SpeechBackend
import org.futo.voiceinput.cohere.CohereBackend
import org.futo.voiceinput.moonshine.MoonshineBackend
import org.futo.voiceinput.moonshine.toMoonshineModelVariant
import org.futo.voiceinput.nemotron.SherpaStreamingBackend
import org.futo.voiceinput.nemotron.nemotronModelDirectory
import org.futo.voiceinput.nemotron.toNemotronProfile
import org.futo.voiceinput.parakeet.ParakeetBackend
import org.futo.voiceinput.parakeet.orukeetBackend
import org.futo.voiceinput.parakeet.parakeetUnifiedBackend
import org.futo.voiceinput.settings.MOONSHINE_MODEL_VARIANT
import org.futo.voiceinput.settings.getSettingBlocking

internal fun RecognitionModel.selection() = RecognitionModelSelection(
    runtimeId = runtimeId,
    moonshineVariantId = variantId.takeIf { runtimeId == "moonshine" },
    nemotronVariantId = variantId.takeIf { runtimeId == "nemotron" },
    asr4allVariantId = variantId.takeIf { runtimeId == "asr4all" }
)

/** Constructs independent managed runtimes; warm leasing and selection changes belong to callers. */
internal fun managedRecognitionBackend(
    context: Context,
    selection: RecognitionModelSelection,
    languageOverride: String? = null
): SpeechBackend = when (selection.runtimeId) {
    "parakeet" -> ParakeetBackend()
    "orukeet" -> orukeetBackend()
    "parakeet_unified" -> parakeetUnifiedBackend()
    "parakeet_redux" -> org.futo.voiceinput.redux.ReduxBackend()
    "phonon" -> org.futo.voiceinput.phonon.PhononBackend()
    "whistle" -> org.futo.voiceinput.whistle.WhistleBackend()
    "moonshine" -> MoonshineBackend((selection.moonshineVariantId ?: context.getSettingBlocking(
        MOONSHINE_MODEL_VARIANT.key, MOONSHINE_MODEL_VARIANT.default
    )).toMoonshineModelVariant())
    "cohere" -> CohereBackend(languageOverride = languageOverride)
    "nemotron" -> SherpaStreamingBackend(
        modelDirectory = { selectedContext ->
            selection.nemotronVariantId?.let { selectedContext.nemotronModelDirectory(it.toNemotronProfile()) }
                ?: selectedContext.nemotronModelDirectory()
        },
        languageOverride = languageOverride
    )
    "asr4all" -> asr4allBackend(context, selection.asr4allVariantId ?: "m")
    else -> error("Unsupported recognition runtime")
}

package org.futo.voiceinput.redux

import org.futo.voiceinput.recognition.PerformanceClass
import org.futo.voiceinput.recognition.RecognitionModel
import org.futo.voiceinput.recognition.RecognitionModelArtifact
import org.futo.voiceinput.recognition.TranscriptionBehavior

object ReduxModel {
    const val FILE_NAME = "redux-packed.gguf"
    private const val REVISION = "741158ae71e64ef5c89385862c18f777d07a97a1"
    val model = RecognitionModel(
        id = "parakeet-redux-packed",
        version = REVISION,
        runtimeId = "parakeet_redux",
        variantId = null,
        directoryName = "parakeet-redux-packed",
        source = "Moondream Parakeet Redux, converted to packed GGUF by mudler/parakeet.cpp",
        licenseAttribution = "Moondream and NVIDIA Parakeet TDT 0.6B V3, CC BY 4.0 (https://creativecommons.org/licenses/by/4.0/). Converted packed GGUF weights; parakeet.cpp runtime MIT.",
        displayName = "Parakeet Redux",
        description = "Compact ternary model for CPU recognition. Returns text after recording stops. Android speed is not yet benchmarked; background noise can reduce accuracy.",
        transcription = TranscriptionBehavior.FINAL_ONLY,
        recognitionLanguages = "25 European languages",
        performanceClass = PerformanceClass.BALANCED,
        artifacts = listOf(RecognitionModelArtifact(
            name = FILE_NAME,
            url = "https://huggingface.co/mudler/parakeet-cpp-gguf/resolve/$REVISION/$FILE_NAME?download=true",
            sizeBytes = 213_319_296L,
            sha256 = "574614b9a4d9f72ab202877a7ad6a1f4bf819dd1d27b9d42dfe8cd429fcebdd5"
        ))
    )
}

package org.futo.voiceinput.asr4all

import org.futo.voiceinput.recognition.PerformanceClass
import org.futo.voiceinput.recognition.RecognitionModel
import org.futo.voiceinput.recognition.RecognitionModelArtifact
import org.futo.voiceinput.recognition.TranscriptionBehavior

object Asr4allModels {
    val models = listOf(
        model("s", "Small", "735bdd8ae688094926068990ccf9b26a80cc43c3", 38489024, 90364,
            "a027754d46abdc7ffcdcc621a1430da7896e4504484657228a22d6eccf283381",
            "025de70620e2e90fd0e3de947fea4783e0e637ec54d877a5d25c8ad3866e6f48"),
        model("m", "Medium", "86f67fdc60fecb4f9662ccd1d99948291db4d905", 72395968, 90363,
            "f50318acb5de7bfc75229f73edcc9276b3553efc9c3e34a7f3f1cf66f76067b4",
            "321501d89e6249a76b8c0f896f9c3e90089f0f9d6135ab82802e5752990cab41"),
        model("l", "Large", "2da6534551f007201368514ec08b0b9a60ba39dc", 123044416, 90362,
            "50113d5aa74c916cce33828a3ec69c86aa64b58513a437ecf84fecfae098d351",
            "05c6fd156f1fa99ea44457ab0d0a8d23b954c272fb614a034476123874e6d125")
    )

    fun selected(variant: String) = models.firstOrNull { it.variantId == variant } ?: models[1]

    private fun model(
        variant: String, label: String, revision: String, pteBytes: Long, metadataBytes: Long,
        pteHash: String, metadataHash: String
    ): RecognitionModel {
        val repository = "https://huggingface.co/futo-org/asr4all-$variant"
        fun artifact(name: String, size: Long, hash: String) = RecognitionModelArtifact(
            name, "$repository/resolve/$revision/executorch/xnnpack_int8/$name", size, hash
        )
        return RecognitionModel(
            id = "asr4all-$variant", version = revision, runtimeId = "asr4all", variantId = variant,
            directoryName = "asr4all-$variant-xnnpack-int8", source = "FUTO ASR4ALL",
            licenseAttribution = "FUTO Model Weights Ethical Use License 1.0",
            displayName = "ASR4ALL $label",
            description = "Shows English text as you speak. Adds punctuation, capital letters, and small corrections.",
            transcription = TranscriptionBehavior.LIVE, recognitionLanguages = "English",
            performanceClass = when (variant) {
                "s" -> PerformanceClass.LIGHT
                "m" -> PerformanceClass.BALANCED
                else -> PerformanceClass.DEMANDING
            },
            artifacts = listOf(
                artifact("asr_encoder.pte", pteBytes, pteHash),
                artifact("metadata.json", metadataBytes, metadataHash)
            )
        )
    }
}

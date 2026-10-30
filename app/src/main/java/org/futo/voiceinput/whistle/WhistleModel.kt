package org.futo.voiceinput.whistle

import org.futo.voiceinput.recognition.*

object WhistleModel {
    val languageCodes = setOf("en", "de", "fr", "es", "it", "nl", "pl")
    private const val REVISION = "741118687e1f008f06d1351a402a731274789790"
    val model = RecognitionModel(
        id = "whistle", version = REVISION, runtimeId = "whistle", variantId = null,
        directoryName = "whistle", source = "Cactus Compute",
        licenseAttribution = "Whistle weights and Needle runtime by Cactus Compute, Apache 2.0. See WHISTLE-NOTICES.txt.",
        displayName = "Whistle",
        description = "A 16.9 MB experimental model for English, German, French, Spanish, Italian, Dutch and Polish. " +
            "Detects the language and produces text after you stop recording. Runs offline on the CPU. " +
            "Android speed, memory use and accuracy have not been verified; download size is not RAM usage.",
        transcription = TranscriptionBehavior.FINAL_ONLY,
        recognitionLanguages = "English, German, French, Spanish, Italian, Dutch, Polish",
        performanceClass = PerformanceClass.LIGHT,
        artifacts = listOf(RecognitionModelArtifact(
            "whistle.cact", "https://huggingface.co/Cactus-Compute/whistle/resolve/$REVISION/whistle.cact",
            16_919_407, "b6e02f048568ac5d01a2042556c658061e699acbc0aa2a1439f52f3d461dffeb"
        ))
    )
}

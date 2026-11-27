package org.futo.voiceinput.parakeet

import org.futo.voiceinput.recognition.PerformanceClass
import org.futo.voiceinput.recognition.RecognitionModel
import org.futo.voiceinput.recognition.RecognitionModelArtifact
import org.futo.voiceinput.recognition.TranscriptionBehavior

object Parakeet110mModel {
    private const val directory = "sherpa-onnx-nemo-parakeet_tdt_transducer_110m-en-36000-int8"
    private const val archiveUrl =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/$directory.tar.bz2"
    private const val archiveHash = "f628312e9fdf8686374cb01a69425c41732529d540860311f16f37cbc32cfe9b"

    val recognitionModel = RecognitionModel(
        id = "parakeet-tdt-110m",
        version = archiveHash,
        runtimeId = "parakeet_110m",
        variantId = null,
        directoryName = directory,
        source = "NVIDIA and Suno Parakeet TDT-CTC 110M, Sherpa-ONNX INT8 TDT export",
        licenseAttribution = "CC BY 4.0",
        displayName = "Parakeet TDT 110M",
        description = "Turns English speech into text after you stop recording. " +
            "It uses less storage than larger Parakeet models. Android performance is still being evaluated.",
        transcription = TranscriptionBehavior.FINAL_ONLY,
        recognitionLanguages = "English",
        performanceClass = PerformanceClass.UNMEASURED,
        artifacts = listOf(
            artifact("encoder.int8.onnx", 131_113_202, "0f35509ddeb9b39002fb077d979a9fe74f06eb0bc4dd5c34f512f82e5111d657"),
            artifact("decoder.int8.onnx", 3_955_863, "f7c331c5504c2e593c76ed22b728e3f554af6c4a383dde862e719ced08b1da19"),
            artifact("joiner.int8.onnx", 1_411_403, "bf7dff69e9f2cdbe9943d70da358f38b361c115ba0105bae7e908e0d6ec782f6"),
            artifact("tokens.txt", 9_953, "450e56bd2f036fe5b6aa821865838cc5aa9d8b0106134ce9a9ba0664abe6cd10")
        ),
        archive = artifact("$directory.tar.bz2", 108_035_095, archiveHash),
        archiveRoot = directory
    )

    private fun artifact(name: String, size: Long, hash: String) =
        RecognitionModelArtifact(name, archiveUrl, size, hash)
}

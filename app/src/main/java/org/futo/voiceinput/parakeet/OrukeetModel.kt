package org.futo.voiceinput.parakeet

import org.futo.voiceinput.recognition.PerformanceClass
import org.futo.voiceinput.recognition.RecognitionModel
import org.futo.voiceinput.recognition.RecognitionModelArtifact
import org.futo.voiceinput.recognition.TranscriptionBehavior

object OrukeetModel {
    private const val revision = "55a984d46f68323301837194ce647c702f55facc"
    private const val directory = "sherpa-onnx-orukeet-v0.1.0-int8"
    private const val archiveUrl =
        "https://huggingface.co/oruk/orukeet/resolve/$revision/onnx/$directory.tar.bz2?download=true"

    val recognitionModel = RecognitionModel(
        id = "orukeet-v0.1.0",
        version = revision,
        runtimeId = "orukeet",
        variantId = null,
        directoryName = directory,
        source = "Oruk AI Orukeet (CC BY-SA 4.0), based on NVIDIA Parakeet TDT 0.6B V3",
        displayName = "Orukeet",
        description = "Multilingual Parakeet adaptation that returns text after recording stops.",
        transcription = TranscriptionBehavior.FINAL_ONLY,
        recognitionLanguages = "25 European languages",
        performanceClass = PerformanceClass.DEMANDING,
        artifacts = listOf(
            artifact("encoder.int8.onnx", 653_182_378, "7b55f2a504a20a8e462899f5befd45f4a1784948d76ed0127902d9cf39405487"),
            artifact("decoder.int8.onnx", 11_845_332, "c185c2afb4c77c94bb1314807ecb3dc1623057a3dc540b83e10301af9bf4cfca"),
            artifact("joiner.int8.onnx", 6_355_335, "1a7e90abf7172d926dd7e2edac2a5d5035c24dfb641a15e131d57b6a5f63cdd3"),
            artifact("tokens.txt", 93_939, "d58544679ea4bc6ac563d1f545eb7d474bd6cfa467f0a6e2c1dc1c7d37e3c35d"),
            artifact("bpe.vocab", 117_408, "41d5e71b3591642eff088151efd7acd4e750124cc0054c8ba9fa3245187a4804"),
            artifact("LICENSE-WEIGHTS", 20_137, "23ee78c8bae49cf08ea2f0c84945c66b987ebe4520881fb51b3dad4fb43d07c2"),
            artifact("NOTICE.md", 5_271, "440361d963edd9621e744f251332b47f2c4de2e2594ecfe42b215e3f6223fa44")
        ),
        archive = artifact(
            "$directory.tar.bz2", 486_807_585,
            "f9191f30178cc9122ce2f023bf9fefafc822028307b0efa4caff645ba3fe8d0a"
        ),
        archiveRoot = directory
    )

    private fun artifact(name: String, size: Long, hash: String) =
        RecognitionModelArtifact(name, archiveUrl, size, hash)
}

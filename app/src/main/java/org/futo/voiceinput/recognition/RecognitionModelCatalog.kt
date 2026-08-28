package org.futo.voiceinput.recognition

import org.futo.voiceinput.parakeet.ParakeetModel
import org.futo.voiceinput.sha256
import java.io.File

enum class TranscriptionBehavior(val label: String) {
    LIVE("Live transcription"),
    BUFFERED_LIVE("Buffered live transcription"),
    FINAL_ONLY("Final-only transcription")
}

enum class PerformanceClass(val label: String) {
    LIGHT("Light"),
    BALANCED("Balanced"),
    DEMANDING("Demanding")
}

data class RecognitionModelArtifact(
    val name: String,
    val url: String,
    val sizeBytes: Long,
    val sha256: String
) {
    init {
        require(url.startsWith("https://"))
        require(sizeBytes > 0L)
        require(sha256.matches(Regex("[0-9a-f]{64}")))
    }
}

data class RecognitionModel(
    val id: String,
    val version: String,
    val runtimeId: String,
    val variantId: String?,
    val directoryName: String,
    val source: String,
    val licenseAttribution: String = source,
    val displayName: String,
    val description: String,
    val transcription: TranscriptionBehavior,
    val recognitionLanguages: String,
    val performanceClass: PerformanceClass,
    val artifacts: List<RecognitionModelArtifact>,
    val archive: RecognitionModelArtifact? = null,
    val archiveRoot: String? = null,
    val completionMarker: String = ".download_complete"
) {
    init {
        require((archive == null) == (archiveRoot == null))
    }

    val transferBytes = archive?.sizeBytes ?: artifacts.sumOf { it.sizeBytes }
    val requiredFreeSpaceBytes = maxOf(transferBytes, artifacts.sumOf { it.sizeBytes })
}

data class RecognitionModelCard(
    val id: String,
    val runtimeId: String,
    val displayName: String,
    val description: String,
    val transcription: TranscriptionBehavior,
    val recognitionLanguages: String,
    val performanceClasses: Set<PerformanceClass>,
    val models: List<RecognitionModel>
)

object RecognitionModelCatalog {
    private const val MOONSHINE_REVISION = "2026-03-16-sha256"
    private const val MOONSHINE_SOURCE = "Moonshine AI"

    val moonshineSmall = moonshinePackage(
        id = "moonshine-small",
        variantId = "small",
        directoryName = "moonshine-small-streaming-en",
        displayName = "Moonshine Small",
        description = "Fast live English transcription with the lightest resource use.",
        performance = PerformanceClass.LIGHT,
        baseUrl = "https://download.moonshine.ai/model/small-streaming-en/quantized",
        artifacts = listOf(
            artifact("adapter.ort", 2_867_424, "1769475563547251", "d8493e0ac76a198b309a8be6f74b3101e235f773ffe5d6b378278cd7e4177992"),
            artifact("cross_kv.ort", 5_298_736, "1769475564280608", "6e57d1361717e00d73336a0c3beafedae784b1e537905ad253dee33db4007466"),
            artifact("decoder_kv.ort", 81_435_904, "1769475578808762", "d5adfcfaa6e582144791f1568bd0f683852c7bfbb8c79acad97499da05e4ffcf"),
            artifact("decoder_kv_with_attention.ort", 81_380_336, "1773704842606341", "2ac12d0b1ab1459ae2572b0d8f0a359a79ac83ad0a5de0b40bdb33c9357048ee"),
            artifact("encoder.ort", 43_853_224, "1769475575556104", "3b21d02eff6aa5651524ada4271d37c1d7bba4eb3d256415074f2cfdbaeb526a"),
            artifact("frontend.ort", 30_984_200, "1769475573327637", "e086451043c1c8652a9614e4a4a81d5807221b611584a3cf31f73779d5900003"),
            artifact("streaming_config.json", 512, "1769475573803065", "26f02b6afb22d60871a5efd85c3d38e569cc0ddb6c5eb6e93d3260152ae8a47a"),
            artifact("tokenizer.bin", 249_974, "1769475574373246", "6884b35fd6377d4c4d32336a0bc152f36b64d1e45b6503683cdc238250a8472d")
        )
    )

    val moonshineMedium = moonshinePackage(
        id = "moonshine-medium",
        variantId = "medium",
        directoryName = "moonshine-medium-streaming-en",
        displayName = "Moonshine Medium",
        description = "Higher-accuracy live English transcription with greater resource use.",
        performance = PerformanceClass.BALANCED,
        baseUrl = "https://download.moonshine.ai/model/medium-streaming-en/quantized",
        artifacts = listOf(
            artifact("adapter.ort", 3_647_712, "1769472581562753", "16307442b7f4229f2f1511fc51b545cec9616e55872c588f3a297bbc6f4762ea"),
            artifact("cross_kv.ort", 11_544_952, "1769472583872013", "354b9a955caeb768b528f447f0a36ce4b850ca7b4531900165df304d97904fba"),
            artifact("decoder_kv.ort", 146_216_448, "1769472617399193", "fa67aa87521247f5bf44d3e44d4e4978e58c1f114249c3c6909c882624056715"),
            artifact("decoder_kv_with_attention.ort", 146_138_304, "1773704874590744", "40919de95d08690da3a8ff6df14cf55b3220046f3b767b4a4b769e7b32aaf2d2"),
            artifact("encoder.ort", 94_202_872, "1769472608775660", "a5f11167a62eef61787fe8410453257d6ddb8eba90af461a9604e5f2e93d5322"),
            artifact("frontend.ort", 47_467_256, "1769472619047095", "378fe8a5d7090a1b9ab88bbb1fc95bde010cdd64ec23419350d2d23c675636e9"),
            artifact("streaming_config.json", 513, "1769472617855699", "28e83b7a28e91472692a035e0dae3116422ae43aeb2bef5ed822c44ce89b88af"),
            artifact("tokenizer.bin", 249_974, "1769472618362358", "6884b35fd6377d4c4d32336a0bc152f36b64d1e45b6503683cdc238250a8472d")
        )
    )

    private const val NEMOTRON_VERSION = "2026-04-25"
    private const val NEMOTRON_MULTILINGUAL_VERSION = "2026-06-11"
    private const val NEMOTRON_MULTILINGUAL_REVISION = "ab43d895f5985b1bbab8b6eac8607fcdc05343f3"

    private const val PARAKEET_UNIFIED_VERSION = "7551fd26fc810cc1e4e043e608db4d13b59be31e"
    private const val PARAKEET_UNIFIED_DIRECTORY =
        "sherpa-onnx-nemo-parakeet-unified-en-0.6b-int8-streaming-560ms"
    private const val PARAKEET_UNIFIED_REPOSITORY =
        "csukuangfj2/sherpa-onnx-nemo-parakeet-unified-en-0.6b-int8-streaming-560ms"

    val parakeetUnified = RecognitionModel(
        id = "parakeet-unified-en-0.6b",
        version = PARAKEET_UNIFIED_VERSION,
        runtimeId = "parakeet_unified",
        variantId = null,
        directoryName = PARAKEET_UNIFIED_DIRECTORY,
        source = "NVIDIA Parakeet Unified (NVIDIA Open Model License), Sherpa-ONNX export by k2-fsa",
        licenseAttribution = "NVIDIA Open Model License",
        displayName = "Parakeet Unified EN 0.6B",
        description = "560 ms buffered live English transcription that recomputes left context; Nemotron is preferred for the fastest updates.",
        transcription = TranscriptionBehavior.BUFFERED_LIVE,
        recognitionLanguages = "English",
        performanceClass = PerformanceClass.DEMANDING,
        artifacts = listOf(
            parakeetUnifiedArtifact("encoder.int8.onnx", 654_046_389, "e566c3f014598a41724f2df028779a2d4cf7943cbefa324964f6a72e8ee255fb"),
            parakeetUnifiedArtifact("decoder.int8.onnx", 7_257_777, "34fea72425d2506600772ba191a6d3f99c0710abdb68d9a3dc89fa8cb2aa473a"),
            parakeetUnifiedArtifact("joiner.int8.onnx", 1_735_860, "869f43f7d24595c55581ad3bf249a935fb8a71389fbdaa7504b9f46f93140f8a"),
            parakeetUnifiedArtifact("tokens.txt", 8_952, "dc0b4584ab2e4ddbf888425c076c61b736e7356a015250db7d307e6f1a8188ff")
        )
    )

    val nemotronEnglishLowLatency = nemotronPackage(
        latencyMs = 80,
        variantId = "low_latency",
        displayName = "Low latency",
        description = "80 ms live English partials with the lowest latency and a lower-accuracy trade-off.",
        performanceClass = PerformanceClass.LIGHT,
        encoderSizeBytes = 652_916_847,
        encoderSha256 = "29a6aaf9155f25562a08a1aeea1f1a1a5d24b2f44a1d68211faf8a92073d1df6",
        archiveSizeBytes = 463_945_379,
        archiveSha256 = "caaf92069dbd1ca054f8e17cab179813bc28b4585f5c392540357ece4722333d"
    )

    val nemotronEnglishBalanced = nemotronPackage(
        latencyMs = 160,
        variantId = "balanced",
        displayName = "Balanced",
        description = "Recommended 160 ms live English profile balancing latency and accuracy.",
        performanceClass = PerformanceClass.BALANCED,
        encoderSizeBytes = 652_916_849,
        encoderSha256 = "71111f61b18e1e65e01e369434a5c0434868d2f44892742ae54240600c681209",
        archiveSizeBytes = 463_945_198,
        archiveSha256 = "0ae73a41cd51599dc7cac9ac083d9d35de53d762ca45923505fde47a3751814b"
    )

    val nemotronEnglishAccuracy = nemotronPackage(
        latencyMs = 560,
        variantId = "accuracy",
        displayName = "Accuracy",
        description = "Highest English accuracy with slower 560 ms live partials.",
        performanceClass = PerformanceClass.DEMANDING,
        encoderSizeBytes = 652_916_849,
        encoderSha256 = "7d932213491ad355c6e5576705dc3494731a52af87d7a1b954559340147909d8",
        archiveSizeBytes = 463_945_051,
        archiveSha256 = "78e2b79fcf7271553a74402a76b771b09ea40117a39566a79f52235b23db6358"
    )

    val nemotronEnglishProfiles = listOf(
        nemotronEnglishLowLatency,
        nemotronEnglishBalanced,
        nemotronEnglishAccuracy
    )

    val nemotronMultilingual = nemotronMultilingualPackage()

    val cards = listOf(
        RecognitionModelCard(
            id = "moonshine",
            runtimeId = "moonshine",
            displayName = "Moonshine",
            description = "Live English transcription optimized for on-device use.",
            transcription = TranscriptionBehavior.LIVE,
            recognitionLanguages = "English",
            performanceClasses = setOf(PerformanceClass.LIGHT, PerformanceClass.BALANCED),
            models = listOf(moonshineSmall, moonshineMedium)
        ),
        RecognitionModelCard(
            id = "nemotron",
            runtimeId = "nemotron",
            displayName = "Nemotron",
            description = "NVIDIA live recognition through Sherpa-ONNX.",
            transcription = TranscriptionBehavior.LIVE,
            recognitionLanguages = "English",
            performanceClasses = PerformanceClass.entries.toSet(),
            models = nemotronEnglishProfiles
        ),
        RecognitionModelCard(
            id = "nemotron-multilingual",
            runtimeId = "nemotron",
            displayName = "Nemotron 3.5",
            description = "Live multilingual dictation with Auto-detect and 28 selectable languages.",
            transcription = TranscriptionBehavior.LIVE,
            recognitionLanguages = "28 languages and Auto-detect",
            performanceClasses = setOf(PerformanceClass.DEMANDING),
            models = listOf(nemotronMultilingual)
        ),
        RecognitionModelCard(
            id = "parakeet",
            runtimeId = "parakeet",
            displayName = "Parakeet TDT",
            description = "Final-only NVIDIA recognition focused on accuracy.",
            transcription = TranscriptionBehavior.FINAL_ONLY,
            recognitionLanguages = "English",
            performanceClasses = setOf(PerformanceClass.DEMANDING),
            models = listOf(ParakeetModel.recognitionModel)
        ),
        RecognitionModelCard(
            id = "parakeet-unified",
            runtimeId = "parakeet_unified",
            displayName = "Parakeet Unified",
            description = "Buffered live English recognition that recomputes left context; choose Nemotron for lower-latency streaming.",
            transcription = TranscriptionBehavior.BUFFERED_LIVE,
            recognitionLanguages = "English",
            performanceClasses = setOf(PerformanceClass.DEMANDING),
            models = listOf(parakeetUnified)
        ),
        RecognitionModelCard(
            id = "whisper",
            runtimeId = "whisper_ggml",
            displayName = "Whisper (legacy)",
            description = "Legacy English and multilingual recognition that returns text after recording stops.",
            transcription = TranscriptionBehavior.FINAL_ONLY,
            recognitionLanguages = "English and multilingual",
            performanceClasses = PerformanceClass.entries.toSet(),
            models = emptyList()
        )
    )

    val defaultModel = moonshineSmall
    val models = cards.flatMap { it.models }

    fun modelFor(runtimeId: String, variantId: String? = null): RecognitionModel? =
        models.firstOrNull {
            it.runtimeId == runtimeId && (variantId == null || it.variantId == variantId)
        }

    private fun moonshinePackage(
        id: String,
        variantId: String,
        directoryName: String,
        displayName: String,
        description: String,
        performance: PerformanceClass,
        baseUrl: String,
        artifacts: List<RecognitionModelArtifact>
    ) = RecognitionModel(
        id = id,
        version = MOONSHINE_REVISION,
        runtimeId = "moonshine",
        variantId = variantId,
        directoryName = directoryName,
        source = MOONSHINE_SOURCE,
        licenseAttribution = "MIT",
        displayName = displayName,
        description = description,
        transcription = TranscriptionBehavior.LIVE,
        recognitionLanguages = "English",
        performanceClass = performance,
        artifacts = artifacts.map { it.copy(url = "$baseUrl/${it.url.substringAfterLast('/')}") }
    )

    private fun artifact(name: String, sizeBytes: Long, generation: String, sha256: String) =
        RecognitionModelArtifact(name, "https://placeholder.invalid/$name?generation=$generation", sizeBytes, sha256)

    private fun nemotronPackage(
        latencyMs: Int,
        variantId: String,
        displayName: String,
        description: String,
        performanceClass: PerformanceClass,
        encoderSizeBytes: Long,
        encoderSha256: String,
        archiveSizeBytes: Long,
        archiveSha256: String
    ): RecognitionModel {
        val directory =
            "sherpa-onnx-nemotron-speech-streaming-en-0.6b-${latencyMs}ms-int8-$NEMOTRON_VERSION"
        val archiveUrl =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/$directory.tar.bz2"
        fun artifact(name: String, sizeBytes: Long, sha256: String) =
            RecognitionModelArtifact(name, archiveUrl, sizeBytes, sha256)

        return RecognitionModel(
            id = "nemotron-speech-streaming-en-0.6b-${latencyMs}ms",
            version = NEMOTRON_VERSION,
            runtimeId = "nemotron",
            variantId = variantId,
            directoryName = directory,
            source = "NVIDIA Nemotron via k2-fsa/sherpa-onnx",
            licenseAttribution = "NVIDIA Open Model License",
            displayName = displayName,
            description = description,
            transcription = TranscriptionBehavior.LIVE,
            recognitionLanguages = "English",
            performanceClass = performanceClass,
            artifacts = listOf(
                artifact("encoder.int8.onnx", encoderSizeBytes, encoderSha256),
                artifact("decoder.int8.onnx", 7_257_753, "0be9702c2f427a2b6bb241d298e0d3836a558de1f5b9fd3018f1cce6e2b3fa98"),
                artifact("joiner.int8.onnx", 1_735_862, "a35eac38a22ebceb04d230ed7afe0d68f446ba6914a036b97f14fece95967e23"),
                artifact("tokens.txt", 8_952, "dc0b4584ab2e4ddbf888425c076c61b736e7356a015250db7d307e6f1a8188ff")
            ),
            archive = RecognitionModelArtifact(
                name = "$directory.tar.bz2",
                url = archiveUrl,
                sizeBytes = archiveSizeBytes,
                sha256 = archiveSha256
            ),
            archiveRoot = directory
        )
    }

    private fun parakeetUnifiedArtifact(name: String, sizeBytes: Long, sha256: String) =
        RecognitionModelArtifact(
            name = name,
            url = "https://huggingface.co/$PARAKEET_UNIFIED_REPOSITORY/resolve/$PARAKEET_UNIFIED_VERSION/$name?download=true",
            sizeBytes = sizeBytes,
            sha256 = sha256
        )

    private fun nemotronMultilingualPackage(): RecognitionModel {
        val directory =
            "sherpa-onnx-nemotron-3.5-asr-streaming-0.6b-560ms-int8-$NEMOTRON_MULTILINGUAL_VERSION"
        val baseUrl =
            "https://huggingface.co/csukuangfj2/$directory/resolve/$NEMOTRON_MULTILINGUAL_REVISION"
        fun artifact(name: String, sizeBytes: Long, sha256: String) =
            RecognitionModelArtifact(name, "$baseUrl/$name?download=true", sizeBytes, sha256)

        return RecognitionModel(
            id = "nemotron-3.5-asr-streaming-0.6b-560ms",
            version = NEMOTRON_MULTILINGUAL_VERSION,
            runtimeId = "nemotron",
            variantId = "multilingual",
            directoryName = directory,
            source = "NVIDIA Nemotron 3.5 (OpenMDW 1.1), Sherpa-ONNX export by k2-fsa",
            licenseAttribution = "OpenMDW 1.1",
            displayName = "Nemotron 3.5 Multilingual",
            description = "560 ms live transcription with explicit language selection or Auto-detect.",
            transcription = TranscriptionBehavior.LIVE,
            recognitionLanguages = "28 languages and Auto-detect",
            performanceClass = PerformanceClass.DEMANDING,
            artifacts = listOf(
                artifact("encoder.int8.onnx", 657_601_403, "012e9321373af99021415e0b0eb3ec827b4be3153be6f30d9b448fe65e896e68"),
                artifact("decoder.int8.onnx", 14_978_075, "19f9c98fc6d0a2c33a65a43b36fdb2e914c26c0aa9764be3aebc502a1e982fb0"),
                artifact("joiner.int8.onnx", 9_504_438, "4101c7c679a0bc30483794b27a059e34e79232aa2068d78d51231a22c8b0d7ce"),
                artifact("tokens.txt", 131_440, "729cc103155bafa785f9cd45746cd41cabe97eab7182fc04d594129587958f8a"),
                artifact("test_wavs/en.wav", 228_908, "eb1eb008904465b74c304aad8342e8c7d3c6e61ffe9f66adcaca9cf0f76a93f4"),
                artifact("test_wavs/ja.wav", 719_916, "780f95a86ba6cc33a4431fcafeacd213417dfa0a6613f93e4400c18f4dd467b0")
            )
        )
    }

}

class SelectedModelDeletionException : IllegalStateException(
    "Select another installed recognition model before deleting this model."
)

class RecognitionModelStore(
    private val rootDirectory: File,
    private val hashFile: (File) -> String = ::sha256
) {
    fun modelDirectory(model: RecognitionModel) = File(rootDirectory, model.directoryName)

    fun isInstalled(model: RecognitionModel, verifyHashes: Boolean = false): Boolean {
        val directory = modelDirectory(model)
        val marker = File(directory, model.completionMarker)
        if (!marker.isFile) return false

        val expectedMarker = "${model.id}@${model.version}"
        val markerValue = runCatching { marker.readText() }.getOrNull()
        if (markerValue != expectedMarker) return false
        if (!artifactsValid(model, verifyHashes)) {
            marker.delete()
            return false
        }
        return true
    }

    fun completeInstall(model: RecognitionModel): Boolean {
        val marker = File(modelDirectory(model), model.completionMarker)
        marker.delete()
        if (!artifactsValid(model, verifyHashes = true)) return false
        marker.writeText("${model.id}@${model.version}")
        return true
    }

    fun invalidate(model: RecognitionModel) {
        File(modelDirectory(model), model.completionMarker).delete()
    }

    fun select(model: RecognitionModel, updateSelection: () -> Unit) {
        check(isInstalled(model)) { "${model.displayName} is not installed" }
        updateSelection()
    }

    suspend fun delete(
        model: RecognitionModel,
        selectedModelId: String?,
        releaseRuntime: suspend (RecognitionModel) -> Unit
    ) {
        if (model.id == selectedModelId) throw SelectedModelDeletionException()
        releaseRuntime(model)
        check(modelDirectory(model).deleteRecursively()) {
            "Failed to delete ${model.displayName}"
        }
    }

    private fun artifactsValid(
        model: RecognitionModel,
        verifyHashes: Boolean,
        directory: File = modelDirectory(model)
    ): Boolean {
        return model.artifacts.all { artifact ->
            val file = File(directory, artifact.name)
            file.isFile && file.length() == artifact.sizeBytes &&
                (!verifyHashes || hashFile(file) == artifact.sha256)
        }
    }
}

package org.futo.voiceinput.recognition

import org.futo.voiceinput.parakeet.ParakeetModel
import java.io.File
import java.security.MessageDigest

enum class TranscriptionBehavior(val label: String) {
    LIVE("Live transcription"),
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
    val sourceUrl: String,
    val displayName: String,
    val description: String,
    val transcription: TranscriptionBehavior,
    val recognitionLanguages: String,
    val performanceClass: PerformanceClass,
    val artifacts: List<RecognitionModelArtifact>,
    val completionMarker: String = ".download_complete"
) {
    val transferBytes = artifacts.sumOf { it.sizeBytes }
    val requiredFreeSpaceBytes = transferBytes
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
            id = "whisper",
            runtimeId = "whisper_ggml",
            displayName = "Whisper (legacy)",
            description = "Existing English and multilingual offline models.",
            transcription = TranscriptionBehavior.LIVE,
            recognitionLanguages = "English and multilingual",
            performanceClasses = PerformanceClass.entries.toSet(),
            models = emptyList()
        )
    )

    val defaultModel = moonshineSmall
    val models = cards.flatMap { it.models }

    fun modelFor(runtimeId: String, variantId: String? = null): RecognitionModel? =
        models.firstOrNull { it.runtimeId == runtimeId && it.variantId == variantId }

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
        sourceUrl = "https://github.com/moonshine-ai/moonshine",
        displayName = displayName,
        description = description,
        transcription = TranscriptionBehavior.LIVE,
        recognitionLanguages = "English",
        performanceClass = performance,
        artifacts = artifacts.map { it.copy(url = "$baseUrl/${it.url.substringAfterLast('/')}") }
    )

    private fun artifact(name: String, sizeBytes: Long, generation: String, sha256: String) =
        RecognitionModelArtifact(name, "https://placeholder.invalid/$name?generation=$generation", sizeBytes, sha256)

}

class SelectedModelDeletionException : IllegalStateException(
    "Select another installed recognition model before deleting this model."
)

class RecognitionModelStore(
    private val rootDirectory: File,
    private val releaseRuntime: (RecognitionModel) -> Unit = {}
) {
    fun modelDirectory(model: RecognitionModel) = File(rootDirectory, model.directoryName)

    fun isInstalled(model: RecognitionModel, verifyHashes: Boolean = false): Boolean {
        val directory = modelDirectory(model)
        val marker = File(directory, model.completionMarker)
        if (!marker.isFile) return false

        val expectedMarker = "${model.id}@${model.version}"
        val markerValue = runCatching { marker.readText() }.getOrNull()
        if (markerValue == "ok" && artifactsValid(model, verifyHashes = true)) {
            marker.writeText(expectedMarker)
            return true
        }
        if (markerValue != expectedMarker || !artifactsValid(model, verifyHashes)) {
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

    fun select(model: RecognitionModel, updateSelection: () -> Unit) {
        check(isInstalled(model)) { "${model.displayName} is not installed" }
        updateSelection()
    }

    fun delete(model: RecognitionModel, selectedModelId: String?) {
        if (model.id == selectedModelId) throw SelectedModelDeletionException()
        releaseRuntime(model)
        check(modelDirectory(model).deleteRecursively()) {
            "Failed to delete ${model.displayName}"
        }
    }

    private fun artifactsValid(
        model: RecognitionModel,
        verifyHashes: Boolean
    ): Boolean {
        val directory = modelDirectory(model)
        return model.artifacts.all { artifact ->
            val file = File(directory, artifact.name)
            file.isFile && file.length() == artifact.sizeBytes &&
                (!verifyHashes || file.sha256() == artifact.sha256)
        }
    }
}

private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

package org.futo.voiceinput.recognition

import java.io.File

data class RecognitionModelSelection(
    val runtimeId: String,
    val moonshineVariantId: String? = null,
    val nemotronVariantId: String? = null
)

data class RecognitionModelReadiness(
    val model: RecognitionModel,
    val isReady: Boolean
)

class RecognitionModelLifecycle(
    private val store: RecognitionModelStore,
    private val models: List<RecognitionModel> = RecognitionModelCatalog.models,
    private val isBundled: (RecognitionModel) -> Boolean = { false }
) {
    fun readiness(
        selection: RecognitionModelSelection,
        verifyHashes: Boolean = false
    ): RecognitionModelReadiness? {
        val variantId = when (selection.runtimeId) {
            "moonshine" -> selection.moonshineVariantId
            "nemotron" -> selection.nemotronVariantId
            else -> null
        }
        val model = models.firstOrNull {
            it.runtimeId == selection.runtimeId && it.variantId == variantId
        } ?: return null
        return RecognitionModelReadiness(
            model = model,
            isReady = isReady(model, verifyHashes)
        )
    }

    fun isReady(model: RecognitionModel, verifyHashes: Boolean = false): Boolean =
        isBundled(model) || store.isInstalled(model, verifyHashes)

    companion object {
        fun create(rootDirectory: File, parakeetBundled: Boolean) = RecognitionModelLifecycle(
            store = RecognitionModelStore(rootDirectory),
            isBundled = { parakeetBundled && it.runtimeId == "parakeet" }
        )
    }
}

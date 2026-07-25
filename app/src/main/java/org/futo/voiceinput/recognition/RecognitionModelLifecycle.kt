package org.futo.voiceinput.recognition

import android.content.Context
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.futo.voiceinput.WhisperGGMLBackend
import org.futo.voiceinput.backend.SpeechBackend
import org.futo.voiceinput.ml.RunState
import org.futo.voiceinput.moonshine.MoonshineBackend
import org.futo.voiceinput.moonshine.getSelectedMoonshineModelVariant
import org.futo.voiceinput.nemotron.SherpaStreamingBackend
import org.futo.voiceinput.parakeet.acquireParakeetRuntime
import org.futo.voiceinput.parakeet.parakeetUnifiedBackend
import org.futo.voiceinput.parakeet.releaseParakeetArtifacts
import org.futo.voiceinput.parakeet.releaseParakeetRuntime
import org.futo.voiceinput.settings.MOONSHINE_MODEL_VARIANT
import org.futo.voiceinput.settings.NEMOTRON_PROFILE
import org.futo.voiceinput.settings.SPEECH_BACKEND
import org.futo.voiceinput.settings.SpeechBackendType
import org.futo.voiceinput.settings.setSettingBlocking
import org.futo.voiceinput.settings.toSpeechBackendType
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

data class RecognitionRuntimeCallbacks(
    val onStatusUpdate: (RunState) -> Unit,
    val onPartialDecode: (String) -> Unit,
    val forceLanguageProvider: () -> String?
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

    fun selectionFor(model: RecognitionModel) = RecognitionModelSelection(
        runtimeId = model.runtimeId,
        moonshineVariantId = model.variantId.takeIf { model.runtimeId == "moonshine" },
        nemotronVariantId = model.variantId.takeIf { model.runtimeId == "nemotron" }
    )

    fun select(model: RecognitionModel, updateSelection: (RecognitionModelSelection) -> Unit) {
        check(isReady(model)) { "${model.displayName} is not installed" }
        updateSelection(selectionFor(model))
    }

    fun completeInstallation(
        model: RecognitionModel,
        updateSelection: (RecognitionModelSelection) -> Unit
    ): Boolean {
        if (!store.completeInstall(model)) return false
        updateSelection(selectionFor(model))
        return true
    }

    suspend fun delete(model: RecognitionModel, selectedModelId: String?) {
        store.delete(model, selectedModelId, ::releaseArtifacts)
    }

    suspend fun releaseArtifacts(model: RecognitionModel) = runtimeMutex.withLock {
        releaseRuntimeArtifacts(model)
    }

    private suspend fun releaseRuntimeArtifacts(model: RecognitionModel) {
        val runtimes = activeRuntimes.filterValues { it == model.id }.keys.toList().also {
            it.forEach(activeRuntimes::remove)
        }
        if (!releaseParakeetArtifacts(model.runtimeId)) {
            runtimes.forEach { it.close() }
        }
    }

    suspend fun load(
        context: Context,
        selection: RecognitionModelSelection,
        callbacks: RecognitionRuntimeCallbacks
    ): SpeechBackend = runtimeMutex.withLock {
        context.updateRecognitionModelSelection(selection)
        val selectedModelId = readiness(selection)?.model?.id
        val backend = when (selection.runtimeId.toSpeechBackendType()) {
            SpeechBackendType.Parakeet -> acquireParakeetRuntime(context)
            SpeechBackendType.ParakeetUnified -> parakeetUnifiedBackend()
            SpeechBackendType.Nemotron -> SherpaStreamingBackend()
            SpeechBackendType.Moonshine -> MoonshineBackend(context.getSelectedMoonshineModelVariant())
            SpeechBackendType.WhisperGGML -> WhisperGGMLBackend(
                callbacks.onStatusUpdate,
                callbacks.onPartialDecode,
                callbacks.forceLanguageProvider
            )
        }
        if (selection.runtimeId != SpeechBackendType.Parakeet.id) {
            activeRuntimes.filterValues {
                models.firstOrNull { model -> model.id == it }?.runtimeId ==
                    SpeechBackendType.Parakeet.id
            }.keys.toList().forEach {
                activeRuntimes.remove(it)
            }
            releaseParakeetArtifacts(SpeechBackendType.Parakeet.id)
            backend.loadOrCloseOnFailure { load(context) }
        }
        selectedModelId?.let { activeRuntimes[backend] = it }
        backend
    }

    suspend fun release(
        backend: SpeechBackend,
        scope: LifecycleCoroutineScope,
        keepWarm: Boolean = false,
        timeoutMs: Long = 0L
    ) = runtimeMutex.withLock {
        activeRuntimes.remove(backend)
        if (!releaseParakeetRuntime(backend, scope, keepWarm, timeoutMs)) {
            backend.close()
        }
    }

    companion object {
        private val runtimeMutex = Mutex()
        private val activeRuntimes = mutableMapOf<SpeechBackend, String>()

        fun create(rootDirectory: File, parakeetBundled: Boolean) = RecognitionModelLifecycle(
            store = RecognitionModelStore(rootDirectory),
            isBundled = { parakeetBundled && it.runtimeId == "parakeet" }
        )
    }
}

internal suspend fun SpeechBackend.loadOrCloseOnFailure(
    load: suspend SpeechBackend.() -> Unit
) {
    try {
        load()
    } catch (failure: Throwable) {
        try {
            withContext(NonCancellable) { close() }
        } catch (closeFailure: Throwable) {
            failure.addSuppressed(closeFailure)
        }
        throw failure
    }
}

fun Context.updateRecognitionModelSelection(selection: RecognitionModelSelection) {
    selection.moonshineVariantId?.let {
        setSettingBlocking(MOONSHINE_MODEL_VARIANT.key, it)
    }
    selection.nemotronVariantId?.let {
        setSettingBlocking(NEMOTRON_PROFILE.key, it)
    }
    setSettingBlocking(SPEECH_BACKEND.key, selection.runtimeId)
}

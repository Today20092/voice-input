package org.futo.voiceinput.recognition

import android.content.Context
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import org.futo.voiceinput.WhisperGGMLBackend
import org.futo.voiceinput.backend.SpeechBackend
import org.futo.voiceinput.cohere.CohereBackend
import org.futo.voiceinput.ml.RunState
import org.futo.voiceinput.moonshine.MoonshineBackend
import org.futo.voiceinput.moonshine.getSelectedMoonshineModelVariant
import org.futo.voiceinput.nemotron.SherpaStreamingBackend
import org.futo.voiceinput.parakeet.acquireParakeetRuntime
import org.futo.voiceinput.parakeet.orukeetBackend
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

enum class RecognitionModelRepairReason { MISSING, INVALID_OR_INCOMPATIBLE }

data class RecognitionModelReadiness(
    val model: RecognitionModel,
    val isReady: Boolean,
    val installedModel: RecognitionModel? = null,
    val optionalUpgrade: RecognitionModel? = null,
    val repairReason: RecognitionModelRepairReason? = null
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
        val installed = if (isBundled(model)) model else store.installedVersion(model, verifyHashes)
        return RecognitionModelReadiness(
            model = model,
            isReady = installed != null,
            installedModel = installed,
            optionalUpgrade = installed?.takeIf { it.version != model.version }?.let { model },
            repairReason = if (installed != null) null else if (store.modelDirectory(model).exists())
                RecognitionModelRepairReason.INVALID_OR_INCOMPATIBLE else RecognitionModelRepairReason.MISSING
        )
    }

    fun isReady(model: RecognitionModel, verifyHashes: Boolean = false): Boolean =
        isBundled(model) || store.installedVersion(model, verifyHashes) != null

    fun downloadDirectory(model: RecognitionModel): File = store.stagingDirectory(model)

    suspend fun acquireSession(model: RecognitionModel): AutoCloseable = runtimeMutex.withLock {
        val token = Any()
        synchronized(activeSessions) { activeSessions[token] = model.id }
        AutoCloseable {
            synchronized(activeSessions) { activeSessions.remove(token) }
            runtimeChanges.update { it + 1 }
        }
    }

    private fun modelInUse(model: RecognitionModel): Boolean =
        activeRuntimes.values.any { it == model.id } ||
            synchronized(activeSessions) { activeSessions.values.any { it == model.id } }

    suspend fun activateInstallation(model: RecognitionModel, updateSelection: (RecognitionModelSelection) -> Unit) {
        try {
            check(store.validateStaged(model)) { "Downloaded model failed validation" }
            publishChange()
            while (true) {
                val revision = runtimeChanges.value
                val activated = runtimeMutex.withLock {
                    currentCoroutineContext().ensureActive()
                    if (modelInUse(model)) false else {
                        val wasInstalled = store.installedVersion(model) != null
                        releaseRuntimeArtifacts(model)
                        store.activateStaged(model)
                        if (!wasInstalled) updateSelection(selectionFor(model))
                        true
                    }
                }
                if (activated) break
                runtimeChanges.first { it != revision }
            }
        } finally {
            publishChange()
        }
    }

    fun selectionFor(model: RecognitionModel) = RecognitionModelSelection(
        runtimeId = model.runtimeId,
        moonshineVariantId = model.variantId.takeIf { model.runtimeId == "moonshine" },
        nemotronVariantId = model.variantId.takeIf { model.runtimeId == "nemotron" }
    )

    fun select(model: RecognitionModel, updateSelection: (RecognitionModelSelection) -> Unit) {
        check(isReady(model)) { "${model.displayName} is not installed" }
        updateSelection(selectionFor(model))
        publishChange()
    }

    suspend fun delete(model: RecognitionModel, selectedModelId: String?) {
        try {
            runtimeMutex.withLock {
                check(!modelInUse(model)) { "Model is in use by dictation" }
                store.delete(model, selectedModelId, ::releaseRuntimeArtifacts)
            }
        } finally { publishChange() }
    }

    fun invalidateInstallation(model: RecognitionModel) {
        try { store.invalidate(model) } finally { publishChange() }
    }

    private suspend fun releaseRuntimeArtifacts(model: RecognitionModel) {
        releaseParakeetArtifacts(model.runtimeId)
    }

    suspend fun load(
        context: Context,
        selection: RecognitionModelSelection,
        callbacks: RecognitionRuntimeCallbacks
    ): SpeechBackend = acquireRuntime(selection) {
        context.updateRecognitionModelSelection(selection)
        val backend = when (selection.runtimeId.toSpeechBackendType()) {
            SpeechBackendType.Parakeet -> acquireParakeetRuntime(context)
            SpeechBackendType.Orukeet -> orukeetBackend()
            SpeechBackendType.ParakeetUnified -> parakeetUnifiedBackend()
            SpeechBackendType.ParakeetRedux -> org.futo.voiceinput.redux.ReduxBackend()
            SpeechBackendType.Nemotron -> SherpaStreamingBackend()
            SpeechBackendType.Cohere -> CohereBackend()
            SpeechBackendType.Moonshine -> MoonshineBackend(context.getSelectedMoonshineModelVariant())
            SpeechBackendType.WhisperGGML -> WhisperGGMLBackend(
                callbacks.onStatusUpdate,
                callbacks.onPartialDecode,
                callbacks.forceLanguageProvider
            )
        }
        if (selection.runtimeId != SpeechBackendType.Parakeet.id) {
            models.firstOrNull { it.runtimeId == SpeechBackendType.Parakeet.id }?.let {
                if (!modelInUse(it)) releaseParakeetArtifacts(SpeechBackendType.Parakeet.id)
            }
            backend.loadOrCloseOnFailure { load(context) }
        }
        backend
    }

    internal suspend fun acquireRuntime(
        selection: RecognitionModelSelection,
        load: suspend (RecognitionModelReadiness?) -> SpeechBackend
    ): SpeechBackend = runtimeMutex.withLock {
        val readiness = readiness(selection)
        val backend = try {
            load(readiness)
        } catch (failure: Exception) {
            if (failure !is CancellationException) {
                readiness?.installedModel?.let(::invalidateInstallation)
            }
            throw failure
        }
        readiness?.model?.id?.let { activeRuntimes[backend] = it }
        backend
    }

    internal suspend fun releaseRuntime(backend: SpeechBackend, close: suspend () -> Unit): Unit = runtimeMutex.withLock {
        try {
            close()
            activeRuntimes.remove(backend)
            Unit
        } finally { runtimeChanges.update { it + 1 } }
    }

    suspend fun release(
        backend: SpeechBackend,
        scope: LifecycleCoroutineScope,
        keepWarm: Boolean = false,
        timeoutMs: Long = 0L
    ) = releaseRuntime(backend) {
        if (!releaseParakeetRuntime(backend, scope, keepWarm, timeoutMs)) backend.close()
    }

    companion object {
        private val runtimeMutex = Mutex()
        private val activeRuntimes = mutableMapOf<SpeechBackend, String>()
        private val activeSessions = mutableMapOf<Any, String>()
        private val runtimeChanges = MutableStateFlow(0L)
        private val changes = MutableStateFlow(0L)
        val invalidations = changes.asStateFlow()
        fun publishChange() { changes.update { it + 1 } }

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

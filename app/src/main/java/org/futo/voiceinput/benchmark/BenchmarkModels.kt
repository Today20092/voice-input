package org.futo.voiceinput.benchmark

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.futo.voiceinput.*
import org.futo.voiceinput.backend.SpeechBackend
import org.futo.voiceinput.downloader.EXTRA_PRESERVE_MODEL_SELECTION
import org.futo.voiceinput.downloader.recognitionModelDownloadIntent
import org.futo.voiceinput.ggml.DecodingMode
import org.futo.voiceinput.ggml.WhisperGGML
import org.futo.voiceinput.ml.loadGGMLModel
import org.futo.voiceinput.recognition.*
import org.futo.voiceinput.settings.*
import java.io.File

data class BenchmarkModel(val id: String, val name: String, val managed: RecognitionModel? = null, val whisper: ModelData? = null) {
    val languages: String get() = managed?.recognitionLanguages ?: if (whisper in ENGLISH_MODELS) "English" else "Multilingual"
    val storageBytes: Long get() = managed?.artifacts?.sumOf { it.sizeBytes } ?: requireNotNull(whisper).sizeBytes
    val transferBytes: Long get() = managed?.transferBytes ?: storageBytes

    fun installed(context: Context): Boolean = managed?.let {
        RecognitionModelLifecycle.create(context.filesDir, BuildConfig.BUNDLE_PARAKEET_MODEL).isReady(it)
    } ?: requireNotNull(whisper).let {
        it.ggml.is_builtin_asset || File(context.filesDir, it.ggml.ggml_file).let { file -> file.isFile && file.length() == it.sizeBytes }
    }

    fun downloadIntent(context: Context): Intent = (managed?.let { context.recognitionModelDownloadIntent(it) }
        ?: context.modelDownloadIntent(listOf(requireNotNull(whisper))))
        .putExtra(EXTRA_PRESERVE_MODEL_SELECTION, true)

    fun identity(context: Context, language: String = "en"): Map<String, String> {
        val model = managed?.let { RecognitionModelStore(context.filesDir).installedVersion(it) ?: it }
        return mapOf(
            "name" to name, "runtime" to (model?.runtimeId ?: "whisper_ggml"),
            "variant" to (model?.variantId ?: id), "version" to (model?.version ?: requireNotNull(whisper).ggml.digest),
            "artifacts" to (model?.artifacts?.joinToString { "${it.name}:${it.sha256}" } ?: requireNotNull(whisper).ggml.digest),
            "language" to if (model?.runtimeId in setOf("parakeet", "orukeet", "parakeet_redux", "whistle")) "auto (recording language $language)" else language,
            "decoding" to "runtime default; Whisper beam search 5, no glossary",
            "runtimeVersions" to "Sherpa-ONNX ${BuildConfig.SHERPA_ONNX_VERSION}; Moonshine ${BuildConfig.MOONSHINE_RUNTIME_VERSION}; ExecuTorch 1.2.0; native runtime revision in app build"
        ) + if (model?.runtimeId == "whistle") mapOf(
            "timingNotice" to "Every Whistle pass includes worker startup and model loading; the model is not kept loaded between passes.",
            "memoryNotice" to "Sampled memory covers the app process and excludes the Whistle worker."
        ) else emptyMap()
    }

    fun recognizer(context: Context, language: String = "en"): BenchmarkRecognizer {
        val appContext = context.applicationContext
        return object : BenchmarkRecognizer {
            private var backend: SpeechBackend? = null
            override suspend fun load() {
                check(installed(appContext)) { "Model is not installed" }
                backend = this@BenchmarkModel.backend(appContext, language)
                requireNotNull(backend).load(appContext)
            }
            override suspend fun transcribe(samples: FloatArray) = requireNotNull(backend).transcribe(samples)
            override suspend fun close() { backend?.close(); backend = null }
        }
    }

    fun supportsLanguage(language: String): Boolean = when {
        whisper in ENGLISH_MODELS -> language == "en"
        managed?.recognitionLanguages == "English" -> language == "en"
        managed?.runtimeId == "cohere" -> org.futo.voiceinput.cohere.CohereLanguage.entries.any { it.id == language }
        managed?.runtimeId == "whistle" -> language in org.futo.voiceinput.whistle.WhistleModel.languageCodes
        managed?.runtimeId == "nemotron" -> org.futo.voiceinput.nemotron.NEMOTRON_MULTILINGUAL_LANGUAGES.any { it.id == language }
        else -> true
    }

    fun backend(context: Context, language: String = "en", onPartial: (String) -> Unit = {}): SpeechBackend {
        require(supportsLanguage(language)) { "Model does not support the selected recording language" }
        if (whisper != null) return object : SpeechBackend {
            private var engine: WhisperGGML? = null
            override suspend fun load(context: Context) = withContext(Dispatchers.IO) {
                if (!whisper.ggml.is_builtin_asset) check(BenchmarkStore.hashFile(File(context.filesDir, whisper.ggml.ggml_file)) == whisper.ggml.digest)
                engine = loadGGMLModel(context, whisper, onPartial)
            }
            override suspend fun transcribe(samples: FloatArray): String = requireNotNull(engine).infer(
                samples, "", arrayOf(language), emptyArray(), DecodingMode.BeamSearch5, true)
            override suspend fun close() { engine?.close(); engine = null }
        }
        val model = requireNotNull(managed)
        return managedRecognitionBackend(context, model.selection(), languageOverride = language)
    }

    suspend fun select(context: Context) {
        check(installed(context)) { "Download this model first" }
        managed?.let { model ->
            RecognitionModelLifecycle.create(context.filesDir, BuildConfig.BUNDLE_PARAKEET_MODEL)
                .select(model) { context.updateRecognitionModelSelection(it) }
        } ?: requireNotNull(whisper).let { model ->
            val english = model in ENGLISH_MODELS
            context.setSetting(if (english) ENGLISH_MODEL_INDEX else MULTILINGUAL_MODEL_INDEX,
                (if (english) ENGLISH_MODELS else MULTILINGUAL_MODELS).indexOf(model))
            context.setSetting(ENABLE_MULTILINGUAL, !english)
            context.setSetting(USE_LANGUAGE_SPECIFIC_MODELS, english)
            context.setSetting(LANGUAGE_TOGGLES, setOf("en"))
            context.setSetting(SPEECH_BACKEND, "whisper_ggml")
        }
    }

    companion object {
        fun all(): List<BenchmarkModel> = RecognitionModelCatalog.cards.flatMap { card ->
            card.models.map { BenchmarkModel(it.id,
                if (it.displayName.startsWith(card.displayName)) it.displayName else "${card.displayName}: ${it.displayName}", managed = it) }
        } + (ENGLISH_MODELS + MULTILINGUAL_MODELS).map {
            BenchmarkModel("whisper-${it.ggml.ggml_file.substringBefore('.')}", "Whisper: ${it.name}", whisper = it)
        }
    }
}

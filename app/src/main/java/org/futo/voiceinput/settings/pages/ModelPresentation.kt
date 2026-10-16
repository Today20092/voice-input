package org.futo.voiceinput.settings.pages

import org.futo.voiceinput.ModelData
import org.futo.voiceinput.recognition.RecognitionModel
import org.futo.voiceinput.ENGLISH_MODELS
import org.futo.voiceinput.MULTILINGUAL_MODELS
import org.futo.voiceinput.recognition.RecognitionModelCard
import org.futo.voiceinput.recognition.RecognitionModelCatalog
import java.util.Locale

data class ModelPresentation(
    val title: String,
    val summary: String,
    val description: String,
    val fields: List<Pair<String, String>>,
    val informationLinks: List<ModelInformationLink>
) {
    val compactSummary: String get() {
        val values = fields.toMap()
        val size = values.getValue("Download size")
        val download = if (size == "Included in app") size else "$size download"
        return "${values.getValue("Languages")} • $download\n" +
            values.getValue("Status")
    }
    val details: String get() = description + "\n\n" +
        fields.joinToString("\n") { (label, value) -> "$label: $value" }
}

data class ModelInformationLink(val label: String, val url: String)

fun modelFamiliesForDisplay(): Map<String, List<RecognitionModelCard>> =
    modelCardsForDisplay().groupBy { card ->
        when (card.runtimeId) {
            "parakeet", "parakeet_unified", "parakeet_redux" -> "Parakeet"
            "nemotron" -> "Nemotron"
            else -> card.displayName
        }
    }

fun modelCardsForDisplay(): List<RecognitionModelCard> = RecognitionModelCatalog.cards
    .sortedWith(compareBy<RecognitionModelCard> { it.transcription.ordinal }.thenBy { card ->
        if (card.models.isEmpty()) {
            (ENGLISH_MODELS + MULTILINGUAL_MODELS).minOf {
                if (it.ggml.is_builtin_asset) 0L else it.sizeBytes
            }
        } else card.models.minOf { it.transferBytes }
    })
    .map { card ->
        // These profiles share weights; tiny archive-size differences are not useful ordering.
        if (card.id == "nemotron") card
        else card.copy(models = card.models.sortedBy { model -> model.transferBytes })
    }

private fun modelInformationLinks(model: RecognitionModel): List<ModelInformationLink> {
    val publisher = when (model.runtimeId) {
        "moonshine" -> "https://github.com/moonshine-ai/moonshine"
        "nemotron" -> if (model.variantId == "multilingual")
            "https://huggingface.co/csukuangfj2/sherpa-onnx-nemotron-3.5-asr-streaming-0.6b-560ms-int8-2026-06-11"
        else "https://huggingface.co/nvidia/nemotron-speech-streaming-en-0.6b"
        "parakeet" -> "https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3"
        "orukeet" -> "https://huggingface.co/oruk/orukeet"
        "parakeet_redux" -> "https://huggingface.co/moondream/parakeet-redux"
        "phonon" -> "https://www.fermionresearch.com/models/phonon-2/"
        "parakeet_unified" -> "https://huggingface.co/csukuangfj2/sherpa-onnx-nemo-parakeet-unified-en-0.6b-int8-streaming-560ms"
        "cohere" -> "https://huggingface.co/CohereLabs/cohere-transcribe-03-2026"
        "asr4all" -> "https://huggingface.co/futo-org/${model.id}"
        else -> return emptyList()
    }
    val artifactUrl = (model.archive ?: model.artifacts.first()).url
    val packagePage = when {
        artifactUrl.startsWith("https://huggingface.co/") && artifactUrl.contains("/resolve/") ->
            artifactUrl.substringBefore("/resolve/") + "/tree/" +
                artifactUrl.substringAfter("/resolve/").substringBefore('/')
        artifactUrl.startsWith("https://github.com/") && artifactUrl.contains("/releases/download/") ->
            artifactUrl.substringBefore("/releases/download/") + "/releases/tag/" +
                artifactUrl.substringAfter("/releases/download/").substringBefore('/')
        else -> null
    }
    return listOf(ModelInformationLink("Model info", publisher)) +
        listOfNotNull(packagePage?.let { ModelInformationLink("App model package", it) })
}

fun recognitionLanguageGuidance(model: RecognitionModel): String = when {
    model.runtimeId == "cohere" ->
        "${model.recognitionLanguages}. Choose the language before dictating. " +
            "Automatic language detection is not available, and mixed-language speech may be inaccurate."
    model.runtimeId == "nemotron" && model.variantId == "multilingual" ->
        "${model.recognitionLanguages}. Choose a recognition language or Auto-detect."
    model.runtimeId == "parakeet" || model.runtimeId == "orukeet" || model.runtimeId == "parakeet_redux" ->
        "${model.recognitionLanguages}. The model recognizes the spoken language automatically; " +
            "no manual language selector is available."
    else -> "${model.recognitionLanguages} only. " +
        "Language selection and automatic language detection are not available."
}

fun presentRecognitionModel(
    model: RecognitionModel,
    installed: Boolean,
    selected: Boolean
): ModelPresentation {
    val installedBytes = model.artifacts.sumOf { it.sizeBytes }
    val status = modelStatus(installed, selected)
    return ModelPresentation(
        title = model.displayName,
        summary = "${model.transcription.label} • ${model.recognitionLanguages}\n" +
            "Download ${model.transferBytes.megabytes()} • Installed ${installedBytes.megabytes()} • $status",
        description = model.description,
        fields = modelDetailFields(
            model.transcription.label, model.recognitionLanguages,
            model.transferBytes.megabytes(), installedBytes.megabytes(), status,
            model.performanceClass.label, model.source, model.licenseAttribution,
            model.version, "${model.artifacts.size} model " +
                if (model.artifacts.size == 1) "artifact" else "artifacts",
            recognitionLanguageGuidance(model)
        ) + if (model.runtimeId == "asr4all") listOf(
            "Runtime" to "ExecuTorch 1.2.0, XNNPACK INT8, one CPU thread",
            "Streaming profile" to "stream_c16r4, the publisher's default",
            "Text cleanup" to "Built-in punctuation, capitalization, and error correction. S1-mini is skipped."
        ) else if (model.runtimeId == "phonon") listOf(
            "Runtime" to "phonon-2-c, ARM64 CPU, four threads; dot-product acceleration when supported",
            "Release status" to "Experimental; Android speed and accuracy have not been verified"
        ) else emptyList(),
        informationLinks = modelInformationLinks(model)
    )
}

fun presentWhisperModel(
    model: ModelData,
    languages: String,
    installed: Boolean,
    selected: Boolean
): ModelPresentation {
    val sizeBytes = model.sizeBytes
    return ModelPresentation(
        title = model.name,
        summary = "Final-only transcription • $languages\n" +
            "Download ${if (model.ggml.is_builtin_asset) "Included (${sizeBytes.megabytes()})" else sizeBytes.megabytes()} • " +
            "Installed ${sizeBytes.megabytes()} • ${modelStatus(installed, selected)}",
        description = when (model.ggml.ggml_file) {
            "tiny_en_acft_q8_0.bin.not.tflite" ->
                "Turns English speech into text after you stop recording. The smallest legacy English option " +
                    "is included in the app and needs less memory and processing than the larger English options."
            "base_en_acft_q8_0.bin" ->
                "Turns English speech into text after you stop recording. It is slower and more accurate than " +
                    "English-39, with a larger download and more memory use."
            "small_en_acft_q8_0.bin" ->
                "Turns English speech into text after you stop recording. The largest legacy English option " +
                    "is slow and needs more storage and memory than the smaller English options."
            "tiny_acft_q8_0.bin" ->
                "Turns speech in the supported Whisper languages into text after you stop recording. " +
                    "The smallest legacy multilingual option needs less storage and memory, but is less accurate. " +
                    "Choose recognition languages in Languages settings."
            "base_acft_q8_0.bin" ->
                "Turns speech in the supported Whisper languages into text after you stop recording. " +
                    "The default legacy multilingual option needs more storage and memory than Multilingual-39, " +
                    "and less than Multilingual-244. Choose recognition languages in Languages settings."
            "small_acft_q8_0.bin" ->
                "Turns speech in the supported Whisper languages into text after you stop recording. " +
                    "The largest legacy multilingual option is slow and needs more storage and memory. " +
                    "Choose recognition languages in Languages settings."
            else -> "Turns speech into text after you stop recording. Choose recognition languages in Languages settings."
        },
        fields = modelDetailFields(
            "Final-only transcription", languages,
            if (model.ggml.is_builtin_asset) "Included in app" else sizeBytes.megabytes(),
            sizeBytes.megabytes(), modelStatus(installed, selected), "Not rated",
            "FUTO Voice Input legacy model catalog", "OpenAI Whisper and whisper.cpp (MIT)",
            model.ggml.ggml_file, "1 model artifact • Q8 GGML",
            "Configure recognition languages in Languages settings."
        ),
        informationLinks = listOf(
            ModelInformationLink("Model info", "https://github.com/openai/whisper"),
            ModelInformationLink("FUTO model integration", "https://github.com/futo-org/voice-input")
        )
    )
}

private fun modelDetailFields(
    transcription: String, languages: String, download: String, storage: String,
    status: String, performance: String, source: String, license: String,
    version: String, files: String, languageGuidance: String
): List<Pair<String, String>> = listOf(
    "Transcription" to transcription,
    "Languages" to languages,
    "Language selection" to languageGuidance,
    "Download size" to download,
    "Model size" to storage,
    "Status" to status,
    "Performance class" to performance,
    "Source" to source,
    "License/attribution" to license,
    "Version" to version,
    "Model files" to files
)

fun selectedRecognitionModelSummary(
    runtimeId: String,
    managedModel: RecognitionModel?,
    englishModel: ModelData,
    multilingualModel: ModelData,
    multilingualEnabled: Boolean
): String = if (runtimeId == "whisper_ggml") {
    buildString {
        append("Whisper • ")
        append(englishModel.name)
        if (multilingualEnabled) {
            append(" + ")
            append(multilingualModel.name)
        }
    }
} else {
    if (managedModel?.runtimeId == "nemotron" && managedModel.variantId != "multilingual") {
        "Nemotron • ${managedModel.displayName}"
    } else {
        managedModel?.displayName ?: runtimeId
    }
}

private fun modelStatus(installed: Boolean, selected: Boolean): String = when {
    selected && installed -> "Selected"
    selected -> "Selected • Download required"
    installed -> "Installed"
    else -> "Download required"
}

private fun Long.megabytes() = String.format(Locale.US, "%.1f MB", this / 1_000_000.0)

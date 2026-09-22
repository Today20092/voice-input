package org.futo.voiceinput.settings.pages

import org.futo.voiceinput.ModelData
import org.futo.voiceinput.recognition.RecognitionModel
import java.util.Locale

data class ModelPresentation(
    val title: String,
    val summary: String,
    val details: String
)

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
        details = "${model.description}\n\n" +
            "Source: ${model.source}\n" +
            "License/attribution: ${model.licenseAttribution}\n" +
            "Version: ${model.version}\n" +
            "Technical: ${model.performanceClass.label} • ${model.artifacts.size} model artifacts"
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
        details = "Returns text after recording stops.\n\n" +
            "Source: FUTO Voice Input legacy model catalog\n" +
            "License/attribution: OpenAI Whisper and whisper.cpp (MIT)\n" +
            "Version: ${model.ggml.ggml_file}\n" +
            "Technical: Q8 GGML • ${model.name.substringBefore(' ')}"
    )
}

fun selectedRecognitionModelSummary(
    runtimeId: String,
    managedModelName: String?,
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
    managedModelName ?: runtimeId
}

private fun modelStatus(installed: Boolean, selected: Boolean): String = when {
    selected && installed -> "Selected"
    selected -> "Selected • Download required"
    installed -> "Installed"
    else -> "Download required"
}

private fun Long.megabytes() = String.format(Locale.US, "%.1f MB", this / 1_000_000.0)

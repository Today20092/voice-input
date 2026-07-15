package org.futo.voiceinput.moonshine

enum class MoonshineModelVariant(
    val id: String,
    val directoryName: String,
    val baseUrl: String
) {
    Small(
        id = "small",
        directoryName = "moonshine-small-streaming-en",
        baseUrl = "https://download.moonshine.ai/model/small-streaming-en/quantized"
    ),
    Medium(
        id = "medium",
        directoryName = "moonshine-medium-streaming-en",
        baseUrl = "https://download.moonshine.ai/model/medium-streaming-en/quantized"
    )
}

fun String.toMoonshineModelVariant(): MoonshineModelVariant =
    MoonshineModelVariant.entries.firstOrNull { it.id == this } ?: MoonshineModelVariant.Small

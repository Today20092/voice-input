package org.futo.voiceinput.moonshine

enum class MoonshineModelVariant(val id: String) {
    Small("small"),
    Medium("medium")
}

fun String.toMoonshineModelVariant(): MoonshineModelVariant =
    MoonshineModelVariant.entries.firstOrNull { it.id == this } ?: MoonshineModelVariant.Small

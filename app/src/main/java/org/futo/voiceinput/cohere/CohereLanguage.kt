package org.futo.voiceinput.cohere

enum class CohereLanguage(val id: String, val displayName: String) {
    English("en", "English"),
    Arabic("ar", "Arabic"),
    Chinese("zh", "Mandarin Chinese"),
    Dutch("nl", "Dutch"),
    French("fr", "French"),
    German("de", "German"),
    Greek("el", "Greek"),
    Italian("it", "Italian"),
    Japanese("ja", "Japanese"),
    Korean("ko", "Korean"),
    Polish("pl", "Polish"),
    Portuguese("pt", "Portuguese"),
    Spanish("es", "Spanish"),
    Vietnamese("vi", "Vietnamese")
}

fun String.toCohereLanguage(): CohereLanguage =
    CohereLanguage.entries.firstOrNull { it.id == this } ?: CohereLanguage.English

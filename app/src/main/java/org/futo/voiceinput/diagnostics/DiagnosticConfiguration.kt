package org.futo.voiceinput.diagnostics

import kotlinx.serialization.Serializable
import java.util.Locale

/** Only effective technical choices belong here; absent values are unavailable, not defaults. */
@Serializable
data class DiagnosticConfiguration(
    val runtime: String? = null,
    val modelVersion: String? = null,
    val runtimeVersion: String? = null,
    val language: String? = null,
    val threads: Int? = null,
    val vadEnabled: Boolean? = null,
    val durationLimitEnabled: Boolean? = null
) {
    fun sanitized() = copy(
        runtime = runtime?.takeIf { it in runtimes },
        modelVersion = modelVersion?.takeIf { revision.matches(it) || it == "legacy" },
        runtimeVersion = runtimeVersion?.takeIf { revision.matches(it) },
        language = language?.lowercase(Locale.ROOT)?.takeIf {
            it == "auto" || (it.substringBefore('-') in languages &&
                (it.indexOf('-') == -1 || it.substringAfter('-') in countries))
        },
        threads = threads?.takeIf { it in 1..256 }
    )

    companion object {
        private val runtimes = setOf("parakeet", "orukeet", "parakeet_unified", "parakeet_redux",
            "phonon", "whistle", "nemotron", "cohere", "moonshine", "asr4all", "whisper_ggml")
        private val revision = Regex("(?:[0-9a-f]{7,64}|[0-9]{4}-[0-9]{2}-[0-9]{2}(?:-sha256)?|[0-9]+(?:\\.[0-9]+){1,3})")
        private val languages = Locale.getISOLanguages().toSet()
        private val countries = Locale.getISOCountries().map { it.lowercase(Locale.ROOT) }.toSet()
    }
}

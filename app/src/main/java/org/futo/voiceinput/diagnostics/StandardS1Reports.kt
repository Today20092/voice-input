package org.futo.voiceinput.diagnostics

import kotlinx.serialization.json.*

/** Projects existing S1 reports onto a small technical schema, never copying free-form native output. */
object StandardS1Reports {
    private val failures = setOf("timeout", "process_died", "bind_failed", "service_failure", "invalid_request",
        "model_not_installed", "non_english_bypass", "out_of_memory", "empty_output", "invalid_output",
        "cancelled", "runtime_error", "incomplete_validation", "unstable_output", "meaning_mismatch",
        "no_discovered_gpu", "native_failure", "benchmark_validation_failed", "unclassified")
    private val numbers = setOf("schemaVersion", "capturedAtEpochMs", "recordedAtEpochMs", "threads",
        "inputApproxWords", "outputCharacters", "chunkCount", "totalMs", "pssKb", "nativeHeapBytes",
        "javaUsedBytes", "thermalStatus")
    private val categories = mapOf(
        "runtimeRequested" to setOf("auto", "cpu", "opencl"),
        "runtimeSelected" to setOf("cpu", "opencl"),
        "styling" to setOf("casual", "semi-casual", "semi-formal", "formal"),
        "structure" to setOf("prose", "lists"), "context" to setOf("general", "email"),
        "outcome" to setOf("success", "fallback", "failed", "applied", "valid_empty"),
        "errorCategory" to failures
    )

    fun project(text: String): String? = runCatching {
        val source = DiagnosticStore.json.parseToJsonElement(text).jsonObject
        buildJsonObject {
            numbers.forEach { key -> (source[key] as? JsonPrimitive)?.longOrNull?.let { put(key, it) } }
            listOf("warm", "nativeLibraryDirPresent").forEach { key ->
                (source[key] as? JsonPrimitive)?.booleanOrNull?.let { put(key, it) }
            }
            categories.forEach { (key, allowed) ->
                (source[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content?.let {
                    put(key, it.takeIf(allowed::contains) ?: "unclassified")
                }
            }
            for (field in listOf("failures", "skippedCandidates")) {
                (source[field] as? JsonObject)?.let { values ->
                    put(field, buildJsonObject {
                        values.forEach { (key, value) ->
                            if (key.matches(Regex("(cpu|opencl)(/[0-9]{1,3})?"))) {
                                val category = (value as? JsonPrimitive)?.content
                                put(key, category?.takeIf(failures::contains) ?: "unclassified")
                            }
                        }
                    })
                }
            }
            (source["measurementsMs"] as? JsonObject)?.let { measurements ->
                put("measurementsMs", buildJsonObject {
                    measurements.forEach { (key, value) ->
                        if (key.matches(Regex("(cpu|opencl)/[0-9]{1,3}"))) {
                            (value as? JsonPrimitive)?.longOrNull?.let { put(key, it) }
                        }
                    }
                })
            }
            put("transcriptIncluded", false)
        }.toString()
    }.getOrNull()
}

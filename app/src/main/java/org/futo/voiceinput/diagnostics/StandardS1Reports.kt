package org.futo.voiceinput.diagnostics

import kotlinx.serialization.json.*

/** Projects existing S1 reports onto a small technical schema, never copying free-form native output. */
object StandardS1Reports {
    private val failures = setOf("timeout", "process_died", "bind_failed", "service_failure", "invalid_request",
        "model_not_installed", "non_english_bypass", "out_of_memory", "empty_output", "invalid_output",
        "cancelled", "runtime_error", "incomplete_validation", "unstable_output", "meaning_mismatch",
        "no_discovered_gpu", "native_failure", "benchmark_validation_failed", "unclassified",
        "cpu_unavailable", "opencl_unavailable", "model_load_failed", "tokenize_overflow", "tokenize_failed",
        "input_too_long", "context_init_failed", "sampler_init_failed", "prefill_failed", "decode_failed",
        "invalid_native_library_dir", "native_library_dir_changed", "built_in_cleanup_bypass")
    private val numbers = setOf("schemaVersion", "capturedAtEpochMs", "recordedAtEpochMs", "threads",
        "inputApproxWords", "outputCharacters", "chunkCount", "totalMs", "pssKb", "nativeHeapBytes",
        "javaUsedBytes", "thermalStatus", "warmTimeoutMs")
    private val nativeNumbers = setOf("loadMs", "tokenizeMs", "prefillMs", "decodeMs", "totalMs",
        "inputTokens", "outputTokens", "threads")
    private val categories = mapOf(
        "runtimeRequested" to setOf("auto", "cpu", "opencl"),
        "runtimeSelected" to setOf("cpu", "opencl"),
        "styling" to setOf("casual", "semi-casual", "semi-formal", "formal"),
        "structure" to setOf("prose", "lists"), "context" to setOf("general", "email"),
        "outcome" to setOf("success", "fallback", "failed", "applied", "valid_empty"),
        "warmDurationId" to setOf("immediate", "2m", "5m", "15m", "30m", "process"),
        "errorCategory" to failures
    )

    fun project(text: String): String? = runCatching {
        val source = DiagnosticStore.json.parseToJsonElement(text).jsonObject
        buildJsonObject {
            (source["reportId"] as? JsonPrimitive)?.content?.takeIf {
                it.matches(Regex("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}"))
            }?.let { put("reportId", it) }
            numbers.forEach { key -> (source[key] as? JsonPrimitive)?.longOrNull?.let { put(key, it) } }
            listOf("warm", "nativeLibraryDirPresent").forEach { key ->
                (source[key] as? JsonPrimitive)?.booleanOrNull?.let { put(key, it) }
            }
            categories.forEach { (key, allowed) ->
                (source[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content?.let {
                    put(key, it.takeIf(allowed::contains) ?: "unclassified")
                }
            }
            (source["nativeMetricsJson"] as? JsonPrimitive)?.content?.let { nativeText ->
                val metrics = runCatching { DiagnosticStore.json.parseToJsonElement(nativeText) }.getOrNull()
                val chunks = when (metrics) {
                    is JsonArray -> metrics.take(64)
                    is JsonObject -> listOf(metrics)
                    else -> emptyList()
                }
                put("nativeMetricsJson", buildJsonArray {
                    chunks.filterIsInstance<JsonObject>().forEach { chunk ->
                        add(buildJsonObject {
                            nativeNumbers.forEach { key ->
                                (chunk[key] as? JsonPrimitive)?.longOrNull?.let { put(key, it) }
                            }
                            (chunk["warm"] as? JsonPrimitive)?.booleanOrNull?.let { put("warm", it) }
                            (chunk["backend"] as? JsonPrimitive)?.content?.takeIf {
                                it in setOf("cpu", "opencl")
                            }?.let { put("backend", it) }
                        })
                    }
                }.toString())
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

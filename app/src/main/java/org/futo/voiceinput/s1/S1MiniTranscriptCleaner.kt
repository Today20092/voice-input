package org.futo.voiceinput.s1

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.futo.voiceinput.settings.LANGUAGE_TOGGLES
import org.futo.voiceinput.settings.NEMOTRON_MULTILINGUAL_LANGUAGE
import org.futo.voiceinput.settings.NEMOTRON_PROFILE
import org.futo.voiceinput.settings.S1_MINI_AUTO_BACKEND
import org.futo.voiceinput.settings.S1_MINI_AUTO_THREADS
import org.futo.voiceinput.settings.S1_MINI_CONTEXT
import org.futo.voiceinput.settings.S1_MINI_ENABLED
import org.futo.voiceinput.settings.S1_MINI_RUNTIME
import org.futo.voiceinput.settings.S1_MINI_STRUCTURE
import org.futo.voiceinput.settings.S1_MINI_STYLING
import org.futo.voiceinput.settings.S1_MINI_WARM_DURATION
import org.futo.voiceinput.settings.SpeechBackendType
import org.futo.voiceinput.settings.getSetting
import org.futo.voiceinput.settings.toS1MiniContext
import org.futo.voiceinput.settings.toS1MiniRuntime
import org.futo.voiceinput.settings.toS1MiniStructure
import org.futo.voiceinput.settings.toS1MiniStyling
import org.futo.voiceinput.settings.toS1MiniWarmDuration
import java.io.File
import kotlin.math.ceil

data class S1MiniCleanupResult(
    val text: String,
    val applied: Boolean,
    val validEmpty: Boolean = false,
    val fallbackCategory: String? = null,
    val diagnosticReportId: String? = null
)

object S1MiniTranscriptCleaner {
    private const val TIMEOUT_MS = 15_000L
    private const val CONTEXT_SIZE = 2048

    suspend fun clean(
        context: Context,
        rawTranscript: String,
        backend: SpeechBackendType,
        detectedLanguage: String?,
        forcedLanguage: String?,
        onCleaning: suspend () -> Unit
    ): S1MiniCleanupResult {
        if (!context.getSetting(S1_MINI_ENABLED)) return S1MiniCleanupResult(rawTranscript, false)

        val english = S1MiniEnglishGate.isEstablishedEnglish(
            backend = backend,
            detectedLanguage = detectedLanguage,
            forcedLanguage = forcedLanguage,
            nemotronProfile = context.getSetting(NEMOTRON_PROFILE),
            nemotronLanguage = context.getSetting(NEMOTRON_MULTILINGUAL_LANGUAGE),
            enabledWhisperLanguages = context.getSetting(LANGUAGE_TOGGLES)
        )
        if (!english) return S1MiniCleanupResult(rawTranscript, false, fallbackCategory = "non_english_bypass")

        val styling = context.getSetting(S1_MINI_STYLING).toS1MiniStyling()
        val structure = context.getSetting(S1_MINI_STRUCTURE).toS1MiniStructure()
        val cleanupContext = context.getSetting(S1_MINI_CONTEXT).toS1MiniContext()
        val requestedRuntime = context.getSetting(S1_MINI_RUNTIME).toS1MiniRuntime()
        val selectedRuntime = when (requestedRuntime) {
            org.futo.voiceinput.settings.S1MiniRuntime.Auto -> context.getSetting(S1_MINI_AUTO_BACKEND)
            else -> requestedRuntime.id
        }
        val threads = if (requestedRuntime == org.futo.voiceinput.settings.S1MiniRuntime.Auto) {
            context.getSetting(S1_MINI_AUTO_THREADS)
        } else {
            minOf(4, Runtime.getRuntime().availableProcessors()).coerceAtLeast(1)
        }
        val warmTimeout = context.getSetting(S1_MINI_WARM_DURATION).toS1MiniWarmDuration().timeoutMs
        val nativeLibraryDir = context.applicationInfo.nativeLibraryDir
        val packagedBackendLibraries = File(nativeLibraryDir).listFiles().orEmpty()
            .map { it.name }
            .filter { it.startsWith("libggml-") || it == "libs1mini.so" || it == "libllama.so" }
            .sorted()

        suspend fun recordDiagnostic(
            outputCharacters: Int,
            chunkCount: Int,
            elapsedMs: Long,
            nativeMetrics: List<String>,
            outcome: String,
            errorCategory: String?
        ): String {
            val backendDiscovery = try {
                withTimeout(2_000L) { S1MiniClient.discoverBackends(context) }
            } catch (error: Throwable) {
                S1MiniBackendDiscovery(emptyList(), listOf(error.javaClass.simpleName))
            }
            val run = S1MiniDiagnosticRun(
                runtimeRequested = requestedRuntime.id,
                runtimeSelected = selectedRuntime,
                threads = threads,
                styling = styling.id,
                structure = structure.id,
                context = cleanupContext.id,
                warm = nativeMetrics.any { "\"warm\":true" in it },
                inputApproxWords = rawTranscript.split(Regex("\\s+")).count { it.isNotBlank() },
                outputCharacters = outputCharacters,
                chunkCount = chunkCount,
                totalMs = elapsedMs,
                nativeMetricsJson = "[${nativeMetrics.joinToString(",")} ]",
                pssKb = S1MiniDiagnostics.pssKb(),
                nativeHeapBytes = S1MiniDiagnostics.nativeHeapBytes(),
                javaUsedBytes = S1MiniDiagnostics.javaUsedBytes(),
                thermalStatus = S1MiniDiagnostics.thermalStatus(context),
                outcome = outcome,
                errorCategory = errorCategory,
                nativeLibraryDirPresent = nativeLibraryDir.isNotBlank() && File(nativeLibraryDir).isDirectory,
                packagedBackendLibraries = packagedBackendLibraries,
                discoveredBackendDevices = backendDiscovery.devices,
                backendLoaderErrors = backendDiscovery.loaderErrors
            )
            runCatching { S1MiniDiagnostics.record(context, run) }
            return run.reportId
        }

        if (!S1MiniModel.isInstalled(context)) {
            val reportId = recordDiagnostic(
                outputCharacters = 0,
                chunkCount = 0,
                elapsedMs = 0,
                nativeMetrics = emptyList(),
                outcome = "fallback",
                errorCategory = "model_not_installed"
            )
            return S1MiniCleanupResult(
                rawTranscript,
                applied = false,
                fallbackCategory = "model_not_installed",
                diagnosticReportId = reportId
            )
        }

        val chunks = S1MiniPrompt.chunkTranscript(rawTranscript)
        val started = SystemClock.elapsedRealtime()
        val nativeMetrics = mutableListOf<String>()
        val outputs = mutableListOf<String>()
        var errorCategory: String? = null
        onCleaning()

        try {
            withTimeout(TIMEOUT_MS) {
                for (chunk in chunks) {
                    val words = chunk.split(Regex("\\s+")).count { it.isNotBlank() }
                    val maxNewTokens = (ceil(words * 1.8).toInt() + 32).coerceIn(64, 1024)
                    val result = S1MiniClient.normalize(
                        context = context,
                        modelPath = S1MiniModel.modelFile(context).absolutePath,
                        prompt = S1MiniPrompt.formattedPrompt(chunk, styling, structure, cleanupContext),
                        contextSize = CONTEXT_SIZE,
                        maxNewTokens = maxNewTokens,
                        threads = threads,
                        runtime = selectedRuntime,
                        warmTimeoutMs = warmTimeout
                    )
                    outputs += result.text.trim()
                    nativeMetrics += result.nativeMetricsJson
                }
            }
        } catch (timeout: TimeoutCancellationException) {
            errorCategory = "timeout"
        } catch (error: S1MiniServiceException) {
            errorCategory = error.category
        } catch (error: Throwable) {
            errorCategory = when (error) {
                is OutOfMemoryError -> "out_of_memory"
                else -> "service_failure"
            }
        }

        val elapsed = SystemClock.elapsedRealtime() - started
        val finalText = if (errorCategory == null) outputs.joinToString("\n").trim() else rawTranscript
        val reportId = recordDiagnostic(
            outputCharacters = if (errorCategory == null) finalText.length else 0,
            chunkCount = chunks.size,
            elapsedMs = elapsed,
            nativeMetrics = nativeMetrics,
            outcome = when {
                errorCategory != null -> "fallback"
                finalText.isEmpty() -> "valid_empty"
                else -> "success"
            },
            errorCategory = errorCategory
        )

        return if (errorCategory != null) {
            S1MiniCleanupResult(
                rawTranscript,
                applied = false,
                fallbackCategory = errorCategory,
                diagnosticReportId = reportId
            )
        } else {
            S1MiniCleanupResult(
                finalText,
                applied = true,
                validEmpty = finalText.isEmpty(),
                diagnosticReportId = reportId
            )
        }
    }
}

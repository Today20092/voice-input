package org.futo.voiceinput.s1

import android.content.Context
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.futo.voiceinput.BuildConfig
import org.futo.voiceinput.settings.S1_MINI_AUTO_BACKEND
import org.futo.voiceinput.settings.S1_MINI_AUTO_THREADS
import org.futo.voiceinput.settings.S1_MINI_BENCHMARK_FINGERPRINT
import org.futo.voiceinput.settings.S1MiniContext
import org.futo.voiceinput.settings.S1MiniStructure
import org.futo.voiceinput.settings.S1MiniStyling
import org.futo.voiceinput.settings.getSetting
import org.futo.voiceinput.settings.setSetting
import java.io.File

data class S1MiniBenchmarkResult(
    val backend: String,
    val threads: Int,
    val medianMs: Long,
    val candidates: Map<String, Long>
)

object S1MiniBenchmark {
    private const val RAW = "so um i need to like send the the report by uh friday no wait make that thursday"
    private const val EXPECTED = "I need to send the report by Thursday."

    fun fingerprint(): String = listOf(
        S1MiniModel.VERSION,
        BuildConfig.S1_LLAMA_COMMIT,
        Build.VERSION.SDK_INT,
        if (Build.VERSION.SDK_INT >= 31) Build.SOC_MANUFACTURER else Build.MANUFACTURER,
        if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else Build.HARDWARE
    ).joinToString("|")

    suspend fun needsRun(context: Context): Boolean =
        S1MiniModel.isInstalled(context) &&
            context.getSetting(S1_MINI_BENCHMARK_FINGERPRINT) != fingerprint()

    suspend fun run(context: Context, force: Boolean = false): S1MiniBenchmarkResult? {
        if (!S1MiniModel.isInstalled(context)) return null
        if (!force && !needsRun(context)) {
            return S1MiniBenchmarkResult(
                context.getSetting(S1_MINI_AUTO_BACKEND),
                context.getSetting(S1_MINI_AUTO_THREADS),
                0L,
                emptyMap()
            )
        }

        val prompt = S1MiniPrompt.formattedPrompt(
            RAW,
            S1MiniStyling.SemiFormal,
            S1MiniStructure.Prose,
            S1MiniContext.General
        )
        val candidates = buildList {
            val available = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
            listOf(2, 4, 6).filter { it <= available }.forEach { add("cpu" to it) }
            if (none { it.first == "cpu" }) add("cpu" to 1)
            add("opencl" to minOf(4, available))
        }
        val measurements = linkedMapOf<String, Long>()
        val failures = linkedMapOf<String, String>()

        for ((backend, threads) in candidates) {
            val key = "$backend/$threads"
            try {
                val samples = mutableListOf<Long>()
                repeat(3) { iteration ->
                    val started = SystemClock.elapsedRealtime()
                    val result = withTimeout(30_000L) {
                        S1MiniClient.normalize(
                            context,
                            S1MiniModel.modelFile(context).absolutePath,
                            prompt,
                            contextSize = 512,
                            maxNewTokens = 96,
                            threads = threads,
                            runtime = backend,
                            warmTimeoutMs = -1L
                        )
                    }
                    check(result.text.trim() == EXPECTED) { "deterministic_output_mismatch" }
                    if (iteration > 0) samples += SystemClock.elapsedRealtime() - started
                }
                measurements[key] = samples.sorted()[samples.size / 2]
            } catch (_: TimeoutCancellationException) {
                // Unsupported or too slow: omit the candidate and retain CPU fallback.
                failures[key] = "timeout"
            } catch (error: S1MiniServiceException) {
                failures[key] = error.category
            } catch (error: Throwable) {
                // A backend must pass deterministic output before it can be selected.
                failures[key] = error.message
                    ?.takeIf { it.matches(Regex("[a-z0-9_]+")) }
                    ?: error.javaClass.simpleName
            }
        }

        val nativeLibraryDir = context.applicationInfo.nativeLibraryDir
        val backendDevices = runCatching { S1MiniClient.availableBackends(context) }.getOrDefault(emptyList())
        runCatching {
            S1MiniDiagnostics.recordBenchmark(
                context,
                S1MiniBenchmarkDiagnostic(
                    measurementsMs = measurements,
                    failures = failures,
                    discoveredBackendDevices = backendDevices,
                    packagedBackendLibraries = File(nativeLibraryDir).listFiles().orEmpty()
                        .map { it.name }
                        .filter {
                            it.startsWith("libggml-") || it == "libs1mini.so" || it == "libllama.so"
                        }
                        .sorted()
                )
            )
        }

        val cpuWinner = measurements.filterKeys { it.startsWith("cpu/") }.minByOrNull { it.value }
            ?: return null
        val openClWinner = measurements.filterKeys { it.startsWith("opencl/") }.minByOrNull { it.value }
        val winner = if (openClWinner != null && openClWinner.value * 100 <= cpuWinner.value * 85) {
            openClWinner
        } else {
            cpuWinner
        }
        val (backend, threadsText) = winner.key.split('/')
        val threads = threadsText.toInt()
        context.setSetting(S1_MINI_AUTO_BACKEND, backend)
        context.setSetting(S1_MINI_AUTO_THREADS, threads)
        context.setSetting(S1_MINI_BENCHMARK_FINGERPRINT, fingerprint())
        S1MiniClient.unload(context)
        return S1MiniBenchmarkResult(backend, threads, winner.value, measurements)
    }
}

package org.futo.voiceinput.s1

import android.content.Context
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
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
import java.security.MessageDigest

data class S1MiniBenchmarkResult(
    val backend: String,
    val threads: Int,
    val medianMs: Long,
    val candidates: Map<String, Long>
)

data class S1MiniBenchmarkCandidate(val backend: String, val threads: Int) {
    val key: String = "$backend/$threads"
}

@Serializable
data class S1MiniBenchmarkValidation(
    val accepted: Boolean,
    val stable: Boolean,
    val preservesMeaning: Boolean,
    val attempts: Int,
    val outputHashes: List<String>,
    val failureReason: String? = null
)

object S1MiniBenchmarkPolicy {
    private val whitespace = Regex("\\s+")
    private val artifact = Regex("\\b(?:report|write-up|writeup|document)\\b")
    private val thursday = Regex("\\bthursday\\b")
    private val friday = Regex("\\bfriday\\b")
    private val filler = Regex("\\b(?:um|uh)\\b")
    private val delivery = Regex(
        "\\b(?:send|sent|submit|submitted|deliver|delivered|due)\\b|\\bgo(?:es)? out\\b"
    )
    private val reversedIntent = Regex(
        "\\b(?:not|never|cancel|canceled|cancelled|skip)\\b"
    )

    fun candidates(
        availableProcessors: Int,
        discoveredDevices: List<String>
    ): List<S1MiniBenchmarkCandidate> = buildList {
        val available = availableProcessors.coerceAtLeast(1)
        listOf(2, 4, 6).filter { it <= available }
            .forEach { add(S1MiniBenchmarkCandidate("cpu", it)) }
        if (none { it.backend == "cpu" }) add(S1MiniBenchmarkCandidate("cpu", 1))
        if (discoveredDevices.any { it.startsWith("gpu:") }) {
            add(S1MiniBenchmarkCandidate("opencl", minOf(4, available)))
        }
    }

    fun validate(outputs: List<String>): S1MiniBenchmarkValidation {
        val normalized = outputs.map { it.trim().replace(whitespace, " ") }
        val stable = normalized.isNotEmpty() && normalized.distinct().size == 1
        val preservesMeaning = normalized.isNotEmpty() && normalized.all { output ->
            val lower = output.lowercase()
            output.isNotEmpty() && artifact.containsMatchIn(lower) &&
                delivery.containsMatchIn(lower) && !reversedIntent.containsMatchIn(lower) &&
                thursday.containsMatchIn(lower) && !friday.containsMatchIn(lower) &&
                !filler.containsMatchIn(lower)
        }
        val failureReason = when {
            outputs.size != 3 -> "incomplete_validation"
            normalized.any { it.isEmpty() } -> "empty_output"
            !stable -> "unstable_output"
            !preservesMeaning -> "meaning_mismatch"
            else -> null
        }
        return S1MiniBenchmarkValidation(
            accepted = failureReason == null,
            stable = stable,
            preservesMeaning = preservesMeaning,
            attempts = outputs.size,
            outputHashes = normalized.map(::sha256).distinct(),
            failureReason = failureReason
        )
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

object S1MiniBenchmark {
    private const val RAW = "so um i need to like send the the report by uh friday no wait make that thursday"
    private const val POLICY_VERSION = "2"

    fun fingerprint(): String = listOf(
        S1MiniModel.VERSION,
        BuildConfig.S1_LLAMA_COMMIT,
        POLICY_VERSION,
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
        val backendDiscovery = runCatching { S1MiniClient.discoverBackends(context) }
            .getOrElse {
                S1MiniBackendDiscovery(emptyList(), listOf(it.javaClass.simpleName))
            }
        val candidates = S1MiniBenchmarkPolicy.candidates(
            Runtime.getRuntime().availableProcessors(),
            backendDiscovery.devices
        )
        val measurements = linkedMapOf<String, Long>()
        val failures = linkedMapOf<String, String>()
        val validationDetails = linkedMapOf<String, S1MiniBenchmarkValidation>()

        for ((backend, threads) in candidates) {
            val key = "$backend/$threads"
            val outputs = mutableListOf<String>()
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
                    outputs += result.text
                    if (iteration > 0) samples += SystemClock.elapsedRealtime() - started
                }
                val validation = S1MiniBenchmarkPolicy.validate(outputs)
                check(validation.accepted) {
                    validation.failureReason ?: "benchmark_validation_failed"
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
            } finally {
                if (outputs.isNotEmpty()) {
                    validationDetails[key] = S1MiniBenchmarkPolicy.validate(outputs)
                }
            }
        }

        val nativeLibraryDir = context.applicationInfo.nativeLibraryDir
        runCatching {
            S1MiniDiagnostics.recordBenchmark(
                context,
                S1MiniBenchmarkDiagnostic(
                    measurementsMs = measurements,
                    failures = failures,
                    skippedCandidates = if (backendDiscovery.devices.none { it.startsWith("gpu:") }) {
                        mapOf("opencl" to "no_discovered_gpu")
                    } else {
                        emptyMap()
                    },
                    validationDetails = validationDetails,
                    discoveredBackendDevices = backendDiscovery.devices,
                    backendLoaderErrors = backendDiscovery.loaderErrors,
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

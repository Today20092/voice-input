package org.futo.voiceinput.benchmark

import android.content.Context
import android.app.ActivityManager
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.Process
import kotlinx.coroutines.*
import org.futo.voiceinput.BuildConfig
import org.futo.voiceinput.recognition.RecognitionModelLifecycle
import java.io.File

class DeviceBenchmark(private val context: Context, val store: BenchmarkStore = BenchmarkStore(File(context.filesDir, "model-benchmarks"))) {
    fun recoverOnce(): List<BenchmarkReport> = synchronized(recoveredRoots) {
        if (recoveredRoots.add(store.root.absolutePath)) store.recoverInterrupted() else store.reports()
    }
    companion object { private val recoveredRoots = mutableSetOf<String>() }
    fun device(): Map<String, String> = mapOf(
        "manufacturer" to Build.MANUFACTURER, "device" to Build.MODEL,
        "android" to Build.VERSION.RELEASE, "sdk" to Build.VERSION.SDK_INT.toString(),
        "chip" to if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else "unavailable before Android 12",
        "build" to "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.GIT_REVISION}",
        "pid" to Process.myPid().toString(), "hardware" to Build.HARDWARE,
        "startupOverhead" to "unavailable; excluded from initialization",
        "energy" to "unavailable; battery state is not an energy measurement"
    )

    fun environment(): Map<String, String> {
        val power = context.getSystemService(PowerManager::class.java)
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val process = ActivityManager.RunningAppProcessInfo().also { ActivityManager.getMyMemoryState(it) }
        return mapOf(
            "thermalStatus" to if (Build.VERSION.SDK_INT >= 29) power.currentThermalStatus.toString() else "unavailable before Android 10",
            "batteryLevel" to (battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)?.toString() ?: "unavailable"),
            "batteryScale" to (battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1)?.toString() ?: "unavailable"),
            "plugged" to (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)?.toString() ?: "unavailable"),
            "powerSave" to power.isPowerSaveMode.toString(), "pid" to Process.myPid().toString(),
            "processImportance" to process.importance.toString(),
            "sessionMonotonicNs" to System.nanoTime().toString()
        )
    }

    suspend fun run(report: BenchmarkReport, onlyModel: String? = null, progress: (String) -> Unit = {}) = withContext(Dispatchers.Default) {
        val lifecycle = RecognitionModelLifecycle.create(context.filesDir, BuildConfig.BUNDLE_PARAKEET_MODEL)
        lifecycle.acquireBenchmark().use {
            synchronized(recoveredRoots) { recoveredRoots.add(store.root.absolutePath) }
            val wake = context.getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "voiceinput:benchmark")
            wake.acquire(60 * 60 * 1000L)
            try {
                val clips = report.clips
                require(clips.isNotEmpty())
                require(report.clips.map { it.language }.distinct().size == 1) { "Use one recording language per comparison" }
                val language = report.clips.first().language
                val models = BenchmarkModel.all().associateBy { it.id }
                if (onlyModel != null) require(onlyModel in report.selectedModelIds)
                report.status = "running"
                store.save(report)
                for (id in report.selectedModelIds.filter { onlyModel == null || it == onlyModel }) {
                    currentCoroutineContext().ensureActive()
                    // Completed cases are durable. Interrupted cases retain evidence and require a new run to repeat.
                    if (report.cases.any { it.modelId == id }) continue
                    val model = models[id]
                    val result = BenchmarkCaseResult(id)
                    report.cases.add(result)
                    store.save(report)
                    progress(model?.name ?: id)
                    if (model == null || !model.installed(context)) {
                        result.status = "skipped"
                        result.reason = "Model not installed or no longer supported; download it and start a new run"
                        store.save(report)
                        continue
                    }
                    if (!model.supportsLanguage(language)) {
                        result.status = "skipped"; result.reason = "Model does not support the selected recording language ($language)"
                        store.save(report); continue
                    }
                    result.identity = model.identity(context, language)
                    result.executionOrder = report.cases.count { it.executionOrder != null } + 1
                    coroutineScope {
                        val sampler = launch {
                            while (isActive) {
                                val memory = Debug.MemoryInfo()
                                Debug.getMemoryInfo(memory)
                                val pss = memory.totalPss.toLong()
                                result.sampledPeakPssKb = maxOf(result.sampledPeakPssKb ?: 0, pss)
                                delay(result.memorySampleIntervalMs)
                            }
                        }
                        try {
                            BenchmarkRunner(environment = ::environment, warmRepetitions = report.warmRepetitions).runCase(result, clips,
                                { store.readAudio(report.runId, it) },
                                { model.recognizer(context, language) }, { store.save(report) })
                        } finally { sampler.cancelAndJoin() }
                    }
                    store.save(report)
                }
                report.status = if (report.cases.size < report.selectedModelIds.size) "prepared" else if (report.cases.all { it.status == "completed" }) "completed" else "partial"
            } catch (cancelled: CancellationException) {
                report.status = "cancelled"
                throw cancelled
            } catch (failure: Exception) {
                report.status = "interrupted"
                throw failure
            } finally {
                if (wake.isHeld) wake.release()
                store.save(report)
            }
        }
    }
}

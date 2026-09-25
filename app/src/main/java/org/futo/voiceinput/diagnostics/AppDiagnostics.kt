package org.futo.voiceinput.diagnostics

import android.app.ActivityManager
import android.app.Application
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.futo.voiceinput.BuildConfig
import org.futo.voiceinput.recognition.RecognitionModelCatalog
import org.futo.voiceinput.s1.S1MiniDiagnostics
import org.futo.voiceinput.settings.*
import java.io.File
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** All ordinary writes are bounded, asynchronous, and independent of recognition success. */
object AppDiagnostics {
    @Volatile private var store: DiagnosticStore? = null
    @Volatile private var application: Context? = null
    private val dropped = AtomicLong()
    private val queue = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(128),
        { runnable -> Thread(runnable, "voice-diagnostics").apply { isDaemon = true } },
        { _, _ -> dropped.incrementAndGet() })

    fun initialize(context: Context) {
        // The cleanup service is a separate process. Its failures are observed by the main-process client.
        val process = if (Build.VERSION.SDK_INT >= 28) Application.getProcessName() else {
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
                .runningAppProcesses?.firstOrNull { it.pid == Process.myPid() }?.processName
        }
        if (process != context.packageName) return
        application = context.applicationContext
        queue.execute {
            runCatching {
                store = DiagnosticStore(File(context.filesDir, "app-diagnostics"),
                    maxBytes = 7L * 1024 * 1024 - 4096,
                    knownModels = RecognitionModelCatalog.models.map { it.id }.toSet() +
                        SpeechBackendType.values().map { it.id } + "s1_mini")
                store?.snapshot() // purge expired evidence on launch, even while opted out
                S1MiniDiagnostics.purgeStandard(context)
                DiagnosticArchive.purgePrepared(File(context.cacheDir, "app-diagnostics-export"))
                // Retire cached standalone standard exports now that the UI uses the combined report.
                File(context.cacheDir, "s1-diagnostics-export").listFiles().orEmpty()
                    .filter { !it.name.contains("WITH-TRANSCRIPTS") }.forEach { it.delete() }
                store?.record(DiagnosticEvent.APP_STARTED)
                recordProcessExits(context)
            }.onFailure { dropped.incrementAndGet() }
        }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // Do not wait on the writer: it may itself have crashed while owning the store monitor.
            val saved = java.util.concurrent.CountDownLatch(1)
            queue.execute {
                try { runCatching { store?.record(DiagnosticEvent.MANAGED_CRASH, error = error) } }
                finally { saved.countDown() }
            }
            runCatching { saved.await(300, TimeUnit.MILLISECONDS) }
            previous?.uncaughtException(thread, error) ?: run {
                Process.killProcess(Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }

    fun event(
        event: DiagnosticEvent,
        sessionId: String? = null,
        model: String? = null,
        metrics: Map<DiagnosticMetric, Long> = emptyMap(),
        error: Throwable? = null,
        detailed: Boolean = false
    ) {
        if (store?.policy()?.enabled == false) return
        if (detailed && detailedRemainingMs() == 0L) return
        val copiedMetrics = metrics.toMap()
        queue.execute {
            runCatching { store?.record(event, sessionId, model, copiedMetrics, error, detailed) }
                .onFailure { dropped.incrementAndGet() }
        }
    }

    fun session(model: String? = null) = DiagnosticSession(model)

    fun collectionEnabled() = store?.policy()?.enabled == true
    fun detailedRemainingMs() = store?.detailedRemainingMs() ?: 0L

    suspend fun setEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        requireNotNull(store) { "Diagnostics not ready" }.setEnabled(enabled)
    }
    suspend fun startDetailed() = withContext(Dispatchers.IO) {
        requireNotNull(store) { "Diagnostics not ready" }.startDetailed()
    }
    suspend fun stopDetailed() = withContext(Dispatchers.IO) { store?.stopDetailed() }

    suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        flush()
        requireNotNull(store) { "Diagnostics not ready" }.clear()
        S1MiniDiagnostics.clearStandard(context)
        File(context.cacheDir, "app-diagnostics-export").deleteRecursively()
        dropped.set(0)
    }

    fun sample(sessionId: String, model: String?, detailed: Boolean = false) {
        if (!collectionEnabled() || (detailed && detailedRemainingMs() == 0L)) return
        queue.execute {
            runCatching {
                val context = application ?: return@runCatching
                val memory = ActivityManager.MemoryInfo()
                (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
                val metrics = mutableMapOf(
                    DiagnosticMetric.PSS_KB to Debug.MemoryInfo().also(Debug::getMemoryInfo).totalPss.toLong(),
                    DiagnosticMetric.JAVA_BYTES to Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() },
                    DiagnosticMetric.NATIVE_BYTES to Debug.getNativeHeapAllocatedSize(),
                    DiagnosticMetric.AVAILABLE_BYTES to memory.availMem,
                    DiagnosticMetric.LOW_MEMORY to if (memory.lowMemory) 1L else 0L,
                    DiagnosticMetric.PROCESS_CPU_MS to Process.getElapsedCpuTime()
                )
                if (Build.VERSION.SDK_INT >= 29) metrics[DiagnosticMetric.THERMAL_STATUS] =
                    (context.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus.toLong()
                store?.record(DiagnosticEvent.RESOURCE_SAMPLE, sessionId, model, metrics, detailed = detailed)
            }.onFailure { dropped.incrementAndGet() }
        }
    }

    suspend fun export(context: Context, happened: String, expected: String): File = withContext(Dispatchers.IO) {
        flush()
        val snapshot = requireNotNull(store) { "Diagnostics not ready" }.snapshot()
        val directory = File(context.cacheDir, "app-diagnostics-export").apply { mkdirs() }
        DiagnosticArchive.purgePrepared(directory)
        val output = File(directory, "voice-input-diagnostics-${System.currentTimeMillis()}.zip")
        try {
            DiagnosticArchive.write(output, snapshot, environment(context).toString(), settings(context).toString(),
                happened, expected, S1MiniDiagnostics.standardReportEntries(context), dropped.get())
            DiagnosticArchive.purgePrepared(directory)
            check(output.exists()) { "Report evidence expired; prepare another report" }
            output
        } catch (error: Exception) {
            output.delete()
            throw error
        }
    }

    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("Bug report", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share bug report").apply {
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun flush() {
        val barrier = java.util.concurrent.CountDownLatch(1)
        // A full queue must not leave export waiting forever.
        queue.execute { barrier.countDown() }
        check(barrier.await(5, TimeUnit.SECONDS)) { "Diagnostics writer busy; try again" }
    }

    private fun environment(context: Context) = buildJsonObject {
        put("schemaVersion", 1)
        put("appVersion", BuildConfig.VERSION_NAME)
        put("versionCode", BuildConfig.VERSION_CODE)
        put("buildFlavor", BuildConfig.FLAVOR)
        put("debug", BuildConfig.DEBUG)
        put("manufacturer", Build.MANUFACTURER)
        put("model", Build.MODEL)
        put("sdk", Build.VERSION.SDK_INT)
        put("abis", Build.SUPPORTED_ABIS.joinToString(","))
        put("processors", Runtime.getRuntime().availableProcessors())
        put("availableStorageBytes", context.filesDir.usableSpace)
        put("collectionEnabled", collectionEnabled())
        put("detailedRemainingMs", detailedRemainingMs())
        put("processExitHistorySupported", Build.VERSION.SDK_INT >= 30)
    }

    private suspend fun settings(context: Context): JsonObject {
        // Deliberately enumerate safe settings. Never serialize DataStore wholesale.
        val backend = context.getSetting(SPEECH_BACKEND).toSpeechBackendType()
        val variant = when (backend) {
            SpeechBackendType.Moonshine -> context.getSetting(MOONSHINE_MODEL_VARIANT)
            SpeechBackendType.Nemotron -> context.getSetting(NEMOTRON_PROFILE)
            else -> null
        }
        val model = RecognitionModelCatalog.modelFor(backend.id, variant)
        return buildJsonObject {
            put("speechBackend", backend.id)
            put("recognitionModel", model?.id ?: backend.id)
            put("recognitionModelVersion", model?.version ?: "legacy")
            put("vadEnabled", context.getSetting(IS_VAD_ENABLED))
            put("durationLimitEnabled", context.getSetting(ENABLE_30S_LIMIT))
            put("manualStopDrainMs", context.getSetting(MANUAL_STOP_DRAIN_MS))
            put("keepWarm", context.getSetting(PARAKEET_KEEP_WARM))
            put("cleanupEnabled", context.getSetting(S1_MINI_ENABLED))
            put("cleanupRuntime", context.getSetting(S1_MINI_RUNTIME).toS1MiniRuntime().id)
            put("audioHistoryEnabled", context.getSetting(AUDIO_HISTORY_ENABLED))
        }
    }

    private fun recordProcessExits(context: Context) {
        if (Build.VERSION.SDK_INT < 30 || !collectionEnabled()) return
        val target = store ?: return
        val since = maxOf(target.policy().collectSinceMs, System.currentTimeMillis() - DiagnosticStore.RETENTION_MS)
        val alreadyRecorded = target.snapshot().records.filter { it.event == DiagnosticEvent.PROCESS_EXIT }
            .mapNotNull { it.metrics[DiagnosticMetric.EXIT_TIMESTAMP] }.toSet()
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        manager.getHistoricalProcessExitReasons(context.packageName, 0, 16)
            .filter { it.timestamp >= since && it.timestamp !in alreadyRecorded }.forEach {
                target.record(DiagnosticEvent.PROCESS_EXIT, metrics = mapOf(
                    DiagnosticMetric.EXIT_TIMESTAMP to it.timestamp,
                    DiagnosticMetric.EXIT_REASON to it.reason.toLong(),
                    DiagnosticMetric.EXIT_STATUS to it.status.toLong(),
                    DiagnosticMetric.PSS_KB to it.pss,
                    DiagnosticMetric.RSS_KB to it.rss,
                    DiagnosticMetric.CLEANUP_PROCESS to if (it.processName == "${context.packageName}:s1_cleanup") 1L else 0L
                ))
            }
    }
}

class DiagnosticSession internal constructor(val model: String?) {
    val id: String = UUID.randomUUID().toString()
    private val started = SystemClock.elapsedRealtime()
    private var firstPartial = true
    @Volatile var terminal = false
        private set

    fun event(event: DiagnosticEvent, metrics: Map<DiagnosticMetric, Long> = emptyMap(),
              error: Throwable? = null, detailed: Boolean = false) {
        AppDiagnostics.event(event, id, model,
            metrics + (DiagnosticMetric.ELAPSED_MS to (SystemClock.elapsedRealtime() - started)), error, detailed)
    }

    @Synchronized fun partial(characters: Int) {
        val first = firstPartial
        firstPartial = false
        event(if (first) DiagnosticEvent.FIRST_PARTIAL else DiagnosticEvent.PARTIAL,
            mapOf(DiagnosticMetric.CHARACTERS to characters.toLong()), detailed = !first)
    }

    @Synchronized fun end(event: DiagnosticEvent, error: Throwable? = null) {
        if (terminal && event != DiagnosticEvent.DELIVERY_ACCEPTED && event != DiagnosticEvent.DELIVERY_REJECTED) return
        terminal = true
        event(event, error = error)
        AppDiagnostics.sample(id, model)
    }
}

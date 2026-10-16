package org.futo.voiceinput.benchmark

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.futo.voiceinput.BuildConfig
import org.futo.voiceinput.downloader.DownloadActivity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** ADB control lives in the test APK. The ordinary app has no exported benchmark control component. */
class BenchmarkAdbTest {
    @Test fun command() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.containsKey("benchmarkOperation"))
        val context = instrumentation.targetContext
        check(BuildConfig.DEBUG && context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
        val benchmark = DeviceBenchmark(context)
        val control = File(context.filesDir, "benchmark-control.json")
        when (args.getString("benchmarkOperation")) {
            "catalog" -> {
                val models = BenchmarkModel.all()
                control.writeText(JSONObject().put("models", JSONArray().apply {
                    models.forEach { put(JSONObject().put("id", it.id).put("name", it.name)
                        .put("installed", it.installed(context)).put("transferBytes", it.transferBytes)
                        .put("storageBytes", it.storageBytes)) }
                }).toString(2))
            }
            "prepare" -> {
                val suiteRoot = File(context.filesDir, "benchmark-import")
                val manifest = File(suiteRoot, "suite.json")
                require(manifest.length() in 1..100_000)
                val suite = JSONObject(manifest.readText())
                val all = BenchmarkModel.all()
                val ids = args.getString("benchmarkModels")?.split(',').orEmpty()
                require(ids.isNotEmpty() && ids.distinct().size == ids.size && ids.all { id -> all.any { it.id == id } })
                val runId = java.util.UUID.randomUUID().toString()
                val inputs = suite.getJSONArray("clips")
                require(inputs.length() in 1..8)
                val clips = (0 until inputs.length()).map { index ->
                    val input = inputs.getJSONObject(index)
                    val id = input.getString("id")
                    require(id.matches(Regex("[a-zA-Z0-9_-]{1,80}")))
                    val filename = input.getString("file")
                    require(filename.matches(Regex("[a-zA-Z0-9_-]+\\.(m4a|wav)")))
                    val original = File(suiteRoot, filename)
                    check(BenchmarkStore.hashFile(original) == input.getString("sha256"))
                    val reference = input.optString("reference").takeIf { it.isNotEmpty() }
                    require(reference.orEmpty().length <= 6000)
                    var conversion = ""
                    val canonical = withContext(Dispatchers.IO) { BenchmarkAudio.decode(original) { conversion = it } }
                    original.copyTo(File(benchmark.store.directory(runId), "$id.original"))
                    BenchmarkClip(id, input.getString("sha256"), benchmark.store.saveAudio(runId, id, canonical),
                        canonical.size, reference, BenchmarkStore.hash(reference.orEmpty().toByteArray()),
                        conversion = conversion,
                        referenceVerified = input.optBoolean("referenceVerified", false))
                }
                require(clips.map { it.id }.distinct().size == clips.size)
                val report = BenchmarkReport(runId = runId, isolation = "fresh-process-adb",
                    device = benchmark.device(), clips = clips, selectedModelIds = ids,
                    installedBefore = all.filter { it.installed(context) }.map { it.id })
                benchmark.store.save(report)
                control.writeText(JSONObject().put("runId", runId).toString())
            }
            "download" -> {
                val model = BenchmarkModel.all().first { it.id == args.getString("benchmarkModel") }
                if (!model.installed(context)) download(context, model)
                control.writeText(JSONObject().put("model", model.id).put("installed", model.installed(context)).toString())
            }
            "run" -> {
                benchmark.store.recoverInterrupted()
                val report = benchmark.store.load(requireNotNull(args.getString("benchmarkRunId")))
                withTimeout(args.getString("benchmarkTimeoutSeconds", "1800")!!.toLong().coerceIn(30, 3600) * 1000) {
                    benchmark.run(report, requireNotNull(args.getString("benchmarkModel")))
                }
                control.writeText(JSONObject().put("status", report.status).toString())
            }
            "verify-references" -> {
                val report = benchmark.store.load(requireNotNull(args.getString("benchmarkRunId")))
                check(report.status !in setOf("running", "downloading")) { "Finish the active comparison before reviewing references" }
                benchmark.store.save(report.withVerifiedSpokenReferences())
            }
            "export" -> {
                benchmark.store.recoverInterrupted()
                val report = benchmark.store.load(requireNotNull(args.getString("benchmarkRunId")))
                File(benchmark.store.directory(report.runId), "report.md").writeText(benchmark.store.markdown(report))
                control.writeText(JSONObject().put("status", report.status).put("cases", report.cases.size).toString())
            }
            else -> error("Unknown benchmark operation")
        }
    }

    private suspend fun download(context: Context, model: BenchmarkModel) {
        val intent = model.downloadIntent(context).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        val scenario = ActivityScenario.launch<DownloadActivity>(intent)
        try {
            withTimeout(20 * 60 * 1000L) {
                var started = false
                while (!model.installed(context)) {
                    var failed = false
                    try { scenario.onActivity { activity ->
                        if (!started) started = activity.startBenchmarkDownload()
                        failed = activity.benchmarkDownloadFailed()
                    } } catch (_: IllegalStateException) { if (!model.installed(context)) break }
                    if (failed) break
                    delay(500)
                }
            }
        } catch (_: TimeoutCancellationException) {
            // The host records a skipped case if assets remain unavailable.
        } finally { scenario.close() }
    }
}

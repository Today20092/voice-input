package org.futo.voiceinput.s1

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import androidx.core.content.FileProvider
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.futo.voiceinput.BuildConfig
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Serializable
data class S1MiniDiagnosticRun(
    val schemaVersion: Int = 1,
    val reportId: String = UUID.randomUUID().toString(),
    val appVersion: String = BuildConfig.VERSION_NAME,
    val modelVersion: String = S1MiniModel.VERSION,
    val quantization: String = "Q4_K_M",
    val runtimeRequested: String,
    val runtimeSelected: String,
    val threads: Int,
    val styling: String,
    val structure: String,
    val context: String,
    val warm: Boolean,
    val inputApproxWords: Int,
    val outputCharacters: Int,
    val chunkCount: Int,
    val totalMs: Long,
    val nativeMetricsJson: String,
    val pssKb: Long,
    val nativeHeapBytes: Long,
    val javaUsedBytes: Long,
    val thermalStatus: Int?,
    val outcome: String,
    val errorCategory: String? = null,
    val transcriptIncluded: Boolean = false
)

@Serializable
private data class S1MiniDiagnosticEnvironment(
    val schemaVersion: Int = 1,
    val appVersion: String = BuildConfig.VERSION_NAME,
    val buildFlavor: String = BuildConfig.FLAVOR,
    val manufacturer: String = Build.MANUFACTURER,
    val brand: String = Build.BRAND,
    val model: String = Build.MODEL,
    val device: String = Build.DEVICE,
    val sdk: Int = Build.VERSION.SDK_INT,
    val supportedAbis: List<String> = Build.SUPPORTED_ABIS.toList(),
    val socManufacturer: String? = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MANUFACTURER else null,
    val socModel: String? = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else null,
    val availableProcessors: Int = Runtime.getRuntime().availableProcessors(),
    val modelFileBytes: Long = S1MiniModel.FILE_SIZE,
    val modelSha256: String = S1MiniModel.SHA256,
    val transcriptIncluded: Boolean = false
)

object S1MiniDiagnostics {
    private const val MAX_RUNS = 25
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    private fun directory(context: Context) = File(context.filesDir, "s1-diagnostics").apply { mkdirs() }
    private fun runsFile(context: Context) = File(directory(context), "runs.jsonl")

    @Synchronized
    fun record(context: Context, run: S1MiniDiagnosticRun) {
        val existing = runsFile(context).takeIf { it.isFile }?.readLines().orEmpty()
            .filter { it.isNotBlank() }
        val retained = (existing + json.encodeToString(run)).takeLast(MAX_RUNS)
        val target = runsFile(context)
        val temporary = File(target.parentFile, "${target.name}.tmp")
        temporary.writeText(retained.joinToString("\n", postfix = if (retained.isEmpty()) "" else "\n"))
        if (!temporary.renameTo(target)) {
            target.delete()
            check(temporary.renameTo(target)) { "Unable to replace S1-mini diagnostics" }
        }
    }

    fun latestText(context: Context): String? {
        val line = runsFile(context).takeIf { it.isFile }?.readLines()?.lastOrNull { it.isNotBlank() }
            ?: return null
        return buildString {
            appendLine("S1-mini diagnostic report")
            appendLine("Transcript included: false")
            appendLine()
            appendLine(line)
        }
    }

    fun copyLatest(context: Context): Boolean {
        val report = latestText(context) ?: return false
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("S1-mini diagnostic report", report))
        return true
    }

    fun exportZip(context: Context): File? {
        val runs = runsFile(context).takeIf { it.isFile }?.readText().orEmpty()
        if (runs.isBlank()) return null
        val exportDir = File(context.cacheDir, "s1-diagnostics-export").apply { mkdirs() }
        exportDir.listFiles()?.forEach { it.delete() }
        val output = File(exportDir, "s1-diagnostics.zip")
        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            fun entry(name: String, contents: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(contents.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry(
                "README.txt",
                "S1-mini by Superwhisper diagnostic bundle.\n" +
                    "Contains device/model/performance metadata only. Transcript included: false.\n"
            )
            entry("environment.json", json.encodeToString(S1MiniDiagnosticEnvironment()))
            entry("runs.jsonl", runs)
            entry("report.txt", latestText(context).orEmpty())
        }
        return output
    }

    fun shareZip(context: Context): Boolean {
        val file = exportZip(context) ?: return false
        val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.files", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }, "Share S1-mini diagnostics").apply {
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        return true
    }

    fun clear(context: Context) {
        directory(context).deleteRecursively()
        File(context.cacheDir, "s1-diagnostics-export").deleteRecursively()
    }

    fun pssKb(): Long = Debug.getPss()
    fun nativeHeapBytes(): Long = Debug.getNativeHeapAllocatedSize()
    fun javaUsedBytes(): Long = Runtime.getRuntime().run { totalMemory() - freeMemory() }
    fun thermalStatus(context: Context): Int? = if (Build.VERSION.SDK_INT >= 29) {
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus
    } else null

    fun memoryClass(context: Context): Int =
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).memoryClass
}

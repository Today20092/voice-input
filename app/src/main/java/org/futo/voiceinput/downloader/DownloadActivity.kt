package org.futo.voiceinput.downloader

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.futo.voiceinput.R
import org.futo.voiceinput.BuildConfig
import org.futo.voiceinput.diagnostics.AppDiagnostics
import org.futo.voiceinput.diagnostics.DiagnosticEvent
import org.futo.voiceinput.diagnostics.DiagnosticMetric
import org.futo.voiceinput.diagnostics.DiagnosticSession
import org.futo.voiceinput.recognition.RecognitionModel
import org.futo.voiceinput.recognition.RecognitionModelCatalog
import org.futo.voiceinput.recognition.RecognitionModelLifecycle
import org.futo.voiceinput.recognition.updateRecognitionModelSelection
import org.futo.voiceinput.s1.EXTRA_ENABLE_S1_MINI_AFTER_DOWNLOAD
import org.futo.voiceinput.settings.S1_MINI_ENABLED
import org.futo.voiceinput.settings.setSettingBlocking
import org.futo.voiceinput.settings.ScreenTitle
import org.futo.voiceinput.settings.ScrollableList
import org.futo.voiceinput.theme.UixThemeAuto
import org.futo.voiceinput.theme.Typography
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import org.futo.voiceinput.recognition.RecognitionModelArtifact
import kotlin.math.max

const val EXTRA_DOWNLOAD_FILE_NAMES = "download_file_names"
const val EXTRA_DOWNLOAD_FILE_URLS = "download_file_urls"
const val EXTRA_DOWNLOAD_FILE_HASHES = "download_file_hashes"
const val EXTRA_DOWNLOAD_FILE_SIZES = "download_file_sizes"
const val EXTRA_TARGET_SUBDIR = "target_subdir"
const val EXTRA_COMPLETION_MARKER = "completion_marker"
const val EXTRA_DOWNLOAD_SOURCE = "download_source"
const val EXTRA_REQUIRED_FREE_SPACE = "required_free_space"
const val EXTRA_MODEL_ID = "recognition_model_id"
const val EXTRA_MODEL_VERSION = "recognition_model_version"
const val EXTRA_ARCHIVE_NAME = "recognition_model_archive_name"
const val EXTRA_ARCHIVE_URL = "recognition_model_archive_url"
const val EXTRA_ARCHIVE_HASH = "recognition_model_archive_hash"
const val EXTRA_ARCHIVE_SIZE = "recognition_model_archive_size"
const val EXTRA_ARCHIVE_ROOT = "recognition_model_archive_root"


fun Intent.putRecognitionModel(model: RecognitionModel) {
    putStringArrayListExtra(EXTRA_DOWNLOAD_FILE_NAMES, ArrayList(model.artifacts.map { it.name }))
    putStringArrayListExtra(EXTRA_DOWNLOAD_FILE_URLS, ArrayList(model.artifacts.map { it.url }))
    putStringArrayListExtra(EXTRA_DOWNLOAD_FILE_HASHES, ArrayList(model.artifacts.map { it.sha256 }))
    putExtra(EXTRA_DOWNLOAD_FILE_SIZES, model.artifacts.map { it.sizeBytes }.toLongArray())
    putExtra(EXTRA_TARGET_SUBDIR, model.directoryName)
    putExtra(EXTRA_COMPLETION_MARKER, model.completionMarker)
    putExtra(EXTRA_DOWNLOAD_SOURCE, model.source)
    putExtra(EXTRA_REQUIRED_FREE_SPACE, model.requiredFreeSpaceBytes)
    putExtra(EXTRA_MODEL_ID, model.id)
    putExtra(EXTRA_MODEL_VERSION, model.version)
    listOf(EXTRA_ARCHIVE_NAME, EXTRA_ARCHIVE_URL, EXTRA_ARCHIVE_HASH, EXTRA_ARCHIVE_SIZE, EXTRA_ARCHIVE_ROOT)
        .forEach { removeExtra(it) }
    model.archive?.let { archive ->
        putExtra(EXTRA_ARCHIVE_NAME, archive.name)
        putExtra(EXTRA_ARCHIVE_URL, archive.url)
        putExtra(EXTRA_ARCHIVE_HASH, archive.sha256)
        putExtra(EXTRA_ARCHIVE_SIZE, archive.sizeBytes)
        putExtra(EXTRA_ARCHIVE_ROOT, model.archiveRoot)
    }
}

internal fun Intent.refreshRecognitionModel(): RecognitionModel? =
    getStringExtra(EXTRA_MODEL_ID)?.let { id ->
        RecognitionModelCatalog.models.firstOrNull { it.id == id }
    }?.also { putRecognitionModel(it) }

fun Context.recognitionModelDownloadIntent(model: RecognitionModel) =
    Intent(this, DownloadActivity::class.java).apply {
        putRecognitionModel(model)
    }

fun Context.startRecognitionModelDownloadActivity(model: RecognitionModel) {
    startActivity(recognitionModelDownloadIntent(model).apply {
        if (this@startRecognitionModelDownloadActivity !is Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    })
}


class ModelInfo(
    val name: String,
    val url: String,
    val targetFile: File = File(name),
    val sha256: String? = null,
    val expectedSize: Long? = null,
    size: Long?,
    progress: Float = 0.0f,
    error: Boolean = false,
    finished: Boolean = false
) {
    var size by mutableStateOf(size)
    var progress by mutableStateOf(progress)
    var error by mutableStateOf(error)
    var finished by mutableStateOf(finished)
    var started by mutableStateOf(false)
    var verifying by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var restarted by mutableStateOf(false)
}

internal fun incompleteDownloads(
    files: List<ModelInfo>,
    isValid: (ModelInfo) -> Boolean
) = files.filterNot(isValid)

val EXAMPLE_MODELS = listOf(
    ModelInfo(
        name = "tiny-encoder-xatn.tflite",
        url = "example.com",
        size = 56L * 1024L * 1024L,
        progress = 0.5f,
        error = true
    ),
    ModelInfo(
        name = "tiny-decoder.tflite",
        url = "example.com",
        size = 73L * 1024L * 1024L,
        progress = 0.3f,
        error = false
    ),
)

data class DownloadConfirmation(
    val source: String,
    val transferBytes: Long,
    val requiredFreeSpaceBytes: Long,
    val availableBytes: Long,
    val cellular: Boolean
) {
    val hasEnoughSpace = availableBytes >= requiredFreeSpaceBytes
}

private fun Long.megabytes() = "%.1f MB".format(this / 1_000_000.0)

@Composable
fun ModelItem(model: ModelInfo, showProgress: Boolean) {
    Column(modifier = Modifier.padding(16.dp, 8.dp)) {
        val color = if (model.error) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        }
        Surface(modifier = Modifier, color = color, shape = RoundedCornerShape(4.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                if (model.error) {
                    Icon(
                        Icons.Default.Warning, contentDescription = "Failed", modifier = Modifier
                            .align(CenterVertically)
                            .padding(4.dp)
                    )
                }

                val size = if (model.size != null) {
                    "%.1f".format(model.size!!.toFloat() / 1000000.0f)
                } else {
                    "?"
                }

                Column {
                    Text(model.name, style = Typography.bodyLarge)
                    Text(
                        "$size MB",
                        style = Typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    if (model.restarted) {
                        Text(stringResource(R.string.download_restarted), style = Typography.bodySmall)
                    }
                    if (showProgress && !model.error) {
                        val progressModifier = Modifier
                            .fillMaxWidth()
                            .padding(0.dp, 8.dp)

                        if (model.finished) {
                            LinearProgressIndicator(
                                progress = 1.0f,
                                modifier = progressModifier,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else if (model.size == null) {
                            LinearProgressIndicator(
                                modifier = progressModifier,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            LinearProgressIndicator(
                                progress = model.progress,
                                modifier = progressModifier,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }

            }
        }
    }
}

@Composable
@Preview(showBackground = true)
fun DownloadPrompt(
    onContinue: () -> Unit = {},
    onCancel: () -> Unit = {},
    models: List<ModelInfo> = EXAMPLE_MODELS,
    confirmation: DownloadConfirmation? = null
) {
    ScrollableList {
        ScreenTitle(stringResource(R.string.download_required))

        Text(
            stringResource(R.string.download_required_body),
            modifier = Modifier.padding(16.dp, 0.dp),
            style = Typography.bodyMedium
        )

        confirmation?.let {
            Text(stringResource(R.string.download_source, it.source), modifier = Modifier.padding(16.dp, 4.dp))
            Text(stringResource(R.string.download_transfer_size, it.transferBytes.megabytes()), modifier = Modifier.padding(16.dp, 4.dp))
            Text(stringResource(R.string.download_required_space, it.requiredFreeSpaceBytes.megabytes()), modifier = Modifier.padding(16.dp, 4.dp))
            if (it.requiredFreeSpaceBytes > it.transferBytes) {
                Text(stringResource(R.string.download_compressed_space_explanation), modifier = Modifier.padding(16.dp, 4.dp))
            }
            Text(
                stringResource(if (it.cellular) R.string.download_network_cellular else R.string.download_network_not_cellular),
                modifier = Modifier.padding(16.dp, 4.dp)
            )
            if (!it.hasEnoughSpace) {
                Text(
                    stringResource(R.string.download_insufficient_space, it.availableBytes.megabytes()),
                    modifier = Modifier.padding(16.dp, 4.dp),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        models.forEach { ModelItem(it, showProgress = false) }

        Spacer(modifier = Modifier.height(8.dp))

        Row {
            Button(
                onClick = onCancel, colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ), modifier = Modifier
                    .padding(8.dp)
                    .weight(1.0f)
            ) {
                Text(stringResource(R.string.cancel))
            }
            Button(
                onClick = onContinue,
                enabled = confirmation?.hasEnoughSpace != false,
                modifier = Modifier
                    .padding(8.dp)
                    .weight(1.5f)
            ) {
                Text(stringResource(R.string.continue_))
            }
        }
    }
}

@Composable
@Preview(showBackground = true)
fun DownloadScreen(models: List<ModelInfo> = EXAMPLE_MODELS, onRetry: (() -> Unit)? = null) {
    val finishedCount = models.count { it.finished }
    val hasUnknownActiveSize = models.any { !it.finished && !it.error && it.size == null }
    val knownSizeProgress = if (!hasUnknownActiveSize && models.all { it.size != null || it.finished }) {
        val totalSize = models.sumOf { it.size ?: 0L }
        if (totalSize > 0L) {
            val downloaded = models.sumOf {
                ((it.size ?: 0L).toDouble() * if (it.finished) 1.0 else it.progress.toDouble()).toLong()
            }
            downloaded.toFloat() / totalSize.toFloat()
        } else {
            finishedCount.toFloat() / max(models.size, 1).toFloat()
        }
    } else {
        null
    }

    ScrollableList {
        ScreenTitle(stringResource(R.string.download_progress))
        if (models.any { it.error }) {
            Text(
                models.mapNotNull { it.errorMessage }.joinToString("\n")
                    .ifEmpty { stringResource(R.string.download_failed) },
                modifier = Modifier.padding(16.dp, 0.dp),
                style = Typography.bodyMedium
            )
        } else {
            Text(
                stringResource(if (models.any { it.verifying }) R.string.download_verifying else R.string.download_in_progress),
                modifier = Modifier.padding(16.dp, 0.dp),
                style = Typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            stringResource(R.string.download_file_count, finishedCount, models.size),
            modifier = Modifier.padding(16.dp, 0.dp),
            style = Typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )

        if (knownSizeProgress == null) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp, 8.dp)
            )
        } else {
            LinearProgressIndicator(
                progress = knownSizeProgress.coerceIn(0.0f, 1.0f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp, 8.dp)
            )
        }

        models.forEach { ModelItem(it, showProgress = true) }
        if (models.any { it.error } && onRetry != null) {
            Button(onClick = onRetry, modifier = Modifier.padding(16.dp, 8.dp)) {
                Text(stringResource(R.string.download_retry))
            }
        }
    }
}

class DownloadActivity : ComponentActivity() {
    private var diagnosticDownload: DiagnosticSession? = null
    private var downloadStartedMs = 0L
    private var managedModel: RecognitionModel? = null
    private val modelLifecycle by lazy {
        RecognitionModelLifecycle.create(filesDir, BuildConfig.BUNDLE_PARAKEET_MODEL)
    }
    private lateinit var modelsToDownload: List<ModelInfo>
    private lateinit var allRequestedFiles: List<ModelInfo>
    private val httpClient = OkHttpClient()
    private var isDownloading by mutableStateOf(false)
    private var completionMarker: File? = null
    private var confirmation: DownloadConfirmation? = null
    private var archiveToDownload: ModelInfo? = null
    private var archiveRoot: String? = null

    private fun updateContent() {
        setContent {
            UixThemeAuto {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isDownloading) {
                        DownloadScreen(models = modelsToDownload,
                            onRetry = { retryDownloads() })
                    } else {
                        DownloadPrompt(
                            onContinue = { startDownload() },
                            onCancel = { cancel() },
                            models = modelsToDownload,
                            confirmation = confirmation
                        )
                    }
                }
            }
        }
    }

    private fun startDownload() {
        if (confirmation?.hasEnoughSpace == false) return
        lifecycleScope.launch {
            managedModel?.let { modelLifecycle.releaseArtifacts(it) }
            startDownloadAfterRuntimeRelease()
        }
    }

    private fun startDownloadAfterRuntimeRelease() {
        diagnosticDownload?.end(DiagnosticEvent.DOWNLOAD_CANCELLED)
        diagnosticDownload = AppDiagnostics.session(managedModel?.id ?: if (
            intent.getBooleanExtra(EXTRA_ENABLE_S1_MINI_AFTER_DOWNLOAD, false)) "s1_mini" else "whisper_ggml")
        downloadStartedMs = SystemClock.elapsedRealtime()
        diagnosticDownload?.event(DiagnosticEvent.DOWNLOAD_STARTED,
            mapOf(DiagnosticMetric.FILE_COUNT to allRequestedFiles.size.toLong()))
        completionMarker?.delete()
        isDownloading = true

        archiveToDownload?.let {
            downloadArchive(it)
            return
        }

        if (modelsToDownload.isEmpty()) {
            downloadsFinished()
            return
        }

        modelsToDownload.forEach { downloadFile(it) }
    }

    private fun retryDownloads() {
        archiveToDownload?.let {
            if (it.error) downloadArchive(it)
            return
        }
        modelsToDownload.filter { it.error }.forEach { downloadFile(it) }
    }

    private fun downloadFile(model: ModelInfo) {
        // Clear failure synchronously so repeated taps cannot enqueue duplicate retries.
        model.started = true
        model.error = false
        model.errorMessage = null
        model.finished = false
        model.restarted = false
        lifecycleScope.launch(Dispatchers.IO) {
            val lastUpdate = AtomicLong(0)
            val context = coroutineContext
            val progress: (Long) -> Unit = { downloaded ->
                context.ensureActive()
                val now = SystemClock.elapsedRealtime()
                val previous = lastUpdate.get()
                if (downloaded == model.expectedSize ||
                    now - previous >= 250L && lastUpdate.compareAndSet(previous, now)) {
                    updateModelOnMain {
                        model.progress = model.expectedSize?.let { downloaded.toFloat() / it } ?: 0f
                    }
                }
            }
            val restart: () -> Unit = { updateModelOnMain { model.restarted = true } }
            try {
                if (model.expectedSize != null && model.sha256 != null) {
                    downloadModelFile(httpClient, model.url, model.targetFile,
                        model.expectedSize, model.sha256, restart, progress)
                } else {
                    modelDownloadMutex.withLock {
                        ensureActive()
                        if (!isValidTargetFile(model)) {
                            val staging = File(model.targetFile.path + ".download.single")
                            downloadSequentialFile(httpClient, model.url, staging,
                                model.expectedSize, model.sha256, restart, progress)
                            ensureActive()
                            if (!staging.renameTo(model.targetFile)) throw IOException("Failed to install downloaded file")
                        }
                    }
                }
                ensureActive()
                markFinished(model)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                markError(model, error = error)
            }
        }
    }

    private fun downloadArchive(model: ModelInfo) {
        model.started = true
        model.error = false
        model.errorMessage = null
        model.verifying = false
        model.restarted = false
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                modelDownloadMutex.withLock {
                    ensureActive()
                    if (allRequestedFiles.all { isValidTargetFile(it) }) {
                        markFinished(model)
                        return@withLock
                    }
                    val total = requireNotNull(model.expectedSize)
                    var lastUpdate = 0L
                    downloadModelArchive(
                        client = httpClient,
                        archive = RecognitionModelArtifact(model.name, model.url, total, requireNotNull(model.sha256)),
                        savedArchive = savedArchive(model),
                        targetDirectory = requireNotNull(allRequestedFiles.firstOrNull()?.targetFile?.parentFile),
                        archiveRoot = requireNotNull(archiveRoot),
                        onRestart = { updateModelOnMain { model.restarted = true } },
                        artifacts = allRequestedFiles.map {
                            RecognitionModelArtifact(it.name, it.url, requireNotNull(it.expectedSize), requireNotNull(it.sha256))
                        }
                    ) { downloaded ->
                        ensureActive()
                        val now = SystemClock.elapsedRealtime()
                        if (downloaded == total || now - lastUpdate >= 250L) {
                            lastUpdate = now
                            updateModelOnMain {
                                model.progress = downloaded.toFloat() / total
                                model.verifying = downloaded == total
                            }
                        }
                    }
                    ensureActive()
                    markFinished(model)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                error.printStackTrace()
                markError(model, getString(R.string.download_archive_failed,
                    error.message ?: error.javaClass.simpleName), error = error)
            }
        }
    }

    private fun savedArchive(model: ModelInfo) = File(filesDir, ".${model.sha256}.archive.download")

    private fun updateModelOnMain(update: () -> Unit) {
        lifecycleScope.launch(Dispatchers.Main) {
            update()
        }
    }

    private fun markError(model: ModelInfo, message: String? = null, error: Throwable? = null, code: Int = 0) {
        // The UI's message can contain URLs or file paths; never include it in a standard report.
        diagnosticDownload?.event(DiagnosticEvent.DOWNLOAD_FAILED,
            mapOf(DiagnosticMetric.BYTES to (model.expectedSize ?: 0L),
                DiagnosticMetric.ERROR_CODE to code.toLong()), error = error)
        updateModelOnMain {
            model.errorMessage = message
            model.error = true
        }
    }

    private fun markFinished(model: ModelInfo) {
        updateModelOnMain {
            model.finished = true
            model.progress = 1.0f

            if (modelsToDownload.all { it.finished }) {
                downloadsFinished()
            }
        }
    }

    private fun isValidTargetFile(model: ModelInfo): Boolean =
        validModelFile(model.targetFile, model.expectedSize, model.sha256)

    override fun onDestroy() {
        httpClient.dispatcher.cancelAll()
        super.onDestroy()
    }

    private fun cancel() {
        diagnosticDownload?.end(DiagnosticEvent.DOWNLOAD_CANCELLED)
        val returnIntent = Intent()
        setResult(RESULT_CANCELED, returnIntent)
        finish()
    }

    private fun downloadsFinished() {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    check(allRequestedFiles.all { isValidTargetFile(it) }) {
                        "Downloaded model files failed validation"
                    }
                    managedModel?.let { model ->
                        check(modelLifecycle.completeInstallation(model, ::updateRecognitionModelSelection)) {
                            "Downloaded ${model.displayName} failed validation"
                        }
                    } ?: completionMarker?.let { marker ->
                        marker.parentFile?.mkdirs()
                        marker.writeText(
                            "${requireNotNull(intent.getStringExtra(EXTRA_MODEL_ID))}@" +
                                requireNotNull(intent.getStringExtra(EXTRA_MODEL_VERSION))
                        )
                    }
                    archiveToDownload?.let { savedArchive(it).delete() }
                }
                finishSuccessfulDownload()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (modelsToDownload.isEmpty()) {
                    modelsToDownload = archiveToDownload?.let { listOf(it) } ?: allRequestedFiles
                }
                diagnosticDownload?.event(DiagnosticEvent.DOWNLOAD_FAILED, error = error)
                modelsToDownload.forEach {
                    it.finished = false
                    it.error = true
                    it.errorMessage = error.message
                }
                isDownloading = true
                updateContent()
            }
        }
    }

    private fun finishSuccessfulDownload() {
        diagnosticDownload?.event(DiagnosticEvent.DOWNLOAD_FINISHED, mapOf(
            DiagnosticMetric.DURATION_MS to SystemClock.elapsedRealtime() - downloadStartedMs,
            DiagnosticMetric.BYTES to allRequestedFiles.sumOf { it.expectedSize ?: 0L }))
        if (intent.getBooleanExtra(EXTRA_ENABLE_S1_MINI_AFTER_DOWNLOAD, false)) {
            setSettingBlocking(S1_MINI_ENABLED.key, true)
        }
        setResult(RESULT_OK, Intent())
        finish()
    }

    private fun obtainModelSizes() {
        modelsToDownload.forEach {
            val request =
                Request.Builder().method("HEAD", null).header("accept-encoding", "identity")
                    .url(it.url).build()

            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    markError(it)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { response ->
                        var responseSize: Long? = null
                        try {
                            responseSize = response.headers["content-length"]?.toLongOrNull()
                        } catch (e: Exception) {
                            println("url failed ${it.url}")
                            println(response.headers)
                            e.printStackTrace()
                            markError(it)
                        }

                        if (response.code != 200) {
                            println("Bad response code ${response.code}")
                            markError(it)
                        }

                        updateModelOnMain {
                            it.size = responseSize
                        }
                    }
                }
            })
        }
    }

    private fun explicitDownloadRequests(): List<ModelInfo>? {
        val names = intent.getStringArrayListExtra(EXTRA_DOWNLOAD_FILE_NAMES) ?: return null
        val urls = intent.getStringArrayListExtra(EXTRA_DOWNLOAD_FILE_URLS)
            ?: throw IllegalStateException("intent extra `$EXTRA_DOWNLOAD_FILE_URLS` must be specified")
        val hashes = intent.getStringArrayListExtra(EXTRA_DOWNLOAD_FILE_HASHES)
            ?: throw IllegalStateException("intent extra `$EXTRA_DOWNLOAD_FILE_HASHES` must be specified")
        val sizes = intent.getLongArrayExtra(EXTRA_DOWNLOAD_FILE_SIZES)
            ?: throw IllegalStateException("intent extra `$EXTRA_DOWNLOAD_FILE_SIZES` must be specified")

        if (names.size != urls.size || hashes.size != names.size || sizes.size != names.size || hashes.any { it.isBlank() }) {
            throw IllegalStateException("download file names, urls, hashes, and sizes must be complete and matching")
        }

        val targetSubdir = intent.getStringExtra(EXTRA_TARGET_SUBDIR)
        val targetDir = if (targetSubdir != null) {
            File(filesDir, targetSubdir)
        } else {
            filesDir
        }

        targetDir.mkdirs()

        completionMarker = intent.getStringExtra(EXTRA_COMPLETION_MARKER)?.let {
            File(targetDir, it)
        }

        intent.getStringExtra(EXTRA_ARCHIVE_URL)?.let { url ->
            archiveRoot = requireNotNull(intent.getStringExtra(EXTRA_ARCHIVE_ROOT))
            archiveToDownload = ModelInfo(
                name = requireNotNull(intent.getStringExtra(EXTRA_ARCHIVE_NAME)),
                url = url,
                sha256 = requireNotNull(intent.getStringExtra(EXTRA_ARCHIVE_HASH)),
                expectedSize = intent.getLongExtra(EXTRA_ARCHIVE_SIZE, -1L).also { require(it > 0L) },
                size = intent.getLongExtra(EXTRA_ARCHIVE_SIZE, -1L)
            )
        }

        return names.indices.map { index ->
            ModelInfo(
                name = names[index],
                url = urls[index],
                targetFile = File(targetDir, names[index]),
                sha256 = hashes[index],
                expectedSize = sizes[index],
                size = sizes[index],
                progress = 0.0f
            )
        }
    }

    private fun legacyDownloadRequests(): List<ModelInfo> {
        val models = intent.getStringArrayListExtra("models")
            ?: throw IllegalStateException("intent extra `models` must be specified for DownloadActivity")

        return models.map {
            ModelInfo(
                name = it,
                url = "https://voiceinput.futo.org/VoiceInput/${it}",
                targetFile = File(filesDir, it),
                size = null,
                progress = 0.0f
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // A restored activity may still contain a manifest from an older app version.
        managedModel = intent.refreshRecognitionModel()
        allRequestedFiles = explicitDownloadRequests() ?: legacyDownloadRequests()
        intent.getStringExtra(EXTRA_DOWNLOAD_SOURCE)?.let { source ->
            val transferBytes = archiveToDownload?.expectedSize
                ?: allRequestedFiles.sumOf { it.expectedSize ?: 0L }
            confirmation = DownloadConfirmation(
                source = source,
                transferBytes = transferBytes,
                requiredFreeSpaceBytes = (intent.getLongExtra(EXTRA_REQUIRED_FREE_SPACE, transferBytes) -
                    (archiveToDownload?.let { savedArchive(it).length().coerceAtMost(transferBytes) } ?: 0L))
                    .coerceAtLeast(0L),
                availableBytes = filesDir.usableSpace,
                cellular = isCellularNetwork()
            )
        }
        lifecycleScope.launch {
            modelsToDownload = withContext(Dispatchers.IO) {
                val incomplete = incompleteDownloads(allRequestedFiles, ::isValidTargetFile)
                if (archiveToDownload == null) {
                    val retained = allRequestedFiles.sumOf { model ->
                        if (model !in incomplete) model.expectedSize ?: model.targetFile.length()
                        else model.expectedSize?.let { retainedDownloadBytes(model.targetFile, it) } ?: 0L
                    }
                    confirmation = confirmation?.let {
                        it.copy(requiredFreeSpaceBytes = (it.requiredFreeSpaceBytes - retained).coerceAtLeast(0L),
                            availableBytes = filesDir.usableSpace)
                    }
                }
                if (archiveToDownload != null && incomplete.isNotEmpty()) {
                    listOf(requireNotNull(archiveToDownload))
                } else {
                    incomplete
                }
            }
            if (modelsToDownload.isEmpty()) {
                downloadsFinished()
                return@launch
            }

            isDownloading = false
            updateContent()
            if (modelsToDownload.any { it.size == null }) obtainModelSizes()
        }
    }

    private fun isCellularNetwork(): Boolean {
        val manager = getSystemService(ConnectivityManager::class.java)
        val network = manager.activeNetwork ?: return false
        return manager.getNetworkCapabilities(network)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
    }
}

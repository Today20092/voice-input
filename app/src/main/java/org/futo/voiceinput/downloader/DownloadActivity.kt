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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.futo.voiceinput.R
import org.futo.voiceinput.sha256
import org.futo.voiceinput.recognition.RecognitionModel
import org.futo.voiceinput.settings.MOONSHINE_MODEL_VARIANT
import org.futo.voiceinput.settings.NEMOTRON_PROFILE
import org.futo.voiceinput.settings.SPEECH_BACKEND
import org.futo.voiceinput.settings.SpeechBackendType
import org.futo.voiceinput.settings.ScreenTitle
import org.futo.voiceinput.settings.ScrollableList
import org.futo.voiceinput.settings.setSettingBlocking
import org.futo.voiceinput.theme.UixThemeAuto
import org.futo.voiceinput.theme.Typography
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

const val EXTRA_DOWNLOAD_FILE_NAMES = "download_file_names"
const val EXTRA_DOWNLOAD_FILE_URLS = "download_file_urls"
const val EXTRA_DOWNLOAD_FILE_HASHES = "download_file_hashes"
const val EXTRA_DOWNLOAD_FILE_SIZES = "download_file_sizes"
const val EXTRA_TARGET_SUBDIR = "target_subdir"
const val EXTRA_COMPLETION_MARKER = "completion_marker"
const val EXTRA_DOWNLOAD_SOURCE = "download_source"
const val EXTRA_REQUIRED_FREE_SPACE = "required_free_space"
const val EXTRA_SELECT_BACKEND = "select_backend"
const val EXTRA_SELECT_VARIANT = "select_variant"
const val EXTRA_MODEL_ID = "recognition_model_id"
const val EXTRA_MODEL_VERSION = "recognition_model_version"
const val EXTRA_ARCHIVE_NAME = "recognition_model_archive_name"
const val EXTRA_ARCHIVE_URL = "recognition_model_archive_url"
const val EXTRA_ARCHIVE_HASH = "recognition_model_archive_hash"
const val EXTRA_ARCHIVE_SIZE = "recognition_model_archive_size"
const val EXTRA_ARCHIVE_ROOT = "recognition_model_archive_root"

private const val PARALLEL_DOWNLOAD_MIN_SIZE = 32L * 1024L * 1024L
private const val PROGRESS_SAVE_INTERVAL = 1024L * 1024L

fun Intent.putRecognitionModel(model: RecognitionModel) {
    putStringArrayListExtra(EXTRA_DOWNLOAD_FILE_NAMES, ArrayList(model.artifacts.map { it.name }))
    putStringArrayListExtra(EXTRA_DOWNLOAD_FILE_URLS, ArrayList(model.artifacts.map { it.url }))
    putStringArrayListExtra(EXTRA_DOWNLOAD_FILE_HASHES, ArrayList(model.artifacts.map { it.sha256 }))
    putExtra(EXTRA_DOWNLOAD_FILE_SIZES, model.artifacts.map { it.sizeBytes }.toLongArray())
    putExtra(EXTRA_TARGET_SUBDIR, model.directoryName)
    putExtra(EXTRA_COMPLETION_MARKER, model.completionMarker)
    putExtra(EXTRA_DOWNLOAD_SOURCE, model.source)
    putExtra(EXTRA_REQUIRED_FREE_SPACE, model.requiredFreeSpaceBytes)
    putExtra(EXTRA_SELECT_BACKEND, model.runtimeId)
    model.variantId?.let { putExtra(EXTRA_SELECT_VARIANT, it) }
    putExtra(EXTRA_MODEL_ID, model.id)
    putExtra(EXTRA_MODEL_VERSION, model.version)
    model.archive?.let { archive ->
        putExtra(EXTRA_ARCHIVE_NAME, archive.name)
        putExtra(EXTRA_ARCHIVE_URL, archive.url)
        putExtra(EXTRA_ARCHIVE_HASH, archive.sha256)
        putExtra(EXTRA_ARCHIVE_SIZE, archive.sizeBytes)
        putExtra(EXTRA_ARCHIVE_ROOT, model.archiveRoot)
    }
}

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
}

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
fun DownloadScreen(models: List<ModelInfo> = EXAMPLE_MODELS) {
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
                stringResource(R.string.download_failed),
                modifier = Modifier.padding(16.dp, 0.dp),
                style = Typography.bodyMedium
            )
        } else {
            Text(
                stringResource(R.string.download_in_progress),
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
    }
}

class DownloadActivity : ComponentActivity() {
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
                        DownloadScreen(models = modelsToDownload)
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

        modelsToDownload.forEach { model ->
            model.started = true
            model.error = false
            model.progress = 0.0f
            if ((model.expectedSize ?: 0L) >= PARALLEL_DOWNLOAD_MIN_SIZE) {
                lifecycleScope.launch(Dispatchers.IO) { downloadRanged(model) }
            } else {
                downloadSingle(model)
            }
        }
    }

    private suspend fun downloadRanged(model: ModelInfo) {
        val totalSize = requireNotNull(model.expectedSize)
        val ranges = downloadRanges(totalSize)
        val file = File(model.targetFile.absolutePath + ".download")
        val progressFiles = ranges.indices.map { File(file.absolutePath + ".range$it") }
        file.parentFile?.mkdirs()
        if (!file.exists()) progressFiles.forEach { it.delete() }

        fun savedBytes(progressFile: File) = runCatching {
            if (progressFile.isFile) progressFile.readText().toLongOrNull() ?: 0L else 0L
        }.getOrDefault(0L)

        val completed = AtomicLong(progressFiles.zip(ranges).sumOf { (progressFile, range) ->
            savedBytes(progressFile).coerceIn(0L, range.size)
        })
        val lastUiUpdate = AtomicLong(0L)
        updateModelOnMain { model.progress = completed.get().toFloat() / totalSize }

        try {
            coroutineScope {
                ranges.mapIndexed { index, range ->
                    async(Dispatchers.IO) {
                        val progressFile = progressFiles[index]
                        var rangeDownloaded = savedBytes(progressFile).coerceIn(0L, range.size)
                        if (rangeDownloaded == range.size) return@async

                        val start = range.resumeAt(rangeDownloaded)
                        val request = Request.Builder()
                            .url(model.url)
                            .header("Range", "bytes=$start-${range.endInclusive}")
                            .build()
                        httpClient.newCall(request).execute().use { response ->
                            val body = response.body
                            if (response.code != 206 || body == null) {
                                throw IOException("Server did not honor range request: HTTP ${response.code}")
                            }

                            RandomAccessFile(file, "rw").use { output ->
                                output.seek(start)
                                body.byteStream().use { input ->
                                    val buffer = ByteArray(128 * 1024)
                                    var lastSaved = rangeDownloaded
                                    while (rangeDownloaded < range.size) {
                                        val read = input.read(
                                            buffer,
                                            0,
                                            minOf(buffer.size.toLong(), range.size - rangeDownloaded).toInt()
                                        )
                                        if (read == -1) throw IOException("Range download ended early")
                                        output.write(buffer, 0, read)
                                        rangeDownloaded += read
                                        val totalDownloaded = completed.addAndGet(read.toLong())
                                        if (rangeDownloaded - lastSaved >= PROGRESS_SAVE_INTERVAL) {
                                            progressFile.writeText(rangeDownloaded.toString())
                                            lastSaved = rangeDownloaded
                                        }
                                        val now = SystemClock.elapsedRealtime()
                                        val previousUpdate = lastUiUpdate.get()
                                        if (totalDownloaded == totalSize ||
                                            now - previousUpdate >= 250L &&
                                            lastUiUpdate.compareAndSet(previousUpdate, now)
                                        ) {
                                            updateModelOnMain {
                                                model.progress = totalDownloaded.toFloat() / totalSize
                                            }
                                        }
                                    }
                                }
                            }
                            progressFile.writeText(rangeDownloaded.toString())
                        }
                    }
                }.awaitAll()
            }

            if (file.length() != totalSize || !isValidDownloadedFile(file, model.sha256)) {
                file.delete()
                progressFiles.forEach { it.delete() }
                throw IOException("Downloaded file failed size or checksum validation")
            }
            if (model.targetFile.exists() && !model.targetFile.delete()) {
                throw IOException("Failed to replace ${model.targetFile.absolutePath}")
            }
            if (!file.renameTo(model.targetFile)) throw IOException("Failed to install ${model.name}")
            progressFiles.forEach { it.delete() }
            markFinished(model)
        } catch (error: Exception) {
            error.printStackTrace()
            markError(model)
        }
    }

    private fun downloadSingle(model: ModelInfo) {
        val request = Request.Builder().get().url(model.url).build()
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = markError(model)

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = response.body
                    if (!response.isSuccessful || body == null) {
                        markError(model)
                        return
                    }
                    val file = File.createTempFile(model.name + ".download", null, cacheDir)
                    try {
                        body.byteStream().use { input ->
                            file.outputStream().use { output -> input.copyTo(output, 128 * 1024) }
                        }
                        if (!isValidDownloadedFile(file, model.sha256)) throw IOException("Checksum failed")
                        model.targetFile.parentFile?.mkdirs()
                        if (model.targetFile.exists() && !model.targetFile.delete()) {
                            throw IOException("Failed to replace ${model.targetFile.absolutePath}")
                        }
                        if (!file.renameTo(model.targetFile)) {
                            file.copyTo(model.targetFile, overwrite = true)
                            file.delete()
                        }
                        markFinished(model)
                    } catch (error: Exception) {
                        error.printStackTrace()
                        file.delete()
                        markError(model)
                    }
                }
            }
        })
    }

    private fun downloadArchive(model: ModelInfo) {
        model.started = true
        model.error = false
        val request = Request.Builder().get().url(model.url).build()
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = markError(model)

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = response.body
                    if (!response.isSuccessful || body == null) {
                        markError(model)
                        return
                    }
                    try {
                        val progressInput = ProgressInputStream(body.byteStream(), model.expectedSize) {
                            updateModelOnMain { model.progress = it }
                        }
                        val artifacts = allRequestedFiles.map {
                            org.futo.voiceinput.recognition.RecognitionModelArtifact(
                                it.name,
                                it.url,
                                requireNotNull(it.expectedSize),
                                requireNotNull(it.sha256)
                            )
                        }
                        extractModelArchive(
                            input = progressInput,
                            targetDirectory = requireNotNull(allRequestedFiles.firstOrNull()?.targetFile?.parentFile),
                            archiveRoot = requireNotNull(archiveRoot),
                            artifacts = artifacts,
                            expectedArchiveSha256 = requireNotNull(model.sha256)
                        )
                        require(model.expectedSize == null || progressInput.bytesRead == model.expectedSize) {
                            "Downloaded archive size mismatch"
                        }
                        markFinished(model)
                    } catch (error: Exception) {
                        error.printStackTrace()
                        markError(model)
                    }
                }
            }
        })
    }

    private fun updateModelOnMain(update: () -> Unit) {
        lifecycleScope.launch(Dispatchers.Main) {
            update()
        }
    }

    private fun markError(model: ModelInfo) {
        updateModelOnMain {
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

    private fun isValidDownloadedFile(file: File, expectedSha256: String?): Boolean {
        return file.exists() && file.length() > 0L && (expectedSha256 == null || sha256(file) == expectedSha256)
    }

    private fun isValidTargetFile(model: ModelInfo): Boolean {
        return isValidDownloadedFile(model.targetFile, model.sha256) &&
            (model.expectedSize == null || model.targetFile.length() == model.expectedSize)
    }

    private fun cancel() {
        val returnIntent = Intent()
        setResult(RESULT_CANCELED, returnIntent)
        finish()
    }

    private fun downloadsFinished() {
        if (!allRequestedFiles.all { isValidTargetFile(it) }) {
            modelsToDownload.forEach { it.error = true }
            return
        }

        completionMarker?.let { marker ->
            marker.parentFile?.mkdirs()
            val modelId = requireNotNull(intent.getStringExtra(EXTRA_MODEL_ID))
            val modelVersion = requireNotNull(intent.getStringExtra(EXTRA_MODEL_VERSION))
            marker.writeText("$modelId@$modelVersion")
        }

        finishSuccessfulDownload()
    }

    private fun finishSuccessfulDownload() {
        val backend = intent.getStringExtra(EXTRA_SELECT_BACKEND)
        intent.getStringExtra(EXTRA_SELECT_VARIANT)?.let { variant ->
            when (backend) {
                SpeechBackendType.Moonshine.id -> setSettingBlocking(MOONSHINE_MODEL_VARIANT.key, variant)
                SpeechBackendType.Nemotron.id -> setSettingBlocking(NEMOTRON_PROFILE.key, variant)
            }
        }
        backend?.let {
            setSettingBlocking(SPEECH_BACKEND.key, it)
        }

        val returnIntent = Intent()
        setResult(RESULT_OK, returnIntent)
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

        allRequestedFiles = explicitDownloadRequests() ?: legacyDownloadRequests()
        intent.getStringExtra(EXTRA_DOWNLOAD_SOURCE)?.let { source ->
            val transferBytes = archiveToDownload?.expectedSize
                ?: allRequestedFiles.sumOf { it.expectedSize ?: 0L }
            confirmation = DownloadConfirmation(
                source = source,
                transferBytes = transferBytes,
                requiredFreeSpaceBytes = intent.getLongExtra(EXTRA_REQUIRED_FREE_SPACE, transferBytes),
                availableBytes = filesDir.usableSpace,
                cellular = isCellularNetwork()
            )
        }
        modelsToDownload = if (archiveToDownload != null &&
            (completionMarker?.isFile != true || allRequestedFiles.any { !isValidTargetFile(it) })) {
            listOf(requireNotNull(archiveToDownload))
        } else if (completionMarker != null && completionMarker?.isFile != true) {
            allRequestedFiles
        } else {
            allRequestedFiles.filter { !isValidTargetFile(it) }
        }

        if (modelsToDownload.isEmpty()) {
            downloadsFinished()
            return
        }

        isDownloading = false
        updateContent()

        if (modelsToDownload.any { it.size == null }) obtainModelSizes()
    }

    private fun isCellularNetwork(): Boolean {
        val manager = getSystemService(ConnectivityManager::class.java)
        val network = manager.activeNetwork ?: return false
        return manager.getNetworkCapabilities(network)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
    }
}

private class ProgressInputStream(
    input: InputStream,
    private val totalBytes: Long?,
    private val onProgress: (Float) -> Unit
) : FilterInputStream(input) {
    var bytesRead = 0L
        private set
    private var lastProgress = 0.0f
    private var lastUpdateTime = 0L

    override fun read(): Int = super.read().also { if (it >= 0) advanced(1) }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        super.read(buffer, offset, length).also { if (it > 0) advanced(it) }

    private fun advanced(count: Int) {
        bytesRead += count
        val total = totalBytes ?: return
        val progress = (bytesRead.toFloat() / total.toFloat()).coerceIn(0.0f, 1.0f)
        val now = SystemClock.elapsedRealtime()
        if (progress - lastProgress >= 0.01f || now - lastUpdateTime >= 250L) {
            lastProgress = progress
            lastUpdateTime = now
            onProgress(progress)
        }
    }
}

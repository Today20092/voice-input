package org.futo.voiceinput.downloader

import android.content.Intent
import android.os.Bundle
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
import org.futo.voiceinput.parakeet.sha256
import org.futo.voiceinput.settings.ScreenTitle
import org.futo.voiceinput.settings.ScrollableList
import org.futo.voiceinput.theme.UixThemeAuto
import org.futo.voiceinput.theme.Typography
import java.io.File
import java.io.IOException

const val EXTRA_DOWNLOAD_FILE_NAMES = "download_file_names"
const val EXTRA_DOWNLOAD_FILE_URLS = "download_file_urls"
const val EXTRA_DOWNLOAD_FILE_HASHES = "download_file_hashes"
const val EXTRA_TARGET_SUBDIR = "target_subdir"
const val EXTRA_COMPLETION_MARKER = "completion_marker"


data class ModelInfo(
    val name: String,
    val url: String,
    val targetFile: File = File(name),
    val sha256: String? = null,
    var size: Long?,
    var progress: Float = 0.0f,
    var error: Boolean = false,
    var finished: Boolean = false
)

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
                        LinearProgressIndicator(
                            progress = model.progress, modifier = Modifier
                                .fillMaxWidth()
                                .padding(0.dp, 8.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
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
    models: List<ModelInfo> = EXAMPLE_MODELS
) {
    ScrollableList {
        ScreenTitle(stringResource(R.string.download_required))

        Text(
            stringResource(R.string.download_required_body),
            modifier = Modifier.padding(16.dp, 0.dp),
            style = Typography.bodyMedium
        )

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
                onClick = onContinue, modifier = Modifier
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

        models.forEach { ModelItem(it, showProgress = true) }
    }
}

class DownloadActivity : ComponentActivity() {
    private lateinit var modelsToDownload: List<ModelInfo>
    private lateinit var allRequestedFiles: List<ModelInfo>
    private val httpClient = OkHttpClient()
    private var isDownloading = false
    private var completionMarker: File? = null

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
                            models = modelsToDownload
                        )
                    }
                }
            }
        }
    }

    private fun startDownload() {
        isDownloading = true
        updateContent()

        if (modelsToDownload.isEmpty()) {
            downloadsFinished()
            return
        }

        modelsToDownload.forEach {
            it.error = false
            it.progress = 0.0f
            val request = Request.Builder().method("GET", null).url(it.url).build()

            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    it.error = true
                    updateContentOnMain()
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { response ->
                        if (!response.isSuccessful) {
                            it.error = true
                            updateContentOnMain()
                            return
                        }

                        response.body?.source()?.let { source ->

                            it.size = response.headers["content-length"]?.toLongOrNull()

                            val fileName = it.name + ".download"
                            val file =
                                File.createTempFile(fileName, null, this@DownloadActivity.cacheDir)

                            try {
                                file.outputStream().use { os ->
                                    val buffer = ByteArray(128 * 1024)
                                    var downloaded = 0L
                                    while (true) {
                                        val read = source.read(buffer)
                                        if (read == -1) {
                                            break
                                        }

                                        os.write(buffer, 0, read)

                                        downloaded += read

                                        if (it.size != null) {
                                            it.progress = downloaded.toFloat() / it.size!!.toFloat()
                                        }

                                        updateContentOnMain()
                                    }
                                }

                                if (!isValidDownloadedFile(file, it.sha256)) {
                                    file.delete()
                                    it.error = true
                                    updateContentOnMain()
                                    return
                                }

                                it.targetFile.parentFile?.mkdirs()
                                if (it.targetFile.exists() && !it.targetFile.delete()) {
                                    throw IOException("Failed to replace ${it.targetFile.absolutePath}")
                                }

                                if (!file.renameTo(it.targetFile)) {
                                    file.copyTo(it.targetFile, overwrite = true)
                                    file.delete()
                                }

                                it.finished = true
                                it.progress = 1.0f
                            } catch (e: Exception) {
                                e.printStackTrace()
                                file.delete()
                                it.error = true
                            }

                            if (modelsToDownload.all { a -> a.finished }) {
                                downloadsFinishedOnMain()
                            } else {
                                updateContentOnMain()
                            }
                        } ?: run {
                            it.error = true
                            updateContentOnMain()
                        }
                    }
                }
            })
        }
    }

    private fun updateContentOnMain() {
        lifecycleScope.launch {
            withContext(Dispatchers.Main) {
                updateContent()
            }
        }
    }

    private fun downloadsFinishedOnMain() {
        lifecycleScope.launch {
            withContext(Dispatchers.Main) {
                downloadsFinished()
            }
        }
    }

    private fun isValidDownloadedFile(file: File, expectedSha256: String?): Boolean {
        return file.exists() && (expectedSha256 == null || sha256(file) == expectedSha256)
    }

    private fun isValidTargetFile(model: ModelInfo): Boolean {
        return isValidDownloadedFile(model.targetFile, model.sha256)
    }

    private fun cancel() {
        val returnIntent = Intent()
        setResult(RESULT_CANCELED, returnIntent)
        finish()
    }

    private fun downloadsFinished() {
        if (!allRequestedFiles.all { isValidTargetFile(it) }) {
            modelsToDownload.forEach { it.error = true }
            updateContentOnMain()
            return
        }

        completionMarker?.let { marker ->
            marker.parentFile?.mkdirs()
            marker.writeText("ok")
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
                    it.error = true
                    updateContentOnMain()
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { response ->
                        try {
                            it.size = response.headers["content-length"]?.toLongOrNull()
                        } catch (e: Exception) {
                            println("url failed ${it.url}")
                            println(response.headers)
                            e.printStackTrace()
                            it.error = true
                        }

                        if (response.code != 200) {
                            println("Bad response code ${response.code}")
                            it.error = true
                        }
                        updateContentOnMain()
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

        if (names.size != urls.size || (hashes != null && hashes.size != names.size)) {
            throw IllegalStateException("download file names, urls, and hashes must have matching sizes")
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

        return names.indices.map { index ->
            val hash = hashes?.get(index)?.ifBlank { null }
            ModelInfo(
                name = names[index],
                url = urls[index],
                targetFile = File(targetDir, names[index]),
                sha256 = hash,
                size = null,
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
        modelsToDownload = allRequestedFiles.filter { !isValidTargetFile(it) }

        if (modelsToDownload.isEmpty()) {
            downloadsFinished()
            return
        }

        isDownloading = false
        updateContent()

        obtainModelSizes()
    }
}

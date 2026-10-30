package org.futo.voiceinput.benchmark

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.futo.voiceinput.RecognizeActivity
import org.futo.voiceinput.theme.UixThemeAuto
import java.io.File

class LiveComparisonActivity : ComponentActivity() {
    private lateinit var store: BenchmarkStore
    private var report by mutableStateOf<BenchmarkReport?>(null)
    private var revision by mutableIntStateOf(0)
    private var failure by mutableStateOf<String?>(null)
    private var editor: EditText? = null
    private var session: LiveBenchmarkSession? = null
    private val recognition = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (text != null) session?.observed(text) else session?.failure("Activity returned no transcript")
        } else session?.failure("Activity reading cancelled")
        revision++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = BenchmarkStore(File(filesDir, "model-benchmarks"))
        lifecycleScope.launch {
            try {
                val runId = requireNotNull(intent.getStringExtra("runId"))
                session = LiveBenchmark.active?.takeIf { it.report.runId == runId }
                report = session?.report ?: withContext(Dispatchers.IO) { store.load(runId) }
            }
            catch (_: Exception) { failure = "Cannot open the saved benchmark" }
        }
        setContent { UixThemeAuto { Surface { Readings() } } }
    }

    private fun start(model: BenchmarkModel, entry: String, reference: String) {
        lifecycleScope.launch {
            try {
                endReading()
                session = withContext(Dispatchers.IO) { LiveBenchmark.arm(this@LiveComparisonActivity, requireNotNull(report), model, entry, reference) }
                if (entry == "activity") recognition.launch(Intent(this@LiveComparisonActivity, RecognizeActivity::class.java).setAction(RecognizerIntent.ACTION_RECOGNIZE_SPEECH))
                else {
                    val target = requireNotNull(editor)
                    val reading = session
                    target.setText("")
                    target.requestFocus()
                    val manager = getSystemService(InputMethodManager::class.java)
                    manager.showSoftInput(target, InputMethodManager.SHOW_IMPLICIT)
                    // Android excludes auxiliary voice keyboards while the soft keyboard is hidden.
                    val shown = withTimeoutOrNull(2000) {
                        while (ViewCompat.getRootWindowInsets(target)?.isVisible(WindowInsetsCompat.Type.ime()) != true) delay(50)
                        true
                    } == true
                    if (session === reading && !isFinishing) {
                        if (shown) manager.showInputMethodPicker()
                        else failure = "Tap the controlled editor to show your keyboard, then try Test keyboard again."
                    }
                }
                revision++
            } catch (_: Exception) { failure = "Cannot start reading. Finish dictation and check that this model is installed." }
        }
    }

    private fun endReading() {
        session?.let { active ->
            if (active.reading.entryPoint == "ime" && active.reading.delivered != null) active.observed(editor?.text?.toString().orEmpty())
            if (active.reading.status in setOf("armed", "delivered")) active.failure("Reading ended without verified final delivery")
        }
        session = null
        LiveBenchmark.end()
    }

    override fun onDestroy() { if (isFinishing) endReading(); super.onDestroy() }

    @Composable private fun Readings() {
        val current = report
        val editorColor = LocalContentColor.current.toArgb()
        var reference by rememberSaveable { mutableStateOf("Please remind me to pick up milk, eggs, and coffee after work tomorrow.") }
        LaunchedEffect(Unit) { while (true) {
            delay(1000)
            session?.takeIf { it.reading.entryPoint == "ime" && it.reading.status == "delivered" }
                ?.observed(editor?.text?.toString().orEmpty())
            revision++
        } }
        val observedRevision = revision
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Guided live readings", style = MaterialTheme.typography.headlineMedium)
            Text("Read the same short passage separately for each model through both entry points. Each reading differs, so these results cannot rank models using identical audio. Configurable cleanup and personal vocabulary are disabled.")
            OutlinedTextField(reference, { if (it.length <= 6000) reference = it }, label = { Text("Passage to read") }, modifier = Modifier.fillMaxWidth())
            Text("Keyboard test: choose Voice Input in the keyboard picker and read into this controlled editor. Activity test: read into the normal speech-recognition window. Keep this page open until delivery is complete. Review spoken deviations below.")
            AndroidView(factory = { context -> EditText(context).apply {
                id = android.R.id.edit
                hint = "Controlled editor for keyboard insertion"
                setTextColor(editorColor)
                setHintTextColor(editorColor)
                minLines = 3
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        session?.takeIf { it.reading.entryPoint == "ime" && it.reading.delivered != null }?.observed(s?.toString().orEmpty())
                    }
                })
                editor = this
            } }, modifier = Modifier.fillMaxWidth().height(140.dp))
            OutlinedButton(onClick = { endReading(); revision++ }) { Text("Finish current reading and verify editor") }
            current?.let { saved ->
                BenchmarkModel.all().filter { it.id in saved.selectedModelIds }.forEach { candidate ->
                    Text(candidate.name, style = MaterialTheme.typography.titleMedium)
                    val readings = saved.liveReadings.filter { it.modelId == candidate.id }
                    Text("${readings.count { it.entryPoint == "ime" && it.deliveryVerified == true }} verified keyboard readings; ${readings.count { it.entryPoint == "activity" && it.deliveryVerified == true }} verified activity readings.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { start(candidate, "ime", reference) }) { Text("Test keyboard") }
                        OutlinedButton(onClick = { start(candidate, "activity", reference) }) { Text("Test activity") }
                    }
                }
                Text("Reading results", style = MaterialTheme.typography.titleLarge)
                saved.liveReadings.toList().forEach { reading ->
                    Text("${reading.modelId} / ${reading.entryPoint}: ${reading.status}. Delivery verified: ${reading.deliveryVerified ?: "pending"}. ${reading.reason.orEmpty()}")
                    Text("Raw: ${reading.raw ?: "Not produced"}")
                    Text("Delivered: ${reading.delivered ?: "Not produced"}")
                    Text("Observed: ${reading.observed ?: "Not observed"}")
                    var actual by remember(reading.id) { mutableStateOf(reading.reference) }
                    OutlinedTextField(actual, { if (it.length <= 6000) actual = it }, label = { Text("Actual spoken words, correct deviations") }, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = {
                        lifecycleScope.launch(Dispatchers.IO) {
                            reading.reference = actual
                            reading.accuracy = reading.raw?.let { BenchmarkScoring.score(actual, it) }
                            store.save(saved)
                        }
                    }) { Text("Save reviewed reference") }
                }
            }
            failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("Detailed live timing boundaries are included in the saved JSON and Markdown report. First partial and VAD endpoint are unavailable when not emitted or VAD is disabled.")
            TextButton(onClick = { endReading(); finish() }) { Text("Return to comparison") }
        }
    }
}

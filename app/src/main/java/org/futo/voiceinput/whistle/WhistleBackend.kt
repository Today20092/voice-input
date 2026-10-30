package org.futo.voiceinput.whistle

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.futo.voiceinput.backend.SpeechBackend
import org.futo.voiceinput.recognition.RecognitionModelStore
import java.io.File

class WhistleBackend : SpeechBackend {
    private val mutex = Mutex()
    private var runner: File? = null
    private var model: File? = null
    private var workDirectory: File? = null
    override var detectedLanguage: String? = null
        private set

    override suspend fun load(context: Context) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val store = RecognitionModelStore(context.filesDir)
            check(store.isInstalled(WhistleModel.model, verifyHashes = true)) { "Whistle is not installed or needs repair" }
            val executable = File(context.applicationInfo.nativeLibraryDir, "libwhistle_runner.so")
            check(executable.canExecute()) { "Whistle runtime is unavailable" }
            runner = executable
            model = File(store.modelDirectory(WhistleModel.model), "whistle.cact")
            workDirectory = File(context.cacheDir, "whistle-work")
            Unit
        }
    }

    override suspend fun transcribe(samples: FloatArray): String = mutex.withLock {
        detectedLanguage = null
        val result = runWhistle(
            checkNotNull(runner) { "Whistle is not loaded" },
            checkNotNull(model) { "Whistle is not loaded" },
            checkNotNull(workDirectory) { "Whistle is not loaded" }, samples
        )
        detectedLanguage = result.language
        result.text
    }

    override suspend fun close() {
        mutex.withLock { runner = null; model = null; workDirectory = null; detectedLanguage = null }
    }
}

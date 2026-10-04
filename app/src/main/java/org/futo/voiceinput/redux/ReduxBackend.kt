package org.futo.voiceinput.redux

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.futo.voiceinput.backend.SpeechBackend
import org.futo.voiceinput.recognition.RecognitionModelStore
import java.io.File

internal object ReduxNative {
    init { System.loadLibrary("parakeet_redux") }
    external fun load(path: String): Long
    external fun transcribe(handle: Long, samples: FloatArray): String
    external fun free(handle: Long)
}

class ReduxBackend : SpeechBackend {
    private val mutex = Mutex()
    private var handle = 0L

    override suspend fun load(context: Context) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (handle == 0L) {
                val store = RecognitionModelStore(context.filesDir)
                check(store.isInstalled(ReduxModel.model)) { "Parakeet Redux is not installed" }
                handle = ReduxNative.load(File(store.modelDirectory(ReduxModel.model), ReduxModel.FILE_NAME).absolutePath)
                check(handle != 0L) { "Could not load Parakeet Redux" }
            }
        }
    }

    override suspend fun transcribe(samples: FloatArray): String = withContext(Dispatchers.Default) {
        mutex.withLock {
            check(handle != 0L) { "Parakeet Redux is not loaded" }
            if (samples.isEmpty()) "" else ReduxNative.transcribe(handle, samples)
        }
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (handle != 0L) ReduxNative.free(handle)
            handle = 0L
        }
    }
}

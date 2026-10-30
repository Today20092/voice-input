package org.futo.voiceinput.phonon

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.futo.voiceinput.backend.SpeechBackend
import org.futo.voiceinput.recognition.RecognitionModelStore

internal object PhononNative {
    init { System.loadLibrary("phonon_jni") }
    external fun load(directory: String, libraryDirectory: String): Long
    external fun transcribe(handle: Long, samples: FloatArray): ByteArray
    external fun free(handle: Long)
}

class PhononBackend : SpeechBackend {
    override val detectedLanguage = "en"
    private val mutex = Mutex()
    private var handle = 0L

    override suspend fun load(context: Context) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (handle == 0L) {
                val store = RecognitionModelStore(context.filesDir)
                // Native manifest parsing must only receive the pinned, verified package.
                check(store.isInstalled(PhononModel.model, verifyHashes = true)) { "Phonon 2 is not installed or needs repair" }
                handle = PhononNative.load(
                    store.modelDirectory(PhononModel.model).absolutePath,
                    context.applicationInfo.nativeLibraryDir
                )
                check(handle != 0L) { "Could not load Phonon 2" }
            }
        }
    }

    override suspend fun transcribe(samples: FloatArray): String = withContext(Dispatchers.Default) {
        mutex.withLock {
            check(handle != 0L) { "Phonon 2 is not loaded" }
            if (samples.isEmpty()) "" else PhononNative.transcribe(handle, samples).toString(Charsets.UTF_8)
        }
    }

    override suspend fun close() = withContext(NonCancellable + Dispatchers.IO) {
        mutex.withLock {
            if (handle != 0L) PhononNative.free(handle)
            handle = 0L
        }
    }
}

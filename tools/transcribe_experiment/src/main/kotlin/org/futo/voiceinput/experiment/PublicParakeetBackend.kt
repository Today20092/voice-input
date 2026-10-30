package org.futo.voiceinput.experiment

import android.content.Context
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.futo.voiceinput.backend.SpeechBackend

internal interface PublicParakeetSession {
    fun reset()
    fun transcribe(samples: FloatArray): String
    fun cancel()
    fun close()
}

/** Isolated, English, final-only experiment. Never registered in the app catalog. */
class PublicParakeetBackend internal constructor(
    private val open: () -> PublicParakeetSession
) : SpeechBackend {
    constructor(model: File, language: String = "en") : this({
        require(language == "en") { "The experimental model supports English only" }
        val digest = MessageDigest.getInstance("SHA-256")
        model.inputStream().buffered().use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        require(actual == "7dd44c74a331d788a4e5f8b16913b3feb29ced22cf5613aad0e0f6cd30516296") {
            "Experimental model checksum mismatch"
        }
        NativeParakeet(model.absolutePath)
    })

    private val mutex = Mutex()
    private val state = Any()
    private var session: PublicParakeetSession? = null
    private var closed = false
    override val detectedLanguage: String get() = "en"

    override suspend fun load(context: Context) = loadForExperiment()

    // Also usable by the host experiment, which does not create an Android Context.
    suspend fun loadForExperiment() = withContext(Dispatchers.IO) {
        mutex.withLock {
            synchronized(state) {
                check(!closed) { "Backend is closed" }
                if (session != null) return@withLock
            }
            val loaded = open()
            try {
                currentCoroutineContext().ensureActive()
                synchronized(state) {
                    check(!closed) { "Backend is closed" }
                    session = loaded
                }
            } catch (error: Throwable) {
                loaded.close()
                throw error
            }
        }
    }

    override suspend fun transcribe(samples: FloatArray): String = mutex.withLock {
        require(samples.isNotEmpty() && samples.all { it.isFinite() && it in -1f..1f }) {
            "Expected nonempty 16 kHz mono PCM in [-1, 1]"
        }
        val active = synchronized(state) {
            check(!closed) { "Backend is closed" }
            checkNotNull(session) { "Backend is not loaded" }.also { it.reset() }
        }
        // Awaiting is cancellable; the native call itself is not. Keep the mutex until
        // coroutineScope has joined the worker, including after its abort callback fires.
        val result = coroutineScope {
            val worker = async(Dispatchers.IO) { active.transcribe(samples) }
            try {
                worker.await()
            } finally {
                if (!isActive) active.cancel()
            }
        }
        currentCoroutineContext().ensureActive()
        synchronized(state) {
            if (closed) throw CancellationException("Backend closed during transcription")
        }
        result
    }

    override suspend fun close() {
        synchronized(state) {
            closed = true
            session?.cancel()
        }
        withContext(NonCancellable + Dispatchers.IO) {
            mutex.withLock {
                synchronized(state) {
                    session?.close()
                    session = null
                }
            }
        }
    }
}

private class NativeParakeet(path: String) : PublicParakeetSession {
    init { System.loadLibrary("share09_parakeet") }
    private val handle = nativeOpen(path.toByteArray(Charsets.UTF_8))
    override fun reset() = nativeReset(handle)
    override fun transcribe(samples: FloatArray) = nativeTranscribe(handle, samples).toString(Charsets.UTF_8)
    override fun cancel() = nativeCancel(handle)
    override fun close() = nativeClose(handle)
    private external fun nativeOpen(path: ByteArray): Long
    private external fun nativeReset(handle: Long)
    private external fun nativeTranscribe(handle: Long, samples: FloatArray): ByteArray
    private external fun nativeCancel(handle: Long)
    private external fun nativeClose(handle: Long)
}

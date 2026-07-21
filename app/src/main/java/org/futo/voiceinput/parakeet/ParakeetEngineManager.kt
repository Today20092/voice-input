package org.futo.voiceinput.parakeet

import android.content.Context
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.futo.voiceinput.backend.SpeechBackend

internal class ParakeetEngineLease(
    internal val id: Long,
    private val backend: ParakeetBackend
) : SpeechBackend {
    override suspend fun load(context: Context) = Unit
    override suspend fun transcribe(samples: FloatArray): String = backend.transcribe(samples)
    override suspend fun close() = Unit
}

object ParakeetEngineManager {
    private val mutex = Mutex()
    private var backend: ParakeetBackend? = null
    private var unloadJob: Job? = null
    private val activeLeaseIds = mutableSetOf<Long>()
    private var nextLeaseId = 0L

    internal suspend fun acquire(context: Context): ParakeetEngineLease = mutex.withLock {
        val startedAt = System.nanoTime()
        unloadJob?.cancel()
        unloadJob = null

        val current = backend
        if (current != null) {
            current.configureDiagnostics(context)
            current.logEngineAcquired(warm = true, elapsedMs = (System.nanoTime() - startedAt) / 1_000_000)
            return@withLock newLease(current)
        }

        backend = null
        val loadedBackend = ParakeetBackend().also {
            it.load(context.applicationContext)
            it.logEngineAcquired(warm = false, elapsedMs = (System.nanoTime() - startedAt) / 1_000_000)
            backend = it
        }
        newLease(loadedBackend)
    }

    private fun newLease(backend: ParakeetBackend): ParakeetEngineLease {
        val id = ++nextLeaseId
        activeLeaseIds += id
        return ParakeetEngineLease(id, backend)
    }

    internal suspend fun release(
        lease: ParakeetEngineLease,
        scope: LifecycleCoroutineScope,
        keepWarm: Boolean,
        timeoutMs: Long = 0L
    ) = mutex.withLock {
        if (!activeLeaseIds.remove(lease.id)) {
            return@withLock
        }
        if (activeLeaseIds.isNotEmpty()) {
            return@withLock
        }

        unloadJob?.cancel()
        unloadJob = null
        if (!keepWarm) {
            backend?.close()
            backend = null
            return@withLock
        }

        unloadJob = scope.launch {
            delay(timeoutMs)
            mutex.withLock {
                backend?.close()
                backend = null
            }
        }
    }

    suspend fun forceClose() = mutex.withLock {
        unloadJob?.cancel()
        unloadJob = null
        activeLeaseIds.clear()
        backend?.close()
        backend = null
    }

    fun isWarm(): Boolean = backend != null
}

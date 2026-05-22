package org.futo.voiceinput.parakeet

import android.content.Context
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object ParakeetEngineManager {
    private val mutex = Mutex()
    private var backend: ParakeetBackend? = null
    private var unloadJob: Job? = null

    suspend fun acquire(context: Context): ParakeetBackend = mutex.withLock {
        unloadJob?.cancel()
        unloadJob = null

        val current = backend
        if (current != null && ParakeetNative.isLoaded()) {
            return@withLock current
        }

        backend = null
        ParakeetBackend().also {
            it.load(context.applicationContext)
            backend = it
        }
    }

    fun markIdle(scope: LifecycleCoroutineScope, timeoutMs: Long) {
        unloadJob?.cancel()
        ParakeetNative.markIdle()
        unloadJob = scope.launch {
            delay(timeoutMs)
            ParakeetNative.unloadIfIdle(timeoutMs)
            mutex.withLock {
                if (!ParakeetNative.isLoaded()) {
                    backend = null
                }
            }
        }
    }

    suspend fun forceClose() = mutex.withLock {
        unloadJob?.cancel()
        unloadJob = null
        backend?.close()
        backend = null
    }

    fun isWarm(): Boolean = backend != null && ParakeetNative.isLoaded()
}

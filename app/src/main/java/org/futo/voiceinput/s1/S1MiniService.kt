package org.futo.voiceinput.s1

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

class S1MiniService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val activeJob = AtomicReference<Job?>(null)
    private val unloadRunnable = Runnable {
        runCatching { S1MiniNative.unload() }
        stopSelf()
    }

    private val messenger = Messenger(Handler(Looper.getMainLooper()) { message ->
        when (message.what) {
            S1MiniProtocol.MSG_NORMALIZE -> handleNormalize(message)
            S1MiniProtocol.MSG_CANCEL -> {
                S1MiniNative.cancel()
                activeJob.getAndSet(null)?.cancel()
            }
            S1MiniProtocol.MSG_UNLOAD -> {
                S1MiniNative.cancel()
                activeJob.getAndSet(null)?.cancel()
                mainHandler.removeCallbacks(unloadRunnable)
                scope.launch { unloadRunnable.run() }
            }
            S1MiniProtocol.MSG_BACKENDS -> replyBackends(message)
        }
        true
    })

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    private fun handleNormalize(message: Message) {
        val replyTo = message.replyTo ?: return
        val data = message.data
        val requestId = data.getLong(S1MiniProtocol.KEY_REQUEST_ID)
        mainHandler.removeCallbacks(unloadRunnable)
        S1MiniNative.cancel()
        activeJob.getAndSet(null)?.cancel()

        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val result = S1MiniNative.normalize(
                    nativeLibraryDir = applicationInfo.nativeLibraryDir,
                    modelPath = requireNotNull(data.getString(S1MiniProtocol.KEY_MODEL_PATH)),
                    prompt = requireNotNull(data.getString(S1MiniProtocol.KEY_PROMPT)),
                    contextSize = data.getInt(S1MiniProtocol.KEY_CONTEXT_SIZE),
                    maxNewTokens = data.getInt(S1MiniProtocol.KEY_MAX_NEW_TOKENS),
                    threads = data.getInt(S1MiniProtocol.KEY_THREADS),
                    runtime = requireNotNull(data.getString(S1MiniProtocol.KEY_RUNTIME))
                )
                replyTo.send(Message.obtain(null, S1MiniProtocol.MSG_RESULT).apply {
                    this.data = Bundle().apply {
                        putLong(S1MiniProtocol.KEY_REQUEST_ID, requestId)
                        putString(S1MiniProtocol.KEY_TEXT, result.getOrElse(0) { "" })
                        putString(S1MiniProtocol.KEY_METRICS, result.getOrElse(1) { "{}" })
                    }
                })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Log.e("S1MiniService", "S1-mini cleanup failed (${error.javaClass.simpleName})")
                runCatching {
                    replyTo.send(Message.obtain(null, S1MiniProtocol.MSG_ERROR).apply {
                        this.data = Bundle().apply {
                            putLong(S1MiniProtocol.KEY_REQUEST_ID, requestId)
                            putString(
                                S1MiniProtocol.KEY_ERROR_CATEGORY,
                                when (error) {
                                    is OutOfMemoryError -> "out_of_memory"
                                    is IllegalArgumentException -> "invalid_request"
                                    is RuntimeException -> error.message
                                        ?.takeIf { it.matches(Regex("[a-z0-9_]+")) }
                                        ?: "native_failure"
                                    else -> "native_failure"
                                }
                            )
                        }
                    })
                }
            } finally {
                activeJob.compareAndSet(this.coroutineContext[Job], null)
                scheduleUnload(data.getLong(S1MiniProtocol.KEY_WARM_TIMEOUT_MS))
            }
        }
        activeJob.set(job)
        job.start()
    }

    private fun replyBackends(message: Message) {
        val replyTo = message.replyTo ?: return
        scope.launch {
            val backends = runCatching {
                S1MiniNative.availableBackends(applicationInfo.nativeLibraryDir)
            }.getOrDefault(emptyArray())
            replyTo.send(Message.obtain(null, S1MiniProtocol.MSG_BACKENDS).apply {
                data = Bundle().apply { putStringArray(S1MiniProtocol.KEY_BACKENDS, backends) }
            })
        }
    }

    private fun scheduleUnload(timeoutMs: Long) {
        mainHandler.removeCallbacks(unloadRunnable)
        when {
            timeoutMs < 0L -> Unit
            timeoutMs == 0L -> mainHandler.post(unloadRunnable)
            else -> mainHandler.postDelayed(unloadRunnable, timeoutMs)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            S1MiniNative.cancel()
            activeJob.getAndSet(null)?.cancel()
            mainHandler.removeCallbacks(unloadRunnable)
            scope.launch { unloadRunnable.run() }
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(unloadRunnable)
        S1MiniNative.cancel()
        activeJob.getAndSet(null)?.cancel()
        S1MiniNative.unload()
        scope.cancel()
        super.onDestroy()
    }
}

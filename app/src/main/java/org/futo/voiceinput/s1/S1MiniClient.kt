package org.futo.voiceinput.s1

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class S1MiniServiceResult(val text: String, val nativeMetricsJson: String)

class S1MiniServiceException(val category: String) : Exception("S1-mini service failed: $category")

object S1MiniClient {
    private val nextRequestId = AtomicLong(1L)

    suspend fun normalize(
        context: Context,
        modelPath: String,
        prompt: String,
        contextSize: Int,
        maxNewTokens: Int,
        threads: Int,
        runtime: String,
        warmTimeoutMs: Long
    ): S1MiniServiceResult = suspendCancellableCoroutine { continuation ->
        val appContext = context.applicationContext
        val requestId = nextRequestId.getAndIncrement()
        var remote: Messenger? = null

        lateinit var connection: ServiceConnection
        fun finish(block: () -> Unit) {
            runCatching { appContext.unbindService(connection) }
            if (continuation.isActive) block()
        }

        val replies = Messenger(Handler(Looper.getMainLooper()) { message ->
            if (message.data.getLong(S1MiniProtocol.KEY_REQUEST_ID) != requestId) return@Handler true
            when (message.what) {
                S1MiniProtocol.MSG_RESULT -> finish {
                    continuation.resume(
                        S1MiniServiceResult(
                            text = message.data.getString(S1MiniProtocol.KEY_TEXT).orEmpty(),
                            nativeMetricsJson = message.data.getString(S1MiniProtocol.KEY_METRICS) ?: "{}"
                        )
                    )
                }
                S1MiniProtocol.MSG_ERROR -> finish {
                    continuation.resumeWithException(
                        S1MiniServiceException(
                            message.data.getString(S1MiniProtocol.KEY_ERROR_CATEGORY) ?: "unknown"
                        )
                    )
                }
            }
            true
        })

        connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                remote = Messenger(binder)
                try {
                    remote?.send(Message.obtain(null, S1MiniProtocol.MSG_NORMALIZE).apply {
                        replyTo = replies
                        data = Bundle().apply {
                            putLong(S1MiniProtocol.KEY_REQUEST_ID, requestId)
                            putString(S1MiniProtocol.KEY_MODEL_PATH, modelPath)
                            putString(S1MiniProtocol.KEY_PROMPT, prompt)
                            putInt(S1MiniProtocol.KEY_CONTEXT_SIZE, contextSize)
                            putInt(S1MiniProtocol.KEY_MAX_NEW_TOKENS, maxNewTokens)
                            putInt(S1MiniProtocol.KEY_THREADS, threads)
                            putString(S1MiniProtocol.KEY_RUNTIME, runtime)
                            putLong(S1MiniProtocol.KEY_WARM_TIMEOUT_MS, warmTimeoutMs)
                        }
                    })
                } catch (error: RemoteException) {
                    finish { continuation.resumeWithException(error) }
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                finish { continuation.resumeWithException(S1MiniServiceException("process_died")) }
            }

            override fun onBindingDied(name: ComponentName?) = onServiceDisconnected(name)
            override fun onNullBinding(name: ComponentName?) = onServiceDisconnected(name)
        }

        continuation.invokeOnCancellation {
            runCatching { remote?.send(Message.obtain(null, S1MiniProtocol.MSG_CANCEL)) }
            runCatching { appContext.unbindService(connection) }
        }

        val bound = appContext.bindService(
            Intent(appContext, S1MiniService::class.java),
            connection,
            Context.BIND_AUTO_CREATE
        )
        if (!bound) finish { continuation.resumeWithException(S1MiniServiceException("bind_failed")) }
    }

    suspend fun unload(context: Context) = sendOneWay(context, S1MiniProtocol.MSG_UNLOAD)

    private suspend fun sendOneWay(context: Context, what: Int) =
        suspendCancellableCoroutine<Unit> { continuation ->
            val appContext = context.applicationContext
            lateinit var connection: ServiceConnection
            connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    runCatching { Messenger(service).send(Message.obtain(null, what)) }
                    runCatching { appContext.unbindService(this) }
                    if (continuation.isActive) continuation.resume(Unit)
                }
                override fun onServiceDisconnected(name: ComponentName?) = Unit
            }
            continuation.invokeOnCancellation { runCatching { appContext.unbindService(connection) } }
            if (!appContext.bindService(
                    Intent(appContext, S1MiniService::class.java),
                    connection,
                    Context.BIND_AUTO_CREATE
                )
            ) {
                continuation.resume(Unit)
            }
        }
}

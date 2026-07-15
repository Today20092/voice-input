package org.futo.voiceinput.parakeet

import android.content.Context
import android.os.SystemClock
import android.util.Log
import org.futo.voiceinput.backend.SpeechBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.futo.voiceinput.settings.PARAKEET_ENGINE_DIAGNOSTICS
import org.futo.voiceinput.settings.getSetting
import java.util.concurrent.atomic.AtomicLong

class ParakeetBackend : SpeechBackend {
    private companion object {
        const val DIAGNOSTICS_TAG = "ParakeetDiagnostics"
        val requestSequence = AtomicLong()
    }

    @Volatile
    private var diagnosticsEnabled = false

    internal suspend fun configureDiagnostics(context: Context) {
        diagnosticsEnabled = context.getSetting(PARAKEET_ENGINE_DIAGNOSTICS)
    }

    internal fun logEngineAcquired(warm: Boolean, elapsedMs: Long) {
        if (diagnosticsEnabled) {
            Log.i(DIAGNOSTICS_TAG, "engine_acquired warm=$warm elapsedMs=$elapsedMs")
        }
    }

    override suspend fun load(context: Context) = withContext(Dispatchers.IO) {
        configureDiagnostics(context)
        ParakeetNative.init(context.applicationContext)
    }

    override suspend fun transcribe(samples: FloatArray): String = withContext(Dispatchers.Default) {
        val requestId = requestSequence.incrementAndGet()
        val startedAt = SystemClock.elapsedRealtime()
        if (diagnosticsEnabled) {
            Log.i(DIAGNOSTICS_TAG, "request=$requestId decode_start samples=${samples.size}")
        }
        try {
            ParakeetNative.transcribe(samples).also { result ->
                if (diagnosticsEnabled) {
                    val outcome = if (result.isBlank()) "no_speech" else "text"
                    Log.i(
                        DIAGNOSTICS_TAG,
                        "request=$requestId decode_complete elapsedMs=${SystemClock.elapsedRealtime() - startedAt} outputChars=${result.length} outcome=$outcome"
                    )
                }
            }
        } catch (error: Exception) {
            if (diagnosticsEnabled) {
                Log.e(DIAGNOSTICS_TAG, "request=$requestId decode_failed", error)
            }
            throw error
        }
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        ParakeetNative.close()
    }
}

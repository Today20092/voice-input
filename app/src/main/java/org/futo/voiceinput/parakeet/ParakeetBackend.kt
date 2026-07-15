package org.futo.voiceinput.parakeet

import android.content.Context
import org.futo.voiceinput.backend.SpeechBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ParakeetBackend : SpeechBackend {
    override suspend fun load(context: Context) = withContext(Dispatchers.IO) {
        ParakeetNative.init(context.applicationContext)
    }

    override suspend fun transcribe(samples: FloatArray): String = withContext(Dispatchers.Default) {
        ParakeetNative.transcribe(samples)
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        ParakeetNative.close()
    }
}

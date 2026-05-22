package org.futo.voiceinput.parakeet

import android.content.Context

object ParakeetNative {
    init {
        System.loadLibrary("c++_shared")
        System.loadLibrary("onnxruntime")
        System.loadLibrary("parakeet_voiceinput")
    }

    @JvmStatic external fun init(context: Context)
    @JvmStatic external fun isLoaded(): Boolean
    @JvmStatic external fun transcribe(samples: FloatArray): String
    @JvmStatic external fun markIdle()
    @JvmStatic external fun unloadIfIdle(timeoutMs: Long): Boolean
    @JvmStatic external fun close()
}

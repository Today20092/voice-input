package org.futo.voiceinput.s1

internal object S1MiniNative {
    init {
        System.loadLibrary("s1mini")
    }

    /** Returns [cleanedText, nativeMetricsJson]. */
    external fun normalize(
        modelPath: String,
        prompt: String,
        contextSize: Int,
        maxNewTokens: Int,
        threads: Int,
        runtime: String
    ): Array<String>

    external fun cancel()
    external fun unload()
    external fun availableBackends(): Array<String>
}

package org.futo.voiceinput.parakeet

data class ParakeetStreamingPreset(
    val leftContextSec: Float,
    val chunkSec: Float,
    val rightContextSec: Float
)

val PARAKEET_STREAMING_PRESETS = listOf(
    ParakeetStreamingPreset(5.6f, 1.04f, 1.04f),
    ParakeetStreamingPreset(5.6f, 0.56f, 0.56f),
    ParakeetStreamingPreset(5.6f, 0.16f, 0.40f),
    ParakeetStreamingPreset(5.6f, 0.08f, 0.24f),
    ParakeetStreamingPreset(5.6f, 0.08f, 0.16f),
    ParakeetStreamingPreset(5.6f, 0.08f, 0.08f),
)

val DEFAULT_PARAKEET_STREAMING_PRESET = ParakeetStreamingPreset(5.6f, 0.56f, 0.56f)

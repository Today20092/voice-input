package org.futo.voiceinput

/** Four seconds of 20 ms min/max envelopes from 16 kHz PCM. */
internal class RecordingWaveform {
    private val bars = Array(200) { 0f to 0f }
    private var next = 0
    private var count = 0
    private var samplesInBar = 0
    private var low = 0f
    private var high = 0f

    fun clear() {
        next = 0
        count = 0
        samplesInBar = 0
        low = 0f
        high = 0f
    }

    fun append(samples: ShortArray, length: Int) {
        for (i in 0 until length) {
            val sample = samples[i] / 32768f
            low = minOf(low, sample)
            high = maxOf(high, sample)
            if (++samplesInBar == 320) {
                bars[next] = low to high
                next = (next + 1) % bars.size
                count = minOf(count + 1, bars.size)
                samplesInBar = 0
                low = 0f
                high = 0f
            }
        }
    }

    fun snapshot(): List<Pair<Float, Float>> = List(count) {
        bars[(next - count + it + bars.size) % bars.size]
    }
}

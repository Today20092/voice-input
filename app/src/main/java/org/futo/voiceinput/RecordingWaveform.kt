package org.futo.voiceinput

/** Four seconds of 20 ms min/max envelopes from 16 kHz PCM. */
internal class RecordingWaveform {
    private val bars = Array(200) { 0f to 0f }
    private var next = 0
    private var count = 0
    private var samplesInBar = 0
    private var low = 0f
    private var high = 0f
    private var displayGain = 40f

    fun clear() {
        next = 0
        count = 0
        samplesInBar = 0
        low = 0f
        high = 0f
        displayGain = 40f
    }

    fun append(samples: ShortArray, length: Int) {
        for (i in 0 until length) {
            val sample = samples[i] / 32768f
            low = minOf(low, sample)
            high = maxOf(high, sample)
            if (++samplesInBar == 320) {
                // Display-only gain: react quickly to loud speech and recover gently.
                // A floor and bounded gain keep microphone noise close to the baseline.
                val peak = maxOf(-low, high)
                if (peak >= 0.002f) {
                    val target = (0.8f / peak).coerceIn(1f, 40f)
                    displayGain += (target - displayGain) * if (target < displayGain) 0.65f else 0.03f
                }
                bars[next] = low to high
                next = (next + 1) % bars.size
                count = minOf(count + 1, bars.size)
                samplesInBar = 0
                low = 0f
                high = 0f
            }
        }
    }

    fun snapshot(): List<Pair<Float, Float>> {
        val complete = List(count) { bars[(next - count + it + bars.size) % bars.size] }
        return if (samplesInBar == 0) complete else (complete + (low to high)).takeLast(bars.size)
    }

    fun displaySnapshot(): List<Pair<Float, Float>> = snapshot().map { (low, high) ->
        if (maxOf(-low, high) < 0.002f) 0f to 0f
        else (low * displayGain).coerceIn(-1f, 0f) to (high * displayGain).coerceIn(0f, 1f)
    }
}

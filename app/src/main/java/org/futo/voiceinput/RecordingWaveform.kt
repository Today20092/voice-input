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

    fun snapshot(): List<Pair<Float, Float>> {
        val complete = List(count) { bars[(next - count + it + bars.size) % bars.size] }
        return if (samplesInBar == 0) complete else (complete + (low to high)).takeLast(bars.size)
    }

}

/** Reduce the four-second envelope to spaced bars without normalizing away silence. */
internal fun recordingBarAmplitudes(bars: List<Pair<Float, Float>>, count: Int): List<Float> {
    require(count > 0)
    val amplitudes = MutableList(count) { 0f }
    val history = bars.takeLast(200)
    history.forEachIndexed { index, (low, high) ->
        val column = (200 - history.size + index) * count / 200
        amplitudes[column] = maxOf(amplitudes[column], -low, high).coerceIn(0f, 1f)
    }
    return amplitudes
}

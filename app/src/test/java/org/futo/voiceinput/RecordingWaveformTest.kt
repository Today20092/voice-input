package org.futo.voiceinput

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingWaveformTest {
    @Test
    fun preservesPeaksAcrossReadsScrollsAndResets() {
        val waveform = RecordingWaveform()
        val first = ShortArray(320)
        first[0] = Short.MIN_VALUE
        first[159] = Short.MAX_VALUE
        waveform.append(first, 160)
        assertTrue(waveform.snapshot().isEmpty())
        // Ignore the unused portion of a partial AudioRecord read.
        waveform.append(ShortArray(320) { if (it < 160) 0 else Short.MAX_VALUE }, 160)
        assertEquals(listOf(-1f to (32767f / 32768f)), waveform.snapshot())
        val frozen = waveform.snapshot()
        repeat(200) { waveform.append(ShortArray(320), 320) }
        assertEquals(200, waveform.snapshot().size)
        assertTrue(waveform.snapshot().all { it == (0f to 0f) })
        assertEquals(-1f, frozen.single().first)
        waveform.append(first, 160)
        waveform.clear()
        assertTrue(waveform.snapshot().isEmpty())
        waveform.append(ShortArray(320), 320)
        assertEquals(listOf(0f to 0f), waveform.snapshot())
    }
}

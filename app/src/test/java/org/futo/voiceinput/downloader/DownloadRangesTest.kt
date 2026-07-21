package org.futo.voiceinput.downloader

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadRangesTest {
    @Test
    fun splitsFileIntoFourContiguousRangesAndResumesWithinEachRange() {
        val ranges = downloadRanges(10)

        assertEquals(
            listOf(
                DownloadRange(0, 1),
                DownloadRange(2, 4),
                DownloadRange(5, 6),
                DownloadRange(7, 9)
            ),
            ranges
        )
        assertEquals(4, ranges[1].resumeAt(2))
        assertEquals(5, ranges[1].resumeAt(Long.MAX_VALUE))
    }
}

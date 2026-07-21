package org.futo.voiceinput.downloader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadConfirmationTest {
    @Test
    fun insufficientSpaceBlocksDownload() {
        assertFalse(
            DownloadConfirmation(
                source = "source",
                transferBytes = 10,
                requiredFreeSpaceBytes = 10,
                availableBytes = 9,
                cellular = true
            ).hasEnoughSpace
        )
    }

    @Test
    fun exactRequiredSpaceAllowsDownload() {
        assertTrue(
            DownloadConfirmation(
                source = "source",
                transferBytes = 10,
                requiredFreeSpaceBytes = 10,
                availableBytes = 10,
                cellular = false
            ).hasEnoughSpace
        )
    }
}

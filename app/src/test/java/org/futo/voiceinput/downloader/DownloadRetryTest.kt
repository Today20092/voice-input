package org.futo.voiceinput.downloader

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadRetryTest {
    @Test
    fun retrySkipsFilesThatAlreadyValidated() {
        val complete = ModelInfo("complete", "url", size = 10)
        val partial = ModelInfo("partial", "url", size = 20)

        assertEquals(
            listOf(partial),
            incompleteDownloads(listOf(complete, partial)) { it === complete }
        )
    }
}

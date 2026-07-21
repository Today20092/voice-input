package org.futo.voiceinput

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class RecognitionActivityResultTest {
    @Test
    fun successfulAndCanceledResultsMatchTheActivityContract() {
        val success = successfulRecognitionActivityResult("hello world", "en")

        assertEquals(RESULT_OK, success.resultCode)
        assertEquals(listOf("hello world"), success.transcripts)
        assertEquals("en", success.detectedLanguage)
        assertThrows(IllegalArgumentException::class.java) {
            successfulRecognitionActivityResult("", null)
        }

        val canceled = canceledRecognitionActivityResult()

        assertEquals(RESULT_CANCELED, canceled.resultCode)
        assertEquals(emptyList<String>(), canceled.transcripts)
        assertNull(canceled.detectedLanguage)
    }
}

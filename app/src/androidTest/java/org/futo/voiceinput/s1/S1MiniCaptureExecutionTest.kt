package org.futo.voiceinput.s1

import android.content.ContextWrapper
import android.content.Intent
import android.os.Looper
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.File
import java.io.IOException

class S1MiniCaptureExecutionTest {
    @Test
    fun failedShareRemovesThePreparedTranscriptArchiveAndKeepsTheOriginalFailure() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val temporary = File(base.cacheDir, "capture-share-${System.nanoTime()}").apply { mkdirs() }
        val failure = IllegalStateException("No share target")
        val context = object : ContextWrapper(base) {
            override fun getCacheDir(): File = File(temporary, "cache")
            override fun getFilesDir(): File = temporary
            override fun startActivity(intent: Intent) { throw failure }
        }
        try {
            withContext(Dispatchers.IO) {
                S1MiniDiagnostics.recordTranscript(context, "share", "raw", "cleaned", "final", null)
            }
            val caught = runCatching { S1MiniDiagnostics.shareZip(context, includeTranscripts = true) }.exceptionOrNull()
            assertSame(failure, caught)
            assertTrue(File(context.cacheDir, "s1-diagnostics-export").listFiles().orEmpty().isEmpty())
        } finally {
            temporary.deleteRecursively()
        }
    }

    @Test
    fun backgroundStorageKeepsTheOriginalFailureForItsCaller() = runBlocking {
        val failure = IOException("storage unavailable")
        val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun getFilesDir(): File {
                check(Looper.myLooper() != Looper.getMainLooper())
                throw failure
            }
        }
        withContext(Dispatchers.Main) {
            val caught = runCatching { S1MiniDiagnostics.transcriptCaptures(context) }.exceptionOrNull()
            assertSame(failure, caught)
        }
    }

    @Test
    fun captureAccessFromMainUsesBackgroundStorageForStartupLoadDeleteAndClear() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val temporary = File(base.cacheDir, "capture-execution-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getCacheDir(): File = File(temporary, "cache")
            override fun getFilesDir(): File {
                check(Looper.myLooper() != Looper.getMainLooper()) { "Capture disk access on main thread" }
                return temporary
            }
        }
        val now = System.currentTimeMillis()
        try {
            S1MiniDiagnostics.recordTranscript(context, "execution", "raw", "cleaned", "final", null, now)
            withContext(Dispatchers.Main) {
                S1MiniDiagnostics.purgeTranscriptCaptures(context)
                assertEquals(1, S1MiniDiagnostics.transcriptCaptures(context).captures.size)
                assertTrue(S1MiniDiagnostics.deleteTranscriptCapture(context, "execution"))
                assertFalse(S1MiniDiagnostics.transcriptCaptures(context).hasExportableEvidence)
                S1MiniDiagnostics.clearTranscriptCaptures(context)
                S1MiniDiagnostics.clear(context)
            }
        } finally {
            temporary.deleteRecursively()
        }
    }
}

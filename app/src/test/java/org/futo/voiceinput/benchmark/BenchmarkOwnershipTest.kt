package org.futo.voiceinput.benchmark

import kotlinx.coroutines.runBlocking
import org.futo.voiceinput.recognition.*
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BenchmarkOwnershipTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun benchmarkAndDictationOwnershipAreMutuallyExclusiveIncludingLegacy() = runBlocking {
        val lifecycle = RecognitionModelLifecycle.create(temporary.root, false)
        val dictation = lifecycle.acquireSession(null)
        try {
            try { lifecycle.acquireBenchmark(); fail("Benchmark overlapped legacy dictation") } catch (_: IllegalStateException) { }
        } finally { dictation.close() }
        val benchmark = lifecycle.acquireBenchmark()
        try {
            try { lifecycle.acquireSession(null); fail("Legacy dictation overlapped benchmark") } catch (_: IllegalStateException) { }
            try { lifecycle.acquireBenchmark(); fail("Duplicate benchmark accepted") } catch (_: IllegalStateException) { }
            try { lifecycle.delete(RecognitionModelCatalog.defaultModel, null); fail("Deletion overlapped benchmark") } catch (_: IllegalStateException) { }
        } finally { benchmark.close() }
        lifecycle.acquireSession(null).close()
    }
}

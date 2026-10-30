package org.futo.voiceinput.benchmark

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LiveBenchmarkTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun deliveryVerificationDetectsLossAndDuplicationAndAllowsInsertionSpacing() {
        val report = BenchmarkReport(clips = emptyList(), selectedModelIds = emptyList(), installedBefore = emptyList())
        val reading = LiveBenchmarkReading(modelId = "test", entryPoint = "ime", reference = "hello world")
        report.liveReadings.add(reading)
        val model = BenchmarkModel.all().first()
        val session = LiveBenchmarkSession(model, reading, report, BenchmarkStore(temporary.root))
        session.milestone("recordingStarted")
        session.raw("hello world")
        session.delivered("hello world", true)
        session.observed(" hello world ")
        assertEquals(true, reading.deliveryVerified)
        assertEquals("completed", reading.status)
        session.observed("hello world hello world")
        assertEquals(false, reading.deliveryVerified)
        assertEquals("failed", reading.status)
        session.observed("hello")
        assertEquals(false, reading.deliveryVerified)
        session.observed("hello world")
        assertEquals(true, reading.deliveryVerified)
        assertNull(reading.reason)
        session.reference("hello world")
        assertEquals(0.0, reading.accuracy!!.words!!.rate, 0.0)
        assertTrue(reading.milestones["finalReady"]!! >= reading.milestones["recordingStarted"]!!)
        assertTrue(BenchmarkStore(temporary.root).markdown(report).contains("Live ime"))
    }

    @Test fun matchingReceiverCannotVerifyRejectedDelivery() {
        val report = BenchmarkReport(clips = emptyList(), selectedModelIds = emptyList(), installedBefore = emptyList())
        val reading = LiveBenchmarkReading(modelId = "test", entryPoint = "ime", reference = "hello")
        report.liveReadings.add(reading)
        val session = LiveBenchmarkSession(BenchmarkModel.all().first(), reading, report, BenchmarkStore(temporary.root))
        session.delivered("hello", false)
        session.observed("hello")
        assertEquals(false, reading.deliveryVerified)
        assertEquals("failed", reading.status)
        assertEquals("Final delivery was rejected", reading.reason)
    }
}

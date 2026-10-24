package org.futo.voiceinput.s1

import org.junit.Assert.*
import org.junit.Test

class S1MiniTranscriptDeliveryTest {
    @Test fun preparedAndRejectedTextIsNeverLabeledDelivered() {
        val prepared = capture(S1MiniTranscriptDelivery.Prepared)
        assertEquals("Final text", prepared.prepared.text)
        assertEquals(S1MiniTranscriptStageStatus.NotProduced, prepared.finalDelivered.status)
        val rejected = prepared.withDelivery(S1MiniTranscriptDelivery.Rejected)
        assertNull(rejected.finalDelivered.text)
        assertEquals(S1MiniTranscriptDelivery.Rejected, rejected.delivery)
        assertEquals("Raw text", rejected.raw.text)
    }

    @Test fun acceptedEmptyCleanupIsProducedRatherThanMissing() {
        val capture = S1MiniTranscriptCapture.create("empty", 1L, "um", "", "", null,
            S1MiniTranscriptDelivery.Prepared).withDelivery(S1MiniTranscriptDelivery.Accepted)
        assertEquals(S1MiniTranscriptStageStatus.Produced, capture.finalDelivered.status)
        assertEquals("", capture.finalDelivered.text)
        assertEquals(S1MiniTranscriptDelivery.Accepted, capture.delivery)
    }

    @Test fun canceledDeliveryRetainsCleanupFailureWithoutClaimingAcceptance() {
        val capture = capture(S1MiniTranscriptDelivery.Prepared)
            .withDelivery(S1MiniTranscriptDelivery.Canceled)
        assertNull(capture.cleaned.text)
        assertNull(capture.finalDelivered.text)
        assertEquals("service_failure", capture.failureOrBypassReason)
    }

    private fun capture(delivery: S1MiniTranscriptDelivery) = S1MiniTranscriptCapture.create(
        "report", 1L, "Raw text", null, "Final text", "service_failure", delivery)
}

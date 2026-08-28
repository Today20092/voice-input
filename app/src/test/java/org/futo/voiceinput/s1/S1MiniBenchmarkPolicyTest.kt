package org.futo.voiceinput.s1

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class S1MiniBenchmarkPolicyTest {
    @Test
    fun acceptsStableMeaningPreservingRewriteWithoutExactWording() {
        val rewrite = "The report needs to be sent by Thursday."

        val validation = S1MiniBenchmarkPolicy.validate(listOf(rewrite, rewrite, rewrite))

        assertTrue(validation.accepted)
        assertTrue(validation.stable)
        assertTrue(validation.preservesMeaning)
        assertEquals(3, validation.attempts)
        assertEquals(1, validation.outputHashes.size)
    }

    @Test
    fun rejectsUnstableOrMeaningChangingOutput() {
        val unstable = S1MiniBenchmarkPolicy.validate(
            listOf(
                "Send the report by Thursday.",
                "The report needs to be sent by Thursday.",
                "Send the report by Thursday."
            )
        )
        val wrongMeaning = S1MiniBenchmarkPolicy.validate(
            List(3) { "Send the report by Friday." }
        )
        val reversedIntent = S1MiniBenchmarkPolicy.validate(
            List(3) { "Do not send the report by Thursday." }
        )

        assertFalse(unstable.accepted)
        assertFalse(unstable.stable)
        assertFalse(wrongMeaning.accepted)
        assertFalse(wrongMeaning.preservesMeaning)
        assertFalse(reversedIntent.accepted)
        assertFalse(reversedIntent.preservesMeaning)
    }

    @Test
    fun acceptsStableSynonymsAndDescribesPartialValidation() {
        val synonym = "The write-up needs to go out by Thursday."

        val accepted = S1MiniBenchmarkPolicy.validate(List(3) { synonym })
        val partial = S1MiniBenchmarkPolicy.validate(listOf(synonym))

        assertTrue(accepted.accepted)
        assertTrue(accepted.preservesMeaning)
        assertFalse(partial.accepted)
        assertEquals("incomplete_validation", partial.failureReason)
        assertEquals(1, partial.attempts)
        assertEquals(1, partial.outputHashes.size)
    }

    @Test
    fun cpuOnlyDeviceDoesNotScheduleOpenCl() {
        val candidates = S1MiniBenchmarkPolicy.candidates(
            availableProcessors = 8,
            discoveredDevices = listOf("cpu:CPU")
        )

        assertEquals(listOf("cpu/2", "cpu/4", "cpu/6"), candidates.map { it.key })
    }

    @Test
    fun discoveredGpuSchedulesOpenClAfterCpuCandidates() {
        val candidates = S1MiniBenchmarkPolicy.candidates(
            availableProcessors = 8,
            discoveredDevices = listOf("cpu:CPU", "gpu:Adreno")
        )

        assertEquals(listOf("cpu/2", "cpu/4", "cpu/6", "opencl/4"), candidates.map { it.key })
    }
}

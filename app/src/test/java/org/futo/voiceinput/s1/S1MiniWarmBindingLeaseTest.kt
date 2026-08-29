package org.futo.voiceinput.s1

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class S1MiniWarmBindingLeaseTest {
    @Test
    fun immediateDurationDoesNotKeepABinding() {
        var bindCount = 0
        val lease = S1MiniWarmBindingLease()

        lease.retain(
            timeoutMs = 0L,
            bind = {
                bindCount += 1
                { }
            },
            scheduleRelease = { _, _ -> error("Immediate duration must not schedule a release") }
        )

        assertEquals(0, bindCount)
    }

    @Test
    fun processLifetimeKeepsBindingWithoutSchedulingRelease() {
        var bindCount = 0
        var unbindCount = 0
        val lease = S1MiniWarmBindingLease()

        lease.retain(
            timeoutMs = -1L,
            bind = {
                bindCount += 1
                { unbindCount += 1 }
            },
            scheduleRelease = { _, _ -> error("Process lifetime must not schedule a release") }
        )
        lease.retain(
            timeoutMs = -1L,
            bind = {
                bindCount += 1
                { unbindCount += 1 }
            },
            scheduleRelease = { _, _ -> error("Process lifetime must not schedule a release") }
        )

        assertEquals(1, bindCount)
        assertEquals(0, unbindCount)

        lease.release()

        assertEquals(1, unbindCount)
    }

    @Test
    fun finiteDurationKeepsOneBindingAndRenewsItsReleaseTimer() {
        var bindCount = 0
        var unbindCount = 0
        val scheduled = mutableListOf<ScheduledRelease>()
        val lease = S1MiniWarmBindingLease()

        fun bind(): (() -> Unit) {
            bindCount += 1
            return { unbindCount += 1 }
        }

        fun schedule(delayMs: Long, release: () -> Unit): () -> Unit {
            val scheduledRelease = ScheduledRelease(delayMs, release)
            scheduled += scheduledRelease
            return { scheduledRelease.cancelled = true }
        }

        lease.retain(120_000L, ::bind, ::schedule)
        lease.retain(300_000L, ::bind, ::schedule)

        assertEquals(1, bindCount)
        assertEquals(0, unbindCount)
        assertEquals(2, scheduled.size)
        assertEquals(120_000L, scheduled[0].delayMs)
        assertTrue(scheduled[0].cancelled)
        assertEquals(300_000L, scheduled[1].delayMs)
        assertFalse(scheduled[1].cancelled)

        scheduled[1].release()

        assertEquals(1, unbindCount)
        assertTrue(scheduled[1].cancelled)
    }

    @Test
    fun expiredLeaseAllowsTheNextCleanupToCreateAFreshBinding() {
        var bindCount = 0
        var unbindCount = 0
        val scheduled = mutableListOf<ScheduledRelease>()
        val lease = S1MiniWarmBindingLease()
        val bind = {
            bindCount += 1
            { unbindCount += 1 }
        }
        val schedule = { delayMs: Long, release: () -> Unit ->
            val scheduledRelease = ScheduledRelease(delayMs, release)
            scheduled += scheduledRelease
            { scheduledRelease.cancelled = true }
        }

        lease.retain(120_000L, bind, schedule)
        scheduled.single().release()
        lease.retain(120_000L, bind, schedule)

        assertEquals(2, bindCount)
        assertEquals(1, unbindCount)
        assertEquals(2, scheduled.size)
        assertFalse(scheduled.last().cancelled)
    }

    private data class ScheduledRelease(
        val delayMs: Long,
        val release: () -> Unit,
        var cancelled: Boolean = false
    )
}

package org.futo.voiceinput

import org.junit.Assert.*
import org.junit.Test

class MicrophoneRoutingTest {
    private val phone = MicrophoneDevice(1, MicrophoneKind.Phone)
    private val headset = MicrophoneDevice(2, MicrophoneKind.Bluetooth)

    private inner class Platform : MicrophonePlatform {
        var devices = listOf(phone, headset)
        var active: MicrophoneDevice? = phone
        var requested: MicrophoneDevice? = null
        var listener: () -> Unit = {}
        var timeout: () -> Unit = {}
        var accepted = true
        override fun devices() = devices
        override fun activeDevice() = active
        override fun select(device: MicrophoneDevice): Boolean {
            requested = device
            return accepted
        }
        override fun clear() { requested = null }
        override fun watch(onChanged: () -> Unit) { listener = onChanged }
        override fun afterTimeout(action: () -> Unit): () -> Unit {
            timeout = action
            return { timeout = {} }
        }
        override fun close() { clear(); listener = {} }
    }

    @Test fun selectionIsPendingUntilRecorderConfirmsIt() {
        val platform = Platform()
        val routing = MicrophoneRouting(platform) {}
        routing.start()
        assertNull(platform.requested)
        routing.select(headset)
        assertEquals(phone, routing.state.active)
        assertEquals(headset, routing.state.pending)
        platform.active = headset
        platform.listener()
        assertEquals(headset, routing.state.active)
        assertNull(routing.state.pending)
        routing.close()
        assertNull(platform.requested)
    }

    @Test fun previousRequestTimeoutCannotClearANewerSelection() {
        val platform = Platform()
        val routing = MicrophoneRouting(platform) {}
        routing.start()
        routing.select(headset)
        val staleTimeout = platform.timeout
        routing.select(phone)
        platform.active = null
        routing.select(headset)
        staleTimeout()
        assertEquals(headset, routing.state.pending)
        assertNull(routing.state.failure)
    }

    @Test fun rejectedAndUnavailableRoutesKeepActualMicrophoneVisible() {
        val platform = Platform()
        val routing = MicrophoneRouting(platform) {}
        routing.start()
        platform.accepted = false
        routing.select(headset)
        assertEquals(MicrophoneRouteFailure.Rejected, routing.state.failure)
        assertEquals(phone, routing.state.active)
        assertNull(platform.requested)
        platform.devices = listOf(phone)
        routing.select(headset)
        assertEquals(MicrophoneRouteFailure.Unavailable, routing.state.failure)
        assertTrue(routing.state.visible)
    }

    @Test fun timeoutAndDisconnectReleaseRequestWithoutClaimingPhoneIsActive() {
        val platform = Platform()
        val routing = MicrophoneRouting(platform) {}
        routing.start()
        routing.select(headset)
        platform.active = null
        platform.timeout()
        assertEquals(MicrophoneRouteFailure.TimedOut, routing.state.failure)
        assertNull(routing.state.active)
        assertNull(platform.requested)
        routing.select(headset)
        platform.active = headset
        platform.listener()
        platform.devices = listOf(phone)
        platform.active = null
        platform.listener()
        assertEquals(MicrophoneRouteFailure.Disconnected, routing.state.failure)
        assertNull(routing.state.active)
        assertNull(platform.requested)
    }

    @Test fun closedSessionIgnoresCallbacksAndDoesNotChangeAnotherSession() {
        val platform = Platform()
        val routing = MicrophoneRouting(platform) {}
        routing.start()
        routing.select(headset)
        val lateCallback = platform.listener
        val lateTimeout = platform.timeout
        routing.close()
        val next = MicrophoneRouting(platform) {}
        next.start()
        next.select(headset)
        lateCallback()
        lateTimeout()
        routing.close()
        routing.select(phone)
        assertEquals(headset, platform.requested)
        assertEquals(MicrophoneRouteState(), routing.state)
        assertFalse(routing.state.visible)
    }

    @Test fun phoneOnlyDefaultHasNoControlAndNoRouteRequest() {
        val platform = Platform()
        platform.devices = listOf(phone)
        val routing = MicrophoneRouting(platform) {}
        routing.start()
        assertFalse(routing.state.visible)
        assertNull(platform.requested)
        routing.close()
    }
}

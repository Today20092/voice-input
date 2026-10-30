package org.futo.voiceinput.settings

import org.junit.Assert.*
import org.junit.Test
import java.io.*

class KeyboardProviderSessionTest {
    private val keyboard = "org.futo.inputmethod.latin.unstable"

    @Test fun checkIsReadOnlyAndDoesNotClaimExternalInputIsEnabled() {
        val checking = KeyboardProviderSession(keyboard).begin(ProviderMode.Check)
        assertEquals("check", checking.pending!!.mode.argument)
        val result = checking.complete(checking.pending, 67)
        assertEquals(ProviderMemory.ThisApp, result.remembered)
        assertEquals(ProviderOutcome.None, result.outcome)
        assertFalse(result.needsCheck)
        assertNull(result.pending)
    }

    @Test fun explicitSetupSuccessRechecksOnceWithoutRepeatingSetup() {
        val switching = KeyboardProviderSession(keyboard).begin(ProviderMode.Switch)
        assertEquals("switch", switching.pending!!.mode.argument)
        val switched = switching.complete(switching.pending, 66)
        assertEquals(ProviderOutcome.Switched, switched.outcome)
        assertTrue(switched.needsCheck)
        val checking = switched.begin(ProviderMode.Check)
        val checked = checking.complete(checking.pending!!, 67)
        assertFalse(checked.needsCheck)
        assertEquals(ProviderOutcome.Switched, checked.outcome)
    }

    @Test fun cancellationUnknownAndUnavailableHaveSeparateOutcomes() {
        val switching = KeyboardProviderSession(keyboard).begin(ProviderMode.Switch)
        assertEquals(ProviderOutcome.Cancelled, switching.complete(switching.pending!!, 0).outcome)
        assertEquals(ProviderOutcome.Unknown, switching.complete(switching.pending, -1).outcome)
        val missing = switching.unavailable(switching.pending)
        assertEquals(ProviderOutcome.Unavailable, missing.outcome)
        assertFalse(missing.needsCheck)
    }

    @Test fun overlappingRequestsAndStaleResultsAreIgnored() {
        val first = KeyboardProviderSession(keyboard).begin(ProviderMode.Check)
        assertEquals(first, first.begin(ProviderMode.Switch))
        val second = first.complete(first.pending!!, 68).begin(ProviderMode.Switch)
        assertEquals(second, second.complete(first.pending, 66))
        assertEquals(second, second.unavailable(first.pending))
    }

    @Test fun returningFromManualSettingsRechecksWithoutClaimingSuccess() {
        val settings = KeyboardProviderSession(keyboard).begin(ProviderMode.Settings)
        val returned = settings.complete(settings.pending!!, -1)
        assertTrue(returned.needsCheck)
        assertEquals(ProviderOutcome.None, returned.outcome)
        val check = returned.begin(ProviderMode.Check)
        val result = check.complete(check.pending!!, 68)
        assertEquals(ProviderMemory.Other, result.remembered)
        assertFalse(result.needsCheck)
    }

    @Test fun manualSettingsChecksAfterActuallyReturningAndNotOnInitialResume() {
        val settings = KeyboardProviderSession(keyboard).begin(ProviderMode.Settings)
        assertEquals(settings, settings.onResume())
        val away = settings.onPause()
        val returned = restored(away).onResume()
        assertNull(returned.pending)
        assertTrue(returned.needsCheck)
        val checking = returned.begin(ProviderMode.Check)
        assertEquals(checking, checking.onPause().onResume())
    }

    @Test fun recreationPreservesPendingRequestAndRejectsDuplicateResult() {
        val switching = KeyboardProviderSession(keyboard).begin(ProviderMode.Switch)
        val restored = restored(switching)
        val result = restored.complete(restored.pending!!, 66)
        assertEquals(ProviderOutcome.Switched, result.outcome)
        assertEquals(result, result.complete(restored.pending, 0))
    }

    private fun restored(session: KeyboardProviderSession): KeyboardProviderSession {
        val bytes = ByteArrayOutputStream().also { buffer ->
            ObjectOutputStream(buffer).use { it.writeObject(session) }
        }.toByteArray()
        return ObjectInputStream(ByteArrayInputStream(bytes)).use {
            it.readObject() as KeyboardProviderSession
        }
    }
}

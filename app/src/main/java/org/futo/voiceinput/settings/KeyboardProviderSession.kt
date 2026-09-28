package org.futo.voiceinput.settings

import java.io.Serializable

enum class ProviderMode(val argument: String) { Check("check"), Switch("switch"), Settings("") }
enum class ProviderMemory { Unknown, ThisApp, Other }
enum class ProviderOutcome { None, Switched, Cancelled, Unknown, Unavailable }

data class ProviderRequest(val id: Long, val keyboard: String, val mode: ProviderMode) : Serializable

/** Saved with the screen so rotation/process recreation cannot turn a check into a switch. */
data class KeyboardProviderSession(
    val keyboard: String,
    val sequence: Long = 0,
    val pending: ProviderRequest? = null,
    val needsCheck: Boolean = true,
    val remembered: ProviderMemory = ProviderMemory.Unknown,
    val outcome: ProviderOutcome = ProviderOutcome.None,
    val settingsLeft: Boolean = false
) : Serializable {
    fun begin(mode: ProviderMode): KeyboardProviderSession {
        if (pending != null) return this
        return copy(
            sequence = sequence + 1,
            pending = ProviderRequest(sequence + 1, keyboard, mode),
            needsCheck = false,
            remembered = ProviderMemory.Unknown,
            outcome = if (mode == ProviderMode.Check) outcome else ProviderOutcome.None,
            settingsLeft = false
        )
    }

    fun onPause(): KeyboardProviderSession =
        if (pending?.mode == ProviderMode.Settings) copy(settingsLeft = true) else this

    fun onResume(): KeyboardProviderSession =
        if (pending?.mode == ProviderMode.Settings && settingsLeft) complete(pending, 0) else this

    fun complete(request: ProviderRequest, resultCode: Int): KeyboardProviderSession {
        if (pending != request) return this
        return when (request.mode) {
            ProviderMode.Check -> copy(
                pending = null,
                remembered = when (resultCode) {
                    67 -> ProviderMemory.ThisApp
                    68 -> ProviderMemory.Other
                    else -> ProviderMemory.Unknown
                }
            )
            ProviderMode.Switch -> copy(
                pending = null,
                needsCheck = true,
                outcome = when (resultCode) {
                    66 -> ProviderOutcome.Switched
                    0 -> ProviderOutcome.Cancelled
                    else -> ProviderOutcome.Unknown
                }
            )
            ProviderMode.Settings -> copy(pending = null, needsCheck = true)
        }
    }

    fun unavailable(request: ProviderRequest): KeyboardProviderSession =
        if (pending != request) this else copy(
            pending = null, needsCheck = false, outcome = ProviderOutcome.Unavailable,
            remembered = ProviderMemory.Unknown
        )
}

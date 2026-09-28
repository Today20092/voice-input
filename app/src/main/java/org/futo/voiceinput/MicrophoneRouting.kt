package org.futo.voiceinput

enum class MicrophoneKind { Phone, Bluetooth, Other }
data class MicrophoneDevice(val id: Int, val kind: MicrophoneKind)
enum class MicrophoneRouteFailure { Unavailable, Rejected, TimedOut, Disconnected }
data class MicrophoneRouteState(
    val devices: List<MicrophoneDevice> = emptyList(),
    val active: MicrophoneDevice? = null,
    val pending: MicrophoneDevice? = null,
    val failure: MicrophoneRouteFailure? = null,
    val recording: Boolean = false
) {
    val visible: Boolean get() = recording &&
        (devices.any { it.kind == MicrophoneKind.Bluetooth } || failure != null)
}

/** Android routing boundary. All operations and callbacks run on the main thread. */
interface MicrophonePlatform {
    fun devices(): List<MicrophoneDevice>
    fun activeDevice(): MicrophoneDevice?
    fun select(device: MicrophoneDevice): Boolean
    fun clear()
    fun watch(onChanged: () -> Unit)
    fun afterTimeout(action: () -> Unit): () -> Unit
    fun close()
}

class MicrophoneRouting(
    private val platform: MicrophonePlatform,
    private val changed: (MicrophoneRouteState) -> Unit
) {
    var state = MicrophoneRouteState()
        private set
    private var closed = false
    private var requested: MicrophoneDevice? = null
    private var requestVersion = 0L
    private var cancelTimeout: () -> Unit = {}

    fun start() {
        if (closed) return
        state = state.copy(recording = true)
        platform.watch(::refresh)
        refresh()
    }

    fun select(device: MicrophoneDevice) {
        if (closed || !state.recording) return
        cancelTimeout()
        val version = ++requestVersion
        if (device !in platform.devices()) {
            fallback(MicrophoneRouteFailure.Unavailable)
            return
        }
        requested = device
        state = state.copy(pending = device, failure = null)
        if (!platform.select(device)) {
            fallback(MicrophoneRouteFailure.Rejected)
            return
        }
        cancelTimeout = platform.afterTimeout {
            if (!closed && version == requestVersion && state.pending != null) {
                fallback(MicrophoneRouteFailure.TimedOut)
            }
        }
        refresh()
    }

    private fun refresh() {
        if (closed) return
        val devices = platform.devices()
        val active = platform.activeDevice()
        val desired = requested
        if (desired != null && (desired !in devices ||
                    (state.pending == null && active != desired))) {
            fallback(MicrophoneRouteFailure.Disconnected)
            return
        }
        if (active == state.pending && active != null) {
            cancelTimeout()
            state = state.copy(pending = null)
        }
        state = state.copy(devices = devices, active = active)
        changed(state)
    }

    private fun fallback(reason: MicrophoneRouteFailure) {
        cancelTimeout()
        requested = null
        platform.clear()
        // The displayed route comes from the recorder, never from this fallback request.
        state = state.copy(devices = platform.devices(), active = platform.activeDevice(),
            pending = null, failure = reason)
        changed(state)
    }

    fun close() {
        if (closed) return
        closed = true
        cancelTimeout()
        platform.close()
        state = MicrophoneRouteState()
        changed(state)
    }
}

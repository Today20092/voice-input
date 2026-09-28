package org.futo.voiceinput

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRouting
import android.os.Build
import android.os.Handler
import android.os.Looper

/** Controls only this recorder. Device names/addresses are never exposed to diagnostics or UI. */
@Suppress("DEPRECATION")
class AndroidMicrophonePlatform(
    private val context: Context,
    private val recorder: AudioRecord
) : MicrophonePlatform {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var closed = false
    private var changed: () -> Unit = {}
    private var previousMode: Int? = null
    private var scoRequested = false
    private var watching = false
    private var receiverRegistered = false

    companion object {
        // Activity and IME can overlap in one process. An old session must not clear a new request.
        private var owner: AndroidMicrophonePlatform? = null
    }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = notifyChanged()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = notifyChanged()
    }
    private val routingCallback = AudioRouting.OnRoutingChangedListener { notifyChanged() }
    private val scoReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = notifyChanged()
    }

    private fun notifyChanged() {
        if (!closed) changed()
    }

    private fun kind(device: AudioDeviceInfo) = when (device.type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> MicrophoneKind.Phone
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET -> MicrophoneKind.Bluetooth
        else -> MicrophoneKind.Other
    }

    private fun inputs(): List<AudioDeviceInfo> =
        audio.getDevices(AudioManager.GET_DEVICES_INPUTS).toList()

    private fun communicationDevice(input: AudioDeviceInfo): AudioDeviceInfo? {
        if (Build.VERSION.SDK_INT < 31) return null
        val candidates = audio.availableCommunicationDevices.filter { it.type == input.type }
        // Match both ends of the same headset; never guess among multiple endpoints.
        if (input.address.isNotEmpty()) {
            candidates.singleOrNull { it.address == input.address }?.let { return it }
        }
        return candidates.singleOrNull()?.takeIf {
            inputs().count { device -> device.type == input.type } == 1
        }
    }

    override fun devices(): List<MicrophoneDevice> = try {
        val inputs = inputs()
        inputs.filter {
            kind(it) == MicrophoneKind.Phone || (kind(it) == MicrophoneKind.Bluetooth &&
                if (Build.VERSION.SDK_INT >= 31) communicationDevice(it) != null
                else it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO && audio.isBluetoothScoAvailableOffCall &&
                    inputs.count { device -> device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO } == 1)
        }.map { MicrophoneDevice(it.id, kind(it)) }
    } catch (_: RuntimeException) {
        emptyList()
    }

    override fun activeDevice(): MicrophoneDevice? = try {
        recorder.routedDevice?.let { MicrophoneDevice(it.id, kind(it)) }
    } catch (_: RuntimeException) {
        null
    }

    override fun select(device: MicrophoneDevice): Boolean {
        if (closed) return false
        return try {
            val input = inputs().firstOrNull { it.id == device.id } ?: return false
            owner?.takeIf { it !== this }?.clear()
            clear()
            owner = this
            if (device.kind == MicrophoneKind.Bluetooth) {
                previousMode = audio.mode
                audio.mode = AudioManager.MODE_IN_COMMUNICATION
                if (Build.VERSION.SDK_INT >= 31) {
                    val output = communicationDevice(input) ?: return false
                    if (!audio.setCommunicationDevice(output)) return false
                } else {
                    scoRequested = true
                    audio.startBluetoothSco()
                }
            }
            recorder.setPreferredDevice(input)
        } catch (_: RuntimeException) {
            // Includes denied/revoked permissions. Do not log exception/device descriptions.
            false
        }
    }

    override fun clear() {
        runCatching { recorder.setPreferredDevice(null) }
        if (owner !== this) return
        if (Build.VERSION.SDK_INT >= 31) runCatching { audio.clearCommunicationDevice() }
        if (scoRequested) runCatching { audio.stopBluetoothSco() }
        scoRequested = false
        previousMode?.let { mode ->
            runCatching { if (audio.mode == AudioManager.MODE_IN_COMMUNICATION) audio.mode = mode }
        }
        previousMode = null
        owner = null
    }

    override fun watch(onChanged: () -> Unit) {
        changed = onChanged
        try {
            audio.registerAudioDeviceCallback(deviceCallback, handler)
            watching = true
            recorder.addOnRoutingChangedListener(routingCallback, handler)
            if (Build.VERSION.SDK_INT < 31) {
                context.registerReceiver(scoReceiver, IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED))
                receiverRegistered = true
            }
        } catch (_: RuntimeException) {
            // Default capture remains usable if route observation is unavailable.
        }
    }

    override fun afterTimeout(action: () -> Unit): () -> Unit {
        val callback = Runnable { if (!closed) action() }
        handler.postDelayed(callback, 30_000L)
        return { handler.removeCallbacks(callback) }
    }

    override fun close() {
        if (closed) return
        closed = true
        changed = {}
        clear()
        if (watching) {
            runCatching { audio.unregisterAudioDeviceCallback(deviceCallback) }
            runCatching { recorder.removeOnRoutingChangedListener(routingCallback) }
        }
        if (receiverRegistered) runCatching { context.unregisterReceiver(scoReceiver) }
    }
}

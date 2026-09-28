package org.futo.voiceinput

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics

@Composable
private fun microphoneLabel(kind: MicrophoneKind?) = stringResource(when (kind) {
    MicrophoneKind.Phone -> R.string.microphone_phone
    MicrophoneKind.Bluetooth -> R.string.microphone_bluetooth
    MicrophoneKind.Other -> R.string.microphone_other
    null -> R.string.microphone_unknown
})

@Composable
fun MicrophoneRouteControl(state: MicrophoneRouteState, select: (MicrophoneDevice) -> Unit) {
    if (!state.visible) return
    var expanded by remember { mutableStateOf(false) }
    val active = microphoneLabel(state.active?.kind)
    val status = when {
        state.pending != null -> stringResource(R.string.microphone_connecting, active)
        state.failure != null -> stringResource(R.string.microphone_fallback, active)
        else -> stringResource(R.string.microphone_active, active)
    }
    Box {
        TextButton(onClick = { expanded = true },
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            Text(status)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.devices.forEach { device ->
                val peers = state.devices.filter { it.kind == device.kind }
                val label = microphoneLabel(device.kind)
                DropdownMenuItem(text = {
                    Text(if (peers.size > 1) "$label ${peers.indexOf(device) + 1}" else label)
                }, onClick = { expanded = false; select(device) })
            }
        }
    }
}

package org.futo.voiceinput

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.futo.voiceinput.theme.UixThemeAuto
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MicrophoneRouteControlTest {
    @get:Rule val compose = createComposeRule()
    private val phone = MicrophoneDevice(1, MicrophoneKind.Phone)
    private val headset = MicrophoneDevice(2, MicrophoneKind.Bluetooth)

    @Test fun phoneOnlyRecordingDoesNotShowRouteControl() {
        compose.setContent {
            UixThemeAuto {
                MicrophoneRouteControl(MicrophoneRouteState(devices = listOf(phone), active = phone,
                    recording = true)) {}
            }
        }
        compose.onNodeWithText("Change microphone", substring = true).assertDoesNotExist()
    }

    @Test fun pendingSelectionReportsObservedMicrophoneAndAllowsPhoneFallback() {
        var selected: MicrophoneDevice? = null
        compose.setContent {
            UixThemeAuto {
                MicrophoneRouteControl(MicrophoneRouteState(devices = listOf(phone, headset),
                    active = phone, pending = headset, recording = true)) { selected = it }
            }
        }
        compose.onNodeWithText("Changing microphone… Currently: Phone microphone")
            .assertIsDisplayed().performClick()
        compose.onNodeWithText("Phone microphone").performClick()
        compose.runOnIdle { assertEquals(phone, selected) }
    }

    @Test fun multipleHeadsetsUseMatchingActiveAndPickerNumbers() {
        val second = MicrophoneDevice(3, MicrophoneKind.Bluetooth)
        compose.setContent {
            UixThemeAuto {
                MicrophoneRouteControl(MicrophoneRouteState(devices = listOf(phone, headset, second),
                    active = second, recording = true)) {}
            }
        }
        compose.onNodeWithText("Bluetooth microphone 2 · Change microphone")
            .assertIsDisplayed().performClick()
        compose.onNodeWithText("Bluetooth microphone 1").assertIsDisplayed()
        compose.onNodeWithText("Bluetooth microphone 2").assertIsDisplayed()
    }
}

package org.futo.voiceinput.settings.pages

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.voiceinput.R
import org.futo.voiceinput.settings.ENABLE_30S_LIMIT
import org.futo.voiceinput.settings.ENABLE_SOUND
import org.futo.voiceinput.settings.END_OF_SPEECH_PROFILE
import org.futo.voiceinput.settings.EndOfSpeechProfile
import org.futo.voiceinput.settings.IS_VAD_ENABLED
import org.futo.voiceinput.settings.MANUAL_STOP_DRAIN_MS
import org.futo.voiceinput.settings.PARAKEET_USE_VAD
import org.futo.voiceinput.settings.ScreenTitle
import org.futo.voiceinput.settings.ScrollableList
import org.futo.voiceinput.settings.SettingSliderDataStore
import org.futo.voiceinput.settings.SettingRadio
import org.futo.voiceinput.settings.SettingToggleDataStore
import org.futo.voiceinput.settings.SettingsViewModel
import org.futo.voiceinput.settings.Tip
import org.futo.voiceinput.settings.isParakeetSelected
import org.futo.voiceinput.settings.useDataStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun InputScreen(
    settingsViewModel: SettingsViewModel = viewModel(),
    navController: NavHostController = rememberNavController()
) {
    val parakeetSelected = isParakeetSelected()
    val vadEnabled = useDataStore(IS_VAD_ENABLED)

    ScrollableList {
        ScreenTitle(title = stringResource(id = R.string.recording_options), showBack = true, navController = navController)

        SettingToggleDataStore(
            stringResource(R.string.sounds),
            ENABLE_SOUND,
            subtitle = stringResource(R.string.will_play_a_sound_when_started_cancelled),
            disabledSubtitle = stringResource(R.string.will_not_play_sounds_when_started_cancelled)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Tip(stringResource(R.string.stop_on_silence_info))
        SettingToggleDataStore(stringResource(R.string.stop_on_silence), IS_VAD_ENABLED)
        if(vadEnabled.value) {
            SettingRadio(
                title = stringResource(R.string.end_of_speech_profile),
                options = EndOfSpeechProfile.values().map { it.id },
                optionNames = listOf(
                    stringResource(R.string.end_of_speech_fast),
                    stringResource(R.string.end_of_speech_balanced),
                    stringResource(R.string.end_of_speech_patient)
                ),
                setting = END_OF_SPEECH_PROFILE
            )
        }
        if(parakeetSelected) {
            SettingToggleDataStore(
                stringResource(R.string.parakeet_stop_on_silence),
                PARAKEET_USE_VAD,
                subtitle = stringResource(R.string.parakeet_stop_on_silence_info)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Tip(stringResource(R.string.manual_stop_drain_info))
        SettingSliderDataStore(
            title = stringResource(R.string.manual_stop_drain),
            setting = MANUAL_STOP_DRAIN_MS,
            valueRange = 0f..1500f,
            steps = 14,
            valueLabel = { stringResource(R.string.manual_stop_drain_value, it) }
        )

        SettingToggleDataStore(stringResource(R.string.re_enable_30s_limit), ENABLE_30S_LIMIT)
    }
}

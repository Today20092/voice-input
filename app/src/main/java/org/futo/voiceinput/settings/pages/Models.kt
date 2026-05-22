package org.futo.voiceinput.settings.pages

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.voiceinput.R
import org.futo.voiceinput.migration.ConditionalModelUpdate
import org.futo.voiceinput.migration.NeedsMigration
import org.futo.voiceinput.parakeet.isParakeetModelDownloaded
import org.futo.voiceinput.parakeet.startParakeetModelDownloadActivity
import org.futo.voiceinput.settings.DISMISS_MIGRATION_TIP
import org.futo.voiceinput.settings.LANGUAGE_TOGGLES
import org.futo.voiceinput.settings.MANUALLY_SELECT_LANGUAGE
import org.futo.voiceinput.settings.MODELS_MIGRATED
import org.futo.voiceinput.settings.PERSONAL_DICTIONARY
import org.futo.voiceinput.settings.ScreenTitle
import org.futo.voiceinput.settings.ScrollableList
import org.futo.voiceinput.settings.SettingItem
import org.futo.voiceinput.settings.SettingToggleDataStore
import org.futo.voiceinput.settings.SettingsViewModel
import org.futo.voiceinput.settings.Tip
import org.futo.voiceinput.settings.getSettingBlocking
import org.futo.voiceinput.settings.useDataStore

@Composable
fun modelsSubtitle(): String? {
    val context = LocalContext.current
    return if (context.isParakeetModelDownloaded()) {
        stringResource(R.string.parakeet_model_active_subtitle)
    } else {
        stringResource(R.string.parakeet_model_download_required)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalDictionaryEditor(disabled: Boolean) {
    val context = LocalContext.current

    val personalDict = useDataStore(PERSONAL_DICTIONARY)
    val textFieldValue = remember { mutableStateOf(context.getSettingBlocking(
        PERSONAL_DICTIONARY.key, PERSONAL_DICTIONARY.default)) }

    LaunchedEffect(textFieldValue.value) {
        personalDict.setValue(textFieldValue.value)
    }
    
    ScreenTitle(title = stringResource(R.string.personal_dictionary))

    TextField(
        value = textFieldValue.value,
        onValueChange = {
            textFieldValue.value = it
        },
        placeholder = { Text(stringResource(R.string.personal_dictionary_placeholder)) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp, 4.dp),
        enabled = !disabled
    )

}

@Composable
fun ParakeetModelStatus() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isDownloaded = remember { mutableStateOf(context.isParakeetModelDownloaded()) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isDownloaded.value = context.isParakeetModelDownloaded()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    ScreenTitle(stringResource(R.string.parakeet_model))
    SettingItem(
        title = stringResource(R.string.parakeet_unified_model_name),
        subtitle = if (isDownloaded.value) {
            stringResource(R.string.parakeet_model_downloaded)
        } else {
            stringResource(R.string.parakeet_model_download_required)
        },
        onClick = {
            if (!isDownloaded.value) {
                context.startParakeetModelDownloadActivity()
            }
        },
        icon = {
            RadioButton(selected = isDownloaded.value, onClick = null, enabled = false)
        },
        disabled = false
    ) { }
    Tip(stringResource(R.string.parakeet_download_model_tip))
}

@Composable
@Preview
fun ModelsScreen(
    settingsViewModel: SettingsViewModel = viewModel(),
    navController: NavHostController = rememberNavController()
) {
    val (languages, _) = useDataStore(LANGUAGE_TOGGLES)

    val needsUpdate = NeedsMigration()

    val wasMigrated = useDataStore(setting = MODELS_MIGRATED)
    val dismissMigrationTip = useDataStore(setting = DISMISS_MIGRATION_TIP)

    ScrollableList {
        ScreenTitle(stringResource(R.string.model_options), showBack = true, navController = navController)

        ConditionalModelUpdate()

        if(wasMigrated.value && !dismissMigrationTip.value) {
            Tip(stringResource(R.string.new_model_features_tip), onDismiss = { dismissMigrationTip.setValue(true) })
        }

        if(languages.size > 1) {
            SettingToggleDataStore(
                stringResource(R.string.manually_select_language),
                MANUALLY_SELECT_LANGUAGE,
                subtitle = stringResource(R.string.manual_language_selection_toggle_subtitle)
            )
        }

        if(!needsUpdate) {
            PersonalDictionaryEditor(disabled = false)

            Spacer(modifier = Modifier.height(32.dp))
        }

        ParakeetModelStatus()
    }
}

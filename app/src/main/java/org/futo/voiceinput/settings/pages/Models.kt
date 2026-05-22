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
import org.futo.voiceinput.ENGLISH_MODELS
import org.futo.voiceinput.MULTILINGUAL_MODELS
import org.futo.voiceinput.ModelData
import org.futo.voiceinput.R
import org.futo.voiceinput.modelNeedsDownloading
import org.futo.voiceinput.migration.ConditionalModelUpdate
import org.futo.voiceinput.migration.NeedsMigration
import org.futo.voiceinput.parakeet.isParakeetModelDownloaded
import org.futo.voiceinput.parakeet.startParakeetModelDownloadActivity
import org.futo.voiceinput.settings.DISMISS_MIGRATION_TIP
import org.futo.voiceinput.settings.ENABLE_MULTILINGUAL
import org.futo.voiceinput.settings.ENGLISH_MODEL_INDEX
import org.futo.voiceinput.settings.LANGUAGE_TOGGLES
import org.futo.voiceinput.settings.MANUALLY_SELECT_LANGUAGE
import org.futo.voiceinput.settings.MODELS_MIGRATED
import org.futo.voiceinput.settings.MULTILINGUAL_MODEL_INDEX
import org.futo.voiceinput.settings.PERSONAL_DICTIONARY
import org.futo.voiceinput.settings.SPEECH_BACKEND
import org.futo.voiceinput.settings.ScreenTitle
import org.futo.voiceinput.settings.ScrollableList
import org.futo.voiceinput.settings.SettingItem
import org.futo.voiceinput.settings.SettingRadio
import org.futo.voiceinput.settings.SettingToggleDataStore
import org.futo.voiceinput.settings.SettingsViewModel
import org.futo.voiceinput.settings.SpeechBackendType
import org.futo.voiceinput.settings.Tip
import org.futo.voiceinput.settings.USE_LANGUAGE_SPECIFIC_MODELS
import org.futo.voiceinput.settings.getSettingBlocking
import org.futo.voiceinput.settings.isParakeetSelected
import org.futo.voiceinput.settings.toSpeechBackendType
import org.futo.voiceinput.settings.useDataStore
import org.futo.voiceinput.startModelDownloadActivity

@Composable
fun modelsSubtitle(): String? {
    val context = LocalContext.current
    val (backend, _) = useDataStore(SPEECH_BACKEND)
    return when (backend.toSpeechBackendType()) {
        SpeechBackendType.Parakeet -> {
            if (context.isParakeetModelDownloaded(verifyHashes = true)) {
                stringResource(R.string.parakeet_model_active_subtitle)
            } else {
                stringResource(R.string.parakeet_model_download_required)
            }
        }
        SpeechBackendType.WhisperGGML -> stringResource(R.string.whisper_ggml_model_active_subtitle)
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
    val isDownloaded = remember { mutableStateOf(context.isParakeetModelDownloaded(verifyHashes = true)) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isDownloaded.value = context.isParakeetModelDownloaded(verifyHashes = true)
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
fun WhisperModelRadio(
    title: String,
    models: List<ModelData>,
    setting: org.futo.voiceinput.settings.SettingsKey<Int>
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val modelIndex = useDataStore(setting)
    val refresh = remember { mutableStateOf(0) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refresh.value += 1
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    ScreenTitle(title)
    models.forEachIndexed { index, model ->
        val needsDownload = context.modelNeedsDownloading(model)
        refresh.value
        SettingItem(
            title = model.name,
            subtitle = if (needsDownload) stringResource(R.string.whisper_model_download_required) else null,
            onClick = {
                if (modelIndex.value == index && needsDownload) {
                    context.startModelDownloadActivity(listOf(model))
                } else {
                    modelIndex.setValue(index)
                }
            },
            icon = {
                RadioButton(
                    selected = modelIndex.value == index,
                    onClick = {
                        if (modelIndex.value == index && needsDownload) {
                            context.startModelDownloadActivity(listOf(model))
                        } else {
                            modelIndex.setValue(index)
                        }
                    }
                )
            }
        ) { }
    }
}

@Composable
fun WhisperModelOptions() {
    val (useMultilingual, _) = useDataStore(ENABLE_MULTILINGUAL)
    val (languages, _) = useDataStore(LANGUAGE_TOGGLES)
    val (useLanguageSpecificModels, _) = useDataStore(USE_LANGUAGE_SPECIFIC_MODELS)

    if (useMultilingual) {
        WhisperModelRadio(
            stringResource(R.string.multilingual_model),
            MULTILINGUAL_MODELS,
            MULTILINGUAL_MODEL_INDEX
        )
    }

    if((!useMultilingual) || (languages.contains("en") && useLanguageSpecificModels)) {
        WhisperModelRadio(
            stringResource(R.string.english_model),
            ENGLISH_MODELS,
            ENGLISH_MODEL_INDEX
        )
    }

    Tip(stringResource(R.string.parameter_count_tip))
}

@Composable
@Preview
fun ModelsScreen(
    settingsViewModel: SettingsViewModel = viewModel(),
    navController: NavHostController = rememberNavController()
) {
    val (languages, _) = useDataStore(LANGUAGE_TOGGLES)
    val (backend, _) = useDataStore(SPEECH_BACKEND)
    val parakeetSelected = isParakeetSelected()

    val needsUpdate = NeedsMigration()

    val wasMigrated = useDataStore(setting = MODELS_MIGRATED)
    val dismissMigrationTip = useDataStore(setting = DISMISS_MIGRATION_TIP)

    ScrollableList {
        ScreenTitle(stringResource(R.string.model_options), showBack = true, navController = navController)

        if (!parakeetSelected) {
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
        }

        SettingRadio(
            title = stringResource(R.string.speech_backend),
            options = listOf(SpeechBackendType.Parakeet.id, SpeechBackendType.WhisperGGML.id),
            optionNames = listOf(
                stringResource(R.string.backend_parakeet),
                stringResource(R.string.backend_whisper_ggml)
            ),
            setting = SPEECH_BACKEND
        )

        Spacer(modifier = Modifier.height(32.dp))

        when (backend.toSpeechBackendType()) {
            SpeechBackendType.Parakeet -> ParakeetModelStatus()
            SpeechBackendType.WhisperGGML -> WhisperModelOptions()
        }
    }
}

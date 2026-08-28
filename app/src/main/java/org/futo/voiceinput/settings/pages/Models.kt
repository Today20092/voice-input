package org.futo.voiceinput.settings.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import org.futo.voiceinput.ENGLISH_MODELS
import org.futo.voiceinput.BuildConfig
import org.futo.voiceinput.MULTILINGUAL_MODELS
import org.futo.voiceinput.ModelData
import org.futo.voiceinput.R
import org.futo.voiceinput.modelNeedsDownloading
import org.futo.voiceinput.downloader.startRecognitionModelDownloadActivity
import org.futo.voiceinput.migration.ConditionalModelUpdate
import org.futo.voiceinput.migration.NeedsMigration
import org.futo.voiceinput.nemotron.NEMOTRON_MULTILINGUAL_LANGUAGES
import org.futo.voiceinput.settings.DISMISS_MIGRATION_TIP
import org.futo.voiceinput.settings.ENABLE_MULTILINGUAL
import org.futo.voiceinput.settings.ENGLISH_MODEL_INDEX
import org.futo.voiceinput.settings.LANGUAGE_TOGGLES
import org.futo.voiceinput.settings.MANUALLY_SELECT_LANGUAGE
import org.futo.voiceinput.settings.MODELS_MIGRATED
import org.futo.voiceinput.settings.MOONSHINE_MODEL_VARIANT
import org.futo.voiceinput.settings.NEMOTRON_PROFILE
import org.futo.voiceinput.settings.NEMOTRON_MULTILINGUAL_LANGUAGE
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
import org.futo.voiceinput.settings.getSettingBlocking
import org.futo.voiceinput.settings.toSpeechBackendType
import org.futo.voiceinput.settings.useDataStore
import org.futo.voiceinput.startModelDownloadActivity
import org.futo.voiceinput.recognition.RecognitionModelCatalog
import org.futo.voiceinput.recognition.RecognitionModelLifecycle
import org.futo.voiceinput.recognition.RecognitionModel
import org.futo.voiceinput.recognition.RecognitionModelSelection
import org.futo.voiceinput.recognition.updateRecognitionModelSelection

@Composable
fun modelsSubtitle(): String? {
    val context = LocalContext.current
    val (backend, _) = useDataStore(SPEECH_BACKEND)
    val (moonshineVariantId, _) = useDataStore(MOONSHINE_MODEL_VARIANT)
    val (nemotronProfileId, _) = useDataStore(NEMOTRON_PROFILE)
    val (englishModelIndex, _) = useDataStore(ENGLISH_MODEL_INDEX)
    val (multilingualModelIndex, _) = useDataStore(MULTILINGUAL_MODEL_INDEX)
    val (multilingualEnabled, _) = useDataStore(ENABLE_MULTILINGUAL)
    val readiness = remember(context) {
        RecognitionModelLifecycle.create(context.filesDir, BuildConfig.BUNDLE_PARAKEET_MODEL)
    }.readiness(
        RecognitionModelSelection(backend, moonshineVariantId, nemotronProfileId)
    )
    val selected = selectedRecognitionModelSummary(
        runtimeId = backend,
        managedModelName = readiness?.model?.displayName,
        englishModel = ENGLISH_MODELS[englishModelIndex.coerceIn(ENGLISH_MODELS.indices)],
        multilingualModel = MULTILINGUAL_MODELS[multilingualModelIndex.coerceIn(MULTILINGUAL_MODELS.indices)],
        multilingualEnabled = multilingualEnabled
    )
    return if (backend.toSpeechBackendType() != SpeechBackendType.WhisperGGML &&
        readiness?.isReady != true
    ) {
        "$selected • Download required"
    } else {
        selected
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
fun ManagedRecognitionModelCatalog() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val backend = useDataStore(SPEECH_BACKEND)
    val moonshineVariant = useDataStore(MOONSHINE_MODEL_VARIANT)
    val nemotronProfile = useDataStore(NEMOTRON_PROFILE)
    val refresh = remember { mutableStateOf(0) }
    val modelLifecycle = remember(context) {
        RecognitionModelLifecycle.create(context.filesDir, BuildConfig.BUNDLE_PARAKEET_MODEL)
    }
    val selectedModelId = modelLifecycle.readiness(
        RecognitionModelSelection(backend.value, moonshineVariant.value, nemotronProfile.value)
    )?.model?.id

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refresh.value += 1
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    refresh.value
    RecognitionModelCatalog.cards.forEach { card ->
        ScreenTitle(card.displayName)
        Tip(card.description)

        if (card.models.isEmpty()) {
            val selected = backend.value == card.runtimeId
            if (card.id == "whisper") {
                WhisperModelOptions(whisperSelected = selected)
            }
        } else {
            card.models.forEach { model ->
                ManagedRecognitionModelItem(
                    model = model,
                    selectedModelId = selectedModelId,
                    modelLifecycle = modelLifecycle,
                    onDeleted = { refresh.value += 1 }
                )
            }
            if (card.id == "nemotron-multilingual" &&
                selectedModelId == RecognitionModelCatalog.nemotronMultilingual.id
            ) {
                SettingRadio(
                    title = "Recognition language",
                    options = NEMOTRON_MULTILINGUAL_LANGUAGES.map { it.id },
                    optionNames = NEMOTRON_MULTILINGUAL_LANGUAGES.map { it.displayName },
                    setting = NEMOTRON_MULTILINGUAL_LANGUAGE
                )
            }
        }
    }
}

@Composable
private fun ManagedRecognitionModelItem(
    model: RecognitionModel,
    selectedModelId: String?,
    modelLifecycle: RecognitionModelLifecycle,
    onDeleted: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val bundled = model.runtimeId == SpeechBackendType.Parakeet.id &&
        BuildConfig.BUNDLE_PARAKEET_MODEL
    val installed = modelLifecycle.isReady(model)
    val selected = selectedModelId == model.id
    val presentation = presentRecognitionModel(model, installed, selected)
    val showDetails = remember { mutableStateOf(false) }
    val selectOrDownload = {
        if (installed) {
            modelLifecycle.select(model, context::updateRecognitionModelSelection)
        } else {
            context.startRecognitionModelDownloadActivity(model)
        }
    }

    SettingItem(
        title = presentation.title,
        subtitle = presentation.summary,
        onClick = selectOrDownload,
        icon = { RadioButton(selected = selected, onClick = selectOrDownload) }
    ) {
        Column {
            TextButton(onClick = { showDetails.value = true }) { Text("Details") }
            if (installed && !bundled) {
                TextButton(
                    enabled = !selected,
                    onClick = {
                        lifecycleOwner.lifecycleScope.launch {
                            modelLifecycle.delete(model, selectedModelId)
                            onDeleted()
                        }
                    }
                ) { Text(if (selected) "Selected" else "Delete") }
            }
        }
    }
    if (showDetails.value) {
        ModelDetailsDialog(presentation) { showDetails.value = false }
    }
}

@Composable
fun WhisperModelRadio(
    title: String,
    models: List<ModelData>,
    setting: org.futo.voiceinput.settings.SettingsKey<Int>,
    whisperSelected: Boolean,
    variantSelected: Boolean
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
        val presentation = presentWhisperModel(
            model = model,
            languages = title,
            installed = !needsDownload,
            selected = whisperSelected && variantSelected && modelIndex.value == index
        )
        val showDetails = remember(model.ggml.ggml_file) { mutableStateOf(false) }
        refresh.value
        SettingItem(
            title = presentation.title,
            subtitle = presentation.summary,
            onClick = {
                context.updateRecognitionModelSelection(RecognitionModelSelection("whisper_ggml"))
                if (modelIndex.value == index && needsDownload) {
                    context.startModelDownloadActivity(listOf(model))
                } else {
                    modelIndex.setValue(index)
                }
            },
            icon = {
                RadioButton(
                    selected = whisperSelected && variantSelected && modelIndex.value == index,
                    onClick = {
                        context.updateRecognitionModelSelection(RecognitionModelSelection("whisper_ggml"))
                        if (modelIndex.value == index && needsDownload) {
                            context.startModelDownloadActivity(listOf(model))
                        } else {
                            modelIndex.setValue(index)
                        }
                    }
                )
            }
        ) {
            TextButton(onClick = { showDetails.value = true }) { Text("Details") }
        }
        if (showDetails.value) {
            ModelDetailsDialog(presentation) { showDetails.value = false }
        }
    }
}

@Composable
fun WhisperModelOptions(whisperSelected: Boolean) {
    val multilingualEnabled = useDataStore(ENABLE_MULTILINGUAL).value
    WhisperModelRadio(
        "English",
        ENGLISH_MODELS,
        ENGLISH_MODEL_INDEX,
        whisperSelected,
        variantSelected = true
    )
    WhisperModelRadio(
        "Multilingual",
        MULTILINGUAL_MODELS,
        MULTILINGUAL_MODEL_INDEX,
        whisperSelected,
        variantSelected = multilingualEnabled
    )

    Tip(stringResource(R.string.parameter_count_tip))
}

@Composable
private fun ModelDetailsDialog(presentation: ModelPresentation, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(presentation.title) },
        text = { Text(presentation.details) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
@Preview
fun ModelsScreen(
    settingsViewModel: SettingsViewModel = viewModel(),
    navController: NavHostController = rememberNavController()
) {
    val (languages, _) = useDataStore(LANGUAGE_TOGGLES)
    val (backend, _) = useDataStore(SPEECH_BACKEND)
    val whisperSelected = backend.toSpeechBackendType() == SpeechBackendType.WhisperGGML

    NeedsMigration()

    val wasMigrated = useDataStore(setting = MODELS_MIGRATED)
    val dismissMigrationTip = useDataStore(setting = DISMISS_MIGRATION_TIP)

    ScrollableList {
        ScreenTitle(stringResource(R.string.model_options), showBack = true, navController = navController)

        if (whisperSelected) {
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

        }

        PersonalDictionaryEditor(disabled = false)
        Spacer(modifier = Modifier.height(32.dp))

        ManagedRecognitionModelCatalog()
    }
}

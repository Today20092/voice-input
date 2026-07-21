package org.futo.voiceinput.settings.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
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
import org.futo.voiceinput.parakeet.isParakeetModelDownloaded
import org.futo.voiceinput.parakeet.isParakeetUnifiedModelDownloaded
import org.futo.voiceinput.parakeet.releaseRuntime
import org.futo.voiceinput.moonshine.isMoonshineModelDownloaded
import org.futo.voiceinput.moonshine.MoonshineModelVariant
import org.futo.voiceinput.moonshine.toMoonshineModelVariant
import org.futo.voiceinput.nemotron.isNemotronModelDownloaded
import org.futo.voiceinput.nemotron.NEMOTRON_MULTILINGUAL_LANGUAGES
import org.futo.voiceinput.nemotron.recognitionModel
import org.futo.voiceinput.nemotron.toNemotronProfile
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
import org.futo.voiceinput.settings.USE_LANGUAGE_SPECIFIC_MODELS
import org.futo.voiceinput.settings.getSettingBlocking
import org.futo.voiceinput.settings.toSpeechBackendType
import org.futo.voiceinput.settings.useDataStore
import org.futo.voiceinput.startModelDownloadActivity
import org.futo.voiceinput.recognition.RecognitionModelCatalog
import org.futo.voiceinput.recognition.RecognitionModel
import org.futo.voiceinput.recognition.RecognitionModelStore

@Composable
fun modelsSubtitle(): String? {
    val context = LocalContext.current
    val (backend, _) = useDataStore(SPEECH_BACKEND)
    val (moonshineVariantId, _) = useDataStore(MOONSHINE_MODEL_VARIANT)
    val (nemotronProfileId, _) = useDataStore(NEMOTRON_PROFILE)
    return when (backend.toSpeechBackendType()) {
        SpeechBackendType.Parakeet -> {
            if (context.isParakeetModelDownloaded(verifyHashes = true)) {
                stringResource(R.string.parakeet_model_active_subtitle)
            } else {
                stringResource(R.string.parakeet_model_download_required)
            }
        }
        SpeechBackendType.ParakeetUnified -> {
            if (context.isParakeetUnifiedModelDownloaded(verifyHashes = true)) {
                stringResource(R.string.parakeet_unified_model_active_subtitle)
            } else {
                stringResource(R.string.parakeet_unified_model_download_required)
            }
        }
        SpeechBackendType.Nemotron -> {
            val profile = nemotronProfileId.toNemotronProfile()
            if (context.isNemotronModelDownloaded(profile)) {
                stringResource(R.string.nemotron_model_active_subtitle, profile.recognitionModel().displayName)
            } else {
                stringResource(R.string.nemotron_model_download_required, profile.recognitionModel().displayName)
            }
        }
        SpeechBackendType.Moonshine -> {
            if (context.isMoonshineModelDownloaded(moonshineVariantId.toMoonshineModelVariant())) {
                stringResource(R.string.moonshine_model_active_subtitle)
            } else {
                stringResource(R.string.moonshine_model_download_required)
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
fun ManagedRecognitionModelCatalog() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val backend = useDataStore(SPEECH_BACKEND)
    val moonshineVariant = useDataStore(MOONSHINE_MODEL_VARIANT)
    val nemotronProfile = useDataStore(NEMOTRON_PROFILE)
    val refresh = remember { mutableStateOf(0) }
    val store = remember(context) { RecognitionModelStore(context.filesDir) }
    val selectedModelId = RecognitionModelCatalog.modelFor(
        runtimeId = backend.value,
        variantId = when (backend.value) {
            SpeechBackendType.Moonshine.id -> moonshineVariant.value
            SpeechBackendType.Nemotron.id -> nemotronProfile.value
            else -> null
        }
    )?.id

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
            SettingItem(
                title = card.displayName,
                subtitle = "${card.transcription.label} • ${card.recognitionLanguages} • " +
                    card.performanceClasses.joinToString(" to ") { it.label },
                onClick = { backend.setValue(card.runtimeId) },
                icon = { RadioButton(selected = selected, onClick = null) }
            ) { }
        } else {
            card.models.forEach { model ->
                ManagedRecognitionModelItem(
                    model = model,
                    selectedModelId = selectedModelId,
                    store = store,
                    onSelect = {
                        model.variantId?.let {
                            when (model.runtimeId) {
                                SpeechBackendType.Moonshine.id -> moonshineVariant.setValue(it)
                                SpeechBackendType.Nemotron.id -> nemotronProfile.setValue(it)
                            }
                        }
                        backend.setValue(model.runtimeId)
                    },
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
    store: RecognitionModelStore,
    onSelect: () -> Unit,
    onDeleted: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val bundled = model.runtimeId == SpeechBackendType.Parakeet.id &&
        BuildConfig.BUNDLE_PARAKEET_MODEL
    val pinnedVersions = RecognitionModelCatalog.versionsFor(model.id)
    val installedModel = if (bundled) model else store.installedModel(pinnedVersions)
    val installed = installedModel != null
    val update = if (bundled) null else store.findUpdate(model, pinnedVersions)
    val selected = selectedModelId == model.id
    val status = when {
        update != null && selected -> "Selected ${update.installed.version} • Update ${update.available.version} available"
        update != null -> "Installed ${update.installed.version} • Update ${update.available.version} available"
        selected -> "Selected — choose another installed model before deleting"
        installed -> "Installed"
        else -> "Download required"
    }
    val subtitle = "${model.description}\n${model.transcription.label} • " +
        "${model.recognitionLanguages} • ${model.performanceClass.label}\n" +
        "${model.source} • ${model.license} • ${"%.1f".format(model.transferBytes / 1_000_000.0)} MB • $status"
    val selectOrDownload = {
        if (installed) {
            if (bundled) onSelect() else store.select(requireNotNull(installedModel), onSelect)
        } else {
            context.startRecognitionModelDownloadActivity(model)
        }
    }

    SettingItem(
        title = model.displayName,
        subtitle = subtitle,
        onClick = selectOrDownload,
        icon = { RadioButton(selected = selected, onClick = selectOrDownload) }
    ) {
        if (installed && !bundled) {
            Column {
                if (update != null) {
                    TextButton(onClick = {
                        context.startRecognitionModelDownloadActivity(update.available, isUpdate = true)
                    }) { Text("Update") }
                }
                TextButton(
                    enabled = !selected,
                    onClick = {
                        lifecycleOwner.lifecycleScope.launch {
                            requireNotNull(installedModel).releaseRuntime()
                            store.delete(requireNotNull(installedModel), selectedModelId = selectedModelId)
                            onDeleted()
                        }
                    }
                ) { Text(if (selected) "Selected" else "Delete") }
            }
        }
    }
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
    val whisperSelected = backend.toSpeechBackendType() == SpeechBackendType.WhisperGGML

    val needsUpdate = NeedsMigration()

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

        if (whisperSelected) WhisperModelOptions()
    }
}

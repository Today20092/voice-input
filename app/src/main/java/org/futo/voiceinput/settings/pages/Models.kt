package org.futo.voiceinput.settings.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.Surface
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
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
import org.futo.voiceinput.openURI
import org.futo.voiceinput.downloader.startRecognitionModelDownloadActivity
import org.futo.voiceinput.migration.ConditionalModelUpdate
import org.futo.voiceinput.migration.NeedsMigration
import org.futo.voiceinput.settings.DISMISS_MIGRATION_TIP
import org.futo.voiceinput.settings.ENABLE_MULTILINGUAL
import org.futo.voiceinput.settings.ENGLISH_MODEL_INDEX
import org.futo.voiceinput.settings.MODELS_MIGRATED
import org.futo.voiceinput.settings.MOONSHINE_MODEL_VARIANT
import org.futo.voiceinput.settings.NEMOTRON_PROFILE
import org.futo.voiceinput.settings.MULTILINGUAL_MODEL_INDEX
import org.futo.voiceinput.settings.SPEECH_BACKEND
import org.futo.voiceinput.settings.ScreenTitle
import org.futo.voiceinput.settings.ScrollableList
import org.futo.voiceinput.settings.SettingsViewModel
import org.futo.voiceinput.settings.SpeechBackendType
import org.futo.voiceinput.settings.Tip
import org.futo.voiceinput.settings.toSpeechBackendType
import org.futo.voiceinput.settings.useDataStore
import org.futo.voiceinput.settings.useDataStoreValueNullable
import org.futo.voiceinput.startModelDownloadActivity
import org.futo.voiceinput.recognition.RecognitionModelCatalog
import org.futo.voiceinput.recognition.RecognitionModelLifecycle
import org.futo.voiceinput.recognition.RecognitionModel
import org.futo.voiceinput.recognition.RecognitionModelSelection
import org.futo.voiceinput.recognition.RecognitionModelReadiness
import org.futo.voiceinput.recognition.RecognitionModelRepairReason
import org.futo.voiceinput.recognition.updateRecognitionModelSelection

@Composable
private fun observeModelReadinessChanges(): Long {
    val revision by RecognitionModelLifecycle.invalidations.collectAsState()
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) RecognitionModelLifecycle.publishChange()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return revision
}

@Composable
fun modelsSubtitle(): String? {
    observeModelReadinessChanges()
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
        managedModel = readiness?.model,
        englishModel = ENGLISH_MODELS[englishModelIndex.coerceIn(ENGLISH_MODELS.indices)],
        multilingualModel = MULTILINGUAL_MODELS[multilingualModelIndex.coerceIn(MULTILINGUAL_MODELS.indices)],
        multilingualEnabled = multilingualEnabled
    )
    val updateDismissed = readiness?.optionalUpgrade?.let {
        context.getSharedPreferences("model_upgrade_notices", 0).getBoolean("${it.id}@${it.version}", false)
    } ?: false
    return if (backend.toSpeechBackendType() != SpeechBackendType.WhisperGGML &&
        readiness?.isReady != true
    ) {
        "$selected • Download required"
    } else if (readiness?.optionalUpgrade != null && !updateDismissed) {
        "$selected • Optional update"
    } else {
        selected
    }
}

@Composable
fun ManagedRecognitionModelCatalog() {
    observeModelReadinessChanges()
    val context = LocalContext.current
    // Wait for the saved selection before initializing which family is open.
    val backend = useDataStoreValueNullable(SPEECH_BACKEND.key, SPEECH_BACKEND.default) ?: return
    val moonshineVariant = useDataStore(MOONSHINE_MODEL_VARIANT)
    val nemotronProfile = useDataStore(NEMOTRON_PROFILE)
    val englishIndex = useDataStore(ENGLISH_MODEL_INDEX).value.coerceIn(ENGLISH_MODELS.indices)
    val multilingualIndex = useDataStore(MULTILINGUAL_MODEL_INDEX).value.coerceIn(MULTILINGUAL_MODELS.indices)
    val multilingualEnabled = useDataStore(ENABLE_MULTILINGUAL).value
    val modelLifecycle = remember(context) {
        RecognitionModelLifecycle.create(context.filesDir, BuildConfig.BUNDLE_PARAKEET_MODEL)
    }
    val selectedModel = modelLifecycle.readiness(
        RecognitionModelSelection(backend, moonshineVariant.value, nemotronProfile.value)
    )?.model
    val selectedModelId = selectedModel?.id
    val selectedSummary = selectedRecognitionModelSummary(
        backend, selectedModel, ENGLISH_MODELS[englishIndex],
        MULTILINGUAL_MODELS[multilingualIndex], multilingualEnabled
    )

    modelFamiliesForDisplay().forEach { (family, cards) ->
        key(family) {
            val selected = cards.any { card ->
                card.models.any { it.id == selectedModelId } ||
                    (card.id == "whisper" && backend == card.runtimeId)
            }
            val expanded = rememberSaveable { mutableStateOf(selected) }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            TextButton(
                onClick = { expanded.value = !expanded.value },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).semantics {
                    heading()
                    stateDescription = if (expanded.value) "Expanded" else "Collapsed"
                }
            ) {
                Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(family, style = MaterialTheme.typography.titleMedium)
                    if (selected) Text("Selected: $selectedSummary", style = MaterialTheme.typography.bodyMedium)
                }
                Text(if (expanded.value) "Hide" else "Show")
            }
            if (expanded.value) cards.forEach { card ->
                if (cards.size > 1 && card.displayName != family) {
                    Text(card.displayName, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.titleMedium)
                }
                Text(card.description, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (card.id == "whisper") {
                    WhisperModelOptions(whisperSelected = selected)
                } else {
                    card.models.forEach { model ->
                        ManagedRecognitionModelItem(model, selectedModelId, modelLifecycle)
                    }
                    card.models.firstOrNull { it.id == selectedModelId }?.let {
                        RecognitionModelLanguageOptions(it, showGuidance = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelCatalogItem(
    presentation: ModelPresentation,
    selected: Boolean,
    onSelect: () -> Unit,
    actions: @Composable ColumnScope.() -> Unit
) {
    val values = presentation.fields.toMap()
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RadioButton(selected = selected, onClick = null)
                Text(presentation.title, style = MaterialTheme.typography.titleMedium)
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Languages: ${values.getValue("Languages")}", style = MaterialTheme.typography.bodyMedium)
                Text("Download: ${values.getValue("Download size")}", style = MaterialTheme.typography.bodyMedium)
                Text("Model size: ${values.getValue("Model size")}", style = MaterialTheme.typography.bodyMedium)
                Text(values.getValue("Status"), style = MaterialTheme.typography.labelLarge)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = actions)
        }
    }
}

@Composable
private fun ManagedRecognitionModelItem(
    model: RecognitionModel,
    selectedModelId: String?,
    modelLifecycle: RecognitionModelLifecycle
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val bundled = model.runtimeId == SpeechBackendType.Parakeet.id &&
        BuildConfig.BUNDLE_PARAKEET_MODEL
    val revision by RecognitionModelLifecycle.invalidations.collectAsState()
    revision
    val installed = modelLifecycle.isReady(model)
    val readiness = modelLifecycle.readiness(modelLifecycle.selectionFor(model))
    val selected = selectedModelId == model.id
    val presentation = presentRecognitionModel(readiness?.installedModel ?: model, installed, selected)
    val showDetails = remember { mutableStateOf(false) }
    val selectOrDownload = {
        if (installed) {
            modelLifecycle.select(model, context::updateRecognitionModelSelection)
        } else {
            context.startRecognitionModelDownloadActivity(model)
        }
    }

    ModelCatalogItem(
        presentation = presentation,
        selected = selected,
        onSelect = selectOrDownload
    ) {
        Column {
            OutlinedButton(onClick = { showDetails.value = true }) { Text("Details") }
            readiness?.optionalUpgrade?.let { successor ->
                TextButton(onClick = { context.startRecognitionModelDownloadActivity(successor) }) {
                    Text("Update")
                }
            }
            if (installed && !bundled) {
                TextButton(
                    enabled = !selected,
                    onClick = {
                        lifecycleOwner.lifecycleScope.launch {
                            modelLifecycle.delete(model, selectedModelId)
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

    Text(title, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelLarge)
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
        ModelCatalogItem(
            presentation = presentation,
            selected = whisperSelected && variantSelected && modelIndex.value == index,
            onSelect = {
                context.updateRecognitionModelSelection(RecognitionModelSelection("whisper_ggml"))
                if (modelIndex.value == index && needsDownload) {
                    context.startModelDownloadActivity(listOf(model))
                } else {
                    modelIndex.setValue(index)
                }
            }
        ) {
            OutlinedButton(onClick = { showDetails.value = true }) { Text("Details") }
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

    Text(
        stringResource(R.string.parameter_count_tip),
        modifier = Modifier.padding(16.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ModelDetailsDialog(presentation: ModelPresentation, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(presentation.title, style = MaterialTheme.typography.titleLarge) },
        text = {
            SelectionContainer {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(presentation.description, style = MaterialTheme.typography.bodyLarge)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        presentation.informationLinks.forEach { link ->
                            OutlinedButton(
                                onClick = { context.openURI(link.url) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(link.label) }
                        }
                    }
                    HorizontalDivider()
                    presentation.fields.forEach { (label, value) ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(value, style = MaterialTheme.typography.bodyMedium)
                            if (label == "Model size") {
                                Text(
                                    "Size of the unpacked model files stored on your phone. Memory use while transcribing can be different.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
fun RecognitionModelNotice(
    readiness: RecognitionModelReadiness?,
    dismissed: Boolean,
    onDownload: (RecognitionModel) -> Unit,
    onDismiss: () -> Unit
) {
    if (readiness == null) return
    if (!readiness.isReady) {
        val action = if (readiness.repairReason == RecognitionModelRepairReason.INVALID_OR_INCOMPATIBLE)
            "Repair" else "Download"
        Tip("${readiness.model.displayName}: $action required")
        TextButton(onClick = { onDownload(readiness.model) }) { Text("$action model") }
    } else if (readiness.optionalUpgrade != null && !dismissed) {
        Tip("An optional update is available for ${readiness.model.displayName}. " +
            "You can keep using the installed version.", onDismiss = onDismiss)
        TextButton(onClick = { onDownload(readiness.optionalUpgrade) }) { Text("Update model") }
    }
}

@Composable
private fun SelectedRecognitionModelNotice() {
    observeModelReadinessChanges()
    val context = LocalContext.current
    val backend = useDataStore(SPEECH_BACKEND).value
    val moonshine = useDataStore(MOONSHINE_MODEL_VARIANT).value
    val nemotron = useDataStore(NEMOTRON_PROFILE).value
    val lifecycle = remember(context) {
        RecognitionModelLifecycle.create(context.filesDir, BuildConfig.BUNDLE_PARAKEET_MODEL)
    }
    val readiness = lifecycle.readiness(RecognitionModelSelection(backend, moonshine, nemotron))
    val successor = readiness?.optionalUpgrade
    val key = successor?.let { "${it.id}@${it.version}" }
    val preferences = remember(context) { context.getSharedPreferences("model_upgrade_notices", 0) }
    val dismissed = remember(key) { mutableStateOf(key?.let { preferences.getBoolean(it, false) } ?: false) }
    val englishIndex = useDataStore(ENGLISH_MODEL_INDEX).value.coerceIn(ENGLISH_MODELS.indices)
    val multilingualIndex = useDataStore(MULTILINGUAL_MODEL_INDEX).value.coerceIn(MULTILINGUAL_MODELS.indices)
    val multilingualEnabled = useDataStore(ENABLE_MULTILINGUAL).value
    val english = ENGLISH_MODELS[englishIndex]
    val multilingual = MULTILINGUAL_MODELS[multilingualIndex]
    val legacyDownloads = (listOf(english) + if (multilingualEnabled) listOf(multilingual) else emptyList())
        .filter { context.modelNeedsDownloading(it) }
    val title = selectedRecognitionModelSummary(backend, readiness?.model, english, multilingual, multilingualEnabled)
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Selected model", style = MaterialTheme.typography.labelLarge)
            Text(title, style = MaterialTheme.typography.titleLarge)
            val needsDownload = readiness?.isReady == false ||
                (backend == "whisper_ggml" && legacyDownloads.isNotEmpty())
            val repair = readiness?.repairReason == RecognitionModelRepairReason.INVALID_OR_INCOMPATIBLE
            Text(if (repair) "Repair needed" else if (needsDownload) "Download needed" else "Ready to use",
                style = MaterialTheme.typography.bodyMedium)
            if (needsDownload) {
                Button(onClick = {
                    if (readiness != null) context.startRecognitionModelDownloadActivity(readiness.model)
                    else context.startModelDownloadActivity(legacyDownloads)
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("${if (repair) "Repair" else "Download"} $title")
                }
            } else if (successor != null && !dismissed.value) {
                Text("An optional update is available. Your installed model is still usable.")
                Button(onClick = { context.startRecognitionModelDownloadActivity(successor) }) { Text("Update model") }
                TextButton(onClick = {
                    dismissed.value = true
                    key?.let { preferences.edit().putBoolean(it, true).apply() }
                    RecognitionModelLifecycle.publishChange()
                }) { Text("Keep installed version") }
            }
        }
    }
}

@Composable
@Preview
fun ModelsScreen(
    settingsViewModel: SettingsViewModel = viewModel(),
    navController: NavHostController = rememberNavController()
) {
    val (backend, _) = useDataStore(SPEECH_BACKEND)
    val whisperSelected = backend.toSpeechBackendType() == SpeechBackendType.WhisperGGML

    NeedsMigration()

    val wasMigrated = useDataStore(setting = MODELS_MIGRATED)
    val dismissMigrationTip = useDataStore(setting = DISMISS_MIGRATION_TIP)

    ScrollableList {
        ScreenTitle(stringResource(R.string.model_options), showBack = true, navController = navController)
        SelectedRecognitionModelNotice()

        if (whisperSelected) {
            ConditionalModelUpdate()

            if(wasMigrated.value && !dismissMigrationTip.value) {
                Tip(stringResource(R.string.new_model_features_tip), onDismiss = { dismissMigrationTip.setValue(true) })
            }

        }

        ManagedRecognitionModelCatalog()
    }
}

@Composable
fun PersonalDictionaryScreen(navController: NavHostController = rememberNavController()) {
    ScrollableList {
        ScreenTitle(stringResource(R.string.personal_dictionary), showBack = true, navController = navController)
        PersonalDictionaryEditor(disabled = false, showTitle = false)
    }
}

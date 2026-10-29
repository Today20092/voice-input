package org.futo.voiceinput.settings.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.voiceinput.R
import org.futo.voiceinput.loadCurrentModelList
import org.futo.voiceinput.settings.DataStoreItem
import org.futo.voiceinput.settings.ENABLE_MULTILINGUAL
import org.futo.voiceinput.settings.ENGLISH_MODEL_INDEX
import org.futo.voiceinput.settings.LANGUAGE_TOGGLES
import org.futo.voiceinput.settings.MANUALLY_SELECT_LANGUAGE
import org.futo.voiceinput.settings.MULTILINGUAL_MODEL_INDEX
import org.futo.voiceinput.settings.PERSONAL_DICTIONARY
import org.futo.voiceinput.settings.ScreenTitle
import org.futo.voiceinput.settings.SettingItem
import org.futo.voiceinput.settings.SettingToggleDataStoreItem
import org.futo.voiceinput.settings.SettingsRootList
import org.futo.voiceinput.settings.SettingsViewModel
import org.futo.voiceinput.settings.Tip
import org.futo.voiceinput.settings.TipKind
import org.futo.voiceinput.settings.USE_LANGUAGE_SPECIFIC_MODELS
import org.futo.voiceinput.settings.useDataStore
import org.futo.voiceinput.settings.useInvertedDataStore
import org.futo.voiceinput.shared.ENGLISH_MODELS
import org.futo.voiceinput.shared.MULTILINGUAL_MODELS
import org.futo.voiceinput.shared.types.ModelLoader
import org.futo.voiceinput.shared.types.getLanguageFromWhisperString
import org.futo.voiceinput.theme.Typography

@Composable
fun modelsSubtitle(): String? {
    val context = LocalContext.current
    val models = remember {
        context.loadCurrentModelList()
    }

    return models.joinToString { context.getString(it.name) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalDictionaryEditor(disabled: Boolean) {
    val personalDict = useDataStore(PERSONAL_DICTIONARY)
    val textFieldValue = remember { mutableStateOf(personalDict.value) }

    LaunchedEffect(textFieldValue.value) {
        personalDict.setValue(textFieldValue.value)
    }

    if(textFieldValue.value.isNotEmpty() && !textFieldValue.value.contains(',') && (textFieldValue.value.contains(' ') || textFieldValue.value.contains('\n'))) {
        Tip(stringResource(R.string.personal_dictionary_no_comma_warning), TipKind.Warning)
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
@Preview
fun ModelsScreen(
    settingsViewModel: SettingsViewModel = viewModel(),
    navController: NavHostController = rememberNavController()
) {
    val (useMultilingual, _) = useDataStore(ENABLE_MULTILINGUAL)

    val englishModelIndex = useDataStore(ENGLISH_MODEL_INDEX)
    val multilingualModelIndex = useDataStore(MULTILINGUAL_MODEL_INDEX)

    val (languages, _) = useDataStore(LANGUAGE_TOGGLES)
    val (useLanguageSpecificModels, _) = useDataStore(USE_LANGUAGE_SPECIFIC_MODELS)

    val context = LocalContext.current

    val modelEn = ENGLISH_MODELS[englishModelIndex.value]
    val model = MULTILINGUAL_MODELS[multilingualModelIndex.value]

    val personalDict = useDataStore(PERSONAL_DICTIONARY)

    SettingsRootList {
        ScreenTitle(stringResource(R.string.model_options), showBack = true, navController = navController)

        if(languages.size > 1 && model.caps.supportsLanguageDetection) {
            SettingToggleDataStoreItem(
                stringResource(R.string.autodetect_language),
                useInvertedDataStore(MANUALLY_SELECT_LANGUAGE),
                subtitle = stringResource(R.string.autodetect_language_subtitle)
            )
        }

        if((model.caps.supportsPersonalDict && languages != setOf("en")) || (modelEn.caps.supportsPersonalDict && (useLanguageSpecificModels || languages == setOf("en")))) {
            PersonalDictionaryEditor(disabled = false)
        }

        Spacer(modifier = Modifier.height(32.dp))

        val modelItems = @Composable { title: Int, setting: DataStoreItem<Int>, list: List<Pair<Int, ModelLoader>> ->
            Text(stringResource(title), style = Typography.Heading.Medium.copy(fontSize = 16.sp), modifier = Modifier
                .padding(24.dp, 4.dp).alpha(0.8f))
            list.forEach {
                SettingItem(
                    title = stringResource(it.second.name),
                    subtitle = if(setting.value == it.first && !it.second.caps.supportsPersonalDict && personalDict.value.isNotBlank()) stringResource(R.string.no_personal_dict_subtitle) else null,
                    onClick = {
                        setting.setValue(it.first)
                    },
                    disabled = false,
                    icon = { RadioButton(selected = setting.value == it.first, onClick = { setting.setValue(it.first) }) }
                ) { }
            }
        }

        if (useMultilingual) {
            val unsupportedLanguages = languages.map { getLanguageFromWhisperString(it) }.filter { it !in model.caps.supportedLanguages }
            if(unsupportedLanguages.isNotEmpty()) {
                Tip(stringResource(
                    R.string.language_settings_model_doesnt_support_languages,
                    stringResource(model.name),
                    unsupportedLanguages.joinToString(", ")
                ), TipKind.Error)
            }

            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(24.dp), modifier = Modifier.padding(16.dp)) {
                Column(Modifier.padding(0.dp, 8.dp)) {
                    Text(stringResource(R.string.multilingual_model), style = Typography.Heading.Medium,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(0.dp, 16.dp))

                    val sortedModels = MULTILINGUAL_MODELS.mapIndexed { idx, it -> idx to it }
                        .sortedBy { it.second.caps.paramCount }

                    modelItems(
                        R.string.streaming_models, multilingualModelIndex,
                        sortedModels.filter { it.second.caps.supportsStreaming })

                    modelItems(
                        R.string.nonstreaming_models, multilingualModelIndex,
                        sortedModels.filter { !it.second.caps.supportsStreaming })
                }
            }
            Spacer(Modifier.height(16.dp))
        }


        if((!useMultilingual) || (languages.contains("en") && useLanguageSpecificModels)) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(24.dp), modifier = Modifier.padding(16.dp)) {
                Column(Modifier.padding(0.dp, 8.dp)) {
                    Text(stringResource(R.string.english_model), style = Typography.Heading.Medium,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(0.dp, 16.dp))

                    val sortedModels = ENGLISH_MODELS.mapIndexed { idx, it -> idx to it }
                        .sortedBy { it.second.caps.paramCount }

                    modelItems(
                        R.string.streaming_models_recommended, englishModelIndex,
                        sortedModels.filter { it.second.caps.supportsStreaming && !it.second.caps.banishedToOther })

                    modelItems(
                        R.string.nonstreaming_models, englishModelIndex,
                        sortedModels.filter { !it.second.caps.supportsStreaming && !it.second.caps.banishedToOther })

                    modelItems(
                        R.string.other_models, englishModelIndex,
                        sortedModels.filter { it.second.caps.banishedToOther })
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        Tip(stringResource(R.string.parameter_count_tip), TipKind.Info)
    }
}

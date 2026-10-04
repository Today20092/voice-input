package org.futo.voiceinput.settings.pages

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Job
import org.futo.voiceinput.LANGUAGE_LIST
import org.futo.voiceinput.MULTILINGUAL_MODELS
import org.futo.voiceinput.R
import org.futo.voiceinput.cohere.CohereLanguage
import org.futo.voiceinput.cohere.toCohereLanguage
import org.futo.voiceinput.moonshine.toMoonshineModelVariant
import org.futo.voiceinput.nemotron.NEMOTRON_MULTILINGUAL_LANGUAGES
import org.futo.voiceinput.nemotron.toNemotronLanguageCode
import org.futo.voiceinput.nemotron.toNemotronProfile
import org.futo.voiceinput.recognition.RecognitionModel
import org.futo.voiceinput.recognition.RecognitionModelCatalog
import org.futo.voiceinput.settings.COHERE_LANGUAGE
import org.futo.voiceinput.settings.MOONSHINE_MODEL_VARIANT
import org.futo.voiceinput.settings.NEMOTRON_PROFILE
import org.futo.voiceinput.settings.NEMOTRON_MULTILINGUAL_LANGUAGE
import org.futo.voiceinput.settings.SettingRadio
import org.futo.voiceinput.settings.useDataStoreValueNullable
import org.futo.voiceinput.settings.ALLOW_UNDERTRAINED_LANGUAGES
import org.futo.voiceinput.settings.ENABLE_MULTILINGUAL
import org.futo.voiceinput.settings.LANGUAGE_TOGGLES
import org.futo.voiceinput.settings.MULTILINGUAL_MODEL_INDEX
import org.futo.voiceinput.settings.MANUALLY_SELECT_LANGUAGE
import org.futo.voiceinput.settings.USE_LANGUAGE_SPECIFIC_MODELS
import org.futo.voiceinput.settings.SettingToggleDataStore
import org.futo.voiceinput.settings.ScreenTitle
import org.futo.voiceinput.settings.SettingListLazy
import org.futo.voiceinput.settings.SettingToggleRaw
import org.futo.voiceinput.settings.SettingsViewModel
import org.futo.voiceinput.settings.Tip
import org.futo.voiceinput.settings.SPEECH_BACKEND
import org.futo.voiceinput.settings.ScrollableList
import org.futo.voiceinput.settings.SpeechBackendType
import org.futo.voiceinput.settings.toSpeechBackendType
import org.futo.voiceinput.settings.useDataStore
import org.futo.voiceinput.startModelDownloadActivity


@Composable
fun LanguageToggle(
    id: String,
    name: String,
    languages: Set<String>,
    setLanguages: (Set<String>) -> Job,
    subtitle: String?
) {
    val disabled = languages.contains(id) && languages.size == 1

    SettingToggleRaw(
        name,
        languages.contains(id),
        {
            setLanguages((languages.filter { it != id } + if (it) {
                listOf(id)
            } else {
                listOf()
            }).toSet())
        },
        subtitle = if(disabled) { stringResource(R.string.only_language_enabled) } else { subtitle },
        disabled = disabled
    )
}

@Composable
@Preview
fun LanguagesScreen(
    settingsViewModel: SettingsViewModel = viewModel(),
    navController: NavHostController = rememberNavController()
) {
    val backend = useDataStore(SPEECH_BACKEND).value.toSpeechBackendType()
    if (backend == SpeechBackendType.WhisperGGML) {
        WhisperLanguagesScreen(navController)
    } else {
        val variant = when (backend) {
            SpeechBackendType.Moonshine -> useDataStore(MOONSHINE_MODEL_VARIANT).value.toMoonshineModelVariant().id
            SpeechBackendType.Nemotron -> useDataStore(NEMOTRON_PROFILE).value.toNemotronProfile().id
            else -> null
        }
        val model = requireNotNull(RecognitionModelCatalog.modelFor(backend.id, variant))
        ScrollableList {
            ScreenTitle(stringResource(R.string.languages_title), showBack = true, navController = navController)
            ScreenTitle(model.displayName)
            RecognitionModelLanguageOptions(model)
        }
    }
}

@Composable
fun RecognitionModelLanguageOptions(model: RecognitionModel, showGuidance: Boolean = true) {
    if (showGuidance) {
        Tip(recognitionLanguageGuidance(model))
        Tip("Personal vocabulary corrections still apply after recognition. They are separate from vocabulary hints sent to a model during recognition.")
    }
    if (model.runtimeId == "cohere") {
        SettingRadio(
            title = "Recognition language",
            options = CohereLanguage.entries.map { it.id },
            optionNames = CohereLanguage.entries.map { it.displayName },
            setting = COHERE_LANGUAGE,
            normalizeValue = { it.toCohereLanguage().id }
        )
    } else if (model.runtimeId == "nemotron" && model.variantId == "multilingual") {
        SettingRadio(
            title = "Recognition language",
            options = NEMOTRON_MULTILINGUAL_LANGUAGES.map { it.id },
            optionNames = NEMOTRON_MULTILINGUAL_LANGUAGES.map { it.displayName },
            setting = NEMOTRON_MULTILINGUAL_LANGUAGE,
            normalizeValue = { it.toNemotronLanguageCode() }
        )
    }
}

@Composable
private fun WhisperLanguagesScreen(navController: NavHostController) {
    // Do not run effects against temporary defaults before saved preferences arrive.
    val multilingual = useDataStoreValueNullable(ENABLE_MULTILINGUAL.key, ENABLE_MULTILINGUAL.default) ?: return
    val multilingualModelIndex = useDataStoreValueNullable(MULTILINGUAL_MODEL_INDEX.key, MULTILINGUAL_MODEL_INDEX.default) ?: return
    val savedLanguages = useDataStoreValueNullable(LANGUAGE_TOGGLES.key, LANGUAGE_TOGGLES.default) ?: return
    val languages = savedLanguages.filter { id -> LANGUAGE_LIST.any { it.id == id } }
        .toSet().ifEmpty { setOf("en") }
    val setMultilingual = useDataStore(ENABLE_MULTILINGUAL).setValue
    val setLanguages = useDataStore(LANGUAGE_TOGGLES).setValue
    val context = LocalContext.current

    val (allowUndertrainedLanguages, _) = useDataStore(ALLOW_UNDERTRAINED_LANGUAGES)
    val needsMultilingual = languages.any { it != "en" }
    LaunchedEffect(savedLanguages, multilingual) {
        if (savedLanguages != languages) setLanguages(languages).join()
        if (multilingual != needsMultilingual) setMultilingual(needsMultilingual).join()
    }
    LaunchedEffect(multilingualModelIndex, needsMultilingual) {
        if (needsMultilingual) {
            context.startModelDownloadActivity(listOf(MULTILINGUAL_MODELS[multilingualModelIndex.coerceIn(MULTILINGUAL_MODELS.indices)]))
        }
    }

    SettingListLazy {
        item {
            ScreenTitle(stringResource(R.string.languages_title), showBack = true, navController = navController)
        }

        item {
            if (languages.size > 1 && languages.contains("en")) {
                Tip(stringResource(R.string.use_language_specific_models_info))
                SettingToggleDataStore(
                    stringResource(R.string.use_language_specific_models),
                    USE_LANGUAGE_SPECIFIC_MODELS
                )
            }
            if (languages.size > 1) {
                SettingToggleDataStore(
                    stringResource(R.string.manually_select_language),
                    MANUALLY_SELECT_LANGUAGE,
                    subtitle = stringResource(R.string.manual_language_selection_toggle_subtitle)
                )
            }
            Tip(stringResource(R.string.language_tip_1))
            Tip(stringResource(R.string.language_tip_2))
            Tip(stringResource(R.string.language_tip_3))
        }

        items(LANGUAGE_LIST.size) {
            val language = LANGUAGE_LIST[it]

            if(allowUndertrainedLanguages && it > 0) {
                if (language.trainedHourCount < 1000 && LANGUAGE_LIST[it - 1].trainedHourCount >= 1000) {
                    Spacer(modifier = Modifier.height(48.dp))
                    Tip(stringResource(R.string.language_unsupported_warning_1000))
                } else if (language.trainedHourCount < 100 && LANGUAGE_LIST[it - 1].trainedHourCount >= 100) {
                    Spacer(modifier = Modifier.height(48.dp))
                    Tip(stringResource(R.string.language_unsupported_warning_100))
                }
            }

            val subtitle = if (language.trainedHourCount < 1000) {
                stringResource(R.string.may_be_low_accuracy_x_hours, language.trainedHourCount)
            } else {
                stringResource(R.string.trained_on_x_hours, language.trainedHourCount)
            }

            // Only show languages trained with over 1000 hours for now, as anything lower
            // can be laughably bad on the tiny model
            if (allowUndertrainedLanguages || language.trainedHourCount > 1000) {
                LanguageToggle(language.id, language.name, languages, setLanguages, subtitle)
            }
        }
    }
}

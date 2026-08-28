package org.futo.voiceinput.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.futo.voiceinput.BuildConfig
import org.futo.voiceinput.moonshine.MoonshineModelVariant
import org.futo.voiceinput.nemotron.NemotronProfile
import org.futo.voiceinput.theme.presets.DevThemeYellow
import org.futo.voiceinput.theme.presets.VoiceInputTheme

suspend fun <T> Context.getSetting(key: Preferences.Key<T>, default: T): T {
    val valueFlow: Flow<T> =
        this.dataStore.data.map { preferences -> preferences[key] ?: default }.take(1)

    return valueFlow.first()
}

fun <T> Context.getSettingFlow(key: Preferences.Key<T>, default: T): Flow<T> {
    return dataStore.data.map { preferences -> preferences[key] ?: default }.take(1)
}

suspend fun <T> Context.setSetting(key: Preferences.Key<T>, value: T) {
    this.dataStore.edit { preferences ->
        preferences[key] = value
    }
}


fun <T> Context.getSettingBlocking(key: Preferences.Key<T>, default: T): T {
    val context = this

    return runBlocking {
        context.getSetting(key, default)
    }
}

fun <T> Context.setSettingBlocking(key: Preferences.Key<T>, value: T) {
    val context = this
    runBlocking {
        context.setSetting(key, value)
    }
}

fun <T> LifecycleOwner.deferGetSetting(key: Preferences.Key<T>, default: T, onObtained: (T) -> Unit): Job {
    val context = (this as Context)
    return lifecycleScope.launch {
        withContext(Dispatchers.Default) {
            val value = context.getSetting(key, default)

            withContext(Dispatchers.Main) {
                onObtained(value)
            }
        }
    }
}

fun <T> LifecycleOwner.deferSetSetting(key: Preferences.Key<T>, value: T): Job {
    val context = (this as Context)
    return lifecycleScope.launch {
        withContext(Dispatchers.Default) {
            context.setSetting(key, value)
        }
    }
}

data class SettingsKey<T>(
    val key: Preferences.Key<T>,
    val default: T
)

suspend fun <T> Context.getSetting(key: SettingsKey<T>): T {
    return getSetting(key.key, key.default)
}

fun <T> Context.getSettingFlow(key: SettingsKey<T>): Flow<T> {
    return getSettingFlow(key.key, key.default)
}

suspend fun <T> Context.setSetting(key: SettingsKey<T>, value: T) {
    return setSetting(key.key, value)
}

fun <T> LifecycleOwner.deferGetSetting(key: SettingsKey<T>, onObtained: (T) -> Unit): Job {
    return deferGetSetting(key.key, key.default, onObtained)
}

fun <T> LifecycleOwner.deferSetSetting(key: SettingsKey<T>, value: T): Job {
    return deferSetSetting(key.key, value)
}


val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
val ENABLE_SOUND = SettingsKey(booleanPreferencesKey("enable_sounds"), true)
val ENABLE_ANIMATIONS = SettingsKey(booleanPreferencesKey("enable_animations"), true)
val VERBOSE_PROGRESS = SettingsKey(booleanPreferencesKey("verbose_progress"), false)
val ENABLE_MULTILINGUAL = SettingsKey(booleanPreferencesKey("enable_multilingual"), false)
val DISALLOW_SYMBOLS = SettingsKey(booleanPreferencesKey("disallow_symbols"), true)
val ENABLE_30S_LIMIT = SettingsKey(booleanPreferencesKey("enable_30s_limit"), false)

enum class SpeechBackendType(val id: String) {
    Parakeet("parakeet"),
    ParakeetUnified("parakeet_unified"),
    Nemotron("nemotron"),
    Moonshine("moonshine"),
    WhisperGGML("whisper_ggml")
}

fun String.toSpeechBackendType(): SpeechBackendType {
    return SpeechBackendType.values().firstOrNull { it.id == this } ?: SpeechBackendType.Parakeet
}

val SPEECH_BACKEND = SettingsKey(stringPreferencesKey("speech_backend"), SpeechBackendType.Moonshine.id)
val MOONSHINE_MODEL_VARIANT =
    SettingsKey(stringPreferencesKey("moonshine_model_variant"), MoonshineModelVariant.Small.id)
val NEMOTRON_PROFILE =
    SettingsKey(stringPreferencesKey("nemotron_profile"), NemotronProfile.Balanced.id)
val NEMOTRON_MULTILINGUAL_LANGUAGE =
    SettingsKey(stringPreferencesKey("nemotron_multilingual_language"), "en")
val PARAKEET_KEEP_WARM = SettingsKey(booleanPreferencesKey("parakeet_keep_warm"), true)
val PARAKEET_KEEP_WARM_TIMEOUT_MS =
    SettingsKey(longPreferencesKey("parakeet_keep_warm_timeout_ms"), 5 * 60 * 1000L)
val PARAKEET_ENGINE_DIAGNOSTICS =
    SettingsKey(booleanPreferencesKey("parakeet_engine_diagnostics"), false)

enum class S1MiniStyling(val id: String, val label: String) {
    Casual("casual", "Casual"),
    SemiCasual("semi-casual", "Semi-casual"),
    SemiFormal("semi-formal", "Semi-formal"),
    Formal("formal", "Formal")
}

enum class S1MiniStructure(val id: String, val label: String) {
    Prose("prose", "Prose"),
    Lists("lists", "Lists")
}

enum class S1MiniContext(val id: String, val label: String) {
    General("general", "General"),
    Email("email", "Email")
}

enum class S1MiniRuntime(val id: String, val label: String) {
    Auto("auto", "Auto (recommended)"),
    Cpu("cpu", "CPU"),
    OpenCl("opencl", "OpenCL (experimental)")
}

enum class S1MiniWarmDuration(val id: String, val label: String, val timeoutMs: Long) {
    Immediate("immediate", "Immediately unload", 0L),
    TwoMinutes("2m", "2 minutes", 2 * 60 * 1000L),
    FiveMinutes("5m", "5 minutes", 5 * 60 * 1000L),
    FifteenMinutes("15m", "15 minutes", 15 * 60 * 1000L),
    ThirtyMinutes("30m", "30 minutes", 30 * 60 * 1000L),
    ProcessLifetime("process", "Until Android stops cleanup", -1L)
}

fun String.toS1MiniStyling() =
    S1MiniStyling.entries.firstOrNull { it.id == this } ?: S1MiniStyling.SemiFormal
fun String.toS1MiniStructure() =
    S1MiniStructure.entries.firstOrNull { it.id == this } ?: S1MiniStructure.Prose
fun String.toS1MiniContext() =
    S1MiniContext.entries.firstOrNull { it.id == this } ?: S1MiniContext.General
fun String.toS1MiniRuntime() =
    S1MiniRuntime.entries.firstOrNull { it.id == this } ?: S1MiniRuntime.Auto
fun String.toS1MiniWarmDuration() =
    S1MiniWarmDuration.entries.firstOrNull { it.id == this } ?: S1MiniWarmDuration.TwoMinutes

val S1_MINI_ENABLED = SettingsKey(booleanPreferencesKey("s1_mini_enabled"), false)
val S1_MINI_TRANSCRIPT_DIAGNOSTICS =
    SettingsKey(booleanPreferencesKey("s1_mini_transcript_diagnostics"), false)
val S1_MINI_STYLING =
    SettingsKey(stringPreferencesKey("s1_mini_styling"), S1MiniStyling.SemiFormal.id)
val S1_MINI_STRUCTURE =
    SettingsKey(stringPreferencesKey("s1_mini_structure"), S1MiniStructure.Prose.id)
val S1_MINI_CONTEXT =
    SettingsKey(stringPreferencesKey("s1_mini_context"), S1MiniContext.General.id)
val S1_MINI_RUNTIME =
    SettingsKey(stringPreferencesKey("s1_mini_runtime"), S1MiniRuntime.Auto.id)
val S1_MINI_WARM_DURATION =
    SettingsKey(stringPreferencesKey("s1_mini_warm_duration"), S1MiniWarmDuration.TwoMinutes.id)
val S1_MINI_AUTO_BACKEND = SettingsKey(stringPreferencesKey("s1_mini_auto_backend"), "cpu")
val S1_MINI_AUTO_THREADS = SettingsKey(intPreferencesKey("s1_mini_auto_threads"), 4)
val S1_MINI_BENCHMARK_FINGERPRINT =
    SettingsKey(stringPreferencesKey("s1_mini_benchmark_fingerprint"), "")

@Composable
fun isParakeetSelected(): Boolean {
    val (backend, _) = useDataStore(SPEECH_BACKEND)
    return backend.toSpeechBackendType() == SpeechBackendType.Parakeet
}

val ENGLISH_MODEL_INDEX = SettingsKey(intPreferencesKey("english_model_index"), 0)

val MULTILINGUAL_MODEL_INDEX = SettingsKey(intPreferencesKey("multilingual_model_index"), 1)

val LANGUAGE_TOGGLES = SettingsKey(stringSetPreferencesKey("enabled_languages"), setOf("en"))

val IS_ALREADY_PAID = SettingsKey(booleanPreferencesKey("already_paid"), false)
val IS_PAYMENT_PENDING = SettingsKey(booleanPreferencesKey("payment_pending"), false)
val HAS_SEEN_PAID_NOTICE = SettingsKey(booleanPreferencesKey("seen_paid_notice"), false)
val FORCE_SHOW_NOTICE = SettingsKey(booleanPreferencesKey("force_show_notice"), false)

// UNIX timestamp in seconds of when to next show the payment reminder
val NOTICE_REMINDER_TIME = SettingsKey(longPreferencesKey("notice_reminder_time"), 0L)

val LAST_UPDATE_CHECK_RESULT = SettingsKey(stringPreferencesKey("last_update_check_result_${BuildConfig.FLAVOR}"), "")

val EXT_LICENSE_KEY = SettingsKey(stringPreferencesKey("license_key"), "")
val EXT_PENDING_PURCHASE_ID = SettingsKey(stringPreferencesKey("purchase_id"), "")
val EXT_PENDING_PURCHASE_LAST_CHECK = SettingsKey(longPreferencesKey("purchase_status_last_check"), 0)

val IS_VAD_ENABLED = SettingsKey(booleanPreferencesKey("enable_vad"), true)
val PARAKEET_USE_VAD = SettingsKey(booleanPreferencesKey("parakeet_use_vad"), true)

enum class EndOfSpeechProfile(
    val id: String,
    val silenceFrames: Int
) {
    Fast("fast", 33),
    Balanced("balanced", 66),
    Patient("patient", 100)
}

fun String.toEndOfSpeechProfile(): EndOfSpeechProfile {
    return EndOfSpeechProfile.values().firstOrNull { it.id == this } ?: EndOfSpeechProfile.Balanced
}

val END_OF_SPEECH_PROFILE =
    SettingsKey(stringPreferencesKey("end_of_speech_profile"), EndOfSpeechProfile.Balanced.id)
val MANUAL_STOP_DRAIN_MS = SettingsKey(longPreferencesKey("manual_stop_drain_ms"), 300L)
val USE_LANGUAGE_SPECIFIC_MODELS = SettingsKey(booleanPreferencesKey("USE_LANGUAGE_SPECIFIC_MODELS"), true)

val ALLOW_UNDERTRAINED_LANGUAGES = SettingsKey(booleanPreferencesKey("allow_undertrained_languages"), false)
val MANUALLY_SELECT_LANGUAGE = SettingsKey(booleanPreferencesKey("manually_select_language"), false)

val PERSONAL_DICTIONARY = SettingsKey(stringPreferencesKey("personal_dict"), "")

val THEME_KEY = SettingsKey(
    key = stringPreferencesKey("activeThemeOption"),
    default = if(BuildConfig.FLAVOR == "dev" || BuildConfig.FLAVOR == "devSameId") { DevThemeYellow.key } else { VoiceInputTheme.key }
)

val BEAM_SEARCH = SettingsKey(key = booleanPreferencesKey("use_beam_search"), default = true)
val MODELS_MIGRATED = SettingsKey(key = booleanPreferencesKey("models_migrated_1"), default = false)
val DISMISS_MIGRATION_TIP = SettingsKey(key = booleanPreferencesKey("dismiss_migration_tip"), default = false)

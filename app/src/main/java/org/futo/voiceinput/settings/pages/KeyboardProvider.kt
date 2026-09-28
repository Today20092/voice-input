package org.futo.voiceinput.settings.pages

import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import org.futo.voiceinput.R
import org.futo.voiceinput.settings.KeyboardProviderSession
import org.futo.voiceinput.settings.ProviderMemory
import org.futo.voiceinput.settings.ProviderMode
import org.futo.voiceinput.settings.ProviderOutcome
import org.futo.voiceinput.settings.ScreenTitle
import org.futo.voiceinput.settings.ScrollableList
import org.futo.voiceinput.settings.SettingsViewModel
import org.futo.voiceinput.settings.installedFutoKeyboards
import org.futo.voiceinput.settings.keyboardSettingsIntents
import org.futo.voiceinput.settings.launchKeyboardIntent
import org.futo.voiceinput.settings.toIntent

@Composable
fun KeyboardProviderScreen(navController: NavHostController, settingsViewModel: SettingsViewModel = viewModel()) {
    val context = LocalContext.current
    val settings by settingsViewModel.uiState.collectAsState()
    val keyboards = remember(settings.numberOfResumes) { installedFutoKeyboards(context) }
    val defaultKeyboard = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        ?.substringBefore('/')
    ScrollableList {
        ScreenTitle(stringResource(R.string.keyboard_provider_title), showBack = true, navController = navController)
        KeyboardProviderSetup(keyboards, defaultKeyboard)
    }
}

@Composable
internal fun KeyboardProviderSetup(keyboards: List<String>, defaultKeyboard: String?) {
    val context = LocalContext.current
    var session by rememberSaveable {
        mutableStateOf(KeyboardProviderSession(
            defaultKeyboard?.takeIf { it in keyboards } ?: keyboards.firstOrNull().orEmpty()
        ))
    }
    // Keep registration stable even when no keyboard is installed or the activity is recreated.
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        session.pending?.let { request -> session = session.complete(request, result.resultCode) }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            session = when (event) {
                Lifecycle.Event.ON_PAUSE -> session.onPause()
                Lifecycle.Event.ON_RESUME -> session.onResume()
                else -> session
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    fun launch(mode: ProviderMode) {
        if (session.pending != null || session.keyboard.isEmpty()) return
        session = session.begin(mode)
        val request = session.pending!!
        val intents = if (mode == ProviderMode.Settings) {
            keyboardSettingsIntents(context, request.keyboard)
        } else {
            listOf(request.toIntent(context.packageName))
        }
        if (!launchKeyboardIntent(intents) {
            // Keyboard settings may be singleTask and return RESULT_CANCELED immediately.
            // Recheck on pause/resume, including Android 8 saving state before onStop.
            if (mode == ProviderMode.Settings) context.startActivity(it) else launcher.launch(it)
        }) {
            session = session.unavailable(request)
        }
    }
    // A setup/settings result schedules exactly one read-only check. A check never schedules itself.
    LaunchedEffect(session.keyboard, session.needsCheck) {
        if (session.needsCheck && session.keyboard.isNotEmpty()) launch(ProviderMode.Check)
    }

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(stringResource(R.string.keyboard_provider_intro))
        Text(
            stringResource(R.string.keyboard_provider_app, context.packageName),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        if (keyboards.isEmpty()) {
            Text(stringResource(R.string.keyboard_provider_missing))
        }
        keyboards.forEach { keyboard ->
            TextButton(
                enabled = session.pending == null,
                onClick = { session = KeyboardProviderSession(keyboard, sequence = session.sequence) }
            ) {
                Text(stringResource(
                    if (session.keyboard == keyboard) R.string.keyboard_provider_selected
                    else R.string.keyboard_provider_choose,
                    keyboard
                ))
            }
        }
        if (session.keyboard.isNotEmpty()) {
            val outcome = when (session.outcome) {
                ProviderOutcome.None -> null
                ProviderOutcome.Switched -> R.string.keyboard_provider_switched
                ProviderOutcome.Cancelled -> R.string.keyboard_provider_cancelled
                ProviderOutcome.Unknown -> R.string.keyboard_provider_unknown
                ProviderOutcome.Unavailable -> R.string.keyboard_provider_unavailable
            }
            outcome?.let { Text(stringResource(it), modifier = Modifier.padding(vertical = 8.dp)) }
            Text(stringResource(when {
                session.pending != null -> R.string.keyboard_provider_waiting
                session.remembered == ProviderMemory.ThisApp -> R.string.keyboard_provider_remembered
                session.remembered == ProviderMemory.Other -> R.string.keyboard_provider_other
                else -> R.string.keyboard_provider_unchecked
            }))
            Button(
                enabled = session.pending == null && session.keyboard in keyboards,
                onClick = { launch(ProviderMode.Switch) },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            ) { Text(stringResource(R.string.keyboard_provider_use)) }
            TextButton(enabled = session.pending == null, onClick = { launch(ProviderMode.Check) }) {
                Text(stringResource(R.string.keyboard_provider_check))
            }
            TextButton(enabled = session.pending == null, onClick = { launch(ProviderMode.Settings) }) {
                Text(stringResource(R.string.keyboard_provider_settings))
            }
        }
        Text(stringResource(R.string.keyboard_provider_manual), modifier = Modifier.padding(top = 16.dp))
    }
}

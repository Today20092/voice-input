package org.futo.voiceinput.settings

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.inputmethod.InputMethodManager

val futoKeyboardPackages = listOf("org.futo.inputmethod.latin", "org.futo.inputmethod.latin.unstable")

fun installedFutoKeyboards(context: Context): List<String> = futoKeyboardPackages.filter {
    try {
        context.packageManager.getApplicationInfo(it, 0).enabled
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}

fun ProviderRequest.toIntent(targetPackage: String): Intent {
    require(keyboard in futoKeyboardPackages)
    require(mode != ProviderMode.Settings)
    return Intent("org.futo.inputmethod.latin.action.VoiceInputSwitch")
        .setPackage(keyboard)
        .putExtra("targetPackage", targetPackage)
        .putExtra("mode", mode.argument)
}

/** Use the keyboard's declared settings activity, then its launcher on older versions. */
fun keyboardSettingsIntents(context: Context, keyboard: String): List<Intent> {
    require(keyboard in futoKeyboardPackages)
    val manager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    val settings = manager.inputMethodList.firstOrNull { it.packageName == keyboard }?.settingsActivity
    val declared = settings?.let {
        Intent(Intent.ACTION_MAIN).setComponent(ComponentName(
            keyboard, if (it.startsWith('.')) keyboard + it else it
        ))
    }
    val launcher = context.packageManager.getLaunchIntentForPackage(keyboard)?.apply {
        // Prefer returning to this screen when the keyboard's settings closes.
        flags = 0
    }
    return listOfNotNull(declared, launcher)
}

fun launchKeyboardIntent(intents: List<Intent>, launch: (Intent) -> Unit): Boolean {
    for (intent in intents) {
        try {
            launch(intent)
            return true
        } catch (_: ActivityNotFoundException) {
            // Older or removed keyboard. Try its launcher, then show manual instructions.
        } catch (_: SecurityException) {
            // An installed keyboard may not export its settings/switch activity.
        }
    }
    return false
}

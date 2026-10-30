package org.futo.voiceinput.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.futo.voiceinput.R
import org.futo.voiceinput.settings.pages.KeyboardProviderSetup
import org.futo.voiceinput.theme.UixThemeAuto
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyboardProviderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val keyboard = "org.futo.inputmethod.latin.unstable"

    @Test fun protocolAddressesBothKeyboardVariantsAndActualAppFlavor() {
        for (keyboard in futoKeyboardPackages) {
            for (target in listOf("org.futo.voiceinput.moonshine", "org.futo.voiceinput.dev", "org.futo.voiceinput.moonshine.dev")) {
                val intent = ProviderRequest(1, keyboard, ProviderMode.Check).toIntent(target)
                assertEquals("org.futo.inputmethod.latin.action.VoiceInputSwitch", intent.action)
                assertEquals(keyboard, intent.`package`)
                assertEquals(target, intent.getStringExtra("targetPackage"))
                assertEquals("check", intent.getStringExtra("mode"))
                assertEquals(0, intent.flags)
            }
        }
    }

    @Test fun setupSurvivesRecreationAndRechecksOnlyOnce() {
        val registry = RecordingRegistry()
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry) {
                UixThemeAuto {
                    ScrollableList { KeyboardProviderSetup(listOf(keyboard), keyboard) }
                }
            }
        }
        compose.runOnIdle {
            assertEquals(listOf("check"), registry.modes())
            assertEquals(compose.activity.packageName, registry.requests.last().second.getStringExtra("targetPackage"))
            registry.reply(67)
        }
        compose.onNodeWithText(compose.activity.getString(R.string.keyboard_provider_remembered))
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.keyboard_provider_use)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("check", "switch"), registry.modes()) }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle {
            assertEquals(2, registry.requests.size)
            registry.reply(66)
        }
        compose.runOnIdle {
            assertEquals(listOf("check", "switch", "check"), registry.modes())
            registry.reply(67)
        }
        compose.onNodeWithText(compose.activity.getString(R.string.keyboard_provider_switched))
            .performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(3, registry.requests.size) }
    }

    @Test fun missingActivityShowsManualInstructionsWithoutRetryLoop() {
        val registry = RecordingRegistry(unavailable = true)
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry) {
                UixThemeAuto {
                    ScrollableList { KeyboardProviderSetup(listOf(keyboard), keyboard) }
                }
            }
        }
        compose.onNodeWithText(compose.activity.getString(R.string.keyboard_provider_unavailable))
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.keyboard_provider_manual))
            .performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf("check"), registry.modes()) }
    }

    @Test fun cancelledAndUnknownSetupRemainDistinctAfterTheFollowUpCheck() {
        val registry = RecordingRegistry()
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry) {
                UixThemeAuto {
                    ScrollableList { KeyboardProviderSetup(listOf(keyboard), keyboard) }
                }
            }
        }
        compose.runOnIdle { registry.reply(68) }
        for ((code, message) in listOf(
            0 to R.string.keyboard_provider_cancelled,
            -1 to R.string.keyboard_provider_unknown
        )) {
            compose.onNodeWithText(compose.activity.getString(R.string.keyboard_provider_use))
                .performScrollTo().performClick()
            compose.runOnIdle { registry.reply(code) }
            compose.runOnIdle {
                assertEquals("check", registry.modes().last())
                registry.reply(67)
            }
            compose.onNodeWithText(compose.activity.getString(message)).performScrollTo().assertIsDisplayed()
        }
        compose.runOnIdle { assertEquals(listOf("check", "switch", "check", "switch", "check"), registry.modes()) }
    }

    @Test fun settingsLaunchFallsBackAndHandlesUnexportedActivities() {
        val intents = listOf(Intent("declared-settings"), Intent("launcher"))
        val attempted = mutableListOf<String>()
        assertTrue(launchKeyboardIntent(intents) {
            attempted += it.action!!
            if (it.action == "declared-settings") throw ActivityNotFoundException()
        })
        assertEquals(listOf("declared-settings", "launcher"), attempted)
        assertFalse(launchKeyboardIntent(intents) { throw SecurityException() })
        assertFalse(launchKeyboardIntent(emptyList()) { fail("No activity exists") })
    }

    private inner class RecordingRegistry(private val unavailable: Boolean = false) :
        ActivityResultRegistry(), ActivityResultRegistryOwner {
        override val activityResultRegistry: ActivityResultRegistry get() = this
        val requests = mutableListOf<Pair<Int, Intent>>()
        override fun <I, O> onLaunch(
            requestCode: Int, contract: ActivityResultContract<I, O>, input: I,
            options: ActivityOptionsCompat?
        ) {
            requests += requestCode to contract.createIntent(compose.activity, input)
            if (unavailable) throw ActivityNotFoundException()
        }
        fun modes() = requests.map { it.second.getStringExtra("mode") }
        fun reply(code: Int) { dispatchResult(requests.last().first, code, null) }
    }
}

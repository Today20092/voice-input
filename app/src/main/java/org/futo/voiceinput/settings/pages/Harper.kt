package org.futo.voiceinput.settings.pages

import androidx.compose.runtime.Composable
import org.futo.voiceinput.settings.HARPER_ENABLED
import org.futo.voiceinput.settings.HARPER_EXPLICIT_ENGLISH
import org.futo.voiceinput.settings.SettingToggleDataStore
import org.futo.voiceinput.settings.Tip

@Composable
fun HarperOptions() {
    SettingsSeparator("Harper English cleanup • beta")
    SettingToggleDataStore(
        title = "Enable Harper cleanup",
        subtitle = "Fast, offline rules. No model download. Works without S1-mini; runs after it if both are enabled.",
        setting = HARPER_ENABLED
    )
    Tip("Fixes extra spaces, spacing around commas and sentence punctuation, lowercase ‘i’, " +
        "and sentence capitalization when a sentence is recognized. Keeps spelling, word choice, " +
        "and repeated words. It does not add every missing comma or rewrite grammar like S1-mini.")
    SettingToggleDataStore(
        title = "My automatic-language dictation is English",
        subtitle = "Enable only when you dictate English with Orukeet, Parakeet, or another model that cannot confirm the language. " +
            "Known non-English results are still skipped.",
        setting = HARPER_EXPLICIT_ENGLISH
    )
    Tip("Harper is off by default for this beta. When enabled, it requires established English or the declaration above. " +
        "Mixed text containing non-Latin letters is skipped. Turn Harper off to compare results. " +
        "Personal vocabulary corrections are applied last.")
}

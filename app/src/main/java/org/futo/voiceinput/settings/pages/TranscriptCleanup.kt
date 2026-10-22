package org.futo.voiceinput.settings.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.voiceinput.settings.HARPER_ENABLED
import org.futo.voiceinput.settings.S1_MINI_ENABLED
import org.futo.voiceinput.settings.SPEECH_BACKEND
import org.futo.voiceinput.settings.ScreenTitle
import org.futo.voiceinput.settings.ScrollableList
import org.futo.voiceinput.settings.SettingsKey
import org.futo.voiceinput.settings.useDataStore

@Composable
fun TranscriptCleanupScreen(navController: NavHostController = rememberNavController()) {
    val basicEnabled = useDataStore(HARPER_ENABLED).value
    val rewriteEnabled = useDataStore(S1_MINI_ENABLED).value && useDataStore(SPEECH_BACKEND).value != "asr4all"
    ScrollableList {
        ScreenTitle("Transcript Cleanup", showBack = true, navController = navController)
        Column(
            Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CleanupText("Choose how to clean up your text after you stop recording. Both options work offline.")
            if (basicEnabled && rewriteEnabled) {
                CleanupText("For eligible English text, AI rewrite runs first, then Basic text cleanup. Personal vocabulary corrections run last.")
            }
            HarperOptions()
            S1MiniOptions(showTitle = false)
        }
    }
}

@Composable
internal fun CleanupCard(content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (colors.surface == colors.background) lerp(colors.surface, colors.onSurface, 0.04f) else colors.surface
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
internal fun CleanupText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun CleanupToggle(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
internal fun CleanupDetails(title: String, content: @Composable () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column {
        TextButton(
            onClick = { expanded = !expanded },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.fillMaxWidth().semantics {
                stateDescription = if (expanded) "Expanded" else "Collapsed"
            }
        ) {
            Text(title, Modifier.weight(1f))
            Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null)
        }
        if (expanded) content()
    }
}

@Composable
internal fun CleanupHeading(title: String) {
    Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
}

@Composable
internal fun CleanupRadio(title: String, options: List<Pair<String, String>>, setting: SettingsKey<String>) {
    val value = useDataStore(setting)
    Column(Modifier.selectableGroup()) {
        CleanupHeading(title)
        options.forEach { (id, label) ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                    selected = value.value == id, role = Role.RadioButton,
                    onClick = { value.setValue(id) }
                ).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RadioButton(selected = value.value == id, onClick = null)
                Text(label, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

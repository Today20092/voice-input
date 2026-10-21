package org.futo.voiceinput.benchmark

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

@Composable
internal fun ComparisonCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
internal fun ComparisonHeading(text: String) {
    Text(text, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
}

@Composable
internal fun ComparisonText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun ComparisonDetails(title: String, content: @Composable () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column {
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth().semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
        ) {
            Text(title, Modifier.weight(1f))
            Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null)
        }
        if (expanded) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
internal fun ComparisonModelFamily(
    title: String, models: List<BenchmarkModel>, selected: Set<String>, installed: Set<String>,
    language: String, enabled: Boolean, onToggle: (String, Boolean) -> Unit
) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    val selectedCount = models.count { it.id in selected }
    Column {
        HorizontalDivider()
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth().semantics {
                heading()
                stateDescription = if (expanded) "Expanded" else "Collapsed"
            }
        ) {
            Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text("$selectedCount selected · ${models.count { it.id in installed }} installed · ${models.size} ${if (models.size == 1) "model" else "models"}",
                    Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null)
        }
        if (expanded) models.forEach { candidate ->
            val checked = candidate.id in selected
            val supported = candidate.supportsLanguage(language)
            Surface(
                color = if (checked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(
                        value = checked, enabled = enabled && supported, role = Role.Checkbox,
                        onValueChange = { onToggle(candidate.id, it) }
                    ).padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Checkbox(checked, onCheckedChange = null, enabled = enabled && supported)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(candidate.name, style = MaterialTheme.typography.bodyLarge)
                        Text(if (supported) {
                            if (candidate.id in installed) "Installed · %.1f MB".format(candidate.storageBytes / 1_000_000.0)
                            else "Download %.1f MB".format(candidate.transferBytes / 1_000_000.0)
                        }
                            else "Unavailable for this recording language", style = MaterialTheme.typography.bodySmall)
                        Text(candidate.languages, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

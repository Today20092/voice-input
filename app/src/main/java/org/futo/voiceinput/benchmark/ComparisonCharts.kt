package org.futo.voiceinput.benchmark

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ComparisonOverview(rows: List<ComparisonResultSummary>, clipCount: Int, startup: Boolean, onStartup: (Boolean) -> Unit) {
    var metric by rememberSaveable { mutableStateOf(ResultMetric.SPEED) }
    ComparisonCard {
        ComparisonHeading("At a glance")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ResultMetric.entries.forEach { choice ->
                FilterChip(metric == choice, { metric = choice }, label = { Text(choice.label) })
            }
        }
        if (metric == ResultMetric.SPEED) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!startup, { onStartup(false) }, label = { Text("Already loaded") })
            FilterChip(startup, { onStartup(true) }, label = { Text("Load + first run") })
        }
        val title = when (metric) {
            ResultMetric.SPEED -> if (startup) "Load + first recording" else "Transcription time"
            ResultMetric.ACCURACY -> "Word error rate"
            ResultMetric.MEMORY -> "Sampled peak app RAM"
        }
        ComparisonHeading(title)
        ComparisonText(when (metric) {
            ResultMetric.SPEED -> if (startup) "Loading and first decode of recording 1. Lower is faster."
                else "Sum of each recording's already-loaded median across $clipCount ${if (clipCount == 1) "recording" else "recordings"}. Lower is faster."
            ResultMetric.ACCURACY -> "Word errors across all recordings, weighted by reference word count. Lower is better; 0% means no word errors."
            ResultMetric.MEMORY -> "Sampled peak app-process PSS, including app overhead. Lower is less RAM; this is not model allocation size."
        })
        val sorted = rows.sortedWith(compareBy<ComparisonResultSummary> { it.value(metric, startup) == null }.thenBy { it.value(metric, startup) })
        val maximum = sorted.mapNotNull { it.value(metric, startup) }.maxOrNull() ?: 0.0
        val suffix = if (metric == ResultMetric.ACCURACY) "%" else " ${metric.unit}"
        if (sorted.none { it.value(metric, startup) != null }) ComparisonText("No comparable measurements available.")
        sorted.forEach { row ->
            val value = row.value(metric, startup)
            Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(row.name, Modifier.weight(0.64f), style = MaterialTheme.typography.bodyMedium)
                    Text(if (value == null) "—" else "${resultNumber(value)}$suffix",
                        Modifier.weight(0.36f), style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.End)
                }
                if (value != null) Box(Modifier.fillMaxWidth().height(10.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(Modifier.fillMaxWidth(if (maximum > 0) (value / maximum).toFloat().coerceIn(0f, 1f) else 0f)
                        .fillMaxHeight().background(MaterialTheme.colorScheme.secondary))
                } else Text(when {
                    row.result.status != "completed" -> "${row.result.status.replaceFirstChar { it.uppercase() }} · excluded from ranking"
                    row.result.loadsEveryPass && metric != ResultMetric.ACCURACY -> if (metric == ResultMetric.MEMORY) "Worker RAM is not measured" else "Reloads each pass; use Load + first run"
                    metric == ResultMetric.ACCURACY -> "Verified references and scores needed for every recording"
                    else -> "Measurement unavailable or incomplete"
                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (sorted.any { it.value(metric, startup) != null }) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0$suffix", style = MaterialTheme.typography.labelSmall)
            Text("${resultNumber(maximum)}$suffix", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
internal fun ComparisonMetricTable(rows: List<Pair<String, String>>, tag: String) {
    val largeText = LocalDensity.current.fontScale > 1.3f
    Column(Modifier.fillMaxWidth().testTag(tag)) {
        rows.forEach { (label, value) ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp).semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(label, Modifier.weight(if (largeText) 0.56f else 0.64f), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, Modifier.weight(if (largeText) 0.44f else 0.36f), style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace, textAlign = TextAlign.End)
            }
        }
    }
}

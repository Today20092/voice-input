package org.futo.voiceinput.benchmark

import java.util.Locale

internal enum class ResultMetric(val label: String, val unit: String) {
    SPEED("Speed", "ms"), ACCURACY("Accuracy", "%"), MEMORY("RAM", "MiB")
}

internal enum class ResultOrder(val label: String) {
    RUN("Run order"), SPEED("Fastest"), SIZE("Smallest model"), ACCURACY("Fewest errors"), MEMORY("Least RAM")
}

internal data class ComparisonResultSummary(
    val result: BenchmarkCaseResult, val name: String, val modelBytes: Long?,
    val readyMs: Double?, val startupMs: Double?, val wordErrorPercent: Double?, val ramMiB: Double?
) {
    fun value(metric: ResultMetric, startup: Boolean): Double? = when (metric) {
        ResultMetric.SPEED -> if (startup) startupMs else readyMs
        ResultMetric.ACCURACY -> wordErrorPercent
        ResultMetric.MEMORY -> ramMiB
    }
}

internal val BenchmarkCaseResult.loadsEveryPass: Boolean
    get() = modelId == "whistle" || identity["runtime"] == "whistle"

internal fun comparisonSummaries(report: BenchmarkReport, models: List<BenchmarkModel>): List<ComparisonResultSummary> =
    report.cases.map { result ->
        val model = models.firstOrNull { it.id == result.modelId }
        val complete = result.status == "completed" && report.clips.isNotEmpty()
        val repeated = report.clips.map { clip ->
            result.observations.filter { it.clipId == clip.id && it.phase == "warm" && it.elapsedNs >= 0 }.map { it.elapsedNs }
        }
        val ready = if (complete && !result.loadsEveryPass && repeated.all { it.size == report.warmRepetitions && it.isNotEmpty() })
            repeated.sumOf { it.medianNs().toDouble() / 1e6 } else null
        val first = result.observations.firstOrNull { it.clipId == report.clips.firstOrNull()?.id && it.phase == "first" && it.elapsedNs >= 0 }
        val startup = if (complete && first != null) result.initializationNs?.takeIf { it >= 0 }?.let { it / 1e6 + first.elapsedNs / 1e6 } else null
        val scores = report.clips.map { clip ->
            result.observations.firstOrNull { it.clipId == clip.id }?.accuracy?.words?.takeIf { clip.referenceVerified && it.denominator > 0 }
        }
        val errors = if (complete && scores.all { it != null }) {
            val measured = scores.filterNotNull()
            100.0 * measured.sumOf { it.substitutions.toLong() + it.deletions + it.insertions } / measured.sumOf { it.denominator.toLong() }
        } else null
        ComparisonResultSummary(result, model?.name ?: result.identity["name"] ?: result.modelId, model?.storageBytes,
            ready, startup, errors,
            if (complete && !result.loadsEveryPass) result.sampledPeakPssKb?.takeIf { it >= 0 }?.div(1024.0) else null)
    }

internal fun List<ComparisonResultSummary>.ordered(order: ResultOrder, startup: Boolean): List<ComparisonResultSummary> {
    if (order == ResultOrder.RUN) return this
    fun value(row: ComparisonResultSummary): Double? = when (order) {
        ResultOrder.SPEED -> row.value(ResultMetric.SPEED, startup)
        ResultOrder.SIZE -> row.modelBytes?.toDouble()
        ResultOrder.ACCURACY -> row.wordErrorPercent
        ResultOrder.MEMORY -> row.ramMiB
        ResultOrder.RUN -> null
    }
    return sortedWith(compareBy<ComparisonResultSummary> { value(it) == null }.thenBy { value(it) })
}

internal fun resultNumber(value: Double?): String = value?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: "—"

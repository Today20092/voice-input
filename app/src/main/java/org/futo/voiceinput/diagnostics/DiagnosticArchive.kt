package org.futo.voiceinput.diagnostics

import kotlinx.serialization.encodeToString
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DiagnosticArchive {
    const val CACHE_BYTES = 2L * 1024 * 1024

    fun purgePrepared(directory: File, nowMs: Long = System.currentTimeMillis()) {
        val files = directory.listFiles().orEmpty().filter { it.isFile }.sortedByDescending { it.name }
        var bytes = 0L
        var count = 0
        for (file in files) {
            // lastModified is the oldest evidence date, set at creation below, not the ZIP creation time.
            if (file.lastModified() < nowMs - DiagnosticStore.RETENTION_MS ||
                count >= 3 || bytes + file.length() > CACHE_BYTES) file.delete()
            else { bytes += file.length(); count++ }
        }
    }

    fun write(
        output: File,
        snapshot: DiagnosticSnapshot,
        environmentJson: String,
        settingsJson: String,
        happened: String,
        expected: String,
        s1Reports: Map<String, String>,
        droppedEvents: Long
    ) {
        var retained = snapshot
        var omitted = 0L
        do {
            writeArchive(output, retained, environmentJson, settingsJson, happened, expected, s1Reports, droppedEvents, omitted)
            if (output.length() <= CACHE_BYTES) break
            check(retained.records.isNotEmpty()) { "Diagnostic metadata exceeds export budget" }
            val remove = maxOf(1, retained.records.size / 4)
            omitted += remove
            retained = retained.copy(records = retained.records.drop(remove))
        } while (true)
        val s1Times = s1Reports.values.flatMap { contents -> contents.lineSequence().mapNotNull { line ->
            runCatching {
                val obj = DiagnosticStore.json.parseToJsonElement(line) as? kotlinx.serialization.json.JsonObject
                listOf("capturedAtEpochMs", "recordedAtEpochMs").mapNotNull { key ->
                    (obj?.get(key) as? kotlinx.serialization.json.JsonPrimitive)?.content?.toLongOrNull()
                }.minOrNull()
            }.getOrNull()
        }.toList() }
        val oldest = (retained.records.map { it.timestampMs } + s1Times).minOrNull() ?: System.currentTimeMillis()
        check(output.setLastModified(oldest)) { "Unable to set report expiry" }
    }

    private fun writeArchive(output: File, snapshot: DiagnosticSnapshot, environmentJson: String,
        settingsJson: String, happened: String, expected: String, s1Reports: Map<String, String>, droppedEvents: Long,
        omitted: Long) {
        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            fun entry(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("README.txt", "Voice Input standard bug report (schema 2; older records may use schema 1).\n" +
                "Technical records exclude audio, transcripts, vocabulary, clipboard/surrounding text, " +
                "receiving-app names, raw Logcat, and exception messages.\n" +
                "notes.txt contains the reporter's own text, if supplied. Review it before posting publicly.\n" +
                "Crash evidence is best effort. An unfinished session does not prove a crash or hang.\n" +
                "DELIVERY_ACCEPTED means Android accepted the handoff, not that text was displayed.\n" +
                "Durations use a monotonic clock; timestampMs is wall-clock time. Memory is sampled, not a peak guarantee.\n" +
                "S1-mini reports are included when available, with free-form native messages omitted.\n" +
                "collectorId + sequence orders records within one collector lifetime; wall-clock time may change.\n" +
                "collection-modes.json retains at most 32 transitions from seven days. A detailed deadline marks automatic expiry even without a later event.\n" +
                "Unavailable metrics are omitted, never zero. Missing configuration/reportId may mean older records, bypass or failed collection.\n" +
                "Historical writer drops before process death are unavailable; sequence gaps may also reflect rejected records or retention.\n" +
                "DURATION_MS/ELAPSED_MS/AUDIO_MS/PROCESS_CPU_MS are milliseconds; timestampMs/EXIT_TIMESTAMP/mode deadlines are epoch milliseconds.\n" +
                "PSS_KB/RSS_KB use KiB; JAVA_BYTES/NATIVE_BYTES/AVAILABLE_BYTES/BYTES/EXPECTED_BYTES use bytes. EXPECTED_BYTES is planned asset size, not transferred bytes.\n" +
                "CAPTURED_SAMPLES/DRAIN_SAMPLES count real PCM16 samples at 16000 Hz, excluding synthetic final silence. DRAIN_REQUESTED_MS is the requested drain bound. VAD frames are evaluated 480-sample (30 ms) frames, not all captured audio; paused/skipped VAD has fewer frames. Speech decisions are not transcript evidence.\n" +
                "STOP_REASON: 0 manual, 1 VAD, 2 duration limit, 3 cancel, 4 capture failure, -1 unknown. NO_SPEECH means empty recognition, not proof the microphone captured silence.\n" +
                "OPERATION: 0 unknown history write, 1 model activation, 2 model deletion, 3 model invalidation, 10 history open/purge, 11 history append, 12 history finish, 13 history transcript save, 14 history purge, 15 history retranscription.\n" +
                "RESOURCE_UNAVAILABLE=1 means resource collection failed; unsupported Android fields are absent. APPLIED/LOW_MEMORY/CATCHING_UP are 0/1.\n" +
                "THERMAL_STATUS and EXIT_REASON/EXIT_STATUS follow Android PowerManager/ApplicationExitInfo codes; MICROPHONE_* codes follow app microphone routing categories.\n" +
                "S1 reportId is a random local join key, unrelated to transcript text or recording IDs.\n")
            entry("summary.txt", summary(snapshot, droppedEvents, omitted))
            entry("collection-modes.json", DiagnosticStore.json.encodeToString(snapshot.modes))
            entry("environment.json", environmentJson)
            entry("settings.json", settingsJson)
            entry("events.jsonl", snapshot.records.joinToString("\n", postfix = "\n") {
                DiagnosticStore.json.encodeToString(it)
            })
            if (happened.isNotBlank() || expected.isNotBlank()) {
                entry("notes.txt", "What happened:\n${happened.take(4000)}\n\nExpected behavior:\n${expected.take(4000)}\n")
            }
            // An explicit allowlist prevents transcript archives or arbitrary app files joining this ZIP.
            for (name in listOf("runs.jsonl", "benchmark.json")) {
                s1Reports[name]?.let { contents ->
                    val safe = if (name == "runs.jsonl") {
                        contents.lineSequence().mapNotNull(StandardS1Reports::project).joinToString("\n")
                    } else StandardS1Reports.project(contents)
                    safe?.let { entry("s1/$name", it) }
                }
            }
        }
    }

    private fun summary(snapshot: DiagnosticSnapshot, dropped: Long, omitted: Long) = buildString {
        appendLine("Voice Input diagnostic summary")
        appendLine("Records: ${snapshot.records.size}")
        appendLine("Unreadable records skipped: ${snapshot.unreadableRecords}")
        appendLine("Unreadable segments skipped: ${snapshot.unreadableSegments}")
        appendLine("Writer drops (current process): $dropped")
        appendLine("Records rejected by size limit: ${snapshot.rejectedRecords}")
        appendLine("Records omitted for export budget: $omitted")
        appendLine("Diagnostic collection unavailable: ${snapshot.collectionUnavailable}")
        appendLine("Retained collection-mode transitions: ${snapshot.modes.size} (maximum 32; earlier transitions may be unavailable)")
        if (snapshot.records.none { it.sessionId != null }) appendLine("No session evidence available; collection, retention or interruption may explain absence.")
        appendLine("No dictated text or audio is included.")
        appendLine()
        appendLine("Event counts:")
        snapshot.records.groupingBy { it.event }.eachCount().forEach { (event, count) ->
            appendLine("  $event: $count")
        }
        val sessions = snapshot.records.filter { it.sessionId != null }.groupBy { it.sessionId }
        val terminal = setOf(DiagnosticEvent.SESSION_CANCELLED, DiagnosticEvent.SESSION_RESET,
            DiagnosticEvent.SESSION_FAILED, DiagnosticEvent.DELIVERY_ACCEPTED, DiagnosticEvent.DELIVERY_REJECTED,
            DiagnosticEvent.DOWNLOAD_FINISHED, DiagnosticEvent.DOWNLOAD_CANCELLED,
            DiagnosticEvent.RETRANSCRIPTION_FINISHED)
        appendLine()
        appendLine("Recent sessions (up to 25; incomplete history can reflect retention or interruption):")
        sessions.entries.toList().takeLast(25).forEach { (id, records) ->
            appendLine("  $id model=${records.firstNotNullOfOrNull { it.model } ?: "unknown"} " +
                "source=${if (records.any { it.metrics[DiagnosticMetric.RETRANSCRIPTION] == 1L }) "history" else "other"} " +
                "last=${records.last().event} terminal=${records.any { it.event in terminal }}")
            records.filter { it.metrics.containsKey(DiagnosticMetric.DURATION_MS) }.forEach {
                appendLine("    ${it.event}: ${it.metrics[DiagnosticMetric.DURATION_MS]} ms")
                if (it.event == DiagnosticEvent.HARPER_FINISHED) {
                    val outcome = when (it.metrics[DiagnosticMetric.HARPER_OUTCOME]) {
                        0L -> "applied"
                        1L -> "disabled"
                        2L -> "language bypass"
                        3L -> "input too long"
                        4L -> "unavailable; original text retained"
                        5L -> "unchanged"
                        else -> "unknown"
                    }
                    appendLine("      Harper: $outcome; edits=${it.metrics[DiagnosticMetric.HARPER_EDITS] ?: 0}")
                }
            }
            records.filter { it.configuration != null }.forEach {
                appendLine("    ${it.event} effective configuration: ${it.configuration}")
            }
            if (records.none { it.configuration != null }) appendLine("    Effective configuration unavailable")
            records.mapNotNull { it.reportId }.distinct().forEach { appendLine("    S1 reportId: $it") }
            records.filter { it.event == DiagnosticEvent.CAPTURE_SUMMARY }.forEach {
                appendLine("    Captured samples: ${it.metrics[DiagnosticMetric.CAPTURED_SAMPLES] ?: "unavailable"}; " +
                    "VAD speech frames: ${it.metrics[DiagnosticMetric.VAD_SPEECH_FRAMES] ?: "unavailable"}; " +
                    "Drained samples: ${it.metrics[DiagnosticMetric.DRAIN_SAMPLES] ?: "unavailable"}; " +
                    "stop reason: ${it.metrics[DiagnosticMetric.STOP_REASON] ?: "unavailable"}")
            }
            val stop = records.firstOrNull { it.event == DiagnosticEvent.RECORDING_STOPPED }
                ?.metrics?.get(DiagnosticMetric.ELAPSED_MS)
            val delivered = records.lastOrNull { it.event == DiagnosticEvent.DELIVERY_ACCEPTED }
                ?.metrics?.get(DiagnosticMetric.ELAPSED_MS)
            if (stop != null && delivered != null) appendLine("    Stop to delivery: ${delivered - stop} ms")
        }
        appendLine()
        appendLine("Recent failures and exits (up to 20):")
        snapshot.records.filter { it.failure != null || it.event.name.endsWith("FAILED") ||
            it.event == DiagnosticEvent.PROCESS_EXIT || it.event == DiagnosticEvent.DELIVERY_REJECTED
        }.takeLast(20).forEach {
            appendLine("  ${it.timestampMs} ${it.event} ${it.failure?.kind.orEmpty()} ${it.metrics}")
            it.failure?.frames?.forEach { frame -> appendLine("    $frame") }
        }
    }
}

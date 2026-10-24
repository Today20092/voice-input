package org.futo.voiceinput.s1

import java.io.File
import org.futo.voiceinput.diagnostics.StandardS1Reports
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object S1MiniDiagnosticArchive {
    fun write(
        output: File,
        environmentJson: String,
        runsJsonl: String,
        benchmarkJson: String?,
        transcriptsJsonl: String?,
        unreadableTranscriptCaptures: Int
    ) {
        val safeRuns = runsJsonl.lineSequence().mapNotNull(StandardS1Reports::project).joinToString("\n")
        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            fun entry(name: String, contents: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(contents.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            entry(
                "README.txt",
                if (transcriptsJsonl != null) {
                    "WARNING: THIS ARCHIVE CONTAINS DICTATED TRANSCRIPTS.\n" +
                        "S1-mini by Superwhisper diagnostic bundle. Transcript included: true.\n" +
                        "Unreadable transcript captures skipped: $unreadableTranscriptCaptures.\n"
                } else {
                    "S1-mini by Superwhisper diagnostic bundle.\n" +
                        "Contains device/model/performance metadata only. Transcript included: false.\n"
                }
            )
            entry("environment.json", environmentJson)
            entry("runs.jsonl", safeRuns)
            benchmarkJson?.let(StandardS1Reports::project)?.let { entry("benchmark.json", it) }
            entry("report.txt", "S1-mini diagnostic report\nTranscript included: false\n" + safeRuns.lineSequence().lastOrNull().orEmpty())
            if (transcriptsJsonl != null) {
                entry("transcripts.jsonl", transcriptsJsonl)
                entry(
                    "omissions.json",
                    "{\"unreadableTranscriptCaptures\":$unreadableTranscriptCaptures}\n"
                )
            }
        }
    }
}

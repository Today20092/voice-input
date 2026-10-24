package org.futo.voiceinput.s1

import java.io.File
import java.io.IOException
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
        unreadableTranscriptCaptures: Int,
        transcriptExpiresAtEpochMs: Long? = null
    ) {
        val safeRuns = runsJsonl.lineSequence().mapNotNull(StandardS1Reports::project).joinToString("\n")
        try {
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
                        "Unreadable transcript captures skipped: $unreadableTranscriptCaptures.\n" +
                        "Schema 1 delivery is unverified: its finalDelivered field was prepared before handoff.\n" +
                        "Schema 2 accepted delivery means Android accepted the handoff, not that another app displayed it.\n"
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
        if (transcriptsJsonl != null) {
            val expiresAt = transcriptExpiresAtEpochMs ?: System.currentTimeMillis()
            if (!output.setLastModified(expiresAt)) {
                output.delete()
                throw IOException("Unable to set transcript export expiration")
            }
        }
        } catch (error: Throwable) {
            runCatching { output.delete() }
            throw error
        }
    }

    @Synchronized
    fun purgeExpired(directory: File, nowEpochMs: Long) {
        transcriptExports(directory).filter { it.lastModified() <= nowEpochMs }.forEach(::delete)
    }

    @Synchronized
    fun invalidateTranscripts(directory: File) {
        transcriptExports(directory).forEach(::delete)
    }

    private fun transcriptExports(directory: File): List<File> {
        if (!directory.exists()) return emptyList()
        return directory.listFiles { file -> file.isFile && "WITH-TRANSCRIPTS" in file.name }?.toList()
            ?: throw IOException("Unable to read transcript export cache")
    }

    private fun delete(file: File) {
        if (!file.delete() && file.exists()) throw IOException("Unable to delete transcript export")
    }
}

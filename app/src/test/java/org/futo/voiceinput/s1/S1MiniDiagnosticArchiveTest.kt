package org.futo.voiceinput.s1

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.util.zip.ZipFile

class S1MiniDiagnosticArchiveTest {
    @Test
    fun ordinaryArchiveProjectsRawNativeAndExceptionMetadataBeforeSharing() {
        val output = Files.createTempFile("s1-sensitive-metadata", ".zip").toFile()
        val secret = "Avery Support says cobalt orchard 472"
        try {
            S1MiniDiagnosticArchive.write(
                output, "{}",
                """{"reportId":"12345678-1234-1234-1234-123456789abc","totalMs":900,"backendLoaderErrors":["$secret"],"nativeMetricsJson":"[{\"decodeMs\":123,\"error\":\"$secret\"}]"}""",
                """{"failures":{"cpu/4":"$secret"}}""", null, 0
            )
            ZipFile(output).use { zip ->
                val allText = zip.entries().asSequence().joinToString("\n") {
                    zip.getInputStream(it).bufferedReader().readText()
                }
                assertFalse(secret in allText)
                assertTrue("900" in allText)
                assertTrue("12345678-1234-1234-1234-123456789abc" in allText)
                assertTrue("123" in allText)
            }
        } finally {
            output.delete()
        }
    }

    @Test
    fun standardArchiveNeverContainsTranscriptEntryOrText() {
        val output = Files.createTempFile("s1-standard", ".zip").toFile()
        try {
            S1MiniDiagnosticArchive.write(
                output = output,
                environmentJson = "{\"transcriptIncluded\":false}",
                runsJsonl = "{\"outcome\":\"success\"}\n",
                benchmarkJson = null,
                transcriptsJsonl = null,
                unreadableTranscriptCaptures = 0
            )

            ZipFile(output).use { zip ->
                assertFalse(zip.entries().asSequence().any { it.name == "transcripts.jsonl" })
                val allText = zip.entries().asSequence().joinToString("\n") { entry ->
                    zip.getInputStream(entry).bufferedReader().readText()
                }
                assertFalse("private words" in allText)
                assertTrue("Transcript included: false" in allText)
            }
        } finally {
            output.delete()
        }
    }

    @Test
    fun transcriptArchiveIsExplicitlyWarnedAndReportsUnreadableCaptures() {
        val output = Files.createTempFile("s1-sensitive", ".zip").toFile()
        try {
            S1MiniDiagnosticArchive.write(
                output = output,
                environmentJson = "{\"transcriptIncluded\":true}",
                runsJsonl = "",
                benchmarkJson = null,
                transcriptsJsonl = "{\"raw\":\"private words\"}\n",
                unreadableTranscriptCaptures = 2
            )

            ZipFile(output).use { zip ->
                assertTrue(zip.getEntry("transcripts.jsonl") != null)
                val readme = zip.getInputStream(zip.getEntry("README.txt")).bufferedReader().readText()
                assertTrue("WARNING" in readme)
                assertTrue("Unreadable transcript captures skipped: 2" in readme)
            }
        } finally {
            output.delete()
        }
    }

    @Test
    fun allUnreadableTranscriptArchiveStillContainsOmissionReport() {
        val output = Files.createTempFile("s1-all-unreadable", ".zip").toFile()
        try {
            S1MiniDiagnosticArchive.write(
                output = output,
                environmentJson = "{\"transcriptIncluded\":true}",
                runsJsonl = "",
                benchmarkJson = null,
                transcriptsJsonl = "",
                unreadableTranscriptCaptures = 2
            )

            ZipFile(output).use { zip ->
                assertTrue(zip.getEntry("transcripts.jsonl") != null)
                val omissions = zip.getInputStream(zip.getEntry("omissions.json"))
                    .bufferedReader().readText()
                assertTrue("\"unreadableTranscriptCaptures\":2" in omissions)
            }
        } finally {
            output.delete()
        }
    }
}

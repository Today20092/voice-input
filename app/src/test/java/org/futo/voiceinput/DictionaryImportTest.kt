package org.futo.voiceinput

import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.CharacterCodingException
import java.io.File

class DictionaryImportTest {
    @Test fun optionalTransliterationListImportsAndCorrectsPhrases() {
        val incoming = File("../docs/examples/arabic-transliteration.txt").inputStream().use(DictionaryImport::read)
        val preview = DictionaryImport.preview("", incoming)
        assertTrue(preview.invalid.isEmpty())
        assertEquals(0, preview.duplicates)
        assertEquals("inshaAllah, alhamdulillah.",
            PersonalVocabulary.apply("in sha Allah, al hamdu lillah.", preview.appendTo("")))
        assertEquals("mashaAllah and inshaAllah", PersonalVocabulary.apply("mashaAllah and inshaAllah", incoming))
    }

    @Test fun mergesUnicodeMappingsAndLegacyListsWithoutRewritingExistingText() {
        val existing = "FUTO, café\r\nphoto => FUTO\n"
        val preview = DictionaryImport.preview(existing,
            "\uFEFFcafé\r\ninshaAllah\n inshallah=>inshaAllah \nphoto=>FUTO\ninshaAllah\n=> bad\nbad =>\na=>b=>c")
        assertEquals(listOf("inshaAllah", "inshallah => inshaAllah"), preview.additions)
        assertEquals(3, preview.duplicates)
        assertEquals(3, preview.invalid.size)
        assertEquals(existing + "inshaAllah\ninshallah => inshaAllah", preview.appendTo(existing))
        assertEquals("inshaAllah", PersonalVocabulary.apply("inshallah", preview.appendTo(existing)))
        assertEquals("$5", PersonalVocabulary.apply("five dollars", "five dollars => $5"))
    }

    @Test fun readsUtf8AndRejectsOversizedAndInvalidFiles() {
        assertEquals("café", DictionaryImport.read("café".byteInputStream()))
        assertEquals(DictionaryImport.MAX_BYTES,
            DictionaryImport.read(ByteArray(DictionaryImport.MAX_BYTES) { 65 }.inputStream()).length)
        assertThrows(IllegalArgumentException::class.java) {
            DictionaryImport.read(ByteArray(DictionaryImport.MAX_BYTES + 1).inputStream())
        }
        assertThrows(CharacterCodingException::class.java) {
            DictionaryImport.read(byteArrayOf(0xC3.toByte(), 0x28).inputStream())
        }
        assertEquals(listOf("bad\u0000text"), DictionaryImport.preview("", "bad\u0000text").invalid)
    }

    @Test fun previewsAThousandEntriesAndSkipsRepeatedImports() {
        val incoming = (1..1000).joinToString("\n") { "term$it => preferred$it" }
        val first = DictionaryImport.preview("", incoming)
        assertEquals(1000, first.additions.size)
        val merged = first.appendTo("")
        val second = DictionaryImport.preview(merged, incoming)
        assertEquals(1000, second.duplicates)
        assertTrue(second.additions.isEmpty())
        assertEquals(merged, second.appendTo(merged))
        assertEquals("preferred500", PersonalVocabulary.apply("term500", merged))
    }
}

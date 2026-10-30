package org.futo.voiceinput.downloader

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.futo.voiceinput.recognition.RecognitionModelCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecognitionModelRequestTest {
    @Test
    fun asr4allRequestsKeepPinnedUrlsHashesAndStorageRequirements() {
        org.futo.voiceinput.asr4all.Asr4allModels.models.forEach { model ->
            val request = Intent().apply { putRecognitionModel(model) }
            request.refreshRecognitionModel()
            assertEquals(listOf("asr_encoder.pte", "metadata.json"), request.getStringArrayListExtra(EXTRA_DOWNLOAD_FILE_NAMES))
            assertEquals(model.artifacts.map { it.sha256 }, request.getStringArrayListExtra(EXTRA_DOWNLOAD_FILE_HASHES))
            assertEquals(model.artifacts.map { it.url }, request.getStringArrayListExtra(EXTRA_DOWNLOAD_FILE_URLS))
            assertEquals(model.requiredFreeSpaceBytes, request.getLongExtra(EXTRA_REQUIRED_FREE_SPACE, -1))
            assertFalse(request.hasExtra(EXTRA_ARCHIVE_URL))
        }
    }

    @Test
    fun staleManagedRequestUsesCurrentRuntimeManifest() {
        val model = RecognitionModelCatalog.nemotronMultilingual
        val request = Intent().apply {
            putRecognitionModel(model)
            putStringArrayListExtra(EXTRA_DOWNLOAD_FILE_NAMES, arrayListOf(
                "encoder.int8.onnx", "decoder.int8.onnx", "joiner.int8.onnx", "tokens.txt",
                "test_wavs/en.wav", "test_wavs/ja.wav"
            ))
            putExtra(EXTRA_REQUIRED_FREE_SPACE, 683_164_180L)
            putExtra(EXTRA_ARCHIVE_URL, "https://example.com/obsolete.tar.bz2")
        }

        request.refreshRecognitionModel()

        assertEquals(
            listOf("encoder.int8.onnx", "decoder.int8.onnx", "joiner.int8.onnx", "tokens.txt"),
            request.getStringArrayListExtra(EXTRA_DOWNLOAD_FILE_NAMES)
        )
        assertEquals(model.artifacts.map { it.url }, request.getStringArrayListExtra(EXTRA_DOWNLOAD_FILE_URLS))
        assertEquals(model.artifacts.map { it.sha256 }, request.getStringArrayListExtra(EXTRA_DOWNLOAD_FILE_HASHES))
        assertEquals(model.artifacts.map { it.sizeBytes }, request.getLongArrayExtra(EXTRA_DOWNLOAD_FILE_SIZES)?.toList())
        assertEquals(model.directoryName, request.getStringExtra(EXTRA_TARGET_SUBDIR))
        assertEquals(model.version, request.getStringExtra(EXTRA_MODEL_VERSION))
        assertEquals(682_215_356L, request.getLongExtra(EXTRA_REQUIRED_FREE_SPACE, -1))
        assertFalse(request.hasExtra(EXTRA_ARCHIVE_URL))
    }

    @Test
    fun unmanagedRequestIsUnchanged() {
        val request = Intent().apply {
            putStringArrayListExtra("models", arrayListOf("tiny"))
            putExtra(EXTRA_MODEL_ID, "not-in-catalog")
            putStringArrayListExtra(EXTRA_DOWNLOAD_FILE_NAMES, arrayListOf("custom.bin"))
        }

        request.refreshRecognitionModel()

        assertEquals(listOf("tiny"), request.getStringArrayListExtra("models"))
        assertEquals(listOf("custom.bin"), request.getStringArrayListExtra(EXTRA_DOWNLOAD_FILE_NAMES))
    }
}

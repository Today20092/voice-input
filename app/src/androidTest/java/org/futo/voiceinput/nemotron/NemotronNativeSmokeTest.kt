package org.futo.voiceinput.nemotron

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.futo.voiceinput.settings.NEMOTRON_PROFILE
import org.futo.voiceinput.settings.NEMOTRON_MULTILINGUAL_LANGUAGE
import org.futo.voiceinput.settings.getSetting
import org.futo.voiceinput.settings.setSetting
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class NemotronNativeSmokeTest {
    @Test
    fun downloadedEnglishProfilesTranscribeKnownAudioSample() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val profiles = NemotronProfile.entries.filterNot { it.supportsLanguageSelection }
        assumeTrue(profiles.all { context.isNemotronModelDownloaded(it, verifyHashes = true) })
        val bytes = context.assets.open("audio.floats.bin").use { it.readBytes() }
        val samples = FloatArray(bytes.size / Float.SIZE_BYTES)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(samples)
        val previousProfile = context.getSetting(NEMOTRON_PROFILE)

        try {
            profiles.forEach { profile ->
                context.setSetting(NEMOTRON_PROFILE, profile.id)
                val backend = SherpaStreamingBackend()
                backend.load(context)
                val result = backend.transcribe(samples)
                backend.close()

                assertTrue("${profile.name} returned no text", result.isNotBlank())
            }
        } finally {
            context.setSetting(NEMOTRON_PROFILE, previousProfile)
        }
    }

    @Test
    fun multilingualSupportsEnglishJapaneseAndAutoDetect() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val profile = NemotronProfile.Multilingual
        assumeTrue(context.isNemotronModelDownloaded(profile, verifyHashes = true))
        val previousProfile = context.getSetting(NEMOTRON_PROFILE)
        val previousLanguage = context.getSetting(NEMOTRON_MULTILINGUAL_LANGUAGE)
        val modelDirectory = context.nemotronModelDirectory(profile)

        try {
            context.setSetting(NEMOTRON_PROFILE, profile.id)
            listOf(
                "en" to modelDirectory.resolve("test_wavs/en.wav"),
                "ja" to modelDirectory.resolve("test_wavs/ja.wav"),
                "auto" to modelDirectory.resolve("test_wavs/ja.wav")
            ).forEach { (language, wav) ->
                context.setSetting(NEMOTRON_MULTILINGUAL_LANGUAGE, language)
                val backend = SherpaStreamingBackend()
                backend.load(context)
                val result = backend.transcribe(readWav(wav.readBytes()))
                backend.close()

                assertTrue("$language returned no text", result.isNotBlank())
            }
        } finally {
            context.setSetting(NEMOTRON_PROFILE, previousProfile)
            context.setSetting(NEMOTRON_MULTILINGUAL_LANGUAGE, previousLanguage)
        }
    }

    private fun readWav(bytes: ByteArray): FloatArray {
        val pcm = ByteBuffer.wrap(bytes, 44, bytes.size - 44)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()
        return FloatArray(pcm.remaining()) { pcm.get() / 32768.0f }
    }
}

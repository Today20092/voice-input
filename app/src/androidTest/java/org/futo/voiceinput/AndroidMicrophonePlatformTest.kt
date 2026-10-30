package org.futo.voiceinput

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class AndroidMicrophonePlatformTest {
    @Test fun defaultDoesNotChangeModeAndClosingReleasesPhonePreference() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val audio = context.getSystemService(AudioManager::class.java)
            val originalMode = audio.mode
            val recorder = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16_000,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, 32_000)
            val platform = AndroidMicrophonePlatform(context, recorder)
            val routing = MicrophoneRouting(platform) {}
            try {
                assertEquals(AudioRecord.STATE_INITIALIZED, recorder.state)
                recorder.startRecording()
                routing.start()
                assertNull(recorder.preferredDevice)
                assertEquals(originalMode, audio.mode)
                val phone = platform.devices().first { it.kind == MicrophoneKind.Phone }
                routing.select(phone)
                assertEquals(phone.id, recorder.preferredDevice?.id)
                routing.close()
                assertNull(recorder.preferredDevice)
                assertEquals(originalMode, audio.mode)
                routing.close()
            } finally {
                routing.close()
                try { recorder.stop() } finally { recorder.release() }
            }
        }
    }
}

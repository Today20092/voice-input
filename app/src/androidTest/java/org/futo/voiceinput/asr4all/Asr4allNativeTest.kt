package org.futo.voiceinput.asr4all

import android.content.Context
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.futo.voiceinput.recognition.RecognitionModelStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class Asr4allNativeTest {
    @Test fun allThreeVariantsStreamAndFinalizeKnownSpeech() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets.open("jfk.wav").use { it.readBytes() }
        val pcm = ByteBuffer.wrap(bytes, 44, bytes.size - 44).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val samples = FloatArray(pcm.remaining()) { pcm.get() / 32768f }
        val store = RecognitionModelStore(context.filesDir)
        Asr4allModels.models.forEach { model ->
            if (!store.isInstalled(model)) assertTrue("${model.id} artifact hashes", store.completeInstall(model))
            assertTrue("Install ${model.id} before running this test", store.isInstalled(model, true))
            val start = SystemClock.elapsedRealtime()
            val backend = asr4allBackend(context, requireNotNull(model.variantId))
            try {
                backend.load(context)
                val loaded = SystemClock.elapsedRealtime()
                val cpuStart = android.os.Process.getElapsedCpuTime()
                var peakPssKb = 0
                val partials = mutableListOf<String>()
                var firstPartialMs: Long? = null
                backend.startStreaming({ text ->
                    if (firstPartialMs == null) firstPartialMs = SystemClock.elapsedRealtime() - loaded
                    partials.add(text)
                })
                samples.asList().chunked(3200).forEach {
                    backend.acceptAudio(it.toFloatArray())
                    delay(200)
                    val memory = Debug.MemoryInfo()
                    Debug.getMemoryInfo(memory)
                    peakPssKb = maxOf(peakPssKb, memory.totalPss)
                }
                val result = backend.finishStreaming()
                val finished = SystemClock.elapsedRealtime()
                Log.i("Asr4allTest", "${model.id} loadMs=${loaded-start} decodeMs=${finished-loaded} " +
                    "firstPartialMs=$firstPartialMs cpuMs=${android.os.Process.getElapsedCpuTime()-cpuStart} " +
                    "peakPssKb=$peakPssKb nativeHeap=${Debug.getNativeHeapAllocatedSize()} " +
                    "partials=${partials.size} text=$result")
                assertTrue("${model.id}: $result", result.lowercase().contains("country"))
                assertTrue("${model.id}: no streaming partials", partials.isNotEmpty())
                assertTrue("${model.id}: PCEC casing missing", result.first().isUpperCase())
                assertTrue("${model.id}: PCEC punctuation missing", result.any { it in ".,?" })
                backend.close()
                val replay = asr4allBackend(context, requireNotNull(model.variantId))
                try {
                    val warmStart = SystemClock.elapsedRealtime()
                    replay.load(context)
                    val warmLoaded = SystemClock.elapsedRealtime()
                    val whole = replay.transcribe(samples)
                    assertEquals("${model.id}: audio chunk boundaries changed transcript", result, whole)
                    Log.i("Asr4allTest", "${model.id} warmLoadMs=${warmLoaded-warmStart} " +
                        "wholeDecodeMs=${SystemClock.elapsedRealtime()-warmLoaded}")
                } finally { replay.close() }
            } finally { backend.close() }
        }
    }
}

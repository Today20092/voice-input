package org.futo.voiceinput.benchmark

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.futo.voiceinput.backend.StreamingSpeechBackend
import org.futo.voiceinput.recognition.RecognitionModelCatalog
import org.futo.voiceinput.recognition.RecognitionModelLifecycle
import org.futo.voiceinput.recognition.TranscriptionBehavior
import org.futo.voiceinput.recognition.managedRecognitionBackend
import org.futo.voiceinput.settings.*
import org.junit.Assert.*
import org.junit.Test

class ManagedRecognitionConstructionTest {
    @Test fun everyManagedVariantConstructsIndependentMatchingAdaptersWithoutChangingDailySettings() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val lifecycle = RecognitionModelLifecycle.create(context.filesDir, false)
        suspend fun settings() = listOf(
            context.getSetting(SPEECH_BACKEND), context.getSetting(MOONSHINE_MODEL_VARIANT),
            context.getSetting(NEMOTRON_PROFILE), context.getSetting(ASR4ALL_VARIANT),
            context.getSetting(COHERE_LANGUAGE), context.getSetting(NEMOTRON_MULTILINGUAL_LANGUAGE)
        )
        val before = settings()
        for (model in RecognitionModelCatalog.models) {
            val selection = lifecycle.selectionFor(model)
            val production = managedRecognitionBackend(context, selection)
            val comparison = BenchmarkModel(model.id, model.displayName, managed = model)
            val independent = comparison.backend(context, "en")
            try {
                assertNotSame(model.id, production, independent)
                assertEquals(model.id, production.javaClass, independent.javaClass)
                assertEquals(model.id, model.transcription != TranscriptionBehavior.FINAL_ONLY,
                    independent is StreamingSpeechBackend)
                assertEquals(model.id, model, requireNotNull(lifecycle.readiness(selection)).model)
                assertEquals(model.id, before, settings())
            } finally {
                production.close()
                independent.close()
            }
        }
    }

    @Test fun explicitComparisonLanguagesNeverChangeDailyLanguageChoices() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val beforeCohere = context.getSetting(COHERE_LANGUAGE)
        val beforeNemotron = context.getSetting(NEMOTRON_MULTILINGUAL_LANGUAGE)
        val multilingual = BenchmarkModel.all().filter {
            it.managed?.runtimeId == "cohere" || it.managed?.id == RecognitionModelCatalog.nemotronMultilingual.id
        }
        for (model in multilingual) {
            for (language in listOf("en", "ar", "fr")) {
                val backend = model.backend(context, language)
                backend.close()
                assertEquals(beforeCohere, context.getSetting(COHERE_LANGUAGE))
                assertEquals(beforeNemotron, context.getSetting(NEMOTRON_MULTILINGUAL_LANGUAGE))
            }
            try {
                model.backend(context, "xx")
                fail("Unsupported comparison language accepted")
            } catch (_: IllegalArgumentException) { }
        }
    }
}

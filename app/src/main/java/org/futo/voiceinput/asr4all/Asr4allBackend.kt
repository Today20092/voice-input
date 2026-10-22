package org.futo.voiceinput.asr4all

import android.content.Context
import org.futo.voiceinput.nemotron.SherpaStreamingBackend
import org.futo.voiceinput.recognition.RecognitionModelStore
import org.json.JSONObject

fun asr4allBackend(context: Context, variant: String): SherpaStreamingBackend {
    val model = Asr4allModels.selected(variant)
    val store = RecognitionModelStore(context.filesDir)
    val geometry = context.assets.open("asr4all-geometry.json").bufferedReader().use {
        JSONObject(it.readText()).getJSONObject(requireNotNull(model.variantId))
    }
    check(geometry.getString("sha256") == model.artifacts.first().sha256) { "ASR4ALL geometry revision mismatch" }
    return SherpaStreamingBackend(
        modelDirectory = {
            check(store.isInstalled(model, verifyHashes = true)) { "ASR4ALL model is missing or invalid" }
            store.modelDirectory(model)
        },
        backendName = model.displayName,
        decoderFactory = { Asr4allDecoder(it, geometry) }
    )
}

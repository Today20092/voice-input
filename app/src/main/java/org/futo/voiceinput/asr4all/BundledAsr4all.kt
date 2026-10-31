package org.futo.voiceinput.asr4all

import android.content.Context
import org.futo.voiceinput.recognition.RecognitionModelStore
import java.io.File

internal object BundledAsr4all {
    // ExecuTorch needs file paths. Android may reclaim this cache; APK assets remain the source.
    @Synchronized
    fun directory(context: Context): File {
        val model = Asr4allModels.bundled
        val store = RecognitionModelStore(File(context.codeCacheDir, "bundled-recognition"))
        if (!store.isInstalled(model, verifyHashes = true)) {
            val staged = store.stagingDirectory(model)
            check(staged.isDirectory || staged.mkdirs()) { "Cannot prepare included recognition model" }
            model.artifacts.forEach { artifact ->
                context.assets.open("asr4all-medium/${artifact.name}").use { input ->
                    File(staged, artifact.name).outputStream().use { input.copyTo(it) }
                }
            }
            check(store.validateStaged(model)) { "Included recognition model failed validation" }
            store.activateStaged(model)
        }
        return store.modelDirectory(model)
    }
}

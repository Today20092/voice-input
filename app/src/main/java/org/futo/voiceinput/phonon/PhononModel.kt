package org.futo.voiceinput.phonon

import org.futo.voiceinput.recognition.PerformanceClass
import org.futo.voiceinput.recognition.RecognitionModel
import org.futo.voiceinput.recognition.RecognitionModelArtifact
import org.futo.voiceinput.recognition.TranscriptionBehavior

object PhononModel {
    private const val REVISION = "1c91a6a7a26809fecedb35d847bef69663dde8bb"
    private const val ARCHIVE = "phonon-2-c-weights-v1.tar.gz"
    private const val URL = "https://huggingface.co/eschmidbauer/phonon-2-c/resolve/$REVISION/$ARCHIVE?download=true"

    val model = RecognitionModel(
        id = "phonon-2",
        version = REVISION,
        runtimeId = "phonon",
        variantId = null,
        directoryName = "phonon-2",
        source = "Fermion Research Phonon-2, repackaged by eschmidbauer/phonon-2-c",
        licenseAttribution = "Fermion Research and NVIDIA Parakeet TDT 0.6B v3, CC BY 4.0 (https://creativecommons.org/licenses/by/4.0/). Five-value weights repackaged for phonon-2-c; runtime MIT.",
        displayName = "Phonon 2",
        description = "Turns English speech into text after you stop recording, with punctuation and capitalization. " +
            "Its compact five-value weights need less storage than Parakeet TDT and run on your phone's processor. " +
            "This experimental model uses a separate native runtime. Android speed and accuracy have not been verified.",
        transcription = TranscriptionBehavior.FINAL_ONLY,
        recognitionLanguages = "English",
        performanceClass = PerformanceClass.BALANCED,
        artifacts = listOf(
            RecognitionModelArtifact("tokens.txt", URL, 54_086, "04d756a9b6601bde84944f64609fb1d379f86731289ff083ecd9d68ddf5cdc2f"),
            RecognitionModelArtifact("weights.bin", URL, 255_253_016, "7dbcfd8ce258972cc5604f141ac7d64bbf5251c49bbc79c3e0c09b536e23fea5"),
            RecognitionModelArtifact("weights.json", URL, 93_161, "37b0ee8bd7745b30b3c2298d5c7f40ec88f82c7e4995016d21d1b4db37a25397")
        ),
        archive = RecognitionModelArtifact(ARCHIVE, URL, 186_618_134,
            "48137a58011c4b0bc0a97087ea7e868daabf1525ca8447414be230b21faf99e6"),
        archiveRoot = "c_weights"
    )
}

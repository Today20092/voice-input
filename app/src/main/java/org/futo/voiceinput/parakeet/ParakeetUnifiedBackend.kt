package org.futo.voiceinput.parakeet

import org.futo.voiceinput.backend.StreamingSpeechBackend
import org.futo.voiceinput.nemotron.SherpaOnlineDecoder
import org.futo.voiceinput.nemotron.SherpaStreamingBackend
import org.futo.voiceinput.nemotron.SherpaStreamingDecoder
import java.io.File

internal fun parakeetUnifiedBackend(
    decoderFactory: (File) -> SherpaStreamingDecoder = { SherpaOnlineDecoder(it, featureDim = 128) },
    catchingUpSamples: Int = 16_000,
    decoder: SherpaStreamingDecoder? = null
): StreamingSpeechBackend = SherpaStreamingBackend(
    modelDirectory = { it.parakeetUnifiedModelDirectory() },
    backendName = "Parakeet Unified",
    decoderFactory = decoderFactory,
    catchingUpSamples = catchingUpSamples,
    decoder = decoder
)

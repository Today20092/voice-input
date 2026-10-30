# Voxtral 3 and Android voice input

Researched October 23, 2026. Recommendation: do not add Voxtral 3 as an offline production model yet. Older downloadable Voxtral models offer a credible Android prototype path, but their phone performance needs measurement.

## What Voxtral 3 provides

Voxtral 3 is a family: Small Transcribe 3, Mini Transcribe 3, Mini Transcribe Realtime 3, plus separate speech-generation and voice-agent offerings. Mini Transcribe 3 has 4B parameters. Mistral advertises more than 50 transcription languages, diarization and context biasing; realtime adds end-of-turn detection. These features could help multilingual dictation and vocabulary recognition. Speaker diarization and speech generation are less relevant to a voice-typing keyboard. Published benchmark gains are not measurements in this Android app. [Announcement](https://mistral.ai/news/voxtral-3/)

The new transcription model pages label the releases Public Preview and Premier v3.0. I could not find publicly downloadable Voxtral 3 weights on Hugging Face. The announcement offers API access and directs private-cloud/on-premises deployments to sales. Self-hosting availability does not establish public redistribution rights or an Android package. [Mini model](https://docs.mistral.ai/models/voxtral-mini-transcribe-3-0), [realtime model](https://docs.mistral.ai/models/voxtral-mini-transcribe-realtime-3-0), [Small model](https://docs.mistral.ai/models/voxtral-small-transcribe-3-0)

The API costs $0.0035/min for Mini batch, $0.004/min for Small, and $0.008/min for realtime. A cloud integration would send audio off-device and introduce connectivity, credentials and ongoing billing. That would be a separate product decision for this offline app. [Pricing in announcement](https://mistral.ai/news/voxtral-3/)

## Downloadable alternatives

| Model | Availability | Android assessment |
| --- | --- | --- |
| Voxtral 3 transcription family | No public weights verified in this research | Cannot currently recommend as an installable offline recognition model. |
| Voxtral Mini 4B Realtime 2602 | Official Hugging Face weights, Apache 2.0; 13 languages | Best Voxtral candidate for a streaming prototype, requiring a suitable export and native integration. |
| Voxtral Mini 3B 2507 | Official Hugging Face weights, Apache 2.0; eight languages | Older transcription/audio-understanding option with documented mobile demos. It is not Voxtral 3. |

Download links: [4B Realtime model card](https://huggingface.co/mistralai/Voxtral-Mini-4B-Realtime-2602), [3B model card](https://huggingface.co/mistralai/Voxtral-Mini-3B-2507).

The 4B model has roughly 3.4B language-model parameters plus a 970M audio encoder. One BF16 weight representation is approximately 8.86 GB; the repository total includes duplicate formats and is not the required download. Its server-oriented vLLM guidance recommends at least 16 GB GPU memory. That is not a measured Android RAM minimum. The model card labels its ExecuTorch route untested. [Model card](https://huggingface.co/mistralai/Voxtral-Mini-4B-Realtime-2602), [files](https://huggingface.co/mistralai/Voxtral-Mini-4B-Realtime-2602/tree/main)

At four bits per parameter, 4.37B parameters alone occupy approximately 2.2 GB in decimal units. This arithmetic is only a rough storage floor: quantization metadata, tensors kept at higher precision, caches, activations and the app require additional space and memory. It is not a measured download size or peak RAM figure.

## Can it run on Android?

Yes, there is a credible engineering route for the older models; there is no verified ready-to-use Voxtral 3 Android download here.

PyTorch's upstream Voxtral Realtime implementation supports streaming with XNNPACK CPU and quantized exports, including four-bit weights. It also requires audio preprocessing, tokenization and native runner integration. ExecuTorch provides an Android runtime with native and Java/Kotlin integration. This establishes a possible implementation route, not verified performance for this model on a phone. [Realtime implementation](https://github.com/pytorch/executorch/blob/main/examples/models/voxtral_realtime/README.md), [Android runtime](https://github.com/pytorch/executorch/blob/main/docs/source/using-executorch-android.md)

Mistral's pre-exported experimental four-bit Realtime checkpoint explicitly targets macOS M-Series. That particular artifact cannot simply be downloaded into the Android app. A suitable Android export would need to be produced and validated. [Experimental checkpoint](https://huggingface.co/mistral-experimental/Voxtral-Mini-4B-Realtime-2602-ExecuTorch)

For the older 3B model, PyTorch explicitly links Android and iOS XNNPACK demonstration apps. This is stronger mobile evidence than the model's server examples, but it still does not establish acceptable dictation latency, battery use or memory on our devices. [Mobile examples](https://github.com/pytorch/executorch/blob/main/examples/models/voxtral/README.md#android-and-ios-mobile-demo-apps)

## Fit with this repository

The app is an ARM64 Android offline dictation tool with multiple existing recognition runtimes. The current model guide lists Orukeet, ASR4ALL, Moonshine, Parakeet, Nemotron, Cohere, Whistle, Phonon and legacy Whisper. Several options have much smaller downloads than a multi-billion-parameter Voxtral export. This makes added accuracy and language coverage the main reasons to consider Voxtral, rather than assuming it will be lighter or faster. [App model guide](../model-guide.md)

The shared `SpeechBackend` contract accepts float audio samples and exposes loading, transcription, closing and detected language. A Voxtral port would need its own audio/model execution integration; downloading Hugging Face safetensors does not make them compatible with the existing Sherpa-ONNX runtime. Existing llama.cpp transcript cleanup also does not establish streaming audio support. See `app/src/main/java/org/futo/voiceinput/backend/SpeechBackend.kt`, [repository orientation](../../AGENTS.md), and [runtime decision](../adr/0001-use-sherpa-onnx-for-nvidia-streaming.md).

Production integration would also reach the shared model catalog, pinned downloads/hashes, storage validation, lifecycle and cancellation, model settings, language selection, history retranscription, benchmarks, native packaging and relevant tests. A live-transcription implementation additionally needs the app's streaming session behavior. No implementation changes are proposed in this research.

## Recommendation and next step

1. Hold Voxtral 3 until public weights, a usable license and an Android execution route are verified.
2. If Voxtral is worth exploring, prototype the older Mini 4B Realtime model using a quantized CPU export on a high-memory Android phone before integrating it into the catalog.
3. Compare it with existing models using identical recordings: recognition errors, multilingual and mixed-language behavior, punctuation, cold start, sustained recognition speed, total memory, thermals and battery. Include longer dictation, interruption and background/foreground recovery.
4. Add it as an experimental model only if the quality benefit justifies its storage and runtime cost. Do not replace the default based on publisher benchmarks.

These are engineering recommendations, not measured results. Research used current first-party announcements, model cards and upstream runtime documentation. Context7 returned older Voxtral 2 documentation, so its general open-weights language was not used as evidence that Voxtral 3 is open-weight.

No models were downloaded, converted or run. No Android speed, memory, battery or accuracy benchmark was performed; those remain the main adoption risks.

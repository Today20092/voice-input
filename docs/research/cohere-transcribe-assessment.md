# Cohere Transcribe assessment

Researched 2026-09-25. Repository context: HEAD `0ee8922`, including current working-tree source. No app implementation or model download was performed.

## Recommendation

**Yes: evaluate it as an optional, experimental model, then ship only if phone measurements justify it. Keep Orukeet as the default.** Its multilingual coverage makes it interesting, especially for Arabic. The integration route already exists in our pinned Sherpa runtime; the unresolved question is whether its accuracy benefit earns its download size, memory use, and delay on the phones we target.

## What is verified

- Cohere Transcribe is a 2-billion-parameter Conformer encoder/Transformer decoder model, licensed Apache 2.0. Its 14 languages are Arabic, English, French, German, Italian, Spanish, Portuguese, Greek, Dutch, Polish, Mandarin, Japanese, Korean, and Vietnamese. Input preprocessing uses 16 kHz mono audio. [Cohere model card](https://huggingface.co/CohereLabs/cohere-transcribe-03-2026)
- It needs an explicit language. Automatic language detection is absent; mixed-language audio is a stated weakness. It does not supply timestamps or speaker diarization. Cohere recommends VAD or a noise gate because nonspeech can produce hallucinated text. Arabic support therefore does not establish reliable Arabic/English code-switching or dialect accuracy. [Model limitations](https://huggingface.co/CohereLabs/cohere-transcribe-03-2026#strengths-and-limitations)
- Cohere reported 5.42% average English WER at launch on March 26, 2026. This is a dated, vendor-reported benchmark result, not an Android measurement or a direct comparison against our installed model variants. Its published multilingual preference chart does not establish Arabic performance. [Launch results](https://cohere.com/blog/transcribe)
- Native Transformers support exists; its processor chunks long recordings and reassembles transcripts. That behavior is not evidence of incremental streaming recognition. [Transformers model documentation](https://github.com/huggingface/transformers/blob/main/docs/source/en/model_doc/cohere_asr.md)

## Runtime and download feasibility

Sherpa-ONNX provides a ready-made INT8 Cohere model with `encoder.int8.onnx`, `decoder.int8.onnx`, and `tokens.txt`, plus offline decoding and VAD examples. Its example requires an explicit language and exposes punctuation and inverse text normalization controls. It reports 0.778 seconds for 5.768 seconds of audio, but this does not establish phone latency. [Sherpa pretrained-model documentation](https://k2-fsa.github.io/sherpa/onnx/cohere_transcribe/pretrained.html)

Crucially, **the app's pinned Sherpa version, 1.13.4, already contains `OfflineCohereTranscribeModelConfig` and the `cohereTranscribe` field**. A runtime upgrade is not required merely to obtain this configuration API. Kotlin and Java examples are also available. A successful Android build and device run are still unverified. [Pinned Kotlin API](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.4/sherpa-onnx/kotlin-api/OfflineRecognizer.kt), [runtime examples](https://k2-fsa.github.io/sherpa/onnx/cohere_transcribe/examples.html)

Size checks used metadata, without downloading weights:

| Artifact | Verified size |
| --- | ---: |
| Original `model.safetensors` | 4,131,862,976 bytes, about 4.13 GB |
| Sherpa INT8 `.tar.bz2` release | 1,699,791,751 bytes, about 1.70 GB |

Sources: [Cohere repository file metadata](https://huggingface.co/api/models/CohereLabs/cohere-transcribe-03-2026/tree/main), [Sherpa release metadata](https://api.github.com/repos/k2-fsa/sherpa-onnx/releases/tags/asr-models). The release archive is `sherpa-onnx-cohere-transcribe-14-lang-int8-2026-04-01.tar.bz2`; its published SHA-256 is `bd582588d50685a795dcd2807ab77e11361b8312d96c53884682def45ab4206d`.

**Memory inference:** 2B parameters at one byte each are roughly 2 GB of raw weights, before unquantized tensors, activations, caches, and runtime overhead. This is an order-of-magnitude estimate, not measured RAM or unpacked size. Download size must not be presented as RAM usage. The original Hugging Face repository is gated, while Sherpa documents its separately hosted release archive. [Model access notice](https://huggingface.co/CohereLabs/cohere-transcribe-03-2026), [Sherpa download instructions](https://k2-fsa.github.io/sherpa/onnx/cohere_transcribe/pretrained.html)

## Fit with this app

[SpeechBackend](../../app/src/main/java/org/futo/voiceinput/backend/SpeechBackend.kt) already accepts a float waveform and returns final text; [AudioRecognizer](../../app/src/main/java/org/futo/voiceinput/AudioRecognizer.kt) records at 16 kHz and calls that contract after recording. That makes an offline Cohere adapter a natural fit. The current [Parakeet backend](../../app/src/main/java/org/futo/voiceinput/parakeet/ParakeetBackend.kt) specifically configures a NeMo transducer, so Cohere needs its own adapter/configuration, not a weight-file substitution. The runtime version is set in [app/build.gradle](../../app/build.gradle).

Implementation scope, if approved later: add the adapter, model download/verification metadata, selection UI, explicit supported-language selection, and unload behavior. Preserve existing VAD and final-result handling; do not advertise live partial transcription. Do not keep this model resident alongside another large recognizer without measuring the impact.

## Decision gate before release

Run the INT8 build on the user's actual phone and at least one lower-memory target. Compare identical English and Arabic clips against the relevant existing backends, including dialects, names, numbers, noise, silence, and Arabic/English mixing. Record WER/CER and transcript usability, cold load, warm post-stop latency, peak process memory, crashes, and repeated-use thermal behavior.

Suggested initial acceptance targets (not established performance): no crashes or memory kills during 20 repeated dictations; warm p95 post-stop latency below two seconds for 5–15-second clips; a clear accuracy or language-coverage benefit on the user's recordings. Report cold-start separately. If the model fails these targets, keep it out of the normal model menu or limit it to a clearly labeled experimental option. No device benchmark, quantization parity test, or app build was run for this research.

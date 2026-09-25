# Awesome Whisper: useful projects for this Android app

Researched: 2026-09-25. Scope: the user's pasted Awesome Whisper list, assessed against this checkout. Primary repositories and source files were checked; this is a shortlist, not an exhaustive dependency or security audit. Recommendations below are engineering judgments. No APKs were built and no phone benchmarks were run.

## Recommendation

There are useful pieces here, especially Android integration examples, context-sensitive dictation UX, and evaluation tools. Keep Orukeet as the default. Nothing reviewed establishes that replacing it with another Whisper implementation improves this app on its target phones.

| Priority | Project | What we can use | Recommendation |
| --- | --- | --- | --- |
| 1 | [VoiceInk](https://github.com/Beingpax/VoiceInk), [TypeWhisper](https://github.com/TypeWhisper/typewhisper-mac) | Product ideas for context-specific settings and insertion | Start with field-aware formatting; later consider per-app cleanup/language profiles. |
| 2 | [faster-whisper](https://github.com/SYSTRAN/faster-whisper) | Desktop evaluation tool | Compare transcripts on a fixed, human-labelled dictation corpus. Keep it outside the APK. |
| 3 | [whisper.cpp](https://github.com/ggml-org/whisper.cpp) | Native runtime improvements and Android examples | Benchmark a separate current-upstream build against our legacy Whisper path before planning a migration. |
| Conditional | [whisperIME](https://github.com/woheller69/whisperIME) | Android voice-provider integration reference | Add a real `RecognitionService` only for client apps that need that contract. |

## What the app already has

The current [README](../../README.md) documents personal vocabulary and replacement aliases, optional offline S1-mini cleanup after Stop, and local audio history with retranscription. These are already implemented, so desktop apps offering a dictionary, cleanup or history alone do not justify new work here. The incremental opportunity is how existing features adapt to the destination field and app.

Current source inspection confirmed:

- [SpeechBackend.kt](../../app/src/main/java/org/futo/voiceinput/backend/SpeechBackend.kt) already defines a streaming interface. A second interface is unnecessary.
- [ParakeetBackend.kt](../../app/src/main/java/org/futo/voiceinput/parakeet/ParakeetBackend.kt) uses an offline recognizer. A streaming-looking Whisper demo does not turn this backend into a streaming model.
- [VoiceInputMethodService.kt](../../app/src/main/java/org/futo/voiceinput/VoiceInputMethodService.kt) has empty number/date/phone and text input-type branches in `onStartInputView`, a concrete place to assess field-aware behavior.
- [CMakeLists.txt](../../app/src/main/cpp/CMakeLists.txt) builds the existing custom Whisper/GGML sources and separately includes S1's llama.cpp. A native runtime update needs JNI/model-format and linking checks, not a source-folder replacement.

Related prior work: [feature roadmap](android-offline-asr-feature-roadmap.md), [Android integration protocol](android-voice-input-integration-protocol.md), and [streaming options](streaming-asr-android-options.md). Their proposals should be checked against today's source before opening implementation work.

## 1. whisperIME: the most directly relevant Android reference

The project supports an IME, the speech-recognition activity intent, and a system voice `RecognitionService`. It documents offline recognition after model download, English/multilingual model selection, and a 30-second recording limit. Its README also notes device differences in provider selection. Those are useful reference behaviors, not guarantees about every Android keyboard. [Project README](https://github.com/woheller69/whisperIME#voice-recognition-based-on-whisper)

Its [WhisperRecognitionService.java](https://github.com/woheller69/whisperIME/blob/master/app/src/main/java/com/whispertflite/WhisperRecognitionService.java) shows request language handling, start/stop/cancel callbacks and returning recognition results. It explicitly rejects externally supplied audio via `EXTRA_AUDIO_SOURCE`; it is not a complete implementation of every recognition request.

Our [manifest](../../app/src/main/AndroidManifest.xml) advertises a recognition service through `DummyService`, but [DummyService.kt](../../app/src/main/java/org/futo/voiceinput/DummyService.kt) extends plain `Service` and returns null from `onBind`. It is not a working recognition provider. This matches the existing integration research's treatment of a real service as optional future interoperability.

Suggested experiment, only when a target client requires it: expose the existing recognizers through the Android voice-provider contract. Test provider discovery and callbacks with that client, cancellation, absent microphone permission, missing model and unsupported language. Keep the existing recording/backend ownership; do not import its TFLite stack just to gain service integration. This does not replace the IME or guarantee compatibility with every keyboard microphone button.

The repository's own [LICENSE](https://github.com/woheller69/whisperIME/blob/master/LICENSE) is MIT. File-level reuse still requires preserving applicable notices and checking the selected file's dependencies.

## 2. VoiceInk and TypeWhisper: borrow the behavior

VoiceInk documents automatic settings selection by app/URL and different writing modes. TypeWhisper documents reusable workflows, live field insertion with an authoritative final result, and more reliable target-app correction learning. Both are macOS projects; these are product references for our Android implementation. [VoiceInk README](https://github.com/Beingpax/VoiceInk#features), [TypeWhisper README](https://github.com/TypeWhisper/typewhisper-mac#whats-new-in-16)

The smallest useful addition is deterministic formatting by destination: preserve digits in a phone/number field and avoid prose-style cleanup there. A later opt-in per-app profile could select the existing language, vocabulary and cleanup settings. Measure fewer manual corrections, rather than adding another rewrite engine.

Do not equate macOS screen-context access with Android IME capabilities. Start from field information already supplied to the IME. VoiceInk and TypeWhisper publish GPLv3 licenses; TypeWhisper additionally advertises commercial licensing. Our root license is FUTO Source First License 1.0. Treat direct copying as a separate license review; this report makes no compatibility determination. [VoiceInk license](https://github.com/Beingpax/VoiceInk/blob/main/LICENSE), [TypeWhisper license](https://github.com/TypeWhisper/typewhisper-mac/blob/main/LICENSE), [local license](../../LICENSE.md)

[Speech Note](https://github.com/mkiol/dsnote) is a secondary reference for a multi-engine offline product. It supports Linux and Sailfish OS with speech recognition, synthesis and translation. Its code is MPL-2.0, with separately licensed dependencies. Useful to study when improving model-management UX, but porting its desktop stack would be disproportionate. [README and licensing](https://github.com/mkiol/dsnote#license)

## 3. faster-whisper: improve how we choose models

faster-whisper runs Whisper through CTranslate2, supports quantization, and exposes a Python interface. Its headline benchmark uses an RTX 3070 Ti and an Intel i7-12700K; those results do not establish Android performance or compare with Orukeet. Its repository license is MIT. [README and benchmark](https://github.com/SYSTRAN/faster-whisper#benchmark), [license](https://github.com/SYSTRAN/faster-whisper/blob/master/LICENSE)

Use it on a development computer as an independent transcription comparison. Build a small human-corrected corpus covering short utterances, names, numbers, accented speech, silence/background noise and longer dictation. Record model/version/options and score word/character errors. Model output is a comparison, not ground truth.

Run the same corpus on the actual app to measure cold/warm start, stop-to-final latency, peak memory and repeated-use behavior. This separates recognition quality from Android integration and cleanup errors. Human-labelled audio plus phone measurements is more valuable than adopting a runtime based on desktop throughput.

## 4. whisper.cpp: evaluate upstream changes selectively

Current whisper.cpp documents Android support, integer quantization, CPU inference, VAD and Vulkan support. It is MIT-licensed. These make it the strongest native-runtime candidate in the list, but do not show a speedup for this app. [README](https://github.com/ggml-org/whisper.cpp), [license](https://github.com/ggml-org/whisper.cpp/blob/master/LICENSE)

Build an isolated Android comparison using matching audio, model and decoding settings. First compare CPU performance and memory. Investigate Vulkan only after a baseline exists; advertised support is not proof that a particular phone's driver performs well. Check existing model files and JNI callbacks before any migration, and retain legacy behavior until equivalence is demonstrated. Updating this path benefits users selecting Whisper; it does not directly accelerate the default Sherpa/Orukeet path.

## What to defer

| Candidate/group | Reason |
| --- | --- |
| [usefulsensors/openai-whisper / whisper.tflite](https://github.com/moonshine-ai/openai-whisper) | The supplied link redirects to an archived repository, archived August 28, 2023; its README explicitly says development stopped. Do not add a new runtime based on this old list entry. |
| [WhisperX](https://github.com/m-bain/whisperX), [whisper-timestamped](https://github.com/linto-ai/whisper-timestamped) | Word alignment, diarization and confidence tooling can help transcript analysis or a future recording product. They add little to ordinary single-speaker IME dictation; useful as optional desktop analysis first. |
| [Whisper JAX](https://github.com/sanchit-gandhi/whisper-jax), [whisper-openvino](https://github.com/zhuzilin/whisper-openvino) | Their documented paths are JAX CPU/GPU/TPU and an OpenVINO Python fork. Their benchmark environments do not justify an Android runtime change. |
| [Whisper-AT](https://github.com/YuanGongND/whisper-at) | Audio-event tagging is a different product capability from inserting dictated text. Revisit only for a concrete sound-awareness requirement. |
| Ito | The supplied GitHub endpoint repeatedly failed, and raw README retrieval was inconsistent. No recommendation or license claim is made without a dependable primary source. |
| Subtitle tools, hosted APIs, browser apps, desktop transcription wrappers in the pasted list | Defer as outside the current offline Android IME task. This grouping follows the pasted catalog, not an individual source audit of every entry. |

## Decision gate

Prioritize fewer typing corrections through field-aware formatting, then build the evaluation corpus before adding or replacing a speech engine. Broader Android integration through `RecognitionService` is worthwhile only for a demonstrated client compatibility need. These improvements can use our existing recognizers. None of the reviewed material supplies a controlled target-phone comparison against this checkout's Orukeet implementation.

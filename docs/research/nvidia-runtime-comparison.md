# NVIDIA ASR runtime comparison: custom Rust vs sherpa-onnx

Research date: 2026-07-21

## Decision

Adopt **sherpa-onnx as the single NVIDIA-model runtime**, then remove the custom Rust/JNI `transcribe-rs` stack after on-device parity checks. Do not maintain both permanently.

This is the lower-maintenance choice because sherpa-onnx already owns the difficult parts this app would otherwise have to implement: Android/Kotlin bindings, online stream state, cache-aware NeMo decoding, model export scripts, INT8 artifacts, and Android builds. The current custom backend is batch-only: `ParakeetBackend` implements `SpeechBackend`, while live audio requires `StreamingSpeechBackend`; it sends one completed `FloatArray` through JNI to Rust. See [SpeechBackend.kt](../../app/src/main/java/org/futo/voiceinput/backend/SpeechBackend.kt#L5), [ParakeetBackend.kt](../../app/src/main/java/org/futo/voiceinput/parakeet/ParakeetBackend.kt#L13), [ParakeetNative.kt](../../app/src/main/java/org/futo/voiceinput/parakeet/ParakeetNative.kt#L7), and [engine.rs](../../parakeet-native/src/engine.rs#L35).

## Comparison

| Concern | Current Rust / `transcribe-rs` | sherpa-onnx | Finding |
|---|---|---|---|
| Android integration | App-specific Kotlin → JNI → Rust bridge, `cargo-ndk`, copied `.so` files, and an extracted ONNX Runtime AAR | Maintained Kotlin API with `OnlineRecognizer`, `OnlineStream`, `acceptWaveform`, readiness/decode calls, results, and explicit release | Sherpa fits the existing `StreamingSpeechBackend` seam and deletes project-owned native plumbing. [Kotlin API](https://github.com/k2-fsa/sherpa-onnx/blob/master/sherpa-onnx/kotlin-api/OnlineRecognizer.kt) |
| Live streaming | Current Parakeet path transcribes only after stop | Online API retains per-stream state and decodes incrementally | Sherpa directly supplies live partial text; the app still owns coroutine/back-pressure and UI callbacks. [C streaming API](https://github.com/k2-fsa/sherpa-onnx/blob/master/sherpa-onnx/c-api/c-api.h) |
| Cache-aware Nemotron | Would require new cache tensor/state handling and decoder work | Official export scripts, runtime support, INT8 packages, microphone examples, and Android APKs exist | Strongest reason to replace. [Nemotron documentation](https://k2-fsa.github.io/sherpa/onnx/nemo/nemotron-streaming.html) |
| Parakeet TDT 0.6B v3 | Local fork contains its own decoder and maintenance burden; prior audit found decoder-risk around TDT duration handling | Official artifacts and Android simulated-streaming builds include `parakeet_tdt_0.6b_v3` | Use Sherpa offline/final-text mode first; simulated partials are not native cache-aware streaming. [Android model list](https://k2-fsa.github.io/sherpa/onnx/android/apk-simulate-streaming-asr.html) |
| Parakeet Unified EN 0.6B | Not implemented by the current TDT-oriented runtime despite a misleading local directory name | Sherpa 1.13.2 added unified-model export/runtime work | Supported, but NVIDIA says its current inference is **buffered streaming** that recomputes left context; it is not as efficient as cache-aware Nemotron. [NVIDIA model card](https://huggingface.co/nvidia/parakeet-unified-en-0.6b), [sherpa-onnx v1.13.2 release](https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.13.2) |
| Nemotron Streaming EN 0.6B | Not supported | Official 80, 160, 560, and 1120 ms INT8 packages; documented Android APK | Ready for the requested live-English path. NVIDIA identifies it as cache-aware FastConformer-RNNT. [NVIDIA model card](https://huggingface.co/nvidia/nemotron-speech-streaming-en-0.6b), [Sherpa artifacts](https://k2-fsa.github.io/sherpa/onnx/nemo/nemotron-streaming.html) |
| Nemotron 3.5 multilingual | Not supported | Official INT8 packages plus per-stream language prompt and `auto` detection; 40 locales are categorized by readiness | Ready for a later multilingual phase. Eight “adaptation-ready” locales require fine-tuning and must not be advertised as working out of the box. [NVIDIA model card](https://huggingface.co/nvidia/nemotron-3.5-asr-streaming-0.6b), [Sherpa artifacts](https://k2-fsa.github.io/sherpa/onnx/nemo/nemotron-streaming.html#sherpa-onnx-nemotron-3-5-asr-streaming-0-6b-560ms-int8-2026-06-11-multilingual) |
| ABI coverage | App is arm64-only | Sherpa publishes Android support for arm64, arm32, x86, and x86-64 | Keep the app arm64-only initially; broader ABI support is optional. [Sherpa platform matrix](https://github.com/k2-fsa/sherpa-onnx#supported-platforms) |
| Native size | Existing app packages Rust, `libc++_shared`, and ONNX Runtime | v1.13.4 release AARs are 46.6 MB compressed (regular) or 35.9 MB (static-ORT), before ABI filtering | Size is material but much smaller than a 600+ MB downloaded model. Measure the arm64 release APK rather than assuming the whole AAR lands in it. [Official release API](https://api.github.com/repos/k2-fsa/sherpa-onnx/releases/tags/v1.13.4) |
| Runtime version | App explicitly uses ONNX Runtime Android 1.22.0 and extracts it for Rust | Sherpa v1.13.4 moved to ONNX Runtime 1.27.0 and offers regular/static-linked AARs | Do not blindly package both. During cutover inspect the resolved APK native libraries; after Rust removal, remove the app's explicit ORT/extraction wiring if no remaining code needs it. [app/build.gradle](../../app/build.gradle#L276), [v1.13.4 release](https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.13.4) |
| Maintenance/testing | Project owns a vendored `transcribe-rs`, Rust engine/global lifecycle, assets extraction, JNI surface, Cargo/NDK toolchain, and ORT build coupling | Upstream owns decoder/export/platform matrix; app tests only catalog/download/lifecycle/callback behavior | Sherpa substantially reduces code and specialist test burden. [Cargo dependency](../../parakeet-native/Cargo.toml#L16), [Gradle native task](../../app/build.gradle#L296) |

## Model packaging implications

Sherpa's Nemotron packages contain `encoder.int8.onnx`, `decoder.int8.onnx`, `joiner.int8.onnx`, and `tokens.txt`, which is simpler than the current app-specific Parakeet layout. Keep one directory and completion marker per **model + latency profile**, with immutable community release URLs and hashes. The official Sherpa profiles are separate ~600 MB exports, so switching 80/160/560 ms profiles may require another download; the UI must show that instead of implying it is a free runtime toggle. [Sherpa package listing](https://k2-fsa.github.io/sherpa/onnx/nemo/nemotron-streaming.html#download-the-model)

The current catalog points at `main`, has null hashes, names its directory “unified” while downloading a TDT v3 conversion, and assumes one global Parakeet model. That should be replaced by model-specific metadata rather than extended. [ParakeetModel.kt](../../app/src/main/java/org/futo/voiceinput/parakeet/ParakeetModel.kt#L24)

## Licensing

- sherpa-onnx code is Apache-2.0. Model licenses remain separate. [Sherpa license](https://github.com/k2-fsa/sherpa-onnx/blob/master/LICENSE)
- Parakeet TDT 0.6B v3 is CC BY 4.0, so attribution must ship with the model entry/app notices. [Model card](https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3)
- Parakeet Unified and Nemotron Streaming English use the NVIDIA Open Model License. [Unified card](https://huggingface.co/nvidia/parakeet-unified-en-0.6b), [Nemotron English card](https://huggingface.co/nvidia/nemotron-speech-streaming-en-0.6b)
- Nemotron 3.5 uses OpenMDW 1.1. [Model card](https://huggingface.co/nvidia/nemotron-3.5-asr-streaming-0.6b)

Converting or community-hosting ONNX files does not replace the upstream model license. Record both conversion provenance and original license in the model catalog.

## Staged replacement

1. **Prove the path:** integrate a pinned sherpa-onnx arm64 AAR and one pinned Nemotron English INT8 package (160 ms balanced). Implement one `StreamingSpeechBackend`; preserve the app's existing partial/final callback flow, catch-up behavior, and lifecycle cancellation.
2. **Validate on phones:** test live partial stability, final-text equality, cold/warm load, peak RAM, real-time factor, battery/thermal behavior, cancellation, download deletion, and 30-second utterances on at least one slower device and one recent Galaxy-class device. This is the unresolved evidence; desktop/GPU numbers do not answer it.
3. **Expand the catalog:** add English profiles, then Nemotron 3.5 with explicit/auto language selection. Treat each official profile artifact as a separate retained download.
4. **Migrate compatibility models:** run TDT v3 as final-text/offline and Unified as buffered streaming. Compare representative recordings against the existing runtime before making Sherpa the only NVIDIA backend.
5. **Delete redundancy:** remove `parakeet-native/`, vendored `transcribe-rs`, JNI Kotlin declarations, Cargo/NDK Gradle tasks, copied Parakeet native outputs, and the explicit ORT dependency/extraction if dependency inspection confirms nothing else uses it. Keep legacy Whisper/GGML and Moonshine untouched.

## Remaining uncertainty

The recommendation is architectural, not a performance guarantee. The exact arm64 APK increase, peak RAM, whether 80 ms stays real-time on target phones, and any interaction with Moonshine's packaged native dependencies require the phase-one build and device measurements. Those uncertainties justify a staged cutover, not a permanent second NVIDIA stack.

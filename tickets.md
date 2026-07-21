# Tickets: Selectable NVIDIA models and live transcription

Build the recognition model catalog and replace the custom NVIDIA runtime with Sherpa-ONNX through dependency-ordered vertical slices. Source: [selectable NVIDIA recognition models and live transcription spec](docs/specs/selectable-nvidia-models-and-live-streaming.md).

Work the **frontier**: any ticket whose blockers are all done.

## Create the managed recognition model catalog

**What to build:** Give people one Model Options catalog for the existing Moonshine and Parakeet choices. Each recognition model shows why to choose it, approximate transfer/storage requirements derived from its manifest, transcription behavior, language support, performance class, source, installation state, and selection state. Selecting an uninstalled choice confirms and validates its download; installed choices are retained and inactive choices can be deleted.

**Blocked by:** None — can start immediately.

- [x] Moonshine Small remains the default and existing Moonshine and Whisper recognition continue working.
- [x] Existing recognition models are represented by one authoritative catalog/store rather than model-specific UI state.
- [x] Every downloadable package has an immutable identity, pinned source revision, complete artifact manifest, non-null hashes, and completion marker.
- [x] Selecting an uninstalled package shows its source, actual manifest size, required free space, and cellular status before confirmation.
- [x] A confirmed successful download is validated, marked complete, and selected automatically.
- [x] Interrupted, insufficient-space, HTTP-failed, and hash-mismatched downloads never appear installed or selected.
- [x] Installed packages remain available when another model is selected.
- [x] Inactive packages can be deleted after their runtime is released.
- [x] The selected package cannot be deleted and explains that another installed model must be selected first.
- [x] JVM tests cover catalog metadata, manifest totals, installation detection, selection, validation, deletion guards, and failed downloads.
- [x] Thin UI tests cover descriptions, status, confirmation, progress, selection, and deletion behavior.

### Resolution

Implemented by `4310f7a` (`feat: add managed recognition model catalog`).

## Ship Nemotron English Balanced live transcription

**What to build:** Add Nemotron Speech Streaming EN 0.6B with its 160 ms Balanced package as the first complete Sherpa-ONNX recognition path. A person can download it from its model card, select it, dictate with revisable live text, and receive a complete final transcript without dropped speech.

**Blocked by:** Create the managed recognition model catalog.

- [x] A pinned Sherpa-ONNX Android runtime is integrated for arm64 without incompatible duplicate native libraries.
- [x] The pinned 160 ms INT8 Nemotron package downloads, validates, installs, selects, loads, and releases through the shared model flow.
- [x] The IME publishes live recognition as composing text and commits one final result without duplication.
- [x] Recognition-activity callers see partial text in the overlay and receive only the final result.
- [x] Personal vocabulary is applied consistently to partial and final results.
- [x] Streaming preserves all recorded audio when decoding falls behind.
- [x] Partial publication pauses when necessary, exposes Catching up, and resumes or finalizes from the complete audio stream.
- [x] Cancellation, runtime initialization failure, out-of-memory loading, and unavailable input connections fail safely.
- [x] A failed load retains the valid download and offers another installed recognition model.
- [x] Contract tests use a fake streaming backend to cover chunk forwarding, partials, Catching up, finalization, cancellation, and errors.
- [x] An opt-in native smoke check transcribes a known short sample with the pinned package.
- [x] APK inspection proves the selected Sherpa packaging works on arm64 and records its size impact.

### Resolution

Implemented by `ec0f6a7` (`feat: add Nemotron Balanced streaming`).

## Add Low-latency and Accuracy Nemotron profiles

**What to build:** Extend the English Nemotron card with separately downloadable 80 ms Low latency and 560 ms Accuracy profiles. People can see, install, select, retain, and delete each profile independently without cluttering the main model list.

**Blocked by:** Ship Nemotron English Balanced live transcription.

- [x] One Nemotron English card contains Low latency, Balanced, and Accuracy controls.
- [x] Balanced remains the default profile.
- [x] Each profile clearly states its latency/accuracy trade-off and separate download requirement.
- [x] The 80 and 560 ms packages use pinned revisions, complete manifests, hashes, and independent completion markers.
- [x] Installing or deleting one profile does not change another profile's installed assets.
- [x] The selected profile is protected by the same inactive-only deletion rule as other recognition models.
- [x] Switching to an installed profile requires no download; switching to an uninstalled profile uses the shared confirmation flow.
- [x] UI and store tests cover grouped presentation, independent state, selection, retention, and deletion.
- [x] Opt-in smoke checks prove all three profiles can load and transcribe.

### Resolution

Implemented by `1aa54e6` (`feat: add Nemotron latency profiles`).

## Add safe manual model updates

**What to build:** Let people update installed recognition model packages explicitly without risking the currently working version. The catalog reports an available immutable version, confirms the large transfer, validates it alongside the old version, and switches only after success.

**Blocked by:** Create the managed recognition model catalog.

- [x] No recognition model or profile updates automatically.
- [x] The catalog can distinguish the installed immutable version from an available immutable version.
- [x] Update available appears only when a different valid pinned version exists.
- [x] Starting an update shows the same source, size, space, and cellular confirmation as a new installation.
- [x] The working version remains selected and usable while the replacement downloads.
- [x] A validated replacement is activated atomically before the previous version is removed.
- [x] Interrupted, corrupt, or failed updates retain the previous valid version and selection.
- [x] Tests cover update discovery, confirmation, parallel-version storage, failed validation, activation, and cleanup.

### Resolution

Implemented by `683be27` (`feat: add safe manual model updates`).

## Move Parakeet TDT to Sherpa-ONNX

**What to build:** Run Parakeet TDT 0.6B V3 through Sherpa-ONNX as an accuracy-focused final-only choice. Existing Parakeet users keep working while the custom Rust decoder becomes unnecessary for this model.

**Blocked by:** Ship Nemotron English Balanced live transcription.

- [x] Parakeet TDT appears as its own recognition model with a concise final-only explanation and demanding performance class.
- [x] Its package uses immutable community or publisher artifacts, complete manifests, hashes, and visible source attribution.
- [x] CC BY 4.0 attribution is included in model details/notices.
- [x] Selection, download, retention, inactive deletion, update, loading, transcription, and release use the shared flows.
- [x] No live-text claim or cache-aware claim is made for TDT.
- [x] Representative known utterances produce acceptable final-text parity before the Rust TDT path is retired.
- [x] Load failure retains assets and offers another installed model.
- [x] Store/UI tests and an opt-in native smoke check cover the end-to-end TDT choice.

### Resolution

Implemented by `fb5e00f` (`feat: run Parakeet TDT with Sherpa-ONNX`).

## Move Parakeet Unified to buffered Sherpa streaming

**What to build:** Offer Parakeet Unified EN 0.6B as a distinct model using Sherpa's buffered streaming path. People receive live partial text with accurate guidance that Unified recomputes buffered context and is not the preferred 80 ms cache-aware model.

**Blocked by:** Ship Nemotron English Balanced live transcription.

- [x] Unified has a distinct model identity, directory, manifest, status, and card; it cannot share or masquerade as TDT assets.
- [x] Its package uses immutable artifacts, complete manifests, hashes, and visible source attribution.
- [x] The card describes buffered live transcription without claiming cache-aware behavior.
- [x] The card does not advertise an unsupported 80 ms Unified profile.
- [x] Download, selection, retention, deletion, update, loading, live partials, finalization, and release use the shared flows.
- [x] Buffered processing preserves audio and uses Catching up when it falls behind.
- [x] Representative known utterances produce acceptable partial/final parity before the custom Unified path is retired.
- [x] Store/UI/streaming-contract tests and an opt-in native smoke check cover the end-to-end Unified choice.

### Resolution

Implemented by `8e7a7dd` (`feat: add Parakeet Unified streaming`).

## Add multilingual Nemotron 3.5

**What to build:** Add Nemotron 3.5 ASR Streaming 0.6B for people who need live multilingual dictation. They can choose a supported recognition language or Auto-detect, while languages requiring fine-tuning remain hidden.

**Blocked by:** Ship Nemotron English Balanced live transcription.

- [x] Nemotron 3.5 has its own model card, package identity, manifest, source, installation state, and demanding performance guidance.
- [x] Only NVIDIA's transcription-ready and broad-coverage languages are selectable.
- [x] Adaptation-ready languages are absent from the user-facing language list.
- [x] Auto-detect uses the model's supported prompt/detection behavior and reports the detected recognition language where useful.
- [x] The default explicit prompt remains compatible with English when Auto-detect is not selected.
- [x] Package downloads are immutable, hash-checked, retained, deletable when inactive, and manually updateable.
- [x] Live partials, Catching up, finalization, cancellation, and failure fallback use the shared streaming behavior.
- [x] OpenMDW 1.1 license/source information is included in model details/notices.
- [x] Tests cover eligible-language filtering, prompt selection, Auto-detect, model state, and live-stream behavior.
- [x] Opt-in smoke checks cover English, at least one additional transcription-ready language, and Auto-detect.

### Resolution

Implemented by `cfb42c4` (`feat: add multilingual Nemotron 3.5`).

## Remove the redundant Rust NVIDIA runtime

**What to build:** Complete the expand-contract migration by removing the project-owned Rust/JNI NVIDIA inference stack once Sherpa serves TDT and Unified with proven parity. The APK and build use one maintained NVIDIA runtime while legacy Whisper remains intact.

**Blocked by:** Move Parakeet TDT to Sherpa-ONNX; Move Parakeet Unified to buffered Sherpa streaming.

- [x] No production NVIDIA recognition model calls the custom Rust/JNI runtime.
- [x] Custom Parakeet Rust engine, decoder, JNI bridge, tests, and generated native outputs are removed.
- [x] Cargo/NDK build tasks and obsolete ONNX Runtime extraction/copy wiring are removed.
- [x] Native packaging no longer relies on first-match behavior to mask incompatible duplicate ONNX Runtime libraries.
- [x] Legacy Whisper/GGML native code and behavior remain present and tested.
- [x] Moonshine behavior remains present and tested.
- [x] Normal debug assembly, unit tests, and lint complete without Rust/cargo-ndk prerequisites.
- [x] APK inspection proves there is one compatible NVIDIA inference stack and only intended arm64 native libraries.
- [x] Documentation no longer instructs contributors to build or maintain the removed runtime.

### Resolution

Commit `d12ee49` removes the Rust/JNI runtime, Cargo/NDK wiring, duplicate-library packaging workaround, generated outputs, and obsolete build documentation. Follow-up backend contract tests cover Whisper transcription/cleanup and Moonshine buffered and streaming behavior. Unit tests, debug assembly, and lint pass; the broken Compose mutable-collection detector is disabled, the microphone foreground-service permission is explicit, and the obsolete billing activity declaration is removed. APK inspection shows Sherpa, one ONNX Runtime library, legacy Whisper, and Moonshine libraries with no `libparakeet_voiceinput.so`.

## Calibrate and release the complete model catalog

**What to build:** Validate the complete catalog on real hardware and finalize the guidance people use to choose models. Release checks cover speed, memory, thermals, licenses, downloads, existing backends, and APK contents.

**Blocked by:** Add Low-latency and Accuracy Nemotron profiles; Add safe manual model updates; Add multilingual Nemotron 3.5; Remove the redundant Rust NVIDIA runtime.

- [ ] Actual pinned manifest sizes, not planning estimates, are displayed for every model/profile.
- [ ] Light, Balanced, and Demanding labels are reviewed against measured memory and real-time factor rather than phone-name heuristics.
- [ ] On an S24/S25-class device, English Nemotron Balanced produces first useful text within one second.
- [ ] On the same class of device, Balanced keeps pace with incoming audio and produces a caught-up final within one second after stopping.
- [ ] Every tested slower-device failure mode preserves audio and either catches up or offers a safe model switch.
- [ ] Latency, real-time factor, peak memory, sustained thermals, and backlog behavior are recorded for all English Nemotron profiles.
- [ ] Model descriptions accurately distinguish live, final-only, buffered, cache-aware, English, and multilingual choices.
- [ ] Sherpa and every model family have correct source, license, and attribution notices.
- [ ] Download, cellular confirmation, insufficient space, corruption, retention, deletion, and manual update flows pass end-to-end regression checks.
- [ ] Moonshine, Whisper, IME, recognition activity, VAD, cancellation, personal vocabulary, and error behavior pass regression checks.
- [ ] Release APK contents, ABI, native-library count, and size are reviewed and documented.
- [ ] The user-facing catalog remains advisory and does not block installation based on guessed device capability.

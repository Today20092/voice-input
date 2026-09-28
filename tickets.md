# Tickets: Voice Input

Build the recognition model catalog and replace the custom NVIDIA runtime with Sherpa-ONNX through dependency-ordered vertical slices. Source: [selectable NVIDIA recognition models and live transcription spec](docs/specs/selectable-nvidia-models-and-live-streaming.md).

Work the **frontier**: any ticket whose blockers are all done.

The SHARE adoption plan is defined in [the upstream adoption specification](docs/specs/upstream-share-adoption.md), with [all 35 upstream commits reviewed](docs/research/upstream-share-adoption-review-2026-09-26.md). Prioritize SHARE-01, SHARE-02, and SHARE-05. Evaluation tickets do not authorize replacing the default recognizer or removing existing runtimes.

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

**Plan ID:** SHARE-03

**Triage:** ready-for-agent

**Priority:** P2

**What to build:** Reopen explicit, versioned manual updates as a bounded extension of RecognitionModelLifecycle. Keep the working installation while downloading and validating a known pinned successor, coordinate activation with active recognition, and distinguish optional upgrades from missing or incompatible assets. Follow [the adoption specification](docs/specs/upstream-share-adoption.md). Upstream references: `867667a`, `4dfd8c4`, `95e82ad`.

**Blocked by:** Create the managed recognition model catalog.

- [ ] Represent the usable installed version and an optional known pinned successor separately; no automatic transfers, remote discovery service, or fabricated update notification is introduced.
- [ ] Reuse the source, size, free-space, and cellular confirmation flow before starting an update.
- [ ] Stage and hash-check the successor separately while the old installation remains selectable and usable; retain legacy installation compatibility.
- [ ] Coordinate activation with RecordingSession and runtime ownership so an active dictation is not invalidated, a new session cannot race replacement, and the next load uses the validated version.
- [ ] Activate atomically and remove superseded assets only after successful activation; interruption, cancellation, insufficient space, corrupt data, and activation failure retain the previous selection and valid files.
- [ ] Startup/readiness and Compose perform no full-model hashing. Keep install-time validation and cheap versioned markers.
- [ ] Tests exercise two explicit pinned fixtures, old-version readiness, activation failure, interrupted updates, active-session coordination, cleanup, and reloading. Production update UI appears only for a real catalog successor.
- [ ] Relevant unit, download UI, assembly, and lint checks pass. Verify an interrupted update and successful reload on a device before release.

### History

Originally implemented by `683be27`, then deliberately removed by `47566e2` (`refactor: remove speculative model update machinery`). The earlier checked criteria did not describe current master. Reopened on 2026-09-26 for the explicit upstream-adoption request. Adapt the useful invariants to the current lifecycle module; do not restore the old implementation wholesale.

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

## Add predictive-back transitions to settings navigation

**What to build:** Make Android edge-back gestures interactively cross-fade from any settings destination to the previous destination, eliminating the frozen pause before the settings home screen appears. Preserve forward navigation, cancelled gestures, system back, and the in-app back arrow. See [the predictive-back settings specification](docs/specs/predictive-back-settings-navigation.md).

**Blocked by:** None — can start immediately.

**Triage:** ready-for-human

- [x] The compatible Compose navigation stack uses Navigation Compose 2.8.0 or newer without raising the minimum supported Android version.
- [ ] Swiping back from Model Options on Android 15 or newer previews the settings home screen with an interactive cross-fade instead of freezing until commit.
- [ ] Swiping back from Input keeps the outgoing and incoming page text visually distinct instead of compositing both pages' text on top of each other.
- [x] Committing the gesture returns to the correct previous destination, while cancelling it retains the current destination.
- [x] Another settings destination exhibits the same predictive-back behavior through the shared navigation host.
- [x] System back and the in-app back arrow continue to return to the correct previous destination.
- [x] Forward navigation retains clear transition feedback.
- [ ] An instrumentation check covers settings back-stack behavior at the shared navigation-host seam, and a real-device or emulator check verifies the interactive animation.
- [ ] Relevant unit, instrumentation, build, and lint checks pass after the dependency upgrade.

### Beta observation

Confirmed in the signed `v1.4.2-beta.5` prerelease: start on Input, swipe from the left edge toward the right to return to the settings home screen, and hold or continue the gesture. Text from both pages remains visible in the same area and overlaps during the transition. A screen recording supplied during beta testing captures the behavior.

## Exclude test audio from the Nemotron 3.5 Multilingual installation

**What to build:** Install only the files required to run Nemotron 3.5 Multilingual so beta testers are not blocked waiting for packaged English and Japanese test recordings.

**Blocked by:** None — can start immediately.

**Triage:** ready-for-agent

- [ ] Starting a Nemotron 3.5 Multilingual download does not request `test_wavs/en.wav` or `test_wavs/ja.wav`.
- [ ] The model becomes installed after its runtime model and token files download and validate.
- [ ] Existing incomplete installations that are waiting on test recordings can recover without downloading completed runtime files again.
- [ ] A focused catalog check prevents non-runtime test assets from returning to the installation manifest.

### Beta observation

In the signed `v1.4.2-beta.5` prerelease, Download Progress remained at 4 of 6 files while showing `test_wavs/en.wav` and `test_wavs/ja.wav` as the final two resources.

## Resume interrupted model downloads across retries

**What to build:** Preserve validated files and partial transfer progress when a large recognition-model download stalls or is retried, avoiding another full transfer of completed data.

**Blocked by:** None — can start immediately.

**Triage:** ready-for-agent

- [ ] Retrying an interrupted model installation skips files that already downloaded and validated.
- [ ] Retrying a partially downloaded file continues from its saved byte position when the server supports range requests.
- [ ] If a server cannot resume safely, the UI explains that the affected file must restart instead of silently presenting it as resumed.
- [ ] A failed retry cannot replace a previously validated file or mark an incomplete model as installed.
- [ ] A focused download check covers interruption and retry of a large model artifact.

### Beta observation

In the signed `v1.4.2-beta.5` prerelease, retrying the stalled Nemotron 3.5 Multilingual installation appeared to restart the full 657.6 MB `encoder.int8.onnx` transfer.

## Return truthful recognition activity results

**What to build:** Make one-shot Android speech-recognition callers receive the recognized transcript without invented metadata. Successful recognition returns one final result, while cancellation returns no transcript. See [the Android voice-input protocol alignment spec](docs/specs/android-voice-input-protocol-alignment.md).

**Blocked by:** None — can start immediately.

- [x] A successful recognition activity result contains one non-empty transcript.
- [x] Results omit confidence scores when the selected backend does not supply a real calibrated confidence value.
- [x] Cancellation returns a canceled result with no transcript.
- [x] A focused contract test covers successful and canceled result construction.
- [x] Relevant unit tests and lint pass.

### Resolution

Implemented by `14b268c` (`fix: return truthful recognition activity results`).

## Remove the nonfunctional recognition service

**What to build:** Make the installed app advertise only voice-input protocols it actually supports by removing the empty recognition-service implementation and its disabled production declaration, while preserving the IME, recognition activity, and existing keyboard compatibility workaround. See [the Android voice-input protocol alignment spec](docs/specs/android-voice-input-protocol-alignment.md).

**Blocked by:** None — can start immediately.

- [x] The empty recognition-service implementation and misleading disabled declaration are removed.
- [x] The merged manifest still exposes the input method and one-shot recognition activity.
- [x] The existing test-category keyboard compatibility workaround remains unchanged.
- [x] No production `RecognitionService` provider is advertised.
- [x] Relevant unit tests, manifest processing, assembly, and lint pass.

### Resolution

Implemented by `650bebb` (`Remove nonfunctional recognition service`).

## Make recognition-model readiness catalog-driven

**Triage:** ready-for-agent

**What to build:** Make every managed recognition model use one lifecycle module for selection, installed-model validation, and download requirements, so starting voice input and viewing Model Options agree about whether the selected model is ready.

**Blocked by:** None — can start immediately.

- [x] The selected recognition model is resolved from runtime and variant settings in one place.
- [x] Readiness checks use the selected model's validated artifacts, including bundled-model behavior.
- [x] Starting voice input requests the correct model download when the selected model is not installed.
- [x] Model Options displays readiness from the same lifecycle behavior used by voice input.
- [x] Focused tests cover at least one installed and one missing model for each supported runtime family.
- [x] Relevant unit tests, assembly, and lint pass.

### Resolution

Implemented by `149f568` (`refactor: centralize recognition model readiness`).

## Route recognition-model loading and management through the lifecycle module

**Triage:** ready-for-agent

**What to build:** Make recording, Model Options, and model downloads use the recognition-model lifecycle module for loading, selection updates, installation, deletion, and runtime release, leaving no duplicated per-model lifecycle policy in callers.

**Blocked by:** Make recognition-model readiness catalog-driven.

- [x] Loading the selected recognition model is initiated through the lifecycle module while preserving warm-runtime behavior.
- [x] Successful installation and selection update the correct runtime and variant settings through one path.
- [x] Deleting or replacing a model safely releases any runtime that owns its artifacts.
- [x] Repeated runtime-family branches for lifecycle policy are removed from recording, settings, and download callers.
- [x] Existing live transcription and final-only transcription behavior remains unchanged across supported recognition models.
- [x] Relevant unit, instrumentation, assembly, and lint checks pass.

## Lock down recording-session behavior

**Triage:** ready-for-agent

**What to build:** Add runnable checks that preserve the current utterance lifecycle before it is deepened, covering recorder initialization, stop policy, streaming and final-only recognition, cancellation, and cleanup without changing user-visible behavior.

**Blocked by:** None — can start immediately.

- [x] Checks cover recorder initialization failure and bounded retry behavior.
- [x] Checks cover manual, end-of-speech, and duration-limit stopping, including buffered tail handling.
- [x] Checks cover partial live transcription followed by one final transcript.
- [x] Checks cover final-only transcription without intermediate text.
- [x] Checks prove cancellation and reset release jobs, recorder state, buffers, and recognition-model ownership.
- [x] The checks run with the existing test toolchain and pass reliably without microphone hardware.

### Resolution

Implemented by `f233072`, `4a61d22`, and `5ab3750`.

## Deepen the recording session behind AudioRecognizer

**Triage:** ready-for-agent

**What to build:** Concentrate one utterance lifecycle behind the existing AudioRecognizer seam so recorder state, jobs, audio buffers, voice-activity detection, streaming callbacks, stop reasons, final recognition, and cleanup change together while Activity and IME behavior remains stable.

**Blocked by:** Lock down recording-session behavior.

- [x] One recording-session implementation owns the mutable state and ordering rules for a single utterance.
- [x] AudioRecognizer retains a small caller interface for starting, stopping, canceling, and receiving recognition progress and results.
- [x] Existing recognition-model adapters remain internal to the recording flow; no hypothetical adapter is introduced.
- [x] Recorder retry, microphone-blocked detection, voice-activity stopping, buffer growth, and tail draining preserve their verified behavior.
- [x] Activity and IME callers require no recognition-session policy of their own.
- [x] The recording-session checks and relevant unit, instrumentation, assembly, and lint checks pass.

### Resolution

`RecordingSession` now owns each utterance's recorder, jobs, audio, stop/VAD state, streaming replay, and recognition-model ownership. `AudioRecognizer` keeps its existing Activity/IME-facing contract.

## Remove full model hashing from interactive startup

**Triage:** ready-for-agent

**What to build:** Keep model integrity validation at download/install time, but make voice-input startup and Model Options trust a versioned completion marker plus cheap artifact metadata so opening the microphone never hashes entire model files.

**Blocked by:** Make recognition-model readiness catalog-driven.

- [x] Successful model installation verifies configured hashes before writing a versioned completion marker.
- [x] Voice-input startup performs no full-file hashing on the main thread.
- [x] Model Options performs no full-file hashing during composition or recomposition.
- [x] Missing, truncated, or version-mismatched artifacts are still reported as not installed.
- [x] Backend load failure invalidates readiness or produces a clear recovery/download path.
- [x] A focused test proves interactive readiness checks do not read complete artifact contents.
- [x] Relevant unit tests, startup tracing, assembly, and lint pass.

### Resolution

Implemented by `44fb6fe`, `5ab3750`, and `bef4320`.

## Verify long Moonshine dictation and improve segmentation only if needed

**Triage:** ready-for-agent

**What to build:** Establish whether Moonshine's existing streaming segmentation adequately handles longer dictation, and make the smallest demonstrated improvement only if it does not. Short-message dictation must retain its current stopping behavior. Do not add a separate VAD model or a new recording mode merely because recordings can exceed 30 seconds; a documented finding that no change is needed is a valid resolution.

**Blocked by:** None — can start immediately.

- [ ] Inspect the installed Moonshine runtime and current recording flow to distinguish built-in speech segmentation from app-level silence auto-stop and duration limits; document which behavior is already available.
- [ ] Establish a baseline with short messages, approximately 30-second recordings, and recordings of at least two minutes, including natural pauses, extended thinking pauses, and uninterrupted speech. Use existing manual-stop controls where available and record the settings used.
- [ ] Check partial and final transcript continuity, missing or repeated words at segment boundaries, premature stopping, and whether processing falls behind recording. Measure processing overhead, memory growth, and time from Stop to the final speech-recognition transcript on the user's phone; report S1 rewriting separately. Do not infer audio/VAD performance from the existing S1 diagnostic archive.
- [ ] Record an evidence-based decision. If existing behavior is adequate, close with the results and no production changes. If a problem is demonstrated, implement only the necessary correction, preferring Moonshine's existing VAD, segment completion, and configuration over an additional detector.
- [ ] Any changed long-recording path continues capture across segment boundaries until manual Stop, preserves boundary audio and transcript ordering, and handles uninterrupted speech without unbounded segments. Preserve current short-message behavior and defaults in both Activity and IME entry points; elapsed duration alone must not silently change stop behavior.
- [ ] If evidence requires a new user-facing long-dictation mode or a separate VAD model, document the concrete need and proposed behavior for user agreement before expanding scope. Neither is pre-authorized by this ticket. Leave other recognition backends and S1 rewriting unchanged.
- [ ] For production changes, add focused runnable regression checks for the demonstrated failure and short-message behavior, run relevant build/test/lint checks, and repeat the affected device scenarios. If device measurements are unavailable, document that validation gap rather than claiming a benefit or completing unverified criteria.

---

## Clarify and reorganize Model Options

**Triage:** ready-for-agent

**What to build:** Make Model Options accurately explain recognition behavior and keep recognition-model selection separate from transcript cleanup. Present concise model choices first, with technical attribution and version details available on demand.

**Blocked by:** None — can start immediately.

- [ ] Whisper is described as final-only transcription rather than live transcription.
- [ ] Model information distinguishes live transcription, buffered live transcription, and final-only transcription: Moonshine and Nemotron are live, Parakeet Unified is buffered live, and Parakeet TDT and Whisper are final-only.
- [ ] S1-mini transcript cleanup has a dedicated settings destination and is no longer presented inside the recognition-model catalog flow.
- [ ] Recognition-model rows show a compact summary containing recognition behavior, languages, download size, installed size, and installation/selection status.
- [ ] Source, license/attribution, version, and other technical information remain available from a model-details action instead of crowding the primary row.
- [ ] Whisper English and multilingual variants appear directly with the Whisper choice rather than at the bottom of the catalog after selection.
- [ ] Model Options and its parent settings summary identify the exact selected variant where a family has multiple choices.
- [ ] Focused tests cover model-presentation metadata and the settings navigation/placement behavior at stable public seams.
- [ ] Relevant unit tests, instrumentation tests, assembly, and lint pass.
- [ ] Whisper native-runtime modernization and Whisper large-v3-turbo Q5/Q8 models remain out of scope.

## Diagnose intermittent waveform behavior when opening voice input

**Triage:** needs-info

**What to build:** Reproduce and fix the reported intermittent waveform behavior when opening voice input. The user suspects the beta speech-recognition popup change, but the visible symptom and affected entry point are not yet specified.

**Blocked by:** Description of the waveform behavior, affected entry point, and a reproducible case. The supplied 1.4.3 archive identifies Orukeet but lacks waveform draw timing and popup state.

- [ ] Establish whether the waveform appears late, stays flat, disappears, or is clipped, and whether the issue occurs in the IME, speech activity, or Test dictation.
- [ ] Compare the same scenario with the unobtrusive popup enabled and disabled before attributing the problem to that feature.
- [x] Add a first waveform frame timing event and the popup setting/build revision to diagnostic exports without collecting audio or transcript text.
- [ ] Reproduce the failure, add a focused regression check, and fix its cause.
- [ ] Verify the reported phone scenario. Static waveform layout checks alone do not resolve this ticket.

Use [the phone test guide](docs/phone-performance-test.md) for reproduction notes and export instructions.

The [pre-change diagnostic baseline](docs/research/phone-diagnostics-baseline-2026-09-25.md) shows prompt audio arrival and two misleading read-failure events after shutdown. Correcting those events does not resolve the visual report.

---

## SHARE-01: Integrate FUTO Keyboard external-provider setup

**Triage:** ready-for-agent

**Priority:** P1

**What to build:** Add a clear way to choose this fork as FUTO Keyboard's external voice-input provider, using its VoiceInputSwitch activity protocol. Preserve a manual setup path for older keyboards. This does not require a shared recognition library or a custom keyboard build. Upstream references: `bf8dc4c`, `76dc7a1`, `86949dd`. See [the adoption specification](docs/specs/upstream-share-adoption.md).

**Blocked by:** None.

- [ ] Detect the supported stable/unstable FUTO Keyboard package and use the actual installed application ID, including the .moonshine and development flavors, as targetPackage.
- [ ] Read-only checks do not change keyboard preferences. An explicit user choice invokes supported switching/confirmation behavior; success, cancellation, unknown results, and missing activities are handled separately.
- [ ] Do not infer that external input is enabled solely because check mode returns the remembered target package. Re-check after returning from setup, ignore stale responses, and avoid repeated setup loops.
- [ ] Add necessary package visibility and resilient keyboard-settings launching. If unsupported or unavailable, show specific manual instructions without crashing or claiming success.
- [ ] Test protocol arguments and lifecycle/result handling. On a device with FUTO Keyboard, verify microphone launch reaches this fork, dictation returns correctly, and cancellation returns to typing; record the keyboard version and test both supported and fallback paths.
- [ ] Relevant unit/UI, assembly, and lint checks pass. Preserve existing generic recognition-activity and IME integration.


## SHARE-02: Make IME text insertion session-safe

**Triage:** ready-for-agent

**Priority:** P1

**What to build:** Handle spacing, partial composition, editor selection changes, and final commit as one input-session operation. Adapt upstream's insertion fixes without forcing the cursor to a guessed location or losing existing text. Upstream references: `00d649b`, `92e96b2`. See [the adoption specification](docs/specs/upstream-share-adoption.md).

**Blocked by:** None.

- [ ] Reset insertion state on a genuinely new input session, editor change, cancellation, and completion; preserve it correctly across an input-view recreation for the same active session.
- [ ] Suppress unchanged successful composing updates, but do not suppress an identical transcript in a later session or a retry after a failed editor operation. Return the real result of the input-connection operation.
- [ ] Determine leading/trailing spacing from current valid context for both final-only and streaming paths. Cover whitespace, punctuation, text on either side, empty results, and language-appropriate behavior without indiscriminately adding spaces.
- [ ] Preserve selected-text replacement and subsequent user edits. On lost composition ownership, use a documented non-destructive policy instead of cursor-forcing heuristics. Commit the final delivered transcript exactly once after cleanup and vocabulary corrections.
- [ ] Handle a missing/replaced input connection and cancellation safely. Surrounding text never enters standard diagnostics or logs.
- [ ] Focused editor-contract tests cover repeated partials, final replacement, selection/cursor movement, rejected operations, recreation, and two identical consecutive utterances. Verify real editors and FUTO Keyboard on-device.
- [ ] Relevant tests, assembly, and lint pass; preserve Activity result behavior.


## SHARE-04: Show reactive model readiness and upgrade notices

**Triage:** ready-for-agent

**Priority:** P2

**What to build:** Expose required model repair/download and optional known upgrades consistently in Model Options and a compact recognition/settings notice. Derive notices from the lifecycle's version-aware state rather than independent UI checks. Upstream references: `867667a`, `95e82ad`. See [the adoption specification](docs/specs/upstream-share-adoption.md).

**Blocked by:** Add safe manual model updates.

- [ ] Differentiate missing/incompatible assets, a usable current installation, and an optional pinned upgrade. A working older model keeps dictating without a compulsory update.
- [ ] Reflect selection, successful installation, deletion, activation, failure, and returning to the app without stale notices or an app restart.
- [ ] Offer an explicit action through the existing download confirmation. Avoid automatically opening a downloader, interrupting active recording, or performing network requests or full-model hashing during composition.
- [ ] Preserve the distinct legacy Whisper migration flow and the fork's GitHub app updater; a model update must not masquerade as an application update.
- [ ] Unit and UI tests verify notice transitions, optional-update dismissal/continued use, installed-version changes, and absence of notifications when no real successor exists.
- [ ] Relevant tests, assembly, and lint pass; confirm the notice clears after a successful upgrade on-device.


## SHARE-05: Make language settings follow the selected model

**Triage:** ready-for-agent

**Priority:** P1

**What to build:** Make Languages and model capability guidance agree with the selected recognition model. The current Languages page can fall through to Whisper controls for non-Whisper models. Reuse existing model presentation and coordinate with 'Clarify and reorganize Model Options'. Upstream reference: `7633e85`. See [the adoption specification](docs/specs/upstream-share-adoption.md).

**Blocked by:** None.

**Working branch:** codex/share-05

**Claimed by:** SHARE-05 chat (96da worktree)

- [ ] Selecting each shipped model exposes only its supported language controls and accurate automatic-detection behavior. Preserve Nemotron multilingual Auto-detect and Cohere's explicit selector; keep saved Whisper preferences when switching away and back.
- [ ] Whisper training-hour descriptions, language-specific-model controls, and download effects run only for Whisper. Visiting or changing language settings for another model never starts an unrelated Whisper download.
- [x] Share capability/presentation data between Languages and Model Options where it actually varies; avoid a second conflicting list of model capabilities.
- [x] Distinguish inference vocabulary hints from app-level personal vocabulary corrections. Do not disable the existing correction stage merely because a model cannot accept hints.
- [ ] Tests cover navigation and settings effects for every runtime family, switching variants, invalid/stale saved language choices, and no unrelated downloads. Keep currently supported recognition-language behavior.
- [ ] Relevant unit/UI, assembly, and lint checks pass. Verify at least an English-only model, Nemotron multilingual, Cohere, and Whisper on-device.

### Implementation checkpoint (2026-09-28)

Whisper controls and download effects now live in a Whisper-only composition and wait for saved preferences. Managed models share language guidance and selectors with Model Options. Nemotron keeps Auto-detect; Cohere keeps explicit selection. Invalid saved selector values display the runtime's English fallback. Stale Whisper language IDs recover to English. Parakeet TDT v3's card now uses its existing 25-language model metadata instead of the stale English-only label. Recognition and personal vocabulary processing are unchanged.

Verification: focused presentation tests exercised red/green; the full `:app:testDevDebugUnitTest` result was 142 passed, one skipped, zero failures. `:app:compileDevDebugAndroidTestKotlin` and `git diff --check` passed. Separate Standards and Spec reviews found no actionable issues. Five new UI tests cover model navigation, variants, language selection, saved preferences, stale values, and Whisper download launches, but have not run yet. Assembly, lint, emulator checks, and physical-device checks remain pending the shared slot. The ticket is not complete.


## SHARE-06: Add explicit Bluetooth microphone selection

**Triage:** ready-for-agent

**Priority:** P2

**What to build:** Let a user choose an available Bluetooth microphone or the phone microphone and see which route is actually active. Implement route ownership inside the recording flow while retaining our waveform, history, and cancellation behavior. Upstream reference: `27966c9` and its shared microphone UI. See [the adoption specification](docs/specs/upstream-share-adoption.md).

**Blocked by:** None.

- [ ] Show the route control only when meaningful and report the actual active route; an unavailable or rejected Bluetooth route must not appear active.
- [ ] Use supported Android routing behavior with the required version/permission handling. Keep current default capture behavior until the user makes an explicit choice.
- [ ] Release routing on finish, cancel, reset, failure, and destruction; handle disconnects and failed route changes with a clear fallback.
- [ ] Preserve audio/transcript continuity. If changing the device requires starting a new utterance, make that explicit and retain the current recording instead of silently dropping it.
- [ ] Device names and other private surrounding information do not enter standard diagnostic exports; record only useful permitted route/state categories.
- [ ] Focused state tests and real phone/headset tests cover route selection, denied/unavailable routing, disconnect, repeated sessions, Activity, and IME. Record Android/headset details and any unsupported combinations.
- [ ] Relevant tests, assembly, and lint pass; do not mark hardware validation complete without a real headset test.


## SHARE-07: Keep waveform and live transcript visible together

**Triage:** ready-for-agent

**Priority:** P2

**What to build:** Keep waveform, current partial transcript, selected-model caption, and processing/catch-up status in one coherent recognition UI state. Retain our visual design and behavior rather than importing the upstream screen wholesale. Upstream reference: `53db738`. See [the adoption specification](docs/specs/upstream-share-adoption.md).

**Blocked by:** None.

- [ ] During Activity dictation, live text updates coexist with recording feedback and do not replace the waveform view on every callback.
- [ ] During processing or Catching up, preserve the latest relevant partial text without presenting it as the final delivered transcript. Clear it for a new session or cancellation.
- [ ] IME composition and Activity results remain correct, and Stop, Cancel, language selection, download/error actions, and the optional popup keep working.
- [ ] Use accessible text semantics; do not copy the Canvas-only transcript rendering as a requirement. Check font scaling, TalkBack, orientation, long text, and existing safe-area padding.
- [ ] UI/state tests cover interleaved waveform, partial, status, and completion events without stale-session updates. Relevant tests, assembly, and lint pass.
- [ ] Verify on-device. Keep the separate intermittent-waveform report open unless its specific symptom is reproduced and resolved; this ticket alone does not establish its cause.


## SHARE-08: Recover once from a recoverable streaming failure

**Triage:** ready-for-agent

**Priority:** P2

**What to build:** Allow a classified recoverable streaming failure to finish from the complete retained recording through a clean same-model attempt. Adapt upstream's fallback idea at our existing recording/backend interfaces. Upstream reference: `53db738`; replay preservation reference: `dcd9d2b`. See [the adoption specification](docs/specs/upstream-share-adoption.md).

**Blocked by:** None.

- [ ] Define and test which failures permit replay. Cancellation, model corruption, exhausted memory, and ordinary Catching up do not trigger automatic retries.
- [ ] At most one recovery attempt reuses the complete retained audio, selected model/profile/language, and clean runtime state. No recursive retry, silent model switch, or partial-as-final delivery is introduced.
- [ ] Preserve the recorder/session generation guards, audio history, one final result, cleanup ordering, and personal vocabulary. Canceling while recovery runs stops it.
- [ ] If recovery fails, retain the established error/retranscription path and report a content-free failure category rather than concealing the original failure.
- [ ] Fake-backend tests demonstrate recoverable feed/finalization failure handling, exact sample preservation, one attempt only, cancellation, and no retry for excluded errors.
- [ ] Run relevant session/backend tests, assembly, and lint; verify an injected recoverable failure and a canceled recovery on-device. Do not claim a speed improvement from this work.


## SHARE-09: Evaluate a reproducible transcribe.cpp adapter

**Triage:** ready-for-agent

**Priority:** P2

**What to build:** Run a bounded source/build/measurement experiment behind SpeechBackend and StreamingSpeechBackend to decide whether transcribe.cpp earns a place in the fork. Do not replace production runtimes or extract a shared Android library. Upstream references: `e93865c`, `9c7a627`, `53db738`, `65178f8`, `867667a`, `f4d444b`, `3525563`. See [the adoption specification](docs/specs/upstream-share-adoption.md).

**Blocked by:** None; source discovery can start immediately.

- [ ] Resolve the source-availability gate first. FUTO's b9e8a8e pin was not anonymously fetchable and is absent from the public handy-computer repo. Record a fetchable immutable revision and the relevant difference from FUTO's integration, or conclude source feasibility is blocked with evidence.
- [ ] If source is available, create an isolated experimental ARM64 adapter build. Keep existing defaults, installed selections, and production adapters; minimize toolchain changes and document any required NDK/AGP migration.
- [ ] Verify coexistence with legacy GGML and S1's pinned llama.cpp/OpenCL build, native target/symbol packaging, and complete dependency notices. Do not assume differently pinned GGML trees can share one target.
- [ ] Exercise loading, language selection, partials, complete finalization, cancellation, repeated sessions, failure handling, release, and model validation through existing interfaces.
- [ ] Compare at least one shared model family with the current implementation using the specification's fixed-audio phone protocol. Record quantization differences, cold/warm latency, Stop-to-final p50/p95, accuracy, memory, APK/model size, and sustained-run behavior separately from cleanup.
- [ ] Treat CPU as the baseline. GPU support is not established by the share branch and cannot be claimed without its own tested build and measurements.
- [ ] Write a go/no-go recommendation. A documented no-go is valid, but unavailable hardware or source leaves the corresponding measurement/integration work unverified. Any proposal to replace Sherpa requires a new ADR and full model/feature parity evidence.
- [ ] Run relevant adapter tests and build/lint checks for code produced by the experiment; do not release an experimental adapter merely because it compiles.


## SHARE-10: Evaluate ASR4ALL Small as an optional model

**Triage:** ready-for-agent

**Priority:** P3

**What to build:** Evaluate the smallest upstream ASR4ALL choice against our existing small English models. Integrate it as an explicit optional choice only if the measured result justifies the runtime/storage cost. Upstream references: `e93865c`, `65178f8`, `867667a`. See [the adoption specification](docs/specs/upstream-share-adoption.md).

**Blocked by:** SHARE-09: Evaluate a reproducible transcribe.cpp adapter.

**Gate:** Proceed only after SHARE-09 establishes a reproducible, working adapter. A no-go result there does not unblock model implementation.

- [ ] Pin a fetchable model artifact/version, expected sizes and hashes, publisher/license attribution, supported language, and the engine revision required by that artifact; do not mix the old and v2 upstream filenames.
- [ ] Measure against Moonshine Small with the fixed-audio protocol, including live-text stability, final transcript accuracy, Stop latency, peak memory, thermals, and disk/APK cost.
- [ ] Document whether a useful measured benefit or an acceptable explicit tradeoff exists. If not, close with evidence and no production catalog entry.
- [ ] If justified, add through the managed model lifecycle and explicit download flow, with accurate live/final behavior and measured profiles. Preserve default Orukeet and all existing choices.
- [ ] Contract, download, cancellation, finalization, relevant build/lint, and device checks pass before promotion. ASR4ALL Medium/Large remain out of scope until a separate demonstrated need exists.


## SHARE-11: Evaluate Parakeet 110M as an optional model

**Triage:** ready-for-agent

**Priority:** P3

**What to build:** Evaluate upstream's Parakeet TDT/CTC 110M as a smaller English final-only choice, using Moonshine Small and the existing Parakeet TDT model as comparisons. Upstream reference: `e93865c` and the pinned share model catalog. See [the adoption specification](docs/specs/upstream-share-adoption.md).

**Blocked by:** SHARE-09: Evaluate a reproducible transcribe.cpp adapter.

**Gate:** Proceed only after SHARE-09 establishes a reproducible, working adapter. A no-go result there does not unblock model implementation.

- [ ] Identify the exact supported variant and decoder behavior, pin the artifact and hashes, and record attribution, real transfer/storage sizes, and required runtime revision.
- [ ] Measure accuracy, Stop-to-final time, total processing time, memory, and sustained-run behavior on the same phone/audio as the existing comparison models. Do not infer performance from parameter count.
- [ ] Record a go/no-go decision. A duplicate or inferior choice is not added merely to match upstream; no-go with evidence is a valid outcome.
- [ ] If justified, integrate as a separate optional model using the existing lifecycle, download confirmation, selection, and deletion behavior. Keep its final-only and English-language limitations clear.
- [ ] Tests and device checks cover download validation, load/release, cancellation, transcript delivery, and coexistence with other runtimes; relevant assembly/lint pass. Do not replace multilingual Orukeet or the existing TDT choice.


## SHARE-12: Refresh keyboard help and upstream comparison documentation

**Triage:** ready-for-agent

**Priority:** P2

**What to build:** Correct the README's older upstream comparison and make Help explain how to use this fork with FUTO Keyboard. Separate verified behavior from upstream compatibility reports. Upstream references: `41f77ec`, `86949dd`, `a15c965`. See [the adoption specification](docs/specs/upstream-share-adoption.md).

**Blocked by:** None for factual corrections; descriptions of new behavior wait until that behavior ships.

- [ ] Name and date the upstream branch/revision used for comparisons. Acknowledge its multi-model/streaming work without implying this fork lacks equivalent capabilities or claiming unmeasured performance wins.
- [ ] Explain that FUTO Keyboard's built-in recognizer and the external fork are separate choices. Provide the supported switching path and manual fallback appropriate to the shipped app.
- [ ] Keep this fork's package identity, GitHub update source, Orukeet default, retained models, and cleanup/history features accurate. Link source attribution and actual feature availability.
- [ ] Describe any SwiftKey text-loss warning as an upstream report unless reproduced on our build; do not add a blanket incompatibility claim or disable unrelated keyboards without evidence.
- [ ] Document SHARE-03's reopened status accurately and update the product comparison as accepted features ship. Validate links and ensure UI help does not promise unfinished ticket work.

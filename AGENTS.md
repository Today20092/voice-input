# AGENTS.md

Compact orientation for future agents working in this repo.

## Project Purpose

This is a personal fork of FUTO Voice Input. Upstreams:

- GitHub mirror: `https://github.com/futo-org/voice-input`
- Original GitLab: `https://gitlab.futo.org/keyboard/voiceinput`
- Public fork: `https://github.com/Today20092/voice-input`

The goal is to preserve FUTO Voice Input's Android IME/activity UX with Orukeet as the default offline recognizer. Moonshine, NVIDIA Parakeet, Nemotron, and legacy FUTO Whisper/GGML remain selectable from Model Options.

## High-Level Architecture

- Android app module: `app/`
- Kotlin/Compose UI, settings, recording flow: `app/src/main/java/org/futo/voiceinput/`
- Sherpa-ONNX Parakeet backend: `app/src/main/java/org/futo/voiceinput/parakeet/`
- Shared model catalog, storage, and lifecycle: `app/src/main/java/org/futo/voiceinput/recognition/`
- Optional transcript cleanup: `app/src/main/java/org/futo/voiceinput/s1/` and `app/src/main/java/org/futo/voiceinput/harper/`
- Saved recordings and retranscription: `app/src/main/java/org/futo/voiceinput/history/`
- Local diagnostics and bug-report export: `app/src/main/java/org/futo/voiceinput/diagnostics/`
- Legacy Whisper/GGML native code: `app/src/main/cpp/`
- Parakeet ONNX export tooling: `tools/parakeet_export/`
- Build flavors and native build wiring: `app/build.gradle`

Primary runtime flow:

1. `RecognizeActivity` handles `android.speech.action.RECOGNIZE_SPEECH`, or `VoiceInputMethodService` runs as the keyboard/IME.
2. Both wrap `RecognizerView`, which owns the Compose recognition UI and forwards lifecycle events.
3. `RecognizerView` delegates recording/model work to `AudioRecognizer`.
4. `AudioRecognizer` records 16 kHz mono PCM with `AudioRecord`, applies WebRTC VAD, buffers float samples, then selects a `SpeechBackend`.
5. Default recognition uses `orukeetBackend()`, a `ParakeetBackend` configured for Orukeet. Other backends remain selectable.
6. `ParakeetBackend` uses the maintained Sherpa-ONNX Kotlin runtime.

## Key Files

- `README.md`: user/build overview and release notes for this fork.
- `app/src/main/AndroidManifest.xml`: declares settings launcher, recognition activity, IME service, updater/migration jobs, permissions.
- `app/src/main/java/org/futo/voiceinput/AudioRecognizer.kt`: core recording, VAD, permission/model checks, backend selection, model lifetime.
- `app/src/main/java/org/futo/voiceinput/RecognizerView.kt`: recognition UI state machine, sounds, permission/model prompts, result dispatch hooks.
- `app/src/main/java/org/futo/voiceinput/RecognizeActivity.kt`: speech recognizer activity entry point.
- `app/src/main/java/org/futo/voiceinput/VoiceInputMethodService.kt`: IME entry point, commits final/partial text to the active input connection, switches back on cancel.
- `app/src/main/java/org/futo/voiceinput/settings/Settings.kt`: DataStore keys. `SPEECH_BACKEND` defaults to `orukeet`; saved selections are preserved.
- `app/src/main/java/org/futo/voiceinput/settings/pages/Models.kt`: Model Options UI.
- `app/src/main/java/org/futo/voiceinput/downloader/DownloadActivity.kt`: shared model downloader. Supports explicit file URLs/hashes for Parakeet and legacy FUTO model names for Whisper.
- `app/src/main/java/org/futo/voiceinput/parakeet/ParakeetModel.kt`: Parakeet TDT artifact declarations and download helpers.
- `app/src/main/java/org/futo/voiceinput/recognition/RecognitionModelCatalog.kt`: shared model definitions and `RecognitionModelStore`.
- `app/src/main/java/org/futo/voiceinput/recognition/RecognitionModelLifecycle.kt`: shared model loading and release.
- `app/src/main/java/org/futo/voiceinput/parakeet/ParakeetEngineManager.kt`: shared/warm Sherpa-ONNX Parakeet backend and idle unload timeout.
- `app/src/main/cpp/`: legacy `voiceinput` C++/GGML/Whisper library built by CMake.

## Model Storage And Downloads

Normal APKs download recognition assets separately. For Parakeet TDT, `ParakeetModel.recognitionModel` declares the directory, pinned URLs, sizes, and SHA-256 hashes; `RecognitionModelStore` owns device storage and validation. Consult these definitions before changing downloads or deleting model files.

`-PbundleParakeetModel=true` enables `BuildConfig.BUNDLE_PARAKEET_MODEL` and the `downloadParakeetModels` task in `app/build.gradle`. Keep its bundled artifact names, revision, and hashes consistent with `ParakeetModel.recognitionModel`. See [the model guide](docs/model-guide.md) for user-facing model behavior.

## Build And Test Commands

When the maintainer requests low memory or sets a RAM limit, follow [the low-memory workflow](docs/development.md#low-memory-workflow) before verification or release work.

Before building, follow [local setup](docs/development.md#build-locally), including native submodules, Android tools, and the pinned Rust toolchain.

Common commands:

```powershell
.\gradlew.bat :app:assembleDevDebug
.\gradlew.bat :app:assembleDevDebug -PbundleParakeetModel=true
.\gradlew.bat :app:testDevDebugUnitTest
.\gradlew.bat :app:lintDevDebug
```

Focused unit-test example:

```powershell
.\gradlew.bat :app:testDevDebugUnitTest --tests 'org.futo.voiceinput.benchmark.BenchmarkRunnerTest'
```

For native packaging changes, follow [model catalog release verification](docs/release/model-catalog-verification.md). Microphone routing, text insertion, and inference changes also require device evidence under [contribution guidance](docs/development.md#contributing); report checks you could not run. Compiling instrumentation tests does not execute them on a device.

Only `arm64-v8a` is packaged. [Release CI](.github/workflows/release-apk.yml) uses JDK 21 and runs standalone release unit tests, lint, dev-debug instrumentation compilation, release assembly, and APK content/signature checks. Use the workflow as the source for current CI commands.

## Product Flavors

Declared in `app/build.gradle`:

- `dev`: update checking, both billing implementations, dev settings, app id suffix `.dev`
- `devSameId`: like `dev` without app id suffix
- `standalone`: update checking, PayPal billing, non-dev settings
- `playStore`: no update checking, Play billing, non-dev settings
- `fDroid`: no update checking, PayPal billing, non-dev settings

Generated APK names are customized in `app/build.gradle`. Debug output is under `app/build/outputs/apk/dev/debug/`; release CI discovers the APK under `app/build/outputs/apk/standalone/release/`. Before contribution or release work, read [development and release guidance](docs/development.md) for PR conventions, signing, versioning, and tag requirements.

## Development Notes

- Before branch setup, integration, promotion or cleanup, read [the Git workflow](docs/development.md#git-workflow).
- Prefer existing Compose/settings patterns in `settings/Components.kt`, `settings/Hooks.kt`, and `settings/pages/*`.
- Keep backend-agnostic recognition behavior in `AudioRecognizer` or `RecognizerView`; keep Parakeet specifics in `parakeet/*`.
- Check each recognition model's declared `TranscriptionBehavior` before changing live or final-only transcription; Parakeet variants differ. See [the NVIDIA runtime decision](docs/adr/0001-use-sherpa-onnx-for-nvidia-streaming.md).
- Before changing logging, transcript capture, or export, read [the vocabulary and capture boundary](CONTEXT.md), [diagnostics design and known gaps](docs/diagnostics.md), and [ADR 0003](docs/adr/0003-local-content-free-app-diagnostics.md). Keep dictated content out of standard diagnostics and Logcat; transcript capture remains a separate explicit option. Preserve separate audio-history retention.
- `ParakeetEngineManager` keeps Parakeet warm by default via `PARAKEET_KEEP_WARM`; force-close it before deleting/replacing model files.
- The IME cannot request microphone permission directly; `VoiceInputMethodService.requestPermission()` rejects and shows settings/error UI.
- Do not remove legacy Whisper/GGML paths unless explicitly requested; this fork intentionally keeps them as selectable fallback.
- This repo may have local uncommitted changes. Check `git status --short` before editing and avoid reverting user changes.

## Agent skills

### Issue tracker

Tickets are tracked in GitHub Issues at `Today20092/voice-input`. `tickets.md` is a historical migration record; external PRs are not a triage surface. See `docs/agents/issue-tracker.md`.

### Triage labels

Use the canonical labels `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, and `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Before exploring this single-context repository, read [domain guidance](docs/agents/domain.md), then `CONTEXT.md` and relevant decisions under `docs/adr/` as directed there.

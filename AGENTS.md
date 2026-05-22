# AGENTS.md

Compact orientation for future agents working in this repo.

## Project Purpose

This is a personal fork of FUTO Voice Input. Upstreams:

- GitHub mirror: `https://github.com/futo-org/voice-input`
- Original GitLab: `https://gitlab.futo.org/keyboard/voiceinput`
- Public fork: `https://github.com/Today20092/futo_with_parakeet`

The goal is to preserve FUTO Voice Input's Android IME/activity UX while making NVIDIA Parakeet TDT 0.6B V3 via ONNX Runtime the default offline recognizer. Legacy FUTO Whisper/GGML remains selectable from Model Options.

## High-Level Architecture

- Android app module: `app/`
- Kotlin/Compose UI, settings, recording flow: `app/src/main/java/org/futo/voiceinput/`
- Parakeet Kotlin backend/JNI bridge: `app/src/main/java/org/futo/voiceinput/parakeet/`
- Rust Parakeet native library: `parakeet-native/`
- Legacy Whisper/GGML native code: `app/src/main/cpp/`
- Parakeet ONNX export tooling: `tools/parakeet_export/`
- Build flavors and native build wiring: `app/build.gradle`

Primary runtime flow:

1. `RecognizeActivity` handles `android.speech.action.RECOGNIZE_SPEECH`, or `VoiceInputMethodService` runs as the keyboard/IME.
2. Both wrap `RecognizerView`, which owns the Compose recognition UI and forwards lifecycle events.
3. `RecognizerView` delegates recording/model work to `AudioRecognizer`.
4. `AudioRecognizer` records 16 kHz mono PCM with `AudioRecord`, applies WebRTC VAD, buffers float samples, then selects a `SpeechBackend`.
5. Default backend is `ParakeetBackend`; optional backend is `WhisperGGMLBackend`.
6. `ParakeetBackend` calls `ParakeetNative`, which loads `c++_shared`, `onnxruntime`, and `parakeet_voiceinput`.
7. Rust JNI functions in `parakeet-native/src/lib.rs` call `parakeet-native/src/engine.rs`, which uses `transcribe-rs` and ONNX Runtime.

## Key Files

- `README.md`: user/build overview and release notes for this fork.
- `app/src/main/AndroidManifest.xml`: declares settings launcher, recognition activity, IME service, updater/migration jobs, permissions.
- `app/src/main/java/org/futo/voiceinput/AudioRecognizer.kt`: core recording, VAD, permission/model checks, backend selection, model lifetime.
- `app/src/main/java/org/futo/voiceinput/RecognizerView.kt`: recognition UI state machine, sounds, permission/model prompts, result dispatch hooks.
- `app/src/main/java/org/futo/voiceinput/RecognizeActivity.kt`: speech recognizer activity entry point.
- `app/src/main/java/org/futo/voiceinput/VoiceInputMethodService.kt`: IME entry point, commits final/partial text to the active input connection, switches back on cancel.
- `app/src/main/java/org/futo/voiceinput/settings/Settings.kt`: DataStore keys. `SPEECH_BACKEND` defaults to `parakeet`.
- `app/src/main/java/org/futo/voiceinput/settings/pages/Models.kt`: Model Options UI for Parakeet vs Whisper/GGML.
- `app/src/main/java/org/futo/voiceinput/downloader/DownloadActivity.kt`: shared model downloader. Supports explicit file URLs/hashes for Parakeet and legacy FUTO model names for Whisper.
- `app/src/main/java/org/futo/voiceinput/parakeet/ParakeetModel.kt`: Parakeet file list, Hugging Face URLs, download marker, hash verification, model download intent.
- `app/src/main/java/org/futo/voiceinput/parakeet/ParakeetEngineManager.kt`: shared/warm Parakeet backend, idle unload timeout.
- `app/src/main/java/org/futo/voiceinput/parakeet/ParakeetNative.kt`: JNI declarations and native library load order.
- `parakeet-native/src/assets.rs`: finds downloaded model under app `filesDir`, or extracts packaged assets if `bundleParakeetModel=true`.
- `parakeet-native/src/engine.rs`: global Parakeet engine load/transcribe/close/idle handling.
- `parakeet-native/transcribe-rs/`: local Rust transcription engine dependency with Parakeet implementation.
- `app/src/main/cpp/`: legacy `voiceinput` C++/GGML/Whisper library built by CMake.

## Model Storage And Downloads

Normal APKs do not bundle Parakeet model files. Runtime download target:

```text
filesDir/parakeet-unified-en-0.6b-onnx/
```

Required files are listed in `ParakeetModel.files`:

- `config.json`
- `vocab.txt`
- `encoder-model.int8.onnx`
- `decoder_joint-model.int8.onnx`
- `preprocessor.onnx` downloaded from remote `nemo128.onnx`
- `.download_complete` marker after all requested files validate

`BuildConfig.BUNDLE_PARAKEET_MODEL` is controlled by Gradle property `-PbundleParakeetModel=true`. In that mode, `downloadParakeetModels` places assets under `app/src/main/assets/parakeet-unified-en-0.6b-onnx`, and Rust extracts them to `filesDir` on first load.

Hashes in `ParakeetModel.kt` and `app/build.gradle` are currently nullable/blank placeholders. If validated hashes become available, update both places consistently.

## Build And Test Commands

Prereqs: JDK 17+, Android SDK platform 35, NDK `28.2.13676358`, Rust target `aarch64-linux-android`, `cargo-ndk`.

Common commands:

```powershell
.\gradlew.bat :app:assembleDevDebug
.\gradlew.bat :app:assembleDevDebug -PbundleParakeetModel=true
.\gradlew.bat :app:testDevDebugUnitTest
.\gradlew.bat :app:lintDevDebug
```

Gradle `preBuild` depends on `cargoNdkBuildParakeet`, which:

- extracts ONNX Runtime Android AAR headers/libs from Maven
- builds `parakeet-native` with `cargo ndk -t arm64-v8a`
- copies native outputs to `app/src/main/jniLibs/arm64-v8a`
- copies `libc++_shared.so` from the configured NDK

First supported ABI is `arm64-v8a`.

## Product Flavors

Declared in `app/build.gradle`:

- `dev`: update checking, both billing implementations, dev settings, app id suffix `.dev`
- `devSameId`: like `dev` without app id suffix
- `standalone`: update checking, PayPal billing, non-dev settings
- `playStore`: no update checking, Play billing, non-dev settings
- `fDroid`: no update checking, PayPal billing, non-dev settings

Generated APK names are customized in `app/build.gradle`, but the GitHub workflow currently expects/copies `app/build/outputs/apk/dev/debug/app-dev-debug.apk`; verify this if release automation changes.

## Development Notes

- Prefer existing Compose/settings patterns in `settings/Components.kt`, `settings/Hooks.kt`, and `settings/pages/*`.
- Keep backend-agnostic recognition behavior in `AudioRecognizer` or `RecognizerView`; keep Parakeet specifics in `parakeet/*`.
- Parakeet currently returns only a final transcript after recording stops. Legacy Whisper/GGML can emit partials through `onPartialDecode`.
- `ParakeetEngineManager` keeps Parakeet warm by default via `PARAKEET_KEEP_WARM`; force-close it before deleting/replacing model files.
- The IME cannot request microphone permission directly; `VoiceInputMethodService.requestPermission()` rejects and shows settings/error UI.
- Do not remove legacy Whisper/GGML paths unless explicitly requested; this fork intentionally keeps them as selectable fallback.
- Be careful with generated/native outputs under `app/src/main/jniLibs/`; they are produced by Gradle/Rust build tasks.
- This repo may have local uncommitted changes. Check `git status --short` before editing and avoid reverting user changes.

# FUTO Parakeet Voice Input

This is a personal fork of FUTO Voice Input that keeps the FUTO voice keyboard experience and replaces the speech recognizer backend with NVIDIA Parakeet TDT 0.6B V3 running through ONNX Runtime.

This fork's Parakeet integration and repository changes were built with AI assistance using Codex (GPT-5).

The goal is straightforward: keep the FUTO UI, recording flow, dark theme support, VAD silence stopping, microphone animation, and keyboard switch-back behavior, while using Parakeet as the main recognizer because it is fast and accurate for English speech.

## What Changed

- FUTO Voice Input remains the base Android app and UI.
- The old Whisper/ggml recognition path is bypassed for transcription.
- A new Kotlin backend layer calls a Rust JNI library.
- The Rust library loads Parakeet ONNX models through `transcribe-rs` and ONNX Runtime.
- The Model Options screen shows Parakeet as the active backend.
- The old English and multilingual model choices are still visible for now, but they are not used by transcription.

## Active Model

The active backend is:

```text
Unified Parakeet TDT 0.6B V3
```

The build downloads the ONNX model assets from:

```text
https://huggingface.co/istupakov/parakeet-tdt-0.6b-v3-onnx
```

By default, the Parakeet model files are not packaged into the APK. The app downloads them on first use or when you tap the Parakeet model in the Model Options screen.

Downloaded model files are stored in app-private storage:

```text
filesDir/parakeet-tdt-0.6b-v3-int8/
```

## Building Locally

Required tools:

- Android Studio or Android SDK command line tools
- JDK 17 or newer
- Android SDK platform 35
- Android NDK `28.2.13676358`
- Rust with `aarch64-linux-android` target
- `cargo-ndk`

Install the Rust target and cargo helper:

```powershell
rustup target add aarch64-linux-android
cargo install cargo-ndk
```

Create `local.properties` if Android Studio has not already created it:

```properties
sdk.dir=C\:\\Users\\User\\AppData\\Local\\Android\\Sdk
```

Build the debug APK:

```powershell
.\gradlew.bat :app:assembleDevDebug
```

If you need a development build that packages the Parakeet model into the APK, enable the bundled-model Gradle property:

```powershell
.\gradlew.bat :app:assembleDevDebug -PbundleParakeetModel=true
```

The APK is written to:

```text
app/build/outputs/apk/dev/debug/app-dev-debug.apk
```

## GitHub Releases

This repository includes a GitHub Actions workflow that builds an APK.

The first working Parakeet fork release is tagged as:

```text
v1.0.0-parakeet
```

To create a release:

```bash
git tag v1.0.0-parakeet
git push origin v1.0.0-parakeet
```

GitHub Actions will:

- install Android build components
- install Rust and `cargo-ndk`
- build `:app:assembleDevDebug`
- attach the APK to the GitHub Release

You can also run the workflow manually from the Actions tab. Manual runs upload the APK as a workflow artifact but do not create a GitHub Release unless the run is for a tag.

## Notes

- First supported ABI is `arm64-v8a`.
- This is intended for sideloading and personal testing.
- The normal APK does not include the Parakeet ONNX model files. The model downloads at runtime from Hugging Face.
- Parakeet currently returns a final transcript after recording stops; live partial transcripts are not implemented.
- The app requires network access to download the model the first time, then transcription runs offline.

## Attribution And License

This fork is based on FUTO Voice Input and keeps FUTO's license and notices. FUTO Voice Input is licensed under the FUTO Source First License. Review [LICENSE.md](LICENSE.md) before distributing modified builds.

Parakeet model assets come from `istupakov/parakeet-tdt-0.6b-v3-onnx`, an ONNX conversion of NVIDIA Parakeet TDT 0.6B V3, licensed CC-BY-4.0.

This fork is not affiliated with or endorsed by FUTO.

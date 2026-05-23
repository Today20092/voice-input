# FUTO Parakeet Voice Input

This is a personal fork of FUTO Voice Input that keeps the FUTO voice keyboard experience and uses NVIDIA Parakeet TDT 0.6B V3 through ONNX Runtime by default. The legacy FUTO Whisper/GGML backend can also be selected from Model Options.

This fork's Parakeet integration and repository changes were built with AI assistance using Codex (GPT-5).

The goal is straightforward: keep the FUTO UI, recording flow, dark theme support, VAD silence stopping, microphone animation, and keyboard switch-back behavior, while using Parakeet as the main recognizer because it is fast and accurate for English speech.

## What Changed

- FUTO Voice Input remains the base Android app and UI.
- Parakeet is the default speech backend.
- The legacy Whisper/GGML recognition path can be selected as an optional backend.
- A Kotlin backend layer calls a Rust JNI library for Parakeet.
- The Rust library loads Parakeet ONNX models through `transcribe-rs` and ONNX Runtime.
- The Model Options screen lets you choose Parakeet or Whisper/GGML.
- Only the selected backend's model files are required before voice input starts.

## Active Model

The default backend is:

```text
Unified Parakeet TDT 0.6B V3
```

The app downloads the ONNX model assets from:

```text
https://huggingface.co/istupakov/parakeet-tdt-0.6b-v3-onnx
```

By default, the Parakeet model files are not packaged into the APK. This keeps the APK much smaller than the first bundled-model release.

After installing the APK, the model is downloaded by the app:

1. Open FUTO Voice Input Settings.
2. Tap **Model**.
3. Tap **Unified Parakeet TDT 0.6B V3** under **Parakeet Model**.
4. Confirm the download.

If you try to use voice input before downloading the model, the app shows a download prompt before it starts recording. Once the download finishes, the app loads Parakeet from local app storage and transcription runs offline.

Downloaded model files are stored in app-private storage:

```text
filesDir/parakeet-tdt-0.6b-v3-int8/
```

If you select **Whisper/GGML** in Model Options, the app uses the legacy FUTO English and multilingual GGML model settings and downloader. Those GGML files are also stored in app-private storage and run offline after download. Parakeet downloads from Hugging Face, while Whisper/GGML models download through the existing FUTO model downloader.

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
app/build/outputs/apk/standalone/release/app-standalone-release.apk
```

## GitHub Releases

This repository includes a GitHub Actions workflow that builds an APK when a `v*` tag is pushed.

The first bundled-model Parakeet fork release was tagged as:

```text
v1.0.0-parakeet
```

To create a new release:

```bash
git tag v1.1.0-parakeet-runtime-download
git push origin master
git push origin v1.1.0-parakeet-runtime-download
```

GitHub Actions will:

- install Android build components
- install Rust and `cargo-ndk`
- build `:app:assembleStandaloneRelease`
- create a GitHub Release for the pushed tag
- attach the APK to that GitHub Release

You can also run the workflow manually from the Actions tab. Manual runs upload the APK as a workflow artifact but do not create a GitHub Release unless the run is for a tag.

## Notes

- First supported ABI is `arm64-v8a`.
- This is intended for sideloading and personal testing.
- The normal APK does not include the Parakeet ONNX model files. The model downloads at runtime from Hugging Face.
- Parakeet currently returns a final transcript after recording stops; live partial transcripts are not implemented.
- The app requires network access to download the selected backend's model the first time, then transcription runs offline.

## Attribution And License

This fork is based on FUTO Voice Input and keeps FUTO's license and notices. FUTO Voice Input is licensed under the FUTO Source First License. Review [LICENSE.md](LICENSE.md) before distributing modified builds.

Parakeet model assets come from `istupakov/parakeet-tdt-0.6b-v3-onnx`, an ONNX conversion of NVIDIA Parakeet TDT 0.6B V3, licensed CC-BY-4.0.

This fork is not affiliated with or endorsed by FUTO.

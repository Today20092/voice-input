# FUTO Voice Input Moonshine

This personal fork keeps the FUTO voice keyboard experience, uses Orukeet as its default offline recognizer, and adds optional on-device transcript cleanup with S1-mini by Superwhisper. Moonshine, Parakeet TDT, Orukeet, Parakeet Unified, Nemotron, and legacy FUTO Whisper/GGML models are available from Model Options.

This fork's Parakeet integration and repository changes were built with AI assistance using Codex (GPT-5).

The goal is straightforward: keep the FUTO UI and recording flow while adding responsive streaming transcription, personal vocabulary corrections, and private offline transcript cleanup.

## What Changed

- Orukeet is the default recognizer for new installations and returns the transcript after Stop. Existing saved model choices are preserved.
- Moonshine v2 Small Streaming remains available and emits live partial transcripts.
- Moonshine v2 Medium Streaming is available as a higher-accuracy option.
- Parakeet TDT, Parakeet Unified, Nemotron English and multilingual, and legacy Whisper/GGML are selectable alternatives.
- Batch and streaming recognizers share backend-neutral Kotlin contracts.
- Personal vocabulary entries correct partial and final transcripts; use `heard => preferred` for explicit aliases.
- Optional **S1-mini by Superwhisper** cleanup runs locally on final English transcripts after Stop.
- S1-mini includes styling, structure, context, keep-warm, CPU/OpenCL, optimization, and transcript-free diagnostics controls.
- The stable app uses the distinct `org.futo.voiceinput.moonshine` package ID.
- Only the selected backend's model files are required before voice input starts.

## Stable 1.4.3

Version 1.4.3 combines the tested history, Cohere, and recognizer-popup betas,
adds app-wide local diagnostics, and fixes S1-mini's keep-warm behavior.
Audio history has transcript previews and confirmed bulk clearing. Cohere Transcribe
is an optional on-device model with an explicit language selector. The recognition
popup shows the selected model and provides the new layout option.

Under **Support → Diagnostics**, review local technical evidence and manually share
a bug-report ZIP. Standard reports exclude dictated text and audio. Collection can
be disabled, and detailed mode stops automatically after 30 minutes.

Orukeet remains the default, and saved model selections are preserved. See the
[1.4.3 release notes](docs/releases/v1.4.3.md) for downloads, limitations, and
verification details.

## Previous stable 1.4.2

Version 1.4.2 includes the beta 18 features, defaults new installations to Orukeet,
and routes issue reports and feedback to this fork on GitHub. The waveform now
uses PCM amplitude directly, without adaptive gain that resized bars during pauses.
Immediate partial bars and the taller waveform remain. See the
[release notes](docs/releases/v1.4.2.md) for installation details.

## Beta 18: waveform visibility and bulk dictionary entry

`v1.4.2-beta.18` builds on beta 17. The recording waveform is taller and uses
bounded, adaptive display gain so quiet speech is easier to see. Partial bars
are visible immediately; the saved audio and recognition input are unchanged.

Personal Dictionary now supports pasting multiple entries and importing UTF-8
text files up to 1 MiB. Preview additions, skip duplicates, and fix invalid
mappings before adding. Existing entries are preserved. These corrections run
after recognition and optional S1-mini cleanup, including with Orukeet; they do
not train or provide recognition hints to Orukeet. Use `heard phrase => preferred phrase`
for exact corrections. The optional `arabic-transliteration.txt` release download
is an editable example list, not bundled or automatically enabled in the app.

Iconless settings rows now align with the page margin, including Back up
recordings. Audio-history explanations use smaller supporting text.

## Beta 17: waveform and settings

`v1.4.2-beta.17` builds directly on beta 16. Recording now shows a scrolling
four-second waveform from captured microphone samples, with tap-to-stop preserved.
Settings are grouped into Speech, Recording, Appearance, Support, Advanced, and
About. Personal dictionary has its own page; language controls are under Languages.
S1-mini runtime tuning is under Advanced. Reports, transcript capture, and ZIP
export are under Support → Diagnostics, with the existing sharing consent intact.
Audio history and all beta 16 backup and recovery behavior are retained.

## Audio history

Added in `v1.4.2-beta.16`, based on beta 15. Open **Audio history** from the main settings page to
view saved recordings, retranscribe them with the currently selected recognition
model, and copy the resulting text. Existing successful transcripts are saved too.

History rows show a short transcript preview so you can find an entry before
opening it. **Clear history** asks for confirmation, then removes saved audio and
text. It preserves entries currently recording or transcribing and reports deleted,
in-use, and failed counts. New recordings still save while backups are enabled.

Backups are enabled by default and kept for 24 hours. Set retention to any value
from 1 to 720 hours, for example 2 hours or 72 hours for three days. Turning backups
off stops new saves; existing recordings keep their expiry. Each recording can also
be deleted with its transcript. Shortening retention deletes older recordings.

The app writes 16 kHz mono PCM into private, Android-backup-excluded storage during
capture, including canceled and failed attempts. Interrupted files remain readable
up to the last complete sample written. Storage failures show a warning and do not
prevent ordinary transcription. Uninstalling the app or clearing its data removes
the recordings. Recordings use about 1.9 MB per minute.

Expiry is checked on launch, capture, history access, and by a periodic Android job.
Android can delay background deletion while the app or device is stopped. Active
capture and retranscription are protected from deletion until they finish.
Retranscription stays on-device and uses current language, model, vocabulary, and
cleanup settings. Keep the history screen open until it finishes.

## Screenshots

### Model Options

<img src="docs/screenshots/model-options.png" alt="Model Options screen" width="360">

Orukeet is selected by default. This screenshot may show fewer options than the current release.

## Project branches

- Release tags identify the exact source used for each APK; see [GitHub Releases](#github-releases).
- `codex/orukeet-beta` contains the Orukeet releases and the latest S1-mini compatibility fix.
- `codex/s1-mini-beta` preserves the S1-mini development and release-validation history.

## Available models

| Model | Languages | Transcription behavior |
| --- | --- | --- |
| **Moonshine Small** | English | Live partial transcripts; lighter resource use than Medium. |
| **Moonshine Medium** | English | Live partial transcripts; higher-accuracy option with greater resource use. |
| **Parakeet TDT 0.6B V3** | 25 European languages | Final transcript after Stop. |
| **Orukeet** (default) | 25 European languages | Final transcript after Stop; uses the same Sherpa-ONNX runtime as Parakeet TDT. |
| **Parakeet Unified EN 0.6B** | English | Buffered live updates that recompute recent context. |
| **Nemotron** | English | Live transcription with Low latency (80 ms), Balanced (160 ms), or Accuracy (560 ms) profiles. |
| **Nemotron 3.5 Multilingual** | 28 languages | Live transcription; choose a language or Auto-detect. |
| **Whisper (legacy)** | English and multilingual model options | Legacy FUTO Whisper/GGML recognition. |

Nemotron profile times describe processing chunks, not guaranteed end-to-end latency. Speed and memory use depend on the phone and selected model.

**S1-mini is a separate cleanup model**, not a speech recognizer. It edits the final English transcript from the selected recognizer; see [Transcript Cleanup](#optional-s1-mini-transcript-cleanup).

## Downloading models

Orukeet is selected by default and downloaded on first use. Moonshine Small and
Medium remain available, with quantized assets downloaded from:

```text
https://download.moonshine.ai/model/small-streaming-en/quantized/
https://download.moonshine.ai/model/medium-streaming-en/quantized/
```

Model files are downloaded on first use rather than packaged into the APK.

After installing the APK, the model is downloaded by the app:

1. Open FUTO Voice Input Moonshine Settings.
2. Open **Model Options**.
3. Select a recognizer and, where available, its model or profile.
4. Confirm the download.

If you try voice input before downloading the model, the app prompts for the download. Transcription runs offline after installation.

Downloaded model files are stored in app-private storage:

```text
filesDir/moonshine-small-streaming-en/
filesDir/moonshine-medium-streaming-en/
```

All recognizers store their downloaded files in app-private storage and run offline after download. Only the selected recognizer needs to be downloaded; S1-mini is an additional optional download.

Orukeet's pinned INT8 package downloads about **487 MB** and installs about **672 MB** of files. Installation needs about **1.16 GB** free for the saved archive and extracted model. Interrupted transfers resume on retry when the server supports byte ranges. Installation verifies the archive and extracted resources before marking the model ready, then removes the saved archive.

## Optional S1-mini transcript cleanup

Open **Transcript Cleanup** from the main settings screen to download and enable **S1-mini by Superwhisper**.
The pinned Q4_K_M model is approximately 484.2 MB. It is English-only, disabled by default, and
runs only after recording stops. If cleanup times out or fails, the app keeps the raw transcript.
Personal Vocabulary corrections run after cleanup.

Starting with **v1.4.2-beta.14**, enabled cleanup also assumes English when the recognizer cannot
report a language. This fixes cleanup being skipped with Orukeet and covers the other English
recognizer paths. Known non-English input is still bypassed. This assumption is intended for English
dictation; turn cleanup off when dictating another language with a recognizer that cannot identify it.
The current stable release, **v1.4.2-beta.13**, predates this fix.

The first run benchmarks validated CPU configurations and experimental OpenCL on the phone. The
Auto setting selects OpenCL only when it produces the expected output and is at least 15% faster than
the best CPU result. Standard diagnostics contain timing, runtime, memory, and thermal data without
audio, transcripts, prompts, or vocabulary, and can be exported as a ZIP for bug reports.
Separate, explicitly enabled transcript diagnostics can capture and export transcription text.

## Building Locally

Required tools:

- Android Studio or Android SDK command line tools
- JDK 17 or newer
- Android SDK platform 35
- Android NDK `28.2.13676358`

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

Debug APKs are written under:

```text
app/build/outputs/apk/dev/debug/
```

Standalone release builds use `:app:assembleStandaloneRelease` and write APKs under
`app/build/outputs/apk/standalone/release/`. GitHub Actions supplies the release signing configuration.

## GitHub Releases

- **Stable:** [v1.4.2 — Orukeet](https://github.com/Today20092/voice-input/releases/latest), the tested `v1.4.2-beta.13` build promoted unchanged. Its tag and APK filename retain the beta suffix.
- **Beta:** [v1.4.2-beta.15](https://github.com/Today20092/voice-input/releases/tag/v1.4.2-beta.15), which adds resumable archive downloads and reduces download-processing overhead while retaining beta 14's S1-mini fix. Existing downloaded models can be reused after updating.

This repository includes a GitHub Actions workflow that builds and verifies an APK when a `v*` tag is pushed.

To create a new release:

```bash
git tag v1.4.3
git push origin HEAD
git push origin v1.4.3
```

GitHub Actions will:

- install Android build components
- build `:app:assembleStandaloneRelease`
- create a GitHub Release for the pushed tag
- attach the APK to that GitHub Release

You can also run the workflow manually from the Actions tab. Manual runs upload the APK as a workflow artifact but do not create a GitHub Release unless the run is for a tag.

## Notes

- First supported ABI is `arm64-v8a`.
- This is intended for sideloading and personal testing.
- The normal APK does not include speech model files.
- Live updates depend on the selected recognizer; Parakeet TDT and Orukeet return their final transcript after Stop.
- The app requires network access to download the selected backend's model the first time, then transcription runs offline.

## Attribution And License

This fork is based on FUTO Voice Input and keeps FUTO's license and notices. FUTO Voice Input is licensed under the FUTO Source First License. Review [LICENSE.md](LICENSE.md) before distributing modified builds.

Parakeet TDT model assets come from `twmht/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8`, a Sherpa-ONNX export of NVIDIA Parakeet TDT 0.6B V3, licensed CC-BY-4.0. Parakeet Unified and Nemotron English use the NVIDIA Open Model License; Nemotron 3.5 Multilingual uses OpenMDW-1.1. Moonshine model metadata lists the MIT license. Model Options includes attribution for the selected model.

The optional downloaded cleanup model is **S1-mini by Superwhisper**, licensed under Apache License
2.0 with the publisher's required naming condition. See
[the model notice](docs/third-party/S1-mini-NOTICE.md). llama.cpp is included as a pinned submodule
under its MIT license; Khronos OpenCL headers and loader are included as pinned submodules under
their respective upstream licenses.

Orukeet weights are licensed CC BY-SA 4.0 by Oruk AI and retain NVIDIA Parakeet attribution. The installer verifies and preserves the package's `LICENSE-WEIGHTS` and `NOTICE.md`. See the [model card](https://huggingface.co/oruk/orukeet) and [pinned package manifest](https://huggingface.co/oruk/orukeet/blob/55a984d46f68323301837194ce647c702f55facc/onnx/manifest.json). The authors report lower WER than Parakeet on many evaluated splits; these are not Android benchmarks, and LibriSpeech test-other was used for adaptation and checkpoint selection.

This fork is not affiliated with or endorsed by FUTO.

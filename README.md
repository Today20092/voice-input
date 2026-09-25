# FUTO Voice Input Moonshine

[![Latest release](https://img.shields.io/github/v/release/Today20092/voice-input)](https://github.com/Today20092/voice-input/releases/latest)
[![APK build](https://github.com/Today20092/voice-input/actions/workflows/release-apk.yml/badge.svg?branch=master)](https://github.com/Today20092/voice-input/actions/workflows/release-apk.yml)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84)
![ARM64](https://img.shields.io/badge/ABI-arm64--v8a-blue)
[![License](https://img.shields.io/badge/License-FUTO%20Source%20First-blue)](LICENSE.md)

Offline voice typing for Android, with a choice of speech models, recoverable recordings, personal dictionary corrections, and optional local transcript cleanup.

This personal fork keeps the [FUTO Voice Input](https://github.com/futo-org/voice-input) keyboard and speech-recognition activity experience. Orukeet is the default recognizer; Moonshine, NVIDIA Parakeet and Nemotron, Cohere Transcribe, and legacy Whisper remain selectable. Speech recognition and cleanup run on your phone after their models are downloaded.

[Download the APK](https://github.com/Today20092/voice-input/releases/latest) · [What differs from FUTO](#compared-with-original-futo-voice-input) · [Model guide](#choose-a-model) · [Release notes](docs/releases/v1.4.3.md) · [Report a problem](https://github.com/Today20092/voice-input/issues)

## Why this fork exists

I made this fork because I really like FUTO Voice Input. It already has a good interface and a working Android voice-input flow, which made it a useful starting point. I wanted to keep that experience and try faster local speech models, starting with NVIDIA Parakeet.

The app has since grown to include Orukeet and Cohere Transcribe, both of which have worked well in my use, alongside live-transcription options and optional English text cleanup. My current testing phone is a **Samsung Galaxy S25 Ultra**, and local transcription has been working very well on it. This is hands-on experience, not a controlled performance benchmark; results on other phones may differ.

The goal is to make local dictation useful in everyday Android apps while keeping control over the model, recordings, and resulting text. Audio history lets you recover or retranscribe a recording, a personal dictionary fixes recurring names and phrases, and local diagnostics help investigate failures without automatically uploading your dictation.

If another open-source app has a feature you think would improve this one, please [open an issue](https://github.com/Today20092/voice-input/issues) with a link to the project and a description of what you find useful. Suggestions can help guide new features or compatible integrations, with credit to the original authors and respect for their licenses.

## AI development disclosure

**The code changes made for this fork are written by AI agents, directed by me.** I choose what to build, describe requirements, test the app on my phone, and provide feedback. I am not presenting these changes as code I individually wrote by hand. FUTO's original code and the third-party projects and models retain their own authorship and attribution.

Development uses Codex, originally with GPT-5 and now with **GPT-6 Astra**, following [Matt Pocock's AI coding workflow and skills](https://github.com/mattpocock/skills). I use that approach for planning and implementation, writing and running tests, investigating bugs, and reviewing fixes. I also use AI to research other open-source apps, identify useful features, and assess how they could fit here with appropriate attribution and license compliance.

AI-generated code can contain mistakes. Automated checks and my device testing help catch them, but do not establish that every feature works on every phone. The release notes document what was actually checked, and bug reports are welcome.

Codex and GPT models are development tools only. The app's speech recognition and optional cleanup use the downloaded on-device models described below.

## Get started

1. Install the signed APK from [GitHub Releases](https://github.com/Today20092/voice-input/releases/latest). Android 8.0 or newer and an ARM64 device are required.
2. Open the app's settings, grant microphone permission, and follow the voice-input setup.
3. Open **Model Options**, keep Orukeet or select another recognizer, and confirm its download.
4. Start dictating. Live text depends on the model; the final result is delivered after recording stops.

The stable app uses `org.futo.voiceinput.moonshine`, a separate package from upstream FUTO Voice Input. Stable 1.4.3 updates this fork's earlier releases and tested betas while retaining settings and downloaded models. Existing model selections are preserved.

## Compared with original FUTO Voice Input

FUTO provides the foundation: local speech recognition, the Android voice-keyboard integration, the floating recognition activity, and the settings interface. This fork keeps that foundation and extends the model choices and dictation workflow.

The comparison below covers the **standalone FUTO Voice Input app**, using [upstream revision `d6e1eb2`](https://github.com/futo-org/voice-input/tree/d6e1eb2d139dc1a6342a4681c283686cca4bfceb) checked on September 25, 2026. It does not compare against the separate FUTO Keyboard app or unmerged upstream proposals.

| Area | Original FUTO Voice Input | This fork | Change |
| --- | --- | --- | --- |
| Android integration | Voice IME and floating activity for compatible keyboards and apps. | Keeps those entry points and the familiar recording flow. | Retained |
| Offline recognition | Local Whisper-based recognition with downloadable English and multilingual models. | Keeps Whisper and adds Orukeet, Moonshine, Parakeet, Nemotron, and Cohere. Orukeet is the default for new installs. | Expanded |
| Text while speaking | Existing Whisper partial-decode output. | Adds Moonshine and Nemotron streaming, plus Parakeet Unified buffered live updates. Final-only models remain available. | Expanded |
| Model and language selection | Whisper model sizes and language settings. | Adds model-family choices, streaming profiles, Cohere's explicit language selector, and Nemotron multilingual options. | Expanded |
| Personal dictionary | A text field for personal vocabulary in Model Options. | Adds a dedicated page, explicit `heard => preferred` corrections, bulk paste, UTF-8 import, duplicate checks, and previews. | Expanded |
| Recording history | No dedicated saved-recording history page in the reviewed upstream settings. | Adds local audio backups, transcript previews, retranscription, copying, retention controls, and safe bulk clearing. | Added |
| Transcript cleanup | No S1-mini cleanup stage. | Adds optional local English rewriting with S1-mini by Superwhisper, style controls, CPU/OpenCL selection, and keep-warm settings. | Added |
| Recording display | Original recording and progress UI. | Adds a scrolling microphone waveform and selected-model caption. | Expanded |
| Floating popup | Centered speech-recognition window. | Adds an opt-in bottom-positioned popup without background dimming; the voice keyboard layout stays the same. | Optional addition |
| Troubleshooting | Existing crash-logging and feedback support. | Adds bounded app-wide diagnostic history, a timed detailed mode, and a reviewed, manually shared bug-report ZIP. | Expanded |
| Installation | Upstream FUTO application package. | Uses the separate `org.futo.voiceinput.moonshine` package and publishes signed ARM64 APKs through this repository. | Separate distribution |

### Work combined in 1.4.3

The stable release merges our **history usability**, **Cohere Transcribe**, and **recognizer popup** beta work, along with reviewed **app-wide diagnostics** and the **S1-mini keep-warm fix**. Earlier additions, including the other recognition backends, waveform, dictionary imports, and audio recovery, remain included.

The popup positioning was adapted from the idea in [upstream proposal #172](https://github.com/futo-org/voice-input/pull/172), with this fork's model caption added alongside it. These are additions integrated into this fork; this does not mean the beta work was merged into upstream FUTO.

See the [1.4.3 release notes](docs/releases/v1.4.3.md) for verification and limitations and the [release archive](https://github.com/Today20092/voice-input/releases) for earlier changes. More model choices do not establish a universal speed or accuracy advantage over the original app; that depends on the selected model, language, and phone.

## Choose a model

These are the options exposed by this app, not every capability of the upstream models. The use cases describe intended trade-offs, not a measured ranking across Android devices.

| Model | Languages in the app | Text appears | Purpose and trade-off | Model source |
| --- | --- | --- | --- | --- |
| **Orukeet · default** | 25 European languages | After Stop | Starting point for everyday dictation. A Parakeet-derived model with multilingual and accent-focused adaptation; no live partials. | [Oruk AI](https://huggingface.co/oruk/orukeet) |
| **Moonshine Small** | English | Live | Lighter English streaming option when resource use matters. | [Moonshine AI](https://github.com/moonshine-ai/moonshine) |
| **Moonshine Medium** | English | Live | Larger English streaming option intended to favor accuracy, with greater resource use than Small. | [Moonshine AI](https://github.com/moonshine-ai/moonshine) |
| **Parakeet TDT 0.6B V3** | 25 European languages | After Stop | NVIDIA's multilingual alternative to Orukeet, useful for comparing results on your own speech. | [NVIDIA](https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3) · [INT8 export](https://huggingface.co/twmht/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8) |
| **Parakeet Unified EN 0.6B** | English | Buffered live | Recomputes recent context for live updates. More work per update than a recognizer that reuses cached streaming state. | [Sherpa-ONNX export](https://huggingface.co/csukuangfj2/sherpa-onnx-nemo-parakeet-unified-en-0.6b-int8-streaming-560ms) |
| **Nemotron English** | English | Live | Choose Low latency, Balanced, or Accuracy profiles to trade update frequency against recognition context. | [NVIDIA](https://huggingface.co/nvidia/nemotron-speech-streaming-en-0.6b) · [Sherpa-ONNX packages](https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models) |
| **Nemotron 3.5 Multilingual** | 28 languages, with Auto-detect | Live | Multilingual streaming with explicit language selection or automatic detection. | [Sherpa-ONNX export](https://huggingface.co/csukuangfj2/sherpa-onnx-nemotron-3.5-asr-streaming-0.6b-560ms-int8-2026-06-11) |
| **Cohere Transcribe · beta** | 14 languages, including Arabic and English | After Stop | Another multilingual option, particularly for Arabic. Explicit language selection; a large download and substantial memory use. | [Cohere Labs](https://huggingface.co/CohereLabs/cohere-transcribe-03-2026) · [INT8 export](https://huggingface.co/csukuangfj2/sherpa-onnx-cohere-transcribe-14-lang-int8-2026-04-01) |
| **Whisper · legacy** | English and multilingual options | Final after Stop; legacy decode progress may show partials | Preserves FUTO's original Whisper/GGML path as a fallback and comparison option. | [FUTO source and model integration](https://github.com/futo-org/voice-input) |
| **S1-mini by Superwhisper** | English text | After recognition | Optional transcript cleanup, not speech recognition. Adds processing time to improve the presentation of dictated text. | [Superwhisper GGUF](https://huggingface.co/superwhisper/s1-mini-GGUF) |

Nemotron English's 80, 160, and 560 ms profile values describe audio chunks, not guaranteed end-to-end latency. Phone hardware, recording length, language, and model all affect results. Publisher benchmarks are not measurements of this Android app.

Cohere download and recognition, history improvements, and the popup were manually tried on a Samsung Galaxy S25 Ultra. No comparative speed, memory, or accuracy benchmark was collected. Cohere remains labeled Beta; it has no automatic language detection, and mixed-language dictation can be inaccurate.

### Model downloads

Models download separately into app-private storage. The normal APK does not bundle weights. Network access is needed for the initial download; recognition then works offline. S1-mini is a separate optional download.

- **Orukeet:** about 487 MB to download and 672 MB installed. Allow about 1.16 GB free during installation for the archive and extracted files. Interrupted downloads can resume when the server supports byte ranges.
- **Cohere:** about 2.89 GB of model files, plus working memory during recognition. Longer recordings use chunks of up to 35 seconds; words near boundaries may need checking.
- **S1-mini:** about 484.2 MB for the pinned Q4_K_M model.
- **Moonshine:** quantized assets come directly from Moonshine AI's [Small](https://download.moonshine.ai/model/small-streaming-en/quantized/streaming_config.json) and [Medium](https://download.moonshine.ai/model/medium-streaming-en/quantized/streaming_config.json) download service.

The [model catalog](app/src/main/java/org/futo/voiceinput/recognition/RecognitionModelCatalog.kt) records the app's selected packages and revisions. Linked model pages may describe newer upstream versions than the app downloads.

## Personal dictionary and cleanup

Use **Personal Dictionary** for predictable corrections, such as `heard phrase => preferred phrase`. Bulk paste and UTF-8 imports up to 1 MiB let you preview additions, skip duplicates, and fix invalid mappings. These are text corrections; they do not train Orukeet or supply it with recognition hints. The optional [Arabic transliteration example](docs/examples/arabic-transliteration.txt) is an editable starting point, not enabled automatically.

Enable **S1-mini by Superwhisper** under **Transcript Cleanup** for local English rewriting after Stop. It is off by default. Choose style, structure, context, and how long to keep the model warm. If cleanup times out or fails, the app keeps the raw transcript. Final dictionary corrections run after cleanup.

Known non-English input bypasses cleanup. For recognizers that cannot report a language, enabled cleanup assumes English; turn it off for non-English dictation on those paths.

The first cleanup run benchmarks CPU configurations and experimental OpenCL. Auto chooses OpenCL only when its output passes validation and it is at least 15% faster than the best CPU result. Runtime controls and content-free S1 diagnostics are available in settings.

## Audio history and recovery

Open **Audio history** to find recordings by transcript preview, view and copy full text, or retranscribe with the current model, language, dictionary, and cleanup settings. Keep the history screen open while retranscription runs.

Each recording has direct Retranscribe, Copy text, and Delete controls. **Show full transcript** appears when the preview is cut off; Copy text always copies the complete saved transcript.

Recording backups are on by default with 24-hour retention, adjustable from 1 to 720 hours. Saved audio includes canceled and failed attempts so it can be recovered. Storage failures show a warning without blocking ordinary dictation. Audio uses about 1.9 MB per minute in private storage excluded from Android backup.

Delete individual entries or confirm **Clear history** to remove inactive recordings and transcripts. Active recordings and retranscriptions are protected. Turning backups off stops new saves; shortening retention removes older entries. Uninstalling the app or clearing its data removes recordings.

Expiry is checked during app use and by a periodic Android job. Android may delay background deletion while the app or device is stopped.

## Popup and recording UI

The recognition UI shows the selected model and a scrolling waveform driven directly by microphone amplitude. Under **Advanced**, enable **Unobtrusive recognizer popup (beta)** to move the speech-recognition activity near the bottom and remove background dimming. It does not change the voice keyboard layout or recognition engine.

<img src="docs/screenshots/model-options.png" alt="Model Options screen with Orukeet selected" width="360">

This screenshot predates some current model options.

## Diagnostics and privacy

Open **Settings → Support → Diagnostics → Export bug report**, add optional notes, review the summary, and share the ZIP yourself. For intermittent issues, enable detailed mode before reproducing the problem; it stops automatically after 30 minutes.

Standard diagnostics stay local and are on by default, with an off switch and a Clear action. They cover recording, model loading, recognition, cleanup, delivery, downloads, managed failures, and available Android process-exit reasons. Retained standard evidence, including prepared reports, is bounded to seven days and 10 MB.

Standard reports exclude audio, dictated text, personal vocabulary, clipboard or surrounding text, receiving-app names, raw Logcat, URLs, and exception messages. Notes you type into a report are included as entered. Transcript-inclusive exports are separate and require explicit consent. Nothing uploads automatically.

Crash evidence is best effort, and delivery records cannot prove how another app displayed the text. Diagnostics recording and sharing still need a device smoke test. See [the diagnostics guide](docs/diagnostics.md) for archive contents, retention details, and verification.

To collect comparable measurements on your phone, follow the [phone dictation test](docs/phone-performance-test.md). It includes three passages, cold and warm run instructions, and the diagnostic export procedure. Measured results will be added after the reports are reviewed.

## Build locally

Install JDK 17 or newer, Android SDK platform 35, NDK `28.2.13676358`, and CMake `3.22.1`. Initialize the repository's submodules. Point `local.properties` at your Android SDK, or set `ANDROID_HOME`.

On macOS or Linux:

```bash
git submodule update --init --recursive
./gradlew :app:assembleDevDebug
./gradlew :app:testDevDebugUnitTest :app:lintDevDebug
```

On Windows, use `.\gradlew.bat` in place of `./gradlew`.

Debug APKs are written to `app/build/outputs/apk/dev/debug/`. For a development build with bundled Parakeet assets, add `-PbundleParakeetModel=true`.

Standalone builds use `:app:assembleStandaloneRelease` and write to `app/build/outputs/apk/standalone/release/`. GitHub Actions supplies the release signing configuration, runs release unit tests, and verifies the APK signature and ARM64 native libraries.

## Releases and repository

[`master`](https://github.com/Today20092/voice-input/tree/master) contains the combined work. Release tags preserve the source for published APKs; merged beta branches and worktrees have been removed. Historical downloads remain in [GitHub Releases](https://github.com/Today20092/voice-input/releases).

The [release workflow](.github/workflows/release-apk.yml) builds on configured branch pushes and `v*` tags. A tag run publishes an APK; a branch run uploads an artifact. Before a new release, update the app version, release notes, and workflow's release-note path. Do not reuse an existing release tag.

The 1.4.3 combined suite contained 127 tests: no failures, one skipped. Lint had no errors, and the signed release build passed. See [1.4.3 release notes](docs/releases/v1.4.3.md) for the scope of device testing.

## Attribution and licenses

This fork preserves FUTO Voice Input's license and notices. See [LICENSE.md](LICENSE.md), the [GitHub mirror](https://github.com/futo-org/voice-input), and the [original GitLab repository](https://gitlab.futo.org/keyboard/voiceinput). This fork is not affiliated with or endorsed by FUTO.

Model weights have their own terms, separate from the app:

- Orukeet: CC BY-SA 4.0, with Oruk AI and NVIDIA Parakeet attribution. The installer preserves `LICENSE-WEIGHTS` and `NOTICE.md` from the pinned package.
- Parakeet TDT: CC BY 4.0. Parakeet Unified and Nemotron English: NVIDIA Open Model License. Nemotron 3.5 Multilingual: OpenMDW-1.1.
- Moonshine: model metadata lists MIT. Cohere Transcribe: Apache 2.0.
- **S1-mini by Superwhisper:** Apache 2.0 with the publisher's naming condition. See the [model notice](docs/third-party/S1-mini-NOTICE.md).

[Sherpa-ONNX](https://github.com/k2-fsa/sherpa-onnx) supplies the runtime and many converted speech-model packages. S1-mini uses pinned [llama.cpp](https://github.com/ggml-org/llama.cpp) code under MIT, with Khronos OpenCL headers and loader under their upstream licenses. Model source links and attribution are also available in the app.

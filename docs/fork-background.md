# Fork background and attribution

[Back to the README](../README.md)

## Why this fork exists

I made this fork because I really like FUTO Voice Input. It already has a good interface and a working Android voice-input flow, which made it a useful starting point. I wanted to keep that experience and try faster local speech models, starting with NVIDIA Parakeet.

The app has since grown to include Orukeet and Cohere Transcribe, both of which have worked well in my use, alongside live-transcription options and optional English text cleanup. My current testing phone is a **Samsung Galaxy S25 Ultra**, and local transcription has been working very well on it. This is hands-on experience, not a controlled performance benchmark; results on other phones may differ.

The goal is to make local dictation useful in everyday Android apps while keeping control over the model, recordings, and resulting text. Audio history lets you recover or retranscribe a recording, a personal dictionary fixes recurring names and phrases, and local diagnostics help investigate failures without automatically uploading your dictation.

If another open-source app has a feature you think would improve this one, please [open an issue](https://github.com/Today20092/voice-input/issues) with a link to the project and a description of what you find useful. Suggestions can help guide new features or compatible integrations, with credit to the original authors and respect for their licenses.

## AI development disclosure

**The code changes made for this fork are written by AI agents, directed by me.** I choose what to build, describe requirements, test the app on my phone, and provide feedback. I am not presenting these changes as code I individually wrote by hand. FUTO's original code and the third-party projects and models retain their own authorship and attribution.

Development uses Codex, originally with GPT-5 and later with **GPT-6 Astra**, following [Matt Pocock's AI coding workflow and skills](https://github.com/mattpocock/skills). I use that approach for planning and implementation, writing and running tests, investigating bugs, and reviewing fixes. I also use AI to research other open-source apps, identify useful features, and assess how they could fit here with appropriate attribution and license compliance.

AI-generated code can contain mistakes. Automated checks and my device testing help catch them, but do not establish that every feature works on every phone. The release notes document what was actually checked, and bug reports are welcome.

Codex and GPT models are development tools only. The app's speech recognition and optional cleanup use the downloaded on-device models in the [model guide](model-guide.md).

## Comparison with FUTO Voice Input

FUTO provides the foundation: local speech recognition, the Android voice-keyboard integration, the floating recognition activity, and the settings interface. This fork keeps that foundation and extends the model choices and dictation workflow.

Upstream development includes multi-model recognition and streaming. The September 26, 2026 [source comparison](research/upstream-share-comparison-2026-09-26.md) reviewed FUTO's **`share` branch at [`95e82ada`](https://github.com/futo-org/voice-input/tree/95e82ada5e2513484dccf68295a5ddde6d223730)** against this fork at `c7a28c4`. That branch includes Moonshine, Parakeet, Nemotron, ASR4ALL, streaming profiles, and a transcribe.cpp integration. Several model families and live-transcription capabilities overlap with this fork; different runtimes and model formats do not establish a speed or accuracy advantage. The review inspected source, not an upstream release or a comparative phone benchmark.

The table below is a **historical comparison with the older standalone app**, at [upstream revision `d6e1eb2`](https://github.com/futo-org/voice-input/tree/d6e1eb2d139dc1a6342a4681c283686cca4bfceb), checked on September 25, 2026. Its Whisper-only descriptions do not describe the newer `share` branch or the separate FUTO Keyboard app. See the [commit-by-commit adoption review](research/upstream-share-adoption-review-2026-09-26.md) for branch-specific differences and attribution.

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

The [adoption plan](specs/upstream-share-adoption.md) and [SHARE tickets](../tickets.md) describe proposed work, not released features. In particular, SHARE-03 reopens manual model updates: implementation `683be27` was deliberately removed by `47566e2`. The reviewed baseline has model installation and integrity checks, but no versioned manual-upgrade workflow. New setup, update, and model-experiment features must be verified before they are documented as available.

### Work combined in 1.4.3

The stable release merges our **history usability**, **Cohere Transcribe**, and **recognizer popup** beta work, along with reviewed **app-wide diagnostics** and the **S1-mini keep-warm fix**. Earlier additions, including the other recognition backends, waveform, dictionary imports, and audio recovery, remain included.

The popup positioning was adapted from the idea in [upstream proposal #172](https://github.com/futo-org/voice-input/pull/172), with this fork's model caption added alongside it. These are additions integrated into this fork; this does not mean the beta work was merged into upstream FUTO.

See the [1.4.3 release notes](releases/v1.4.3.md) for verification and limitations and the [release archive](https://github.com/Today20092/voice-input/releases) for earlier changes. More model choices do not establish a universal speed or accuracy advantage over the original app; that depends on the selected model, language, and phone.

## Attribution and licenses

This fork preserves FUTO Voice Input's license and notices. See [LICENSE.md](../LICENSE.md), the [GitHub mirror](https://github.com/futo-org/voice-input), and the [original GitLab repository](https://gitlab.futo.org/keyboard/voiceinput). This fork is not affiliated with or endorsed by FUTO.

Model weights have their own terms, separate from the app:

- Parakeet Redux: CC BY 4.0 weights by Moondream derived from NVIDIA Parakeet, with a pinned MIT parakeet.cpp runtime. See the [Redux comparison and attribution](parakeet-redux-comparison.md).
- Orukeet: CC BY-SA 4.0, with Oruk AI and NVIDIA Parakeet attribution. The installer preserves `LICENSE-WEIGHTS` and `NOTICE.md` from the pinned package.
- Parakeet TDT: CC BY 4.0. Parakeet Unified and Nemotron English: NVIDIA Open Model License. Nemotron 3.5 Multilingual: OpenMDW-1.1.
- Moonshine: model metadata lists MIT. Cohere Transcribe: Apache 2.0.
- **S1-mini by Superwhisper:** Apache 2.0 with the publisher's naming condition. See the [model notice](third-party/S1-mini-NOTICE.md).

[Sherpa-ONNX](https://github.com/k2-fsa/sherpa-onnx) supplies the runtime and many converted speech-model packages. S1-mini uses pinned [llama.cpp](https://github.com/ggml-org/llama.cpp) code under MIT, with Khronos OpenCL headers and loader under their upstream licenses. Model source links and attribution are also available in the app.

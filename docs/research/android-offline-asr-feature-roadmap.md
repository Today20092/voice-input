# Android Offline ASR Feature Roadmap

Research date: 2026-07-15

## Recommendation

Do not add another recognizer yet. The app already has the hard parts: true Moonshine streaming with partial text, an accuracy-oriented Medium option, WebRTC VAD, personal-vocabulary correction, selectable Parakeet/Whisper fallbacks, and app-private model storage. The best near-term return is to make dictation easier to control and the existing models safer to operate across Android devices.

Implement the first four items below, in order. Treat the benchmark gate as part of each change, not as a user-facing analytics system.

Scoring is relative to this repository: 5 is best for impact, feasibility, and fit; 1 is lowest cost/risk.

| Rank | Feature | Impact | Feasibility | Cost | Risk | Repo fit | Verdict |
|---:|---|---:|---:|---:|---:|---:|---|
| 1 | Adjustable end-of-speech profiles | 5 | 5 | 1 | 1 | 5 | Do now |
| 2 | Field-aware voice commands and formatting | 5 | 4 | 2 | 2 | 5 | Do now |
| 3 | Optional continuous dictation | 4 | 4 | 3 | 2 | 5 | Do next |
| 4 | Verified, resumable model downloads | 4 | 5 | 2 | 1 | 5 | Do next |
| 5 | Repeatable on-device ASR benchmark and device-fit warning | 4 | 4 | 2 | 1 | 5 | Build alongside 1–4 |
| 6 | Complete Android `RecognitionService` support | 3 | 3 | 3 | 3 | 4 | Later, after core UX |
| 7 | Microphone/device diagnostics | 3 | 3 | 2 | 2 | 4 | Add only from real bug reports |

## Ranked implementation notes

### 1. Adjustable end-of-speech profiles

Expose three presets—Short, Normal, Long—that only change the existing post-speech threshold. Keep Normal equal to today's behavior, and keep manual stop available. The capture loop already performs backend-neutral VAD and stops after a fixed number of non-speech frames, so this is one setting and one shared threshold rather than backend-specific work ([current capture loop](../../app/src/main/java/org/futo/voiceinput/AudioRecognizer.kt#L349-L589), [current VAD settings UI](../../app/src/main/java/org/futo/voiceinput/settings/pages/Input.kt#L43-L111)).

Why first: premature cut-offs and long waits are directly perceptible, vary by speaking style, and cannot be solved by choosing a larger ASR model. Do not add an adaptive endpointer yet; presets cover the real control need and are testable.

Acceptance check: replay the same short WAV corpus through each profile; all transcripts must match while stop latency increases monotonically from Short to Long.

### 2. Field-aware voice commands and formatting

Add a small deterministic postprocessor for commands such as “new line,” “new paragraph,” “comma,” “question mark,” “delete that,” and “undo dictation.” Use `EditorInfo.inputType`, `imeOptions`, and nearby cursor text to choose safe formatting for normal text, email/URL, number, phone, and multiline fields. Android explicitly exposes content class, variations, capitalization flags, action semantics, hint locales, and the `IME_FLAG_NO_PERSONALIZED_LEARNING` privacy signal through [`EditorInfo`](https://developer.android.com/reference/android/view/inputmethod/EditorInfo). `InputConnection` already supports composing, committing, deleting surrounding text, and editor actions; `commitText` replaces the composing region and moves the cursor ([Android `InputConnection`](https://developer.android.com/reference/android/view/inputmethod/InputConnection)).

This repository already reads `EditorInfo` but leaves every field-type branch empty, and currently inspects only one character around the cursor ([IME implementation](../../app/src/main/java/org/futo/voiceinput/VoiceInputMethodService.kt#L202-L384)). Reuse the existing personal-vocabulary/final-result pipeline; do not build a grammar engine or add an LLM.

Security boundary: disable text-history access, personal-vocabulary application, and destructive voice commands for password variations and fields requesting no personalized learning. Require explicit enablement for destructive commands.

Acceptance check: one table-driven unit test covering plain text, multiline, email, number, password, punctuation adjacency, and “undo dictation.”

### 3. Optional continuous dictation

Add an opt-in mode that treats each VAD endpoint as an utterance boundary, commits that utterance, then immediately starts the next recording while the IME remains visible. Provide visible Pause and Finish controls; never run after the input view closes. Moonshine is designed for incremental audio and cached streaming state, and its Android library is intended for live on-device applications ([Moonshine repository and Android example](https://github.com/moonshine-ai/moonshine)). The current app already keeps streaming state during one utterance and uses composing text for live partials ([Moonshine backend](../../app/src/main/java/org/futo/voiceinput/moonshine/MoonshineBackend.kt#L19-L107)); the new work is lifecycle/UI orchestration, not a new inference path.

Keep this off by default. Android editors are inconsistent around composing spans: FUTO Keyboard removed composing-based voice insertion after encountering duplication and compatibility bugs, which argues for committing each completed utterance rather than keeping one unbounded composing span ([FUTO Keyboard v0.1.26 release notes](https://github.com/futo-org/android-keyboard/releases/tag/0.1.26)).

Acceptance check: dictate five VAD-separated utterances into a native `EditText`, Compose field, and WebView; closing the IME must release the microphone immediately.

### 4. Verified, resumable model downloads

Populate SHA-256 values for every published Moonshine and Parakeet artifact, retain partial files, resume with HTTP range requests when the server supports them, verify before replacement, and write the completion marker last. The downloader already stages to a temporary file and has optional hash validation, but Moonshine currently passes blank hashes and a retry restarts each file ([Moonshine download metadata](../../app/src/main/java/org/futo/voiceinput/moonshine/MoonshineModel.kt#L16-L62), [download implementation](../../app/src/main/java/org/futo/voiceinput/downloader/DownloadActivity.kt#L295-L392)).

Keep downloads user-initiated. If background continuation is later required, Android recommends WorkManager for work that must survive leaving the visible state; it supports unmetered-network, battery, charging, and storage constraints ([Android persistent work](https://developer.android.com/develop/background-work/background-tasks/persistent), [WorkManager constraints](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work)). Do not migrate the existing foreground download solely for architectural neatness.

Acceptance check: interrupt a large file twice, resume it, corrupt one byte, and verify that the final marker is absent until a clean hash-verified download completes.

### 5. Repeatable on-device benchmark and device-fit warning

Create a developer-only benchmark corpus and record model load time, time to first non-empty partial, endpoint-to-final latency, real-time factor, and peak process memory for Small, Medium, and Parakeet. Android distinguishes Macrobenchmark for complete user journeys from Microbenchmark for isolated hot code; both produce repeatable output, and Macrobenchmark can emit trace and JSON artifacts ([Android benchmarking overview](https://developer.android.com/topic/performance/benchmarking/benchmarking-overview), [Macrobenchmark guide](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview)). Run performance tests on physical phones; Android explicitly discourages emulator performance numbers.

Use results to show a warning—not an automatic switch—when Medium or Parakeet is unsuitable. `ActivityManager.MemoryInfo` exposes total/available memory and the system low-memory state ([Android memory API](https://developer.android.com/reference/android/app/ActivityManager.MemoryInfo)). Preserve the user's explicit model choice.

Minimum gate: one low/mid/high Android device, cold and warm model load, quiet and noisy samples, short and 30-second utterances. Store only aggregate timing/memory data in debug output; never retain user audio or transcripts.

### 6. Complete `RecognitionService` support

Finish this after the IME path is stable. Android's `RecognitionService.Callback` can report readiness, speech start/end, partial results, final results, and errors, and recognizers may invoke callbacks from any thread ([Android `RecognitionService.Callback`](https://developer.android.com/reference/android/speech/RecognitionService.Callback)). This would let compatible clients use the standard `SpeechRecognizer` path in addition to `ACTION_RECOGNIZE_SPEECH` and the voice IME.

There is already an empty service class, but its manifest declaration is disabled ([service stub](../../app/src/main/java/org/futo/voiceinput/WhisperRecognizerService.kt#L7-L19), [manifest](../../app/src/main/AndroidManifest.xml#L133-L168)). Upstream FUTO explicitly supports only the implicit recognition intent and voice IME today and lists `SpeechRecognizer` support as future work ([upstream FUTO repository](https://github.com/futo-org/voice-input)). WhisperIME and Sayboard demonstrate that an offline Android app can expose both an IME and `RecognitionService`, but Sayboard's manifest/foreground-service permissions also show the added lifecycle surface ([whisperIME](https://github.com/woheller69/whisperIME), [Sayboard](https://github.com/ElishaAz/Sayboard)).

Scope it narrowly: one active session, partials only when requested, language/model support reporting, cancellation, and deterministic Android error mapping. Do not attempt continuous recognition through this API.

### 7. Microphone/device diagnostics

Add a debug page showing selected versus routed input device, sample rate, audio-session ID, read errors, and clipping/silence counters. Only add a user-facing microphone picker if Bluetooth or external-mic bug reports justify it. Android allows an `AudioRecord` to request a preferred input device but warns that the requested device is not guaranteed to be the actual routed device; it also exposes preferred microphone direction controls ([Android `AudioRecord`](https://developer.android.com/reference/android/media/AudioRecord)). The app already uses the `VOICE_RECOGNITION` source and requests a toward-user microphone direction where available ([capture setup](../../app/src/main/java/org/futo/voiceinput/AudioRecognizer.kt#L349-L424)).

Do not add automatic noise suppression or acoustic echo cancellation by default: OEM audio pipelines vary, and the present source is already recognition-oriented. Benchmark any audio-effect toggle against WER before shipping it.

## Comparable Android implementation patterns

| Project | Pattern worth retaining | Lesson for this fork |
|---|---|---|
| FUTO Voice Input | `ACTION_RECOGNIZE_SPEECH` plus voice-IME integration | Keep both existing entry points; `SpeechRecognizer` is additive, not a rewrite ([source](https://github.com/futo-org/voice-input)). |
| Sayboard | On-device Vosk IME, built-in/manual model import, `RecognitionService`, foreground download/import | Model management and system service support are viable, but create permission and lifecycle cost ([source](https://github.com/ElishaAz/Sayboard)). |
| whisperIME | Standalone activity, IME, recognition intent, and system `RecognitionService` sharing one offline Whisper model choice | A shared backend can serve all Android entry points; service discovery is still OEM-dependent ([source](https://github.com/woheller69/whisperIME)). |
| Moonshine Android example | Incremental audio, cached streaming state, offline `.ort` model assets | Continue investing in the existing Moonshine path; it is already the best fit for responsive dictation ([source](https://github.com/moonshine-ai/moonshine)). |
| FUTO Keyboard | Field-aware spacing/dictionaries; removed composing insertion after compatibility failures | Keep postprocessing deterministic and test native, Compose, and WebView editors ([release notes](https://github.com/futo-org/android-keyboard/releases/tag/0.1.26)). |

## Phased roadmap

1. **One small release:** endpoint presets, field-aware punctuation/newline commands, password/no-learning guardrails, and the minimal replay/unit checks.
2. **Reliability release:** verified resumable downloads plus the physical-device ASR benchmark matrix.
3. **Power-user release:** opt-in continuous dictation, validated across editor types.
4. **Integration release:** complete `RecognitionService`, then add diagnostics only for observed device-routing failures.

## Explicit non-recommendations

- **No fourth ASR runtime now.** Small/Medium Moonshine already cover responsive/accurate streaming, while Parakeet and Whisper remain fallbacks. Benchmark the three real choices before increasing APK/native complexity. See [the existing model comparison](streaming-asr-android-options.md).
- **No always-on listening or wake word.** It conflicts with this app's short, user-initiated IME lifecycle and adds background-microphone, battery, notification, and privacy burden.
- **No diarization, timestamps, meeting recorder, or transcript history.** Those are transcription-app features, not text-entry features; retaining audio/text also weakens the current privacy model.
- **No automatic LLM rewrite.** Names, numbers, negation, and intent can change. If implemented later, keep it an explicit optional action with undo as described in [the existing rewrite research](liquid-ai-on-device-rewrite.md).
- **No speculative NNAPI/GPU switch.** Execution-provider gains are device/model dependent; change runtimes only after physical-device traces show a bottleneck.
- **No learned personalization pipeline.** The existing explicit personal vocabulary is simpler, inspectable, and reversible. FUTO Keyboard removed unstable on-device finetuning after battery drain and broken-model problems ([release notes](https://github.com/futo-org/android-keyboard/releases/tag/0.1.26)).

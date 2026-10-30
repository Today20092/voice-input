# Intermittent waveform diagnosis

Status: needs-info. Source review completed against `eb5e1dadfd9d6ceb2ebb78ca4f403d2718bddcf8` on `codex/waveform-bug`. No visual failure has been reproduced, no cause established, and no production fix proposed. Existing timing diagnostics remain unchanged.

## Evidence

The [existing phone baseline](phone-diagnostics-baseline-2026-09-25.md) summarizes the user's 1.4.3-moonshine archive from a Galaxy S25 Ultra running API 36. All 33 sessions identify Orukeet. First audio arrived 54–313 ms after session start in the 32 sessions with that event. Those timings do not measure the launch tap or visible waveform. The archive lacks waveform draw timing, entry point, and popup state. Its two shutdown read-failure events are already explained by diagnostic classification; they do not establish a visual cause. This review uses the committed baseline report, not a new inspection of the raw archive.

## Source observations

- `VoiceInputMethodService.kt:360–391`: starting the IME view resets and initializes the recognizer when needed, otherwise refreshes its content.
- `RecognizeActivity.kt:215–231`: activity creation resets and initializes the recognizer. The unobtrusive setting then removes background dimming and positions the window at bottom center with a 24 dp offset. This is a window-placement path, not evidence that it causes the reported failure.
- `AudioRecognizer.kt:640–739, 805–816`: capture reads samples, emits `FIRST_AUDIO`, appends samples, processes VAD, and dispatches the waveform snapshot to the main thread only while recording and the capture generation remains current.
- `RecordingWaveform.kt:4–41`: 320 samples form a bar; at 16 kHz this is 20 ms. Up to 200 bars are retained. A partial bar is available immediately. Silence produces zero-height bars. Samples use fixed PCM amplitude, so quiet input stays visually small.
- `RecognizerView.kt:577–604`: recording start resets the first-frame flag and supplies empty bars. Subsequent waveform updates install recognition content. The draw callback emits `WAVEFORM_FIRST_FRAME` once when bars are nonempty.
- `RecognizerView.kt:101–145`: the 120 dp canvas draws a baseline and amplitude bars, then invokes the draw callback. A recorded first frame therefore does not prove visible speech movement, unclipped window presentation, or uninterrupted later frames.

Graph tracing found `RecordingSession.startRecording` and the `RecognizerView` file as direct callers of `updateWaveform`, with session creation and permission-grant paths upstream. Anonymous override coverage required reading the relevant source directly. No dependency/API change is proposed, so no external library research is needed.

## Missing reproduction information

Questions sent to coordinator `01a0df68-5245-78d1-9e16-f31f778c85fb` for collection in the main chat:

1. Is the waveform late, flat while speaking, missing/disappearing, or clipped? Does dictation still produce text? If late, approximately how long; if disappearing, what immediately precedes it?
2. What exact launch steps reproduce it: voice keyboard/IME, another app's speech popup, or Settings Test dictation? Which keyboard/app launches it?
3. What app version/build and selected model are affected? Does it happen after force-stop, repeated reopening, or both? Approximately how many attempts fail?
4. On the same build, model, and entry point, does it occur with unobtrusive popup enabled and disabled? Record attempt timestamps and export standard diagnostics after each setting. A short screen recording using non-sensitive test text can distinguish drawing from geometry.

## Next reproduction steps

Use the [phone guide](../phone-performance-test.md) with a build containing the existing first-frame diagnostic. Preserve the user's affected settings for the initial reproduction. Keep the model, microphone route, entry point, orientation, and display/font size constant during the popup comparison. Record these conditions rather than assuming the old archive describes the current phone.

First reproduce the exact launch sequence and classify the visible symptom. Record successful and failed attempt counts and cold/reopen status. Compare popup on/off using that same sequence and export immediately after each setting. Match timestamps to session IDs and compare `FIRST_AUDIO` with `WAVEFORM_FIRST_FRAME`. Absence of an event alone is not proof of missing pixels; check cancellation/reset and recording lifetime too. Do not request audio or transcript-inclusive diagnostics for this visual problem.

Once the exact symptom is reproducible, construct a regression check at the failing boundary before changing production code. Capture/ring-buffer failures can use the existing `RecordingWaveformTest` seam. Rendering or lifecycle failures need an Android check exercising the affected entry point and launch/reopen sequence. Existing buffer tests and the large-text layout test do not reproduce this intermittent report. Final verification must include the reported phone scenario and the controlled popup comparison.

SHARE-07 concerns keeping waveform and live transcript visible together. Its layout changes or passing static checks cannot close this defect. If those changes alter the reproduction build, record its revision and repeat the same scenario rather than transferring a pass from another ticket.

## Validation and handoff

Reviewed source paths, existing buffer tests, ticket criteria, diagnostic baseline, phone guide, and the content-free diagnostics ADR. No Gradle build, emulator run, device test, model download, or new regression test was run. Build/emulator work waits for the shared queue. No unchecked acceptance criterion was marked complete. The remaining blocker is the precise symptom and a reproducible phone case; no speculative instrumentation or fix is warranted yet.

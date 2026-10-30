# S25 Ultra diagnostic baseline, 2026-09-25

This is an observational baseline from the user's existing release, before the audio-history UI and diagnostic changes. It is not a controlled benchmark or a comparison between recognition models. The raw archive remains outside the repository.

## Evidence

- Source archive: `voice-input-diagnostics-1790346881412.zip`.
- Samsung Galaxy S25 Ultra, device model `SM-S938U`, Android API 36, ARM64.
- App `1.4.3-moonshine`, version code 57, standalone release build.
- 485 diagnostic records, zero unreadable records and zero reported writer/export drops.
- All 33 recognition sessions identify `orukeet-v0.1.0`. Of those, 27 reached result-ready and Android accepted delivery; three were canceled and three were reset without a delivered result.
- The export settings show cleanup off, VAD on, the optional duration limit off, 300 ms manual-stop drain, model warming on, and recording backups on. Settings are an export-time snapshot, not proof that every earlier session used them. Detailed mode was active at export.
- No dictated audio, transcript text, or reporter notes were included. Recognition accuracy cannot be calculated from this archive.

## Observed timings

The first four rows use the 27 delivered sessions. Each median summarizes its own set of measurements; they do not describe one representative recording.

| Measurement | Median | Minimum to maximum | Count |
| --- | ---: | ---: | ---: |
| Audio passed to recognition | 10.780 s | 2.880 to 47.880 s | 27 |
| Final recognition stage | 0.378 s | 0.105 to 1.714 s | 27 |
| Stop to result-ready | 0.851 s | 0.586 to 2.316 s | 27 |
| Reported model loading stage | 1.552 s | 1.446 to 2.299 s | 27 |
| Session start to first audio | 0.153 s | 0.054 to 0.313 s | 32 |
| Recorder started to first audio | 0.141 s | 0.038 to 0.196 s | 32 |

The median per-session ratio of recognition duration to audio duration is 0.0343 for these final-only Orukeet runs. It excludes loading, recording, tail draining, cleanup, and delivery. Cold versus warm conditions were not controlled, so the load timings are not labeled as either.

There are 100 resource samples. Main-process PSS ranges from 72,985 to 827,411 KiB, with a highest observed sample of about 808 MiB. These samples span different stages, including times when models may have been released. They do not establish an exact peak or steady-state memory requirement. All recorded thermal status and low-memory flag values are zero. There are no managed-crash, process-exit, model-load-failed, session-failed, or delivery-rejected events in this archive; their absence does not prove that no unrecorded failure occurred.

## Waveform investigation

The archive shows that first audio arrived within 313 ms of session start in every session with a first-audio event. Model loading overlapped capture and took longer. These measurements start inside the recording session; they do not measure time from tapping another app's microphone button or time until a waveform becomes visible.

This release does not record waveform draw timing, entry point, or the unobtrusive popup setting. It therefore cannot establish that the popup caused the reported visual problem, or distinguish late drawing from a flat, missing, or clipped waveform. The new first-frame event and popup/build metadata will help with a follow-up report. The precise visible symptom and entry point are still needed.

Two `RECORDER_READ_FAILED` events have `ERROR_CODE=0`. One occurs immediately after `SESSION_RESET`, and the other immediately after `SESSION_CANCELLED`, at the same session elapsed time as that terminal event. The capture loop logged every nonpositive read even when shutdown had already started. The diagnostic fix suppresses read-failure logging during stop/reset/cancel while retaining evidence of unexpected nonpositive reads in an active session. A focused policy regression test reproduces the old classification and verifies the corrected one. This corrects misleading diagnostics; it is not evidence of a waveform rendering fix.

## Reproduction of the calculations

Parse `events.jsonl`, group by `sessionId`, and select sessions containing `DELIVERY_ACCEPTED` for the first four timing rows. Read `AUDIO_MS` from `RECOGNITION_STARTED` and `DURATION_MS` from the corresponding finished stages. Subtract `RECORDING_STOPPED.ELAPSED_MS` from `RESULT_READY.ELAPSED_MS` for the post-stop delay. For first-audio rows, use all sessions containing the relevant pair of events. Sort each measurement set independently; use the middle value for an odd count and the mean of the middle two for an even count. Divide milliseconds by 1,000 for seconds.

Use the [phone dictation protocol](../phone-performance-test.md) for the next measurements. Keep these ordinary-use observations separate from future controlled clips and from history retranscription results.

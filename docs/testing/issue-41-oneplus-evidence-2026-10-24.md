# Issue 41: OnePlus Moonshine candidate evidence, October 24, 2026

Issue: [#41](https://github.com/Today20092/voice-input/issues/41).
Reporter: [updated phone results](https://github.com/Today20092/voice-input/issues/41#issuecomment-6391943376).
Reviewed attachment: [issue-41-moonshine-phone-evidence.zip](https://github.com/user-attachments/files/33916867/issue-41-moonshine-phone-evidence.zip).
Archive SHA-256: `8e96c7d43b029fcd916e46501836be8ec317d2e45211fd6823bad4c945da0876`.

## Scope and provenance

The ZIP was downloaded to temporary storage and inspected read-only with bounded
entry sizes. Its eight entries contain technical events, diagnostics policy,
session/scenario summaries, process-exit information, CI provenance, hashes and
the submitter's review metadata. There are no audio files, transcript-capture
entries, application preferences or raw Logcat captures. The event stream has
253 records, all marked `Standard`, with no rejected records reported by the
collection policy. No attachment code was executed and no raw event files were
added to this repository.

The reporter identifies a OnePlus 15 CPH2749, Android 16 / SDK 36, running package
`org.futo.voiceinput.dev`, `1.4.10-dev`, versionCode 82. They report verifying the
installed APK against SHA-256
`5abf8b1c29725ba81a3795834e7eb8166b8f2902201754dc9bc69776e40bad8a`.
That matches the independently hash-checked candidate from
[CI run 40140732558](https://github.com/Today20092/voice-input/actions/runs/40140732558)
and the [experimental prerelease](https://github.com/Today20092/voice-input/releases/tag/experiment-issues-40-41-7a6c546).

The included `provenance.txt` identifies source
`380b24de10c90c6a39f70a92f1f8150f57c7f02c`, Moonshine 0.0.68, ORT source
`be835efc56aca19b8e810538ec93c8e150e0fc61`, NDK 28.2.13676358, ARM64/API 26,
KleidiAI off and NNAPI exports on. `SHA256SUMS` contains the known candidate APK
and replacement-AAR hashes. This ZIP has no installed-package metadata or device
environment snapshot: it supports the declared build provenance but does not
independently establish the installed package, version, phone or installed APK
bytes. Those installation/device details remain the reporter's declaration.

## Results independently checked against the event stream

| Outcome | Count | Model |
| --- | ---: | --- |
| Successful live final results and accepted deliveries | 6 | Moonshine Medium |
| Completed history retranscriptions | 2 | Moonshine Medium |
| Completed history retranscription | 1 | Whisper; exact variant not recorded |
| No-speech failures | 3 | Moonshine Medium |
| Cancellations | 4 | Moonshine Medium |
| Total recognition sessions | 16 | 15 Moonshine Medium, 1 Whisper |

All six successful live sessions contain `FIRST_PARTIAL`, `RESULT_READY` and
`DELIVERY_ACCEPTED`. The seventh `FIRST_PARTIAL` belongs to the deliberate
mid-speech cancellation. All 12 `CLEANUP_FINISHED` records have `APPLIED=0`,
consistent with S1 cleanup being off. These events establish the app's accepted
delivery boundary; they do not independently compare a receiving editor's text.

| Scenario / session prefix | Retained evidence |
| --- | --- |
| Short live, `4602d32c` | Partial, final result and accepted delivery; 7,680 ms audio |
| Longer live, `299f11a6` | Partial, final result and accepted delivery; 32,600 ms audio |
| History, `c0b67313` | Moonshine retranscription completed; 32,400 ms audio |
| Deliberate speaking cancellation, `4dbeca63` | Partial, 63 speech VAD frames, cancellation; no final result or delivery |
| Recovery, `79624daa` then `844e88a2` | First retry ended in no speech; later live attempt succeeded, starting about 59 seconds after the no-speech attempt started |
| Whisper history, `840157e8` | Retranscription completed; 32,400 ms audio |
| Return to Moonshine history, `bbfd4665` | Retranscription completed; 32,400 ms audio |
| Additional Moonshine live, `5f2ca818` | Partial, final result and accepted delivery; 4,900 ms audio |

The three other cancellations have no speech VAD frames and capture elapsed
times of 1,173, 1,152 and 909 ms. Their cause is not recorded. They are not three
additional deliberate speaking-cancellation tests. Each of the three no-speech
attempts records `NO_SPEECH` followed by `SESSION_FAILED`; these application
failures must not be counted as successful dictations or native crashes. The
bundle also retains two failed model-download events and eventual completion;
the reporter identifies those failures as DNS lookups.

## Crash, process and timing limits

The event stream contains one `PROCESS_EXIT`, reason 10, status 0. The included
exit-info snapshot identifies `USER REQUESTED`, `REMOVE TASK`, at 20:53:30 EDT,
between live session `a551ac6e` and the Whisper retranscription. There are two
`APP_STARTED` records with different collector IDs. No native-crash exit is
recorded. The complete Moonshine-to-Whisper-to-Moonshine sequence therefore
crossed an intentional process exit; it does not prove a full model round-trip
within one uninterrupted process.

The reporter describes two bounded Logcat captures without SIGILL or fatal
signals, with the final history run outside their time windows, plus another
capture for the additional live test. Raw Logcat is absent from this ZIP, so that
claim cannot be rechecked from the attachment. Missing crash events in a bounded
collection do not establish that no unrecorded crash occurred or identify the
original faulting instruction.

Streaming live `RECOGNITION_FINISHED` durations represent the final flush, not
recognition of the entire recording. Whisper's approximately 121.5-second
history decode is an unexplained observation with no exact model variant; these
functional checks are not a controlled performance comparison. Manual test-button
attempts have no established target build in the retained evidence and are
excluded.

## Implication for a normal signed beta

This is useful affected-device evidence for the exact pinned workaround: Medium
can stream, finalize, retranscribe, cancel while speaking and recover on the
reported phone without a recorded native crash. The original KleidiAI fault
remains a plausible explanation, not a proven instruction-level diagnosis.
Moonshine Small, a second ARM64 phone, older Android versions, sustained long
dictation and repeated deliberate speaking-cancel/retry cycles are not established
by this ZIP. Any separate S25 evidence must retain its own build/signature and
coverage qualifications.

For the proposed normal signed beta, retain the same Moonshine API/models and
the hash-pinned replacement AAR. Its ARM64 `libonnxruntime.so` is 18,550,704 bytes,
SHA-256 `cbe4d71990f1032bcc7b4b7baf3e0b18327af6c25ba4bf106b5ecd42ddf22d30`.
Validate the actual signed APK's runtime bytes and ABI/instruction guards,
preserve ORT and Moonshine attribution, identify the modified runtime in technical
version reporting, and keep S1 speculation disabled. Test the resulting normal
application/update path before calling the production issue resolved.

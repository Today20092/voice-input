# Ticket 21: truthful delivery and diagnostic failure isolation

Source base: `23212c45f3112a09aa244f548eb816e69b34d55f`. Issue: [#21](https://github.com/Today20092/voice-input/issues/21).

## Changed behavior

- Final transcript preparation now returns an optional in-memory capture. It never writes a `finalDelivered` stage before delivery.
- The existing dictation generation check precedes the synchronous handoff. Runs canceled/reset before that handoff persist no stale capture. Accepted or rejected attempts retain raw/cleaned/prepared stages; only accepted handoffs produce the delivered stage. Valid empty cleanup output remains a produced empty stage.
- A successful handoff can reset the IME and cancel the recognition coroutine synchronously. Its capture is saved afterward on the IO dispatcher within noncancelable cleanup, without treating that reset as delivery rejection. Transcript-capture preference is rechecked before writing; failed capture writes cannot replace the delivery outcome.
- A successful IME commit remains accepted if switching back to the previous keyboard fails. That separate failure has a content-free `KEYBOARD_SWITCH_FAILED` event. Android acceptance does not prove the receiving app rendered text.
- Schema 2 captures distinguish `Prepared`, `Accepted`, `Rejected` and `Canceled`. Schema 1 decodes with `Unknown`; the UI labels its former delivered stage as unverified rather than claiming historical acceptance.
- A finalized no-speech failure retains its eligible raw capture and cleanup failure/bypass reason as `Prepared`, with no produced delivered stage. No handoff was attempted, so it is neither rejected nor canceled. A true cancellation before handoff still suppresses stale capture persistence.
- History retranscription uses shared cleanup without claiming delivery to an editor. Saved audio and its prior transcript retain the existing replacement/failure rules.
- S1 diagnostic discovery, library enumeration, resource measurement and record construction are best effort. Their failure cannot replace valid cleanup or its missing-model fallback. Coroutine cancellation is rethrown instead of becoming a service-failure fallback.
- App diagnostic session construction may fail independently; event preparation/queue submission and resource collection are guarded. Dictation/history continue without a session in that case.
- A provider/share-sheet failure removes its prepared transcript ZIP best effort and rethrows the original error to the existing settings error/retry UI. Existing load/delete/export UI handling from #36 is retained. Archive-write partial-file cleanup is coordinated with #19.

## Regression coverage and execution status

The maintainer approved the existing public storage/export, delivery/cancellation and cleanup test boundaries. Tests were committed before their corresponding implementation; no local Gradle, Android build, emulator, native build, or model download was run, per the RAM constraint.

- `TranscriptFinalizerDeviceTest.preparingTextDoesNotPersistAnUndeliveredTranscript` uses existing `TranscriptFinalizer.clean` and capture-load APIs. On the base source, the pre-handoff capture exists and violates the assertion. Execution requires an ARM64 device; CI compilation alone does not establish its red/green result.
- `TranscriptFinalizerDeviceTest.diagnosticResourceFailureDoesNotReplaceMissingModelFallback` injects Android application-info failure through a `ContextWrapper`. It expects the original raw-text fallback, rather than a diagnostic exception. Device execution remains pending.
- `S1MiniTranscriptDeliveryTest` covers prepared/rejected final-stage absence, valid-empty accepted output and canceled/failure-stage retention. These JVM tests need the added schema API; replay before implementation demonstrates compilation failure rather than a runtime red. CI must execute the final tests for behavioral evidence.
- `S1MiniCaptureExecutionTest.failedShareRemovesThePreparedTranscriptArchiveAndKeepsTheOriginalFailure` injects Android share failure and verifies capture export cleanup plus original exception identity. Device execution remains pending.
- The only app override and existing recording-test override of `RecordingSession.finished` now return handoff acceptance. A repository search found no additional overrides. Nullable diagnostic-session callers in dictation, history and downloader were checked.
- `git diff --check` passed locally. Unit tests, lint and instrumentation compilation are delegated to CI and are not yet claimed passing here.

## Required before release

Run CI unit/lint/Android-test compilation and review the merged #19/#21 capture/archive code. On a physical phone, verify normal and valid-empty results through IME/activity, rejected input connection, cancellation before delivery, accepted-commit switch-back failure, diagnostic/storage failures and transcript export/share retry. Verify original recognition errors and ownership release remain intact. These device gates are not satisfied by source review or test compilation.

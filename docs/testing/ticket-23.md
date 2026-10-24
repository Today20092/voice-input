# Ticket 23: bounded, explanatory diagnostic evidence

Issue: [#23](https://github.com/Today20092/voice-input/issues/23). Base includes the #19 privacy/retention and #21 truthful-delivery changes through `bf31d24`.

## Implementation

- Schema 2 events add a random local collector UUID and increasing sequence. They order one collector lifetime even when wall-clock timestamps tie. Existing schema 1 records decode with unavailable provenance rather than fabricated values.
- The existing small policy file retains at most 32 collection-mode changes within seven days. Each stores enabled state and the detailed-mode deadline, so restart, early stop and automatic deadline expiry can be distinguished. Expired entries are purged on access; Clear removes their prior history. The app event budget already reserves 4 KiB for policy metadata. Oversize policy input is rejected rather than loaded without a bound.
- Model-load events record the attempted runtime and installed model revision, rather than export-time model settings. Sherpa's actual pinned dependency version is recorded for its managed paths. Forced Whisper language and reported recognition language are captured only where available. Runtime/model/version/language fields are allowlisted at the storage boundary; unknown native/runtime/thread configuration remains unavailable, never inferred from nominal defaults. No whole-settings dumps, paths or artifact filenames are added.
- `CLEANUP_FINISHED.reportId` joins an ordinary session to its sanitized S1 run via the existing random UUID. The #19 S1 privacy projection already retains that UUID. Missing joins remain explicit possibilities for bypasses, older records or diagnostic failure; no transcript or audio identity is introduced.
- One `CAPTURE_SUMMARY` per recording reports real accepted PCM sample count, VAD evaluated/speech frames, drained samples and requested drain bound where attempted, zero/negative reads, final consecutive silence decisions and stop reason. The counters reuse the existing capture/VAD/drain path and exclude synthetic final silence. VAD frames cover evaluated audio, not every skipped/catch-up read or paused VAD interval. No extra periodic sampling, audio copy or recording is added.
- `NO_SPEECH` identifies empty recognition independently of capture evidence. It is not a claim that capture failed or that the microphone heard silence.
- Actionable model activation/deletion/invalidation and history open/append/finish/save/purge/retranscription failures carry bounded numeric operation and sanitized failure categories. Existing error handling, cancellation and ownership remain intact. No recording IDs, transcript text or paths are added.
- Download sizes now use `EXPECTED_BYTES` only when known. They are planned asset sizes, not zero transferred bytes. Actual transfer counts are not invented.
- Resource sampling failure records an unavailable marker when possible. A missing/failed ordinary store can still produce a report with collection marked unavailable; settings/environment/S1 failures permit bounded fallback metadata. Export checks cancellation before writing.
- Archives add `collection-modes.json` and explain units, enums, omitted/unknown fields, legacy records and S1 joins. Readable summaries separate current-process writer drops, persisted size rejections, unreadable records/segments and records trimmed for the export budget. They print capture evidence and effective configuration. Earlier-process writer-drop totals and exact reasons for absent history remain unavailable.

## Tests and checks

The maintainer approved public collector/storage/export boundaries. Test-first commits: `d0cfcb8` and `638bef1`. Schema additions cannot compile against the prior API; that is compile-red, not a demonstrated runtime failure. The empty-report README/summary regression also exercises the existing `DiagnosticArchive.write` API and fails an assertion on the base behavior when run independently of schema-dependent tests.

`DiagnosticEvidenceTest` covers equal-time sequence ordering, collector restart identity, detailed deadline/history preservation, mode-history bounds, persisted oversize rejection counts, configuration/UUID sanitization, absent evidence, separate drop/trim explanations and readable capture-versus-empty-output evidence. Existing archive tests are updated for the new safe ZIP entry and separate loss counters.

`git diff --check` passed. Static inspection checked the extended `DiagnosticStore.record`, `AppDiagnostics.event` and `DiagnosticSession.event` signatures and existing positional callers. No Gradle, native build, Android emulator, model download or APK build ran locally. CI must execute unit tests/lint and compile Android tests; these results are not claimed passing here.

## Pending device/release evidence

Physical-phone scenarios and detailed-mode overhead remain pending: changed settings between sessions; detailed activation/stop/deadline expiry/restart/process death; permission/no-speech/missing-corrupt model/load-decode/resource/storage failures; cancellation and original-error preservation; fallback export/share and issue-form preview; retention, sensitive fixtures and native Logcat boundaries. Measure overhead with actual device/build/conditions before claiming improvement or full coverage.

Not every native backend exposes effective thread/runtime configuration. Those values remain absent/unavailable, with the app build revision identifying native source; this implementation does not infer them or establish every-model troubleshooting coverage. The capture counters explain observations, not #8's unconfirmed Orukeet root cause. Broader device gates remain in #15; comparative measurement remains in #17.

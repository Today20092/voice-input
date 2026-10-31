# Keep standalone recognition separate from FUTO Keyboard

Latest owner decision: [adopt FUTO's full shared recognizer implementation](../specs/full-futo-recognizer-adoption.md)
and port the fork's added models and app features onto it. Implementation is now
deferred on `codex/issue-48-full-futo-adoption`, separate from the upstream-sync
branch. Future implementation targets `development`. This supersedes the partial-adoption
recommendations below. The exact native-source access gate is unresolved;
production remains unchanged. External-keyboard integration stays separate.

Status: external-provider integration is retained. On October 30, 2026, the owner
reopened the shared-recognizer adoption decision for an upstream-alignment
assessment. A second embedding application is not a prerequisite for that
assessment. No recognition implementation is replaced by this decision update.
The assessment and bounded proof are tracked in
[#48](https://github.com/Today20092/voice-input/issues/48); initial findings are in
[the source assessment](../research/shared-recognizer-migration-assessment-2026-10-30.md).

## Bounded proof recommendation

The [opt-in proof](../research/shared-recognizer-reproducibility-2026-10-30.md)
now builds and runs with included ASR4ALL Medium live transcription and Orukeet
final-only recognition. Activity-result and native-editor delivery checks pass.
Retain the fork's production recognition flow while continuing a deeper
shared-recognizer proof. The owner clarified that upstream maintenance reuse
while preserving added models and app features is the goal. The view-only
adapter is an intermediate proof, not the target architecture or evidence for
rejecting adoption. No full migration parity is asserted.

The injected path reuses the shared view while retaining the fork's entire
recording/session core. Consequently it does not inherit upstream recording
fixes automatically. The module also introduces a conflicting VAD JNI payload;
the proof excludes that runtime instead of arbitrarily choosing a duplicate.
Restoring routing, waveform/status detail, permission/download recovery and
accessible partial presentation requires additional maintained patches. No
speed, accuracy or maintenance-cost improvement has been demonstrated.

Keep the experiment isolated and disabled in normal builds. Next test a runtime
runner injection point inside the shared recorder, plus explicit retained-audio,
session-policy and finalization hooks. Reuse upstream recording/orchestration
where parity permits; preserve the fork's engines, catalog and comparison tools.
Measure the resulting maintained patch and actual upstream code exercised before
deciding whether adoption reduces maintenance work.
Performance comparisons and the remaining parity gates stay open; do not close
#48 as a completed production migration. ADR 0001 is unchanged.

Use FUTO Keyboard's external-provider protocol to launch this app. Keep recording,
recognition, model ownership, history, cleanup, and diagnostics in the standalone
app. Importing upstream's shared Android module into this APK cannot change the
recognizer already compiled into an installed keyboard APK, its inline UI, or its
private model/settings storage.

The upstream keyboard has both an embedded `RecognizerView` path and an external
voice-input path. Its `VoiceInputSwitchActivity` configures the external package
through `targetPackage` and `mode`; our provider setup already implements that
protocol. A read-only `check` reports the remembered package, not whether external
recognition is enabled. Keep the current explicit setup and manual fallback.

Assess adoption of upstream's existing shared recognizer for reuse and simpler
future upstream updates, including for this standalone app alone. Maintaining our
own keyboard build is a separate choice. Compare actual upstream reuse with the
ongoing cost of adapting our recognition models and app features; do not count a
package move alone as upstream alignment. A migration must define recording/session
ownership, settings and model storage, UI lifecycle, and feature parity for history,
cleanup, vocabulary, and diagnostics. Preserve our backend interfaces where useful.
Replacing runtimes with transcribe.cpp is a separate measured decision under ADR
0001, not an automatic consequence of adopting an Android module.

The October 2026 decision to bundle ASR4ALL Medium changes the standalone app's
default and initial offline availability. It does not require a shared module or
a keyboard fork. Existing saved model selections remain valid; English Whisper
becomes an optional download.

Source evidence: upstream keyboard [embedded/external actions](https://github.com/futo-org/android-keyboard/blob/31e720ddcc2df6259069b2ae7cf0f1defaf2cb74/java/src/org/futo/inputmethod/latin/uix/actions/VoiceInputAction.kt)
and [provider switch activity](https://github.com/futo-org/android-keyboard/blob/31e720ddcc2df6259069b2ae7cf0f1defaf2cb74/java/src/org/futo/inputmethod/latin/uix/settings/VoiceInputSwitchActivity.kt).

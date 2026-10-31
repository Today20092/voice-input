# Keep standalone recognition separate from FUTO Keyboard

Status: external-provider integration is retained. On October 30, 2026, the owner
reopened the shared-recognizer adoption decision for an upstream-alignment
assessment. A second embedding application is not a prerequisite for that
assessment. No recognition implementation is replaced by this decision update.
The assessment and bounded proof are tracked in
[#48](https://github.com/Today20092/voice-input/issues/48); initial findings are in
[the source assessment](../research/shared-recognizer-migration-assessment-2026-10-30.md).

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

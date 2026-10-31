# Keep standalone recognition separate from FUTO Keyboard

The owner selected full upstream recognizer adoption for future work under
[#48](https://github.com/Today20092/voice-input/issues/48), then deferred it until
reproducible native source is available. Its proof is preserved separately on
`codex/issue-48-full-futo-adoption`. This upstream-sync branch retains the current
production recognition flow. A second maintained app or keyboard fork is not a
prerequisite for adopting upstream's shared recognizer.

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

Future adoption should use upstream's recording/model-loading/runtime foundation
and port the fork's added models and app features onto it. It must define
recording/session ownership, settings and model storage, UI lifecycle and feature
parity. The current Sherpa implementation under ADR 0001 remains production until
the deferred migration's source, model compatibility and device gates pass.

The October 2026 decision to bundle ASR4ALL Medium changes the standalone app's
default and initial offline availability. It does not require a shared module or
a keyboard fork. Existing saved model selections remain valid; English Whisper
becomes an optional download.

Source evidence: upstream keyboard [embedded/external actions](https://github.com/futo-org/android-keyboard/blob/31e720ddcc2df6259069b2ae7cf0f1defaf2cb74/java/src/org/futo/inputmethod/latin/uix/actions/VoiceInputAction.kt)
and [provider switch activity](https://github.com/futo-org/android-keyboard/blob/31e720ddcc2df6259069b2ae7cf0f1defaf2cb74/java/src/org/futo/inputmethod/latin/uix/settings/VoiceInputSwitchActivity.kt).

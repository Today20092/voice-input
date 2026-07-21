# Android Voice Input Protocol Alignment

## Problem Statement

The app already uses the recommended Android integration split:

- `VoiceInputMethodService` is the primary surface and inserts partial/final text through `InputConnection`.
- `RecognizeActivity` provides one-shot `ACTION_RECOGNIZE_SPEECH` compatibility.
- `AudioRecognizer` and `RecognizerView` share recognition behavior across both entry points.

The remaining mismatch is repository intent, not architecture. `WhisperRecognizerService` is an empty `RecognitionService`, its manifest entry is commented out with the wrong binding permission, and `RecognizeActivity` publishes a fabricated confidence score. These artifacts imply support the app does not provide.

This spec aligns the implementation with [the protocol research](../research/android-voice-input-integration-protocol.md) without adding a third runtime surface.

## Current App Comparison

| Research recommendation | Current app | Decision |
| --- | --- | --- |
| Use an IME for direct dictation into editors | `VoiceInputMethodService` extends `InputMethodService`, sends partials with `setComposingText()`, and finals with `commitText()` | Keep |
| Keep `ACTION_RECOGNIZE_SPEECH` for one-shot compatibility | Exported `RecognizeActivity` returns `RecognizerIntent.EXTRA_RESULTS` | Keep and tighten contract |
| Share inference independently of the Android entry point | Both surfaces use `RecognizerView` and `AudioRecognizer` | Keep |
| Do not use the platform on-device recognizer as the app's publishing protocol | App runs its own Moonshine, Parakeet, and Whisper backends | Keep |
| Add `RecognitionService` only for a concrete client | Empty `WhisperRecognizerService` exists but is disabled | Remove dead stub; defer real service |
| Protect a future provider with `BIND_SPEECH_RECOGNITION_SERVICE` | Commented declaration uses `RECORD_AUDIO` as its binding permission | Remove misleading declaration; document the correct requirement here |
| Return only supported recognition metadata | Activity always reports confidence `1.0f` although backends do not produce confidence | Stop returning fabricated confidence |

## Implementation Spec A: Align Existing Protocol Surfaces

### Goal

Make the two supported Android contracts explicit and truthful without changing the recognition UX or backend behavior.

### Required Changes

1. Keep `VoiceInputMethodService`, its `BIND_INPUT_METHOD` protection, and its `android.view.InputMethod` filter unchanged.
2. Keep `RecognizeActivity` exported for `android.speech.action.RECOGNIZE_SPEECH`.
3. Continue returning exactly one final transcript in `RecognizerIntent.EXTRA_RESULTS`.
4. Remove `RecognizerIntent.EXTRA_CONFIDENCE_SCORES` until a selected backend supplies a real, calibrated confidence value for the returned transcript.
5. Preserve `RESULT_CANCELED` for user cancellation, permission rejection, and a canceled model download.
6. Delete `WhisperRecognizerService.kt` and its commented manifest declaration. It is not a working compatibility surface and is backend-misnamed now that Moonshine is the default.
7. Keep `DummyService` and its `category.TEST` filters because it documents an existing keyboard-compatibility workaround; changing that workaround is outside this protocol alignment.
8. Do not add a foreground service. Recognition remains visible and user-initiated through the IME or activity.

### Acceptance Criteria

- Dictation from the enabled IME still writes partial composing text and commits one final transcript.
- Canceling IME dictation does not commit pending composing text.
- A caller launching `ACTION_RECOGNIZE_SPEECH` for a result receives `RESULT_OK` with one non-empty `EXTRA_RESULTS` item after successful recognition.
- The activity result omits `EXTRA_CONFIDENCE_SCORES` when the backend has no confidence value.
- Canceling the activity returns `RESULT_CANCELED` and no transcript.
- The merged manifest contains the IME and recognition activity but no production `RecognitionService` provider.
- `:app:testDevDebugUnitTest` and `:app:lintDevDebug` pass.

### Smallest Useful Tests

- Add one activity contract test around result construction: transcript present, confidence absent.
- Reuse existing recognition/IME tests if they cover cancellation; add only one focused regression test if they do not.
- Verify the merged manifest in the build rather than adding a custom manifest parser.

## Implementation Spec B: Conditional `RecognitionService`

### Gate

Do not implement this spec until there is a named client, reproducible integration case, or acceptance test that requires `SpeechRecognizer` with this app's explicit `ComponentName`.

### Required Contract When the Gate Is Met

1. Add a backend-neutral `RecognitionService`; do not revive the name `WhisperRecognizerService`.
2. Declare it exported with the `android.speech.RecognitionService` action and `android.permission.BIND_SPEECH_RECOGNITION_SERVICE` binding permission.
3. Reuse the existing recognition engine below the Compose layer. Do not instantiate `RecognizerView` in the service.
4. Implement one active request at a time. A concurrent start returns `SpeechRecognizer.ERROR_RECOGNIZER_BUSY`.
5. Map start, stop, cancel, permission failure, missing model, no speech, backend failure, partial results, and final results to the Android callback contract.
6. Emit exactly one terminal callback (`results()` or `error()`) per accepted request; cancellation must release audio and emit no later result.
7. Create microphone capture from the caller-attributed context exposed by `RecognitionService.Callback` where the platform API requires it.
8. Support only microphone input initially. Add API 33 injected-audio support only when the gated client requires it.

### Acceptance Criteria

- An instrumentation client explicitly binds with `SpeechRecognizer.createSpeechRecognizer(context, componentName)` and completes one successful request.
- Stop finalizes the current request; cancel releases it without a later terminal result.
- A second simultaneous request receives `ERROR_RECOGNIZER_BUSY`.
- Missing permission/model and no-speech paths return documented `SpeechRecognizer` errors.
- The IME and recognition activity behave identically after the provider is added.

## Out of Scope

- Replacing local backends with `createOnDeviceSpeechRecognizer()`.
- Becoming the device's default recognition provider automatically.
- Continuous/background listening.
- Inventing confidence scores or alternate transcripts.
- API 33 support/model-download callbacks without a client that uses them.

## Implementation Order

1. Implement Spec A as one small cleanup/contract change.
2. Validate it on the IME and activity paths.
3. Leave Spec B unimplemented until its gate is satisfied.

# Android integration protocol for an offline voice-input app

Research date: 2026-07-21

## Decision

Keep **`InputMethodService` (IME) as the primary Android integration**, keep the exported **`ACTION_RECOGNIZE_SPEECH` activity as a compatibility surface**, and treat **`RecognitionService` as an optional later interoperability feature—not a replacement architecture**.

That combination best matches this product:

1. The IME is the only standard Android surface that can put locally recognized text directly into almost any focused editor through `InputConnection`. Android explicitly recommends `InputConnection.commitText()` for capable IMEs. [InputMethodService API](https://developer.android.com/reference/android/inputmethodservice/InputMethodService#sendDownUpKeyEvents(int))
2. `ACTION_RECOGNIZE_SPEECH` lets apps that launch a speech-recognition activity use this app and receive an activity result. It is simple and already implemented here, but it is a one-shot, UI-owning contract rather than inline dictation. [RecognizerIntent.ACTION_RECOGNIZE_SPEECH](https://developer.android.com/reference/android/speech/RecognizerIntent#ACTION_RECOGNIZE_SPEECH)
3. A `RecognitionService` would let `SpeechRecognizer` clients call this app's engine through Android's callback protocol. It broadens app-to-app compatibility, but it does not provide direct text-field insertion and ordinary clients normally bind to the system-selected service unless they explicitly name this component. [SpeechRecognizer.createSpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer#createSpeechRecognizer(android.content.Context,%20android.content.ComponentName))
4. `SpeechRecognizer.createOnDeviceSpeechRecognizer()` is a **consumer API for an on-device recognizer supplied by the device**, not a way to publish or run this repo's Moonshine/Parakeet engines. It exists only from API 31 and must be availability-checked. [SpeechRecognizer on-device APIs](https://developer.android.com/reference/android/speech/SpeechRecognizer#createOnDeviceSpeechRecognizer(android.content.Context))

In short: **IME first, recognition activity second, provider service only when a real client requires it.** There is no single Android voice protocol that replaces all three because they solve different entry points.

## Current repository fit

The existing architecture is already aligned with that decision:

- `VoiceInputMethodService` extends `InputMethodService`, renders `RecognizerView`, commits final text with `InputConnection.commitText()`, and uses composing text for partial results.
- `RecognizeActivity` handles `android.speech.action.RECOGNIZE_SPEECH`, runs the same `RecognizerView`, requests microphone permission in an activity, and returns the transcript through `RecognizerIntent.EXTRA_RESULTS`.
- Both paths share recording, VAD, model selection, and backend lifetime through `AudioRecognizer`; this is the correct reuse boundary.
- `WhisperRecognizerService` is an empty `RecognitionService` stub. Its manifest declaration is commented out. The commented declaration uses `RECORD_AUDIO` as the service binding permission; a real provider declaration should instead be protected by Android's `BIND_SPEECH_RECOGNITION_SERVICE` binding permission and independently request `RECORD_AUDIO` for capture. The Android service contract identifies `android.speech.RecognitionService` as the interface clients bind to. [RecognitionService API](https://developer.android.com/reference/android/speech/RecognitionService)
- `DummyService` advertises the recognition-service action only under a test category as an existing workaround; it is not a functional provider.

## API comparison

| Surface | What it is best at | Main limitations | Verdict here |
|---|---|---|---|
| `InputMethodService` / IME | User-triggered dictation into the current editor; final and composing text; access to surrounding editor context | User must enable/select the IME; an IME service cannot itself show a runtime-permission dialog; password/sensitive fields need conservative behavior | **Primary** |
| `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` activity | Lowest-cost compatibility with apps using the classic activity-result flow; app owns permission and UI | One-shot modal flow; caller must use an activity result or `PendingIntent`; no live composing-text channel to the caller | **Keep** |
| `RecognitionService` provider | Callback-based integration for apps using `SpeechRecognizer`; can expose partials, errors, language/support checks, and injected audio | More lifecycle and concurrency work; callers may use another system-selected provider; no direct editor insertion; must map the complete Android callback/error contract | **Optional later** |
| `SpeechRecognizer.createOnDeviceSpeechRecognizer()` | Letting *this app consume* a device-provided offline recognizer | API 31+; device/service/model availability varies; does not expose this app's models to other apps | **Not the app protocol** |

### Why the IME wins for the core experience

Android defines an IME as an application containing a special service declared with `BIND_INPUT_METHOD`, the `android.view.InputMethod` action, and IME metadata. That is exactly the repository's manifest shape. [Create an input method](https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method#Manifest)

The IME owns the interaction while it is visible and can send text through the current `InputConnection`. That supports this repo's important behavior—partial composing text, punctuation-aware commit, cancel/switch-back, and a consistent offline model UI—without requiring every target app to integrate a speech SDK.

### What the recognition activity adds

`ACTION_RECOGNIZE_SPEECH` starts an activity, prompts for speech, and returns results through the activity-result contract. Starting it without expecting a result is explicitly unsupported. The intent requires `EXTRA_LANGUAGE_MODEL`; language, maximum results, offline preference, and other extras are optional. [RecognizerIntent.ACTION_RECOGNIZE_SPEECH](https://developer.android.com/reference/android/speech/RecognizerIntent#ACTION_RECOGNIZE_SPEECH)

The current activity should remain a thin adapter over `RecognizerView`/`AudioRecognizer`. It is useful compatibility, but it cannot replace the IME because a returned string is not an ongoing editor session.

### What a real `RecognitionService` would add

`RecognitionService` is the provider side of `SpeechRecognizer`. `onStartListening(Intent, Callback)` receives a recognition request; the implementation reports readiness, speech events, partial/final results, or an error through the callback and must implement stop and cancel behavior. [RecognitionService.onStartListening](https://developer.android.com/reference/android/speech/RecognitionService#onStartListening(android.content.Intent,%20android.speech.RecognitionService.Callback))

It is worthwhile only if interoperability with `SpeechRecognizer` clients is a concrete requirement. An implementation should:

- adapt the existing `AudioRecognizer` session to one Android `Callback`;
- enforce the service's concurrent-session limit (one session is the minimal safe starting point);
- map permission/model/busy/cancel/no-match failures to `SpeechRecognizer` error codes;
- honor relevant `RecognizerIntent` inputs and explicitly reject unsupported combinations;
- report partials only when the selected backend produces them;
- implement API 33 support/model-download callbacks if claiming those capabilities;
- support `RecognizerIntent.EXTRA_AUDIO_SOURCE` on API 33+ or report it unsupported; when present, the caller supplies and closes the audio descriptor. [RecognizerIntent.EXTRA_AUDIO_SOURCE](https://developer.android.com/reference/android/speech/RecognizerIntent#EXTRA_AUDIO_SOURCE)

Microphone capture inside `onStartListening` also needs caller attribution. Android directs providers to create an attribution context from `Callback.getCallingAttributionSource()` and use that context to construct `AudioRecord`, so both caller permission and proxy access are attributed correctly. [RecognitionService.onStartListening](https://developer.android.com/reference/android/speech/RecognitionService#onStartListening(android.content.Intent,%20android.speech.RecognitionService.Callback))

Do not build this merely to use Android's “on-device” label. A client can explicitly choose the component overload of `createSpeechRecognizer`, but typical clients use the system default. Therefore installing a provider does not guarantee that arbitrary apps will route recognition to it. [SpeechRecognizer.createSpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer#createSpeechRecognizer(android.content.Context,%20android.content.ComponentName))

## Audio, lifecycle, and privacy constraints

1. **Keep recognition user-visible and user-initiated.** `RECORD_AUDIO` is a runtime permission. The existing activity can request it; the IME should continue directing an ungranted user to an activity/settings flow rather than trying to display a permission dialog from the service. [Request runtime permissions](https://developer.android.com/training/permissions/requesting)
2. **Do not turn ordinary dictation into background capture.** A microphone foreground service requires the `microphone` service type, `FOREGROUND_SERVICE_MICROPHONE`, and granted `RECORD_AUDIO`. Because microphone permission is while-in-use, Android restricts creating such a service while the app is in the background. [Foreground-service microphone type](https://developer.android.com/develop/background-work/services/fgs/service-types#microphone)
3. **A manifest service type is not a reason to keep recording.** Short sessions while the IME/activity is visibly active should remain bounded to that interaction; add a true foreground service and notification only if a separately requested feature must continue after the visible UI loses foreground status.
4. **Handle capture contention.** On Android 10+, two ordinary apps may both appear to capture while only one receives real audio; the other can receive silence according to Android's priority rules. A timeout/no-speech path must therefore remain reliable. [Sharing audio input](https://developer.android.com/media/platform/sharing-audio-input#two-ordinary-apps)
5. **Do not assume platform `SpeechRecognizer` is private or offline.** Android warns that the general API may stream audio to remote servers and is not intended for continuous recognition. `EXTRA_PREFER_OFFLINE` is a preference interpreted by the provider, not proof that recognition stayed local. [SpeechRecognizer API](https://developer.android.com/reference/android/speech/SpeechRecognizer) [RecognizerIntent.EXTRA_PREFER_OFFLINE](https://developer.android.com/reference/android/speech/RecognizerIntent#EXTRA_PREFER_OFFLINE)

## Recommended roadmap

### Now: no protocol migration

- Keep `VoiceInputMethodService` as the main surface.
- Keep `RecognizeActivity` as the classic speech-intent adapter.
- Keep all recording/model logic behind the existing shared `RecognizerView` → `AudioRecognizer` path.
- Do not replace local inference with `createOnDeviceSpeechRecognizer()`; that would surrender model behavior and availability to the device provider.
- Do not complete `WhisperRecognizerService` until there is a named client or acceptance test that requires `SpeechRecognizer` compatibility.

### If `RecognitionService` becomes a requirement

Use the smallest migration that preserves the current architecture:

1. Replace the empty stub with a service adapter over the existing recognition core; do not duplicate backend or `AudioRecord` logic.
2. Declare the service with the recognition-service action and `android.permission.BIND_SPEECH_RECOGNITION_SERVICE`; retain `RECORD_AUDIO` as an app runtime permission, not the binding permission.
3. Start with one active session and final results. Add partial callbacks automatically for backends that already emit them.
4. Add one instrumentation contract test that explicitly creates `SpeechRecognizer` with this service's `ComponentName`, exercises start/stop/cancel, and verifies exactly one terminal callback.
5. Add API 33 support queries and injected-audio handling only when a client uses them. `checkRecognitionSupport()` and `triggerModelDownload()` were added in API 33. [SpeechRecognizer API](https://developer.android.com/reference/android/speech/SpeechRecognizer#checkRecognitionSupport(android.content.Intent,%20java.util.concurrent.Executor,%20android.speech.RecognitionSupportCallback))

This is an additive adapter, not a rewrite. The IME and activity should continue to own their existing UX even after a provider service exists.

## Compatibility summary

- **IME:** longstanding Android platform contract; broad editor reach, subject to user enablement and OEM keyboard switching UI.
- **Recognition activity:** `ACTION_RECOGNIZE_SPEECH` dates to API 3; actual discovery depends on callers resolving/choosing an activity.
- **Recognition service / `SpeechRecognizer`:** provider/client contract dates to API 8; default provider and feature support vary by device. Explicit component binding is possible.
- **Platform on-device recognizer:** API 31+ and only when `isOnDeviceRecognitionAvailable()` returns true. [SpeechRecognizer.isOnDeviceRecognitionAvailable](https://developer.android.com/reference/android/speech/SpeechRecognizer#isOnDeviceRecognitionAvailable(android.content.Context))
- **Support checks, model-download callbacks, and supplied audio:** modern portions are API 33+, so a provider must gate them while preserving the older final-result contract.

## Primary sources

- [Android Developers: Create an input method](https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method)
- [Android API: InputMethodService](https://developer.android.com/reference/android/inputmethodservice/InputMethodService)
- [Android API: RecognitionService](https://developer.android.com/reference/android/speech/RecognitionService)
- [Android API: SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer)
- [Android API: RecognizerIntent](https://developer.android.com/reference/android/speech/RecognizerIntent)
- [Android Developers: Foreground service types—microphone](https://developer.android.com/develop/background-work/services/fgs/service-types#microphone)
- [Android Developers: Foreground services in Android 11](https://developer.android.com/about/versions/11/privacy/foreground-services)
- [Android Developers: Sharing audio input](https://developer.android.com/media/platform/sharing-audio-input)
- [Android Developers: Request runtime permissions](https://developer.android.com/training/permissions/requesting)
- [AOSP: RecognitionService.java](https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/speech/RecognitionService.java)
- [AOSP: SpeechRecognizer.java](https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/speech/SpeechRecognizer.java)

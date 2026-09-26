# SHARE-06 preparation

Prepared 2026-09-26 on `codex/share-06`, baseline `eb5e1dadfd9d6ceb2ebb78ca4f403d2718bddcf8`. Implementation is queued behind the first wave. No acceptance criteria are verified by this preparation.

## Existing flow

Activity and IME both use RecognizerView and the RecordingSession in AudioRecognizer.kt. Capture uses VOICE_RECOGNITION, mono PCM at 16 kHz. There is no explicit Bluetooth selection. startRecording clears captured samples, so restarting it to change routes would lose the current utterance. reset invalidates the recognition generation and cancels work. Recorder release is centralized, but its early return when no recorder exists cannot own cleanup of a route acquired before recorder creation.

## Verified API constraints

- Android API 31+ provides availableCommunicationDevices and setCommunicationDevice. Pass an available output device; Android selects its matching input. A true return means the request was accepted. Selection lasts until cleared, disconnected, or the process dies. Clear it at session end. See the [AudioManager reference](https://developer.android.com/reference/android/media/AudioManager#setCommunicationDevice(android.media.AudioDeviceInfo)).
- Observe connection and communication changes with callbacks and bound the pending state. The [Android routing guide](https://developer.android.com/develop/connectivity/bluetooth/ble-audio/audio-manager) recommends waiting no more than 30 seconds. It is a communication-use-case guide, not proof that this app's VOICE_RECOGNITION source routes correctly on every phone.
- AudioRecord.setPreferredDevice accepts an input device or null for default routing. Inspect routedDevice only while recording and observe routing changes. A preference alone does not establish the actual microphone. See [AudioRecord](https://developer.android.com/reference/android/media/AudioRecord).
- API 26-30 requires the legacy SCO path for establishing a Bluetooth connection. startBluetoothSco is asynchronous, requires MODIFY_AUDIO_SETTINGS, and must be paired with stopBluetoothSco even after failure. Its documented 8 kHz input restriction conflicts with our fixed 16 kHz capture assumption. Legacy compatibility must be tested, not inferred from modern routing. See [legacy SCO documentation](https://developer.android.com/reference/android/media/AudioManager#startBluetoothSco()).
- Keep RECORD_AUDIO handling. Add MODIFY_AUDIO_SETTINGS for owned routing operations. Avoid Bluetooth profile APIs and device-name lookups unless needed. If implementation introduces APIs requiring BLUETOOTH_CONNECT on Android 12+, add its runtime permission flow; denial must preserve default capture. Do not request Bluetooth scanning or location permissions merely to show audio routes.

Context7 resolved the official Android reference and supplied AudioManager documentation; its AudioRecord query lacked the needed methods, so the official reference above filled that gap.

## Smallest implementation

1. Add one session-owned routing component with requested, pending, observed and unavailable states. Retain default capture behavior until explicit selection. Enumerate only relevant phone/Bluetooth choices, and keep identifying details out of diagnostics.
2. Expose selection and observed category through RecordingSession to the shared RecognizerView. Show the control only when there is a meaningful alternative or a route failure to explain. Use generic Phone microphone/Bluetooth microphone labels, with distinct choices if multiple devices require disambiguation. Never label a requested device active before recorder observation confirms it.
3. Try a supported in-place change without clearing samples or resetting the recognizer. If the platform requires a new recorder/source, explicitly finish and retain the current utterance before a user-started next recording. Do not silently restart, cancel, or discard the current recording. Do not introduce sample-rate conversion without demonstrated legacy need.
4. Release route requests, callbacks, timeout and any app-owned audio-mode change on finish, cancel, reset, recorder/model failure, and destruction. Release independently of recorder existence and guard callbacks by session generation so old cleanup cannot release a newer session's route.
5. On rejection, timeout or disconnect, show the observed fallback category. Preserve the current buffer/history. If capture cannot continue, finish the retained utterance with an explicit route failure rather than pretending Bluetooth is active. Log only allowlisted route/state categories, never product names, addresses or raw device objects.

Expected edits: AudioRecognizer.kt, one routing component, shared RecognizerView UI and strings, manifest if needed, focused tests. No backend selection, history retention, cleanup or model changes.

## Checks after implementation authorization

- Focused state tests: default makes no routing request; rejected/unavailable/denied route never becomes active; accepted request remains pending until observation; timeout/disconnect fallback; stale callbacks ignored; release before recorder creation and repeated release; old-session cleanup cannot affect new session.
- Session checks: selected route leaves audio buffer, waveform and transcript flow intact; any required new utterance preserves old capture; cancel/reset/failure clean up routing; streaming replay does not reacquire or release live capture routing.
- Real phone/headset matrix: Android 26-30 SCO if available, Android 31-32 classic headset, Android 33+ classic and BLE where available. Test Activity and IME, phone to Bluetooth and back, disconnect, Bluetooth off, denied permission where relevant, competing call, cancel/finish/destroy, repeated sessions, and actual microphone isolation. Record phone, Android build, headset and unsupported combinations in manual validation notes, not exported standard diagnostics.
- UI checks: TalkBack labels and pending/failure announcements, large fonts, orientation, waveform visibility.
- Run relevant unit tests, lint and assembly with --max-workers=2 after obtaining the coordinator's shared build slot. No emulator or expensive build during preparation. JVM tests cannot complete the hardware criteria.

## Coordination and readiness

Ready for implementation dispatch after the first wave. Coordinate AudioRecognizer generation/reset/terminal changes with SHARE-02 and replay/finalization changes with SHARE-08 before editing. Keep route state independent of backend replay, and preserve their completed changes when integrating. Coordinate the shared recording UI with SHARE-07 when dispatched.

Open hardware questions: whether VOICE_RECOGNITION honors communication routing on the target phone; whether switching needs a recorder/source boundary; whether legacy SCO supports this app's 16 kHz request. Missing hardware evidence blocks completion, not preparation. No ticket checkbox may be checked for these without a real test.

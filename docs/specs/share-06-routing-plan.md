# SHARE-06 preparation

Prepared 2026-09-26 on `codex/share-06`, baseline `eb5e1dadfd9d6ceb2ebb78ca4f403d2718bddcf8`. Implementation is queued behind the first wave. No acceptance criteria are verified by this preparation.

## Implementation checkpoint, 2026-09-28

Implementation was dispatched and SHARE-02 incorporated as `35de433`. The routing controller and Android adapter are session-owned. Modern Bluetooth choices come from available communication outputs even when their input endpoint has not appeared yet; confirmation still uses the running recorder's routed input. Legacy selection is offered only when one SCO input is exposed and off-call SCO is supported. Unsupported or ambiguous legacy combinations retain default capture.

Route requests never restart the recorder or reset captured samples. Rejected, disconnected or timed-out requests release app-owned routing and display the observed system fallback. A generation-checked failure path stops capture without clearing retained audio. A shared wrapper adds the control to Activity and IME content. SHARE-07 must preserve that wrapper when integrating its common presentation state.

The adapter uses audio routing APIs, not Bluetooth profile/scanning APIs, and adds only MODIFY_AUDIO_SETTINGS. It catches permission rejection without exporting platform exception messages. Standard diagnostics contain route category, pending category and failure category only. Category codes follow MicrophoneKind and MicrophoneRouteFailure declaration order; -1 means unknown/none. No device IDs, addresses or names are exported.

Six focused JUnit tests pass, and the Android adapter compiles against API 35. Reproduce from PowerShell with a JDK compatible with Kotlin 2.1 on PATH using `./tools/test-microphone-routing.ps1 -CompileAndroid`. The runner uses cached project dependencies and the installed Android 35 SDK, without configuring native builds. This host's default JDK 25 is incompatible with that compiler; Android Studio's bundled JBR passed. The runner does not compile the Compose UI or the whole app.

Full unit suite, assembly, lint, UI/device checks and physical headset evidence remain pending. No headset test has been performed, and no legacy or BLE compatibility claim is made. The build slot is after SHARE-07, followed by SHARE-09. A premature workspace cleanup was recovered from snapshot `465020e`; no source files were lost.

Standards review found no violations. Spec review identified two corrected issues: active labels now use the same numbering as the picker, and unexpected recorder termination now completes the retained utterance with an interruption notice instead of leaving the session appearing to record. Both blocking and nonblocking recorder read exceptions reach that terminal path. CaptureFailed skips tail reads; cancellation and stale generations are excluded. The Spec re-review found both issues addressed and no new deadlock/cancellation issue.

The controller test for stale callbacks uses a fake platform. It does not validate process-wide Android ownership transfer, audio-mode restoration, or recorder/history integration. Those checks, the new CaptureFailed policy assertion, whole-app compilation and Compose UI checks remain on the queued validation list.

## Build verification, 2026-09-28

After SHARE-07 released the exclusive slot, Gradle validation ran with Android Studio JBR, Android SDK 35, NDK 28.2.13676358, `--max-workers=2`, and `CMAKE_BUILD_PARALLEL_LEVEL=2`.

- `:app:testDevDebugUnitTest`: 160 tests, 159 passed, one skipped, no failures. Includes the CaptureFailed tail-policy assertion and six routing-controller tests.
- `:app:lintDevDebug`: passed.
- `:app:assembleDevDebug` and `:app:assembleDevDebugAndroidTest`: passed. Full Kotlin/Compose compilation passed. Native submodules missing after worktree recovery were initialized at their pinned revisions; llama.cpp required Git long-path handling. No tracked native source was changed.
- Added three Compose route-control tests and one Android phone-preference lifecycle test. Test APK compilation passed; a subsequent review tightened the recorder-release `finally` block. These tests have NOT run on the emulator. They do not claim Bluetooth or actual input-audio verification.

Emulator installation failed with `INSTALL_FAILED_INSUFFICIENT_STORAGE: Failed to override installation location`. A prior APK was backed up before using `pm uninstall -k`; app data was retained. Reinstallation of both the new APK and the backed-up prior APK failed, including with a temporary 64 MiB storage threshold and an explicit internal-volume request. The threshold was restored and verified `null`. At handoff, `/data` had 735 MiB free, host C had about 107 GB free, no active install sessions existed, and no disposable temporary APKs were found. No models, settings or recordings were deleted. The package remained uninstalled with retained data, and restoration was escalated to the coordinator. The prior APK is preserved outside the worktree at `C:/Users/User/.codex/visualizations/2026/09/26/01a0df6d-b800-7a42-bb07-d8851c9d7fac/share07-prior.apk`.

The build/native slot was released to SHARE-09, explicitly excluding emulator control pending coordinator recovery. Physical headset validation, Android ownership transfer, UI runtime/accessibility checks and compatibility results remain open. SHARE-06 is not complete.

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

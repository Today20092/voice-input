# SHARE-01 validation

Branch: `codex/share-01`, based on `eb5e1dadfd9d6ceb2ebb78ca4f403d2718bddcf8`.

The FUTO Keyboard page is under the settings home Support section. It targets the selected stable or unstable keyboard and passes this installation's `context.packageName`, including flavor suffixes. Opening the page sends only `mode=check`; only the setup button sends `mode=switch`.

## Protocol evidence

The [keyboard activity at 70a5d390](https://github.com/futo-org/android-keyboard/blob/70a5d390c505a6bbcc4e14966e5628e43ca3f1fc/java/src/org/futo/inputmethod/latin/uix/settings/VoiceInputSwitchActivity.kt) returns 66 after switching and enabling external input, 67 when the remembered provider matches, and 68 otherwise. A check does not report whether external input is enabled. The UI therefore distinguishes remembered-provider status from a confirmed switch.

One request runs at a time. Pending state survives recreation. A setup result schedules one read-only check; a check never schedules another. Manual settings uses pause/resume instead of an activity result because a singleTask settings activity can return cancellation before the user changes anything. Missing or unexported activities fall back to manual instructions.

## Automated checks

- All 148 JVM tests pass, including seven `KeyboardProviderSessionTest` cases. Those seven also passed through the cached Kotlin 2.1.0 compiler during TDD. They cover read-only status, switch/cancel/unknown/unavailable results, overlapping and stale requests, one recheck, and saved pending state including Android 8 saving before onStop.
- All five `KeyboardProviderTest` Android tests pass. They cover intent arguments for both keyboard packages and app flavors, Compose state restoration with a pending setup request, cancellation/unknown responses after rechecking, missing-activity fallback without looping, and resilient settings launching.
- APK assembly, test-APK assembly, and lint pass. Validation used Android Studio JBR, SDK 35, two Gradle workers, and `CMAKE_BUILD_PARALLEL_LEVEL=2`.

Commands run on 2026-09-28:

```powershell
.\gradlew.bat --max-workers=2 :app:testDevDebugUnitTest :app:assembleDevDebug :app:assembleDevDebugAndroidTest :app:lintDevDebug
adb -s emulator-5554 shell am instrument -w -r -e class org.futo.voiceinput.settings.KeyboardProviderTest org.futo.voiceinput.dev.test/androidx.test.runner.AndroidJUnitRunner
```

The emulator was Pixel_10, Android 37, x86_64 with ARM64 translation. The first instrumentation attempt stopped after two passing tests because Android's low-memory killer killed the test app. The interrupted test passed alone; the complete five-test run then passed without code changes.

### Standards review

No findings.

### Spec review

One manual-settings lifecycle finding was fixed with ON_PAUSE tracking and a serialization regression. The final review found no remaining implementation blockers. Physical-device and TalkBack evidence remains outstanding.

## Real keyboard fallback and layout

Installed the [official FUTO Keyboard 0.1.30 release](https://github.com/futo-org/android-keyboard/releases/tag/0.1.30), package `org.futo.inputmethod.latin`, version code 11751. Its APK matched the published SHA-256 `0f7bca8365a13cea1cb2c21fb3c96b6fd73fbd64c3b7bd247b764f3e11ed8eba`.

The installed release has no VoiceInputSwitch activity. Verified detection, the unsupported/manual message, actual `org.futo.voiceinput.dev` identity, keyboard settings launch, and return with enabled controls rather than a setup loop. Verified portrait rendering and landscape scrolling at 1.5 font scale; text and buttons remained readable inside the system-bar padding. This does not establish spoken TalkBack behavior or full dictation interoperability.

Removed only the keyboard installed for this check. Restored the emulator's prior font scale, rotation settings, microphone permission, IME enablement, and storage threshold; retained the existing Voice Input app data. The build/emulator slot was handed to SHARE-05.

## Required interoperability evidence

Full dictation interoperability and TalkBack remain unverified. Fake activity results and the older-keyboard fallback checks do not prove interoperability with a protocol-supporting keyboard.

Record the phone/Android version, Voice Input APK revision, FUTO Keyboard package and version, and recognition model. Test both a protocol-supporting keyboard and the older/manual path:

1. Start with another provider remembered. Open this page and confirm that the read-only check does not alter keyboard preferences.
2. Explicitly choose this app. Verify that the keyboard microphone launches this fork, a short dictation returns text, and cancellation returns to typing.
3. Disable external input while this app remains remembered. Confirm that the page reports only remembered status, not that external input is enabled.
4. Change providers in manual keyboard settings and return. Check refresh, orientation/process recreation during setup, and cancellation. No setup loop should occur.
5. Verify absent/older keyboard instructions, stable/unstable selection, TalkBack labels and order, large font scaling, landscape scrolling, and system-bar padding.

No recognition, IME delivery, model selection, history, cleanup, or diagnostics paths are changed by this ticket.

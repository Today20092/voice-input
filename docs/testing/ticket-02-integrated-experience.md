# Ticket 02 local integrated verification

September 30, 2026. Worker `/root`, dispatched by control tower
`01a0f345-1363-7ee1-ba31-ee9c533599aa`. Exclusive writer/build slot on
`codex/share-beta-integration`, starting at `66a85c56bff84586ef8f329cc7acabaa4cb77af0`.
The signed beta 2 publication milestone is met. This pass does not publish an APK
or close the required physical-device criteria.

## Local toolchain and device

- Android Studio build `AI-261.25134.95.2612.15914620`, bundled JBR 21.0.10.
- Studio SDK at `C:/Users/User/AppData/Local/Android/Sdk`, Android 35,
  NDK 28.2.13676358, CMake 3.22.1, project Gradle wrapper 8.11.1.
- Two Gradle workers and `CMAKE_BUILD_PARALLEL_LEVEL=2`, offline Gradle execution.
- No physical phone was connected at inspection. No physical headset was available
  to this pass. No phone installation or phone-data change was attempted.
- New disposable `Ticket02_Verification` AVD, serial `emulator-5560`, emulator
  36.5.11.0, API 37 / Android 17, x86_64 with ARM64 translation, 4 GiB RAM,
  12 GiB userdata. Initial free userdata was 11 GiB.
- Fingerprint `google/sdk_gphone16k_x86_64/emu64xa16k:17/CP21.260330.005/15181570:user/dev-keys`.
  Gesture navigation, English locale, 1080 x 2424 at 420 dpi, normal font scale.
  Original Pixel_10 userdata and snapshots were untouched.
- The fresh AVD has Gboard, no FUTO Keyboard, and no installed recognition models.
  Microphone permission was granted to the disposable dev app and its IME enabled.
  The microphone test uses a virtual input, not a physical microphone or headset.

## Demonstrated failures and fixes

The first Gradle command failed at `:app:configureCMakeDebug[arm64-v8a]` because
OpenCL-ICD-Loader and llama.cpp lacked CMakeLists.txt. All four native submodules
were uninitialized. `git -c core.longpaths=true submodule update --init --recursive`
restored their exact tracked pins. No native source or dependency version changed.

The first selected instrumentation run reported 34 passes and one failure.
`managedAndWhisperDetailsCanScrollAtLargeFontSizes` demanded a positive scroll
range for every details dialog. Its isolated invocation reproduced the failure,
including on a shorter viewport and at system font scale 2. An identified rerun
named Cohere Transcribe. Requiring overflow is not a content-reachability
requirement. Removed that assertion and its unused import; retained the scroll
action, end-of-range assertion, visible Done button, dismissal and no-download
assertions. The isolated corrected test and final 35-test run passed. This does
not claim a production scrolling defect was fixed.

Real edge gestures exposed a failure missed by direct dispatcher tests. Before
the manifest fix, Input stayed static throughout a slow swipe. Logcat from the
app process reported `OnBackInvokedCallback is not enabled for the application`
and requested `android:enableOnBackInvokedCallback="true"`. Added this flag only to
SettingsActivity, which owns the shared settings NavHost, and checked its merged
manifest. The same gesture then produced an interactive preview, but simultaneous
fades reproduced the reported overlapping text.

Configured only the shared host's pop fades: outgoing fadeOut for 100 ms, incoming
fadeIn for 100 ms after a 100 ms delay. Navigation Compose still owns gesture
progress, cancellation and the back stack. Forward transitions are unchanged.
The final Input video shows outgoing text fading away and then incoming text
appearing before release, without simultaneous page text in inspected frames.
Model Options also previews home before release. A drag from the edge and back to
the edge retained Input. Dispatcher instrumentation additionally verifies cancel,
commit, Advanced, system back and the in-app arrow.

The official [predictive-back setup](https://developer.android.com/develop/ui/compose/system/predictive-back-setup),
[animation configuration](https://developer.android.com/develop/ui/compose/animation/customize)
and [test synchronization](https://developer.android.com/develop/ui/compose/testing/synchronization)
documentation were fetched through Context7. The earlier specification's assumption
that target SDK 35 required no explicit opt-in was corrected from local evidence.

## Final checks

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
$env:ANDROID_HOME = 'C:/Users/User/AppData/Local/Android/Sdk'
$env:CMAKE_BUILD_PARALLEL_LEVEL = '2'
.\gradlew.bat :app:testDevDebugUnitTest :app:assembleDevDebug :app:assembleDevDebugAndroidTest :app:lintDevDebug --max-workers=2 --offline
```

Final Gradle result: success in 53 seconds. JVM results: 190 tests, 189 passed,
one existing skipped `ModelArchiveTest.installsPublishedOrukeetArchive`, no failures
or errors. Lint: zero errors, 86 warnings and 11 informational findings. One warning
is the new API-33 manifest attribute being unused on older Android versions; it is
not suppressed and the APK minimum remains API 26. Other warnings are existing.

Installed both locally built APKs on the new AVD. Executed the following:

```powershell
$classes = @(
    'ImeInsertionEditorTest', 'RecognitionContentTest', 'MicrophoneRouteControlTest',
    'AndroidMicrophonePlatformTest', 'settings.KeyboardProviderTest', 'settings.SettingsNavigationTest',
    'settings.pages.LanguagesScreenTest', 'settings.pages.ManagedRecognitionModelCatalogTest',
    'downloader.DownloadPromptTest', 'downloader.DownloadRetryUiTest', 'downloader.RecognitionModelRequestTest'
) | ForEach-Object { "org.futo.voiceinput.$_" }
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s emulator-5560 shell am instrument -w -r -e class ($classes -join ',') org.futo.voiceinput.dev.test/androidx.test.runner.AndroidJUnitRunner
```

Final result: `OK (35 tests)`, 55.707 seconds, zero failures.

| Android test class | Passed | Scope |
| --- | ---: | --- |
| ImeInsertionEditorTest | 5 | Native EditText operations, rejection, ownership, session lifecycle |
| RecognitionContentTest | 3 | Waveform/text/status, provisional semantics, Stop/Cancel, 2x font, IME padding |
| MicrophoneRouteControlTest | 3 | Hidden phone-only control, pending labels, picker numbering |
| AndroidMicrophonePlatformTest | 1 | Android recorder preference, unchanged mode, repeated release on virtual input |
| settings.KeyboardProviderTest | 5 | Protocol intents, restoration, result handling, unavailable/settings fallback |
| settings.SettingsNavigationTest | 2 | Variant summaries, cleanup placement, predictive dispatcher/back stack |
| settings.pages.LanguagesScreenTest | 5 | Managed families, Whisper preferences, stale values, download effects |
| settings.pages.ManagedRecognitionModelCatalogTest | 5 | Summaries, details, variants, large-font reachability |
| downloader.DownloadPromptTest | 2 | Confirmation UI |
| downloader.DownloadRetryUiTest | 2 | Retry action and restart explanation |
| downloader.RecognitionModelRequestTest | 2 | Current manifest refresh and unmanaged request preservation |

All eight ModelFileDownloadTest cases passed, including an interrupted 33 MiB
fixture, saved ranges, validated-file skip, refused-range restart, preservation of
previous targets and concurrent retries. Six archive fixture tests passed; the
published-Orukeet archive check remains skipped. These are synthetic transfers,
not reproduction of the reported phone's Nemotron stall or an interrupted full
installation on a phone. The downloader's final marker validation was inspected,
but no new end-to-end Android failed-install/marker check is claimed.

App smoke checks verified the missing-model Activity prompt and Cancel, the
absent-keyboard page with actual `org.futo.voiceinput.dev` identity and manual
instructions, and Help rendering/scrolling. Help's provider distinction and setup
instructions were inspected at normal font and 1.5x font in locked landscape.
Font scale and portrait were restored. Resource XML parsing and README/provider
relative links passed. Existing help already describes implemented setup without
promising dictation interoperability; its copy did not need replacement.

APK inspection: `org.futo.voiceinput.dev`, versionCode 63,
`1.4.6-share-beta.2-dev`, minimum SDK 26, target SDK 35, ARM64 only.
Final debug APK SHA-256:
`04e35594d5e65328a99cb1521b59cc1ab92812f347cc25118a8cf47906d457c4`.
Its filename contains starting commit `66a85c5`; it was built with this pass's
working-tree runtime changes, not pristine published beta 2. The published beta 2
APK does not include these fixes.

## Retained evidence and remaining requirements

Logs, unit XML ZIP, lint XML, both APKs, UI XML, screenshots and before/after videos
are retained outside the checkout at
`C:/Users/User/.codex/visualizations/2026/09/30/01a0f388-cbad-7581-9393-2612f638b6a3/ticket02/`.
The disposable AVD remains available. Videos distinguish frozen Input, interactive
overlapping fades, and final successive fades. No personal transcript/editor
content, device name or signing secret was collected.

Ticket 02 remains open. Required next evidence:

1. Record phone model/Android build, Voice Input build, model/settings, FUTO Keyboard
   package/version and headset/firmware. Inspect existing app/data and preserve it
   before any phone installation. The dev APK is not the published signed beta.
2. Test protocol-supporting and older/manual FUTO Keyboard paths: read-only status,
   explicit switch, real microphone launch, returned dictation, Cancel to typing,
   remembered-but-disabled external input, return/recreation and refresh.
3. Dictate into native and WebView editors with streaming/final-only models:
   selected replacement, cursor/user edits, delayed callbacks, two identical
   utterances, cleanup/vocabulary, rotation, editor/app switches and cancellation.
4. Run phone recognition with English-only, Nemotron multilingual, Cohere and
   Whisper choices. The language UI checks do not establish inference.
5. Test real Bluetooth selection, rejection/denial/unavailability, disconnect,
   competing routing, repeated sessions, finish/cancel/reset/failure/destruction,
   continuity, microphone isolation and mode restoration in Activity and IME.
   Record unavailable legacy/classic/BLE combinations instead of inferring
   compatibility. No physical headset acceptance is checked by this pass.
6. Perform spoken TalkBack traversal/announcements and recording orientation,
   safe-area, long-text, optional-popup and language/download/error flows.
   Compose semantics and Help rotation do not substitute for these checks.
7. Verify a complete interrupted installation/retry on Android, including marker
   behavior, and retain evidence for the original stalled transfer scenario.

Missing hardware information was requested. No response or result is assumed.
SHARE-03/04 and the separate waveform report are not resolved here.

## Scoped review

Two read-only code-review agents compared
`66a85c56bff84586ef8f329cc7acabaa4cb77af0...43a4a7599178f6bd8e0f5da91108febf84ff51a7`.
The final checkpoint only adds these review results and the slot handoff; runtime
and test sources are identical to the reviewed and locally verified checkpoint.

### Standards

No Standards findings or baseline smells warrant reporting. The runtime fix uses
the existing shared settings NavHost and platform opt-in, adds no abstraction or
unrelated recognition/model change, and retains content-reachability checks.
Retained logs confirm the local Gradle and 35-test Android results. Physical
phone/headset/TalkBack acceptance remains open under the local tracker rules.

### Spec

No Spec findings. The SettingsActivity opt-in and shared successive fades satisfy
predictive-back requirements without changing routes or forward transitions.
Removing mandatory overflow preserves details reachability. The acceptance
record keeps phone/editor dictation, headset routing, spoken TalkBack, model
inference and full interrupted-installation checks open. Emulator animation
evidence is explicitly permitted by its criterion. No scope creep or unjustified
newly checked criterion was found.

Totals: Standards zero findings; Spec zero findings. Neither axis has an unresolved
review issue. The build/test pass is complete and the exclusive integration slot
returns to the control tower after checkpoint push.

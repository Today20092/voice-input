# Ticket 03 model updates and readiness

September 30, 2026. Isolated branch `codex/ticket-03-atomic-readiness`, based on
`bdedb175e9c391267e158079ac61c6f68d2a4a48`. Tested source checkpoint
`a63e4f8a0ae3db99a54602ce834f1cdf60f3644f`. Only the control tower integrates
this branch. No integration/master edits, release publication, scheduled monitor,
production successor, or recognition-default change was made.

## Implementation

The lifecycle reports the usable installed manifest separately from an optional
explicitly supported successor. Supported older manifests must share model identity,
runtime, variant, directory and artifact names. No production model currently
declares a supported older version, so the production catalog offers no upgrade.
Version declarations require a real compatible pinned manifest before promotion.

Managed downloads use a separate version-specific staging directory. The existing
source, transfer size, available-space and cellular confirmation precedes transfers
and activation, including when a staged candidate already has every file. Available
space excludes the retained working installation; only retained staging/archive
bytes reduce the additional space estimate. Ordinary files and archives retain
the existing resumption and hash-validation paths. Legacy Whisper downloads,
migration and GitHub app updates keep their separate flows.

Validated staging receives a version marker. Activation serializes against both
runtime acquisition and a RecordingSession lease acquired before recording starts.
Recording before backend loading therefore also prevents replacement. Session reset
detaches its generation-owned lease and releases it after old jobs/runtime cleanup;
completion, failure and lost permission release it as well. A failed runtime close
retains ownership. Switching to another runtime no longer force-closes an active
Parakeet lease, and opening its download intent no longer closes the recognizer.

Activation journals the operation, retains the previous directory, renames staging
into place and commits only after the new marker/assets are ready. A failed rename
restores the previous directory. A recreated store restores an interrupted,
uncommitted replacement; after commit it cleans a remaining backup. Existing usable
updates do not change selection. New installation/repair retains the existing
successful-selection behavior. The next lifecycle acquisition observes the activated
manifest and payload. Readiness uses markers and file sizes, without hashing model
contents. Hashing stays in explicit installation/verification operations.

Selection, installation, activation, deletion and failed operations publish shared
invalidation after state settles. Model Options and the settings summary observe it
and refresh on resume. The compact notice distinguishes download, repair and
optional upgrade; dismissal is saved per model/version and leaves readiness intact.
Model Options keeps an explicit update action after dismissal. Generic runtime-load
failure now invalidates through the lifecycle, with a marker version guard that
preserves an unrelated supported older installation.

## Local verification

Installed Android Studio JBR 21.0.10 and SDK were used with project Gradle 8.11.1,
Android platform 35, NDK 28.2.13676358 and CMake 3.22.1. Native submodules were
initialized at their tracked pins; the Windows long-filename checkout error was
resolved with scoped `core.longpaths=true` checkout. The same local setting in the
llama.cpp submodule completed its missing tracked UI file; all submodules are clean.
No dependency version changed or signing secret was accessed.

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
$env:ANDROID_HOME = 'C:/Users/User/AppData/Local/Android/Sdk'
$env:CMAKE_BUILD_PARALLEL_LEVEL = '2'
.\gradlew.bat :app:testDevDebugUnitTest :app:assembleDevDebug :app:assembleDevDebugAndroidTest :app:lintDevDebug --max-workers=2 --no-parallel --offline
```

Final result at the tested source checkpoint: success in 59 seconds, 199 JVM tests,
198 passed, one existing published-Orukeet archive skip, zero failures/errors.
Lint reported zero errors, 87 warnings and ten informational findings. ARM64 debug
app and Android-test APK assembly passed. The initial PowerShell invocation split
an unquoted `-Dorg.gradle.parallel=false` argument; `--no-parallel` corrected the
command. An initial focused test-class initialization failure was corrected by
declaring the runtime release result as Unit; the focused lifecycle suite then passed.

The nine new JVM fixture checks cover supported old/current readiness, corrupt and
interrupted staging, failed rename rollback, simulated process interruption between
directory publication and commit, no-successor silence, cheap readiness, cancellation
while a recording lease is held, multiple recordings, backend acquisition/reload,
failed runtime close, cleanup, guarded invalidation and cross-instance changes after
selection/deletion/failure. Existing downloader confirmation, file/range resumption,
archive and backend tests also passed in the full suite. These are explicit tiny
pinned fixtures; their payload backend is not speech inference.

Debug APK SHA-256:
`3e9ed1b6eb92a56c94092ef1a2147e0f8be92848e05f4243f7fce0e05fb54f9b`.
The filename embeds `a63e4f8`, the committed source tested here.

## Disposable emulator checks

Used the existing disposable `Ticket02_Verification` AVD, serial `emulator-5560`,
API 37 / Android 17, x86_64 with ARM64 translation. Fingerprint:
`google/sdk_gphone16k_x86_64/emu64xa16k:17/CP21.260330.005/15181570:user/dev-keys`.
Installed the exact locally built source-checkpoint app and test APKs with `adb
install -r`. Retained Pixel_10 userdata and other worktrees were untouched.

```powershell
$classes = @(
  'org.futo.voiceinput.settings.pages.RecognitionModelNoticeTest',
  'org.futo.voiceinput.settings.pages.ManagedRecognitionModelCatalogTest',
  'org.futo.voiceinput.downloader.DownloadPromptTest',
  'org.futo.voiceinput.downloader.DownloadRetryUiTest',
  'org.futo.voiceinput.downloader.RecognitionModelRequestTest',
  'org.futo.voiceinput.settings.SettingsNavigationTest',
  'org.futo.voiceinput.RecognitionContentTest'
) -join ','
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s emulator-5560 shell am instrument -w -r -e class $classes org.futo.voiceinput.dev.test/androidx.test.runner.AndroidJUnitRunner
```

Result: `OK (18 tests)`, 73.509 seconds, zero failures. The two new notice tests
exercise explicit actions, dismissal without transfer, no-successor silence and
actual lifecycle invalidation on Android private fixture files. The filesystem
fixture holds a pre-load recording lease, interrupts its waiting update, recreates
the store, activates successfully, acquires a backend through the production
lifecycle seam and reads the new payload. It then verifies failure-to-repair,
reinstallation clearing the notice and deletion showing download-required.
No network transfer or production fixture catalog entry is introduced.

The other 16 tests cover managed model choices/details/large-font reachability,
download source/space/network confirmation, retry presentation, request manifest
refresh, settings navigation and recognition content/Stop/Cancel layout. The
fixture uses a payload backend, not a native speech recognizer. No phone inference,
spoken TalkBack or physical headset result is inferred from these tests.

The exclusive build/device slot was explicitly released to the control tower after
this run. Logs, unit XML ZIP, lint XML and both debug APKs are retained at
`C:/Users/User/.codex/visualizations/2026/09/30/01a0f3d1-3f64-7221-bcfc-3b1355403ed0/ticket03/`.

## Scoped review

Standards reviewed `bdedb17...a00f3db` against AGENTS.md, CONTEXT.md, local tracker
rules and ADR 0001. No documented-standard violations were found. The sole baseline
smell was the stale `startDownloadAfterRuntimeRelease` name after removing the
premature close; it is now `startConfirmedDownload`.

Spec reviewed the SHARE-03/04 detailed criteria and adoption lifecycle contract.
Its sole finding was a missing invalidation after generic runtime-load failure.
Commit `0242709` routes it through the lifecycle and adds JVM/Android repair
transition checks. Follow-up review confirmed the finding resolved, with no new
actionable issue. Commit `a63e4f8` also makes the compact settings summary respect
the saved dismissal; follow-up review found no issue with this correction.

Final review totals: Standards zero unresolved findings; Spec zero unresolved findings.

## Remaining release evidence

Real-engine successor inference, physical-device interrupted-update/reload and
upgrade-notice clearance remain unverified. A genuine compatible successor must be
declared and exercised before a production update is offered. Fixture payload reload,
emulator UI checks and compiled runtime adapters do not establish speech inference
or physical-device behavior. Ticket 03 stays open for that required evidence.

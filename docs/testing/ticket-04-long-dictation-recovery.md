# Ticket 04 long dictation and failure handling

September 30, 2026. Isolated worker branch `codex/ticket-04`, starting at
`bdedb175e9c391267e158079ac61c6f68d2a4a48`. Worktree
`C:/Users/User/.codex/worktrees/1b63/futo-parakeet-voiceinput`.
Control tower owns integration; this worker does not merge or publish.

## Recovery admission

The [pinned-runtime review](../research/ticket-04-recovery-categories-2026-09-30.md)
found no supported recoverable streaming category. Moonshine 0.0.68 collapses
streaming native exceptions to `UNKNOWN (-1)`; its invalid-handle and
invalid-argument codes do not establish a transient failure. Sherpa 1.13.4's
inspected streaming API exposes no typed recoverable result. Keep automatic
same-model retained-audio recovery disabled. No synthetic recoverable category,
new VAD model, recording mode or segmentation change is justified.

Cancellation, exhausted memory, corrupt models, unknown native exceptions,
closed/not-started channels, lifecycle misuse, load failure and failed native
retirement remain excluded. Catching up and normal segment completion are status,
not admitted retry triggers. Source inspection cannot prove that a fresh native
instance is safe after an unknown error. The cached Moonshine AAR still has
SHA-256 `21a8ab5f7b2e0b962b7ef6f0101a7e19fc716ae82d97c163bbbaa247c3ef4a80`.

## Demonstrated corrections and checks

The original model-load handler retried once after OOM. The final-decoding handler
recursively reloaded and called `runModel`, without a retry bound. A retirement
exception could also replace the original OOM. Stop waiting for a failed model
load could independently reload the missing backend.

Removed both OOM retry paths and explicit GC/finalization delays. Decoding OOM
uses the existing terminal failure/retirement/history-close path. Ordinary
exceptions retain that path, cancellation propagates and other fatal Errors are
rethrown. Model-load OOM retires any published backend and reports its original
failure, with a retirement Exception suppressed. The existing canceled/session
guard after joining the load job prevents the fallback reload after a terminal
load failure. Installed-model invalidation is absent from the OOM branch.

Touched methods are `loadModelInner`, `runModel` and `runModelInner` in
`AudioRecognizer.kt`. `runModel`'s final cleanup ordering is unchanged. Ticket03
owns lifecycle leases and model activation; control tower must preserve those
changes when combining its lease lifetime with this terminal error handler.

The focused Android check seeds retained PCM and a fake streaming backend into the real
RecordingSession, then uses public Stop/Cancel and observes failure/final
callbacks. Reflection is limited to arranging a session without a microphone or
installed native model and reading its retained buffer/job. A loader argument
with the existing lifecycle as its default permits a counted injected load
failure. These checks cannot establish native recovery, actual heap exhaustion,
microphone behavior or phone performance.

Baseline source retained both retry paths, with only the injectable loader seam
added. The baseline Android run selected
`loadingOomIsTerminalWithoutReload`,
`failedRetirementDoesNotReplaceTheOriginalOom` and
`stopDuringSuccessfulPendingLoadDeliversOneFinal`. It ran three tests: the healthy
control passed, while the first expected one load but observed two, and the
second expected the original OutOfMemoryError but received the retirement
IllegalStateException. This reproduced the specific failures before correction.

The final `RecordingSessionFailureTest` run passed all six cases in 0.287 seconds:
terminal model-load OOM, Stop during a pending OOM load, healthy pending-load
Stop, decoding OOM with exact retained PCM, original OOM after retirement failure,
and Cancel during finalization followed by an independent new session. No final
is delivered for the failed/canceled attempts; the healthy control delivers one.
The checks seed PCM rather than record it, so they do not validate microphone
capture or actual audio-history persistence. Existing history/backend tests ran
as part of the JVM suite.

## Local verification

Android Studio JBR 21 and SDK `C:/Users/User/AppData/Local/Android/Sdk`, Android
platform 35, NDK 28.2.13676358 and CMake 3.22.1. All four native submodules were
initialized at their tracked pins. `git -c core.longpaths=true` is needed to inspect
llama.cpp's long UI path without a false modified status. No submodule source changed.

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
$env:ANDROID_HOME = 'C:/Users/User/AppData/Local/Android/Sdk'
$env:CMAKE_BUILD_PARALLEL_LEVEL = '2'
.\gradlew.bat :app:testDevDebugUnitTest :app:assembleDevDebug :app:assembleDevDebugAndroidTest :app:lintDevDebug --max-workers=2 --offline --console=plain
```

Final Gradle command passed in 59 seconds. JVM results: 190 total, 189 passed, zero
failures/errors and the existing skipped optional Orukeet ModelArchiveTest.
Lint: zero errors, 86 warnings and 11 informational findings. Native ARM64 compilation,
app assembly and Android-test assembly passed. The first test compile failed
because the harness tried to subclass AndroidX's internal LifecycleCoroutineScope;
it now obtains the scope through a LifecycleOwner and LifecycleRegistry, as
documented by the fetched [AndroidX API](https://github.com/androidx/androidx/blob/androidx-main/lifecycle/lifecycle-common/api/current.txt).
The first corrected production compile required a stable local diagnostic report
ID after removing the try/catch scope. Both final compilations passed.

Installed the local debug app and test APK with `adb install -r`; no app data was
cleared. Baseline and final instrumentation used this runner:

```powershell
adb -s emulator-5560 shell am instrument -w -r -e class org.futo.voiceinput.RecordingSessionFailureTest org.futo.voiceinput.dev.test/androidx.test.runner.AndroidJUnitRunner
```

Device identity was confirmed by `adb -s emulator-5560 emu avd name` as
`Ticket02_Verification`, with fingerprint
`google/sdk_gphone16k_x86_64/emu64xa16k:17/CP21.260330.005/15181570:user/dev-keys`.
This is API 37 / Android 17, x86_64 with ARM64 translation. Retained Pixel_10 userdata
and snapshots were untouched. Only this disposable AVD was used.

Ignored local evidence: `build/ticket04/baseline-build.log`, `baseline-tests.log`,
`final-build.log`, `final-tests.log`, Gradle XML test results and lint reports.
The control tower granted the exclusive build/device slot; it was explicitly
released after these checks. No release was published.

Verified debug APK:
`app/build/outputs/apk/dev/debug/futo-voice-input-moonshine-dev-v1.4.6-share-beta.2-dev-bdedb17-debug.apk`,
SHA-256 `20ee684af14c7cad6f22eff29ccbdc1b3b3dbf646b56ca5f6eadba14df3327bf`.
Its filename records the base hash because verification preceded the scoped
commit. It is a local development artifact, not a signed beta publication.

## Physical-phone gap

`adb devices -l` at inspection listed only `emulator-5560`, the disposable
Ticket02 verification AVD. No physical phone or headset was connected. No loaded
Moonshine model, long microphone dictation, boundary-accuracy comparison, CPU/PSS
profile, thermal measurement or Stop-to-final benchmark has been performed by
this pass. S1 archives are not audio/VAD evidence.

All phone baselines remain open: short-message controls, approximately 30 seconds
and at least two minutes, natural/thinking pauses and uninterrupted speech, both
Moonshine variants and Activity/IME entry points. Follow the existing
[phone baseline procedure](../research/moonshine-long-dictation-2026-09-28.md#physical-phone-baseline-procedure)
and [phone test guide](../phone-performance-test.md), with raw recognition and
S1 rewriting measured separately. Existing default auto-stop, optional duration
limit, segmentation, audio-history policy and recognition selections are preserved.
No speed or accuracy improvement is claimed.

## Standards review

Independent Standards review of `bdedb175...9f71be6` found no documented-rule
violations and no actionable baseline smells. Backend-neutral failure handling
remains in RecordingSession, legacy backends remain intact, diagnostics add no
dictated content and incomplete ticket criteria remain unchecked. The internal
loader callback supports concrete regression tests and defaults to the existing
lifecycle loader. Fake-backend no-op callbacks are appropriate test behavior.
All six changed files were reviewed; the reviewer performed no builds or device
operations.

## Spec review

Independent Spec review found no blocking defects in the authorized terminal-OOM
slice. Both retries are removed; the post-load guard prevents Stop from reloading
after terminal load failure. Decoding OOM reaches the established failure path,
preserves seeded PCM, suppresses final delivery and retains the original OOM when
retirement throws an Exception. Cancellation and successful pending-load Stop
have focused real-session checks.

No unrequested behavior was found. Segmentation and defaults remain unchanged;
no classifier or generic replay framework was introduced. This follows the
contract: "Do not add an unused production exception or generic retry framework
merely to make synthetic tests pass."

The expected gaps remain open:

- The required short, approximately 30-second and at least two-minute phone
  baselines, including continuity, memory, latency, Activity/IME and both variants,
  remain unverified.
- No evidenced adapter category exists to admit recovery. Eligible failure,
  clean replacement and pinned-setting complete-audio recovery tests remain
  deferred; synthetic failures do not open that gate.
- Seeded-buffer checks demonstrate PCM retention, not actual microphone recording
  or persisted audio-history behavior.

Review totals: Standards has zero findings; Spec has zero blocking implementation
findings and the three documented validation gaps above. Ticket04 remains open.
These reviews cover the tested source commit; this follow-up changes evidence only.

An injected recoverable failure and canceled recovery on a phone remain
unverified because there is no admitted production recovery category and no
connected physical phone. Terminal injected-OOM checks do not satisfy those
recovery criteria. Ticket04 stays open.

# Ticket 07 integration and beta 3 preparation

October 1, 2026. Worker `/root`, task context
`01a0f5e8-e6bb-78e3-85b6-bceba3b3b743`, dispatched by control tower
`01a0f345-1363-7ee1-ba31-ee9c533599aa`. Branch `codex/share-beta-integration`, initial merged source
`244e0be98d61f6919d3c85f00b44ae73e03b24b0`. This pass owns the integration writer
and local heavy-build/device slot. Final ticket closure and master merge remain
blocked on the acceptance criteria below. No schedule was created.

## Integration correction and review

Reviewed recording/runtime lease ownership together with terminal OOM handling
and the post-load cancellation/generation guard. Added lease-release assertions
to all six existing Android real-session injected-backend checks, including
pending-load OOM, successful pending-load Stop, finalization OOM, failed retirement
and cancellation. Their fixture close rejects double release; these are actual
RecordingSession paths with injected backends, not microphone/native inference.

Spec review found that generic load failure invalidated the catalog successor
instead of an installed supported older version. Failure invalidation now runs
inside `RecognitionModelLifecycle.acquireRuntime` under the activation mutex,
using the installed manifest attempted. Version-bound marker deletion preserves
unrelated versions. Cancellation and OOM do not invalidate installation markers.

The new older-version regression failed with the pre-fix acquisition body:
`:app:testDevDebugUnitTest --tests
'*RecognitionModelUpdateTest.failedOlderRuntimeLoadInvalidatesTheAttemptedInstallation'`
returned exit 1, AssertionError at the missing invalidation notification. Restoring
the correction passed the full suite. A second new regression confirms canceled
and OOM loads preserve the installed older marker and invalidation revision.

Independent Standards and Spec closeout found no blocking findings. Standards
noted existing duplicated upgrade-dismissal keys as nonblocking maintenance advice.
The isolated adapter remains outside production wiring. No default, signer,
application ID, segmentation or recovery mode changed.

## Local verification

Android Studio JBR 21.0.10, local SDK platform 35, NDK 28.2.13676358, CMake 3.22.1
and Gradle 8.11.1. `CMAKE_BUILD_PARALLEL_LEVEL=2`, two Gradle workers, no parallel
Gradle execution, offline dependencies. Combined command:

```powershell
.\gradlew.bat :app:testDevDebugUnitTest :app:assembleDevDebug :app:assembleDevDebugAndroidTest :app:lintDevDebug --max-workers=2 --no-parallel --offline
```

Post-fix result: 201 JVM tests, 200 passed, one existing optional published-Orukeet
archive skip, zero failures/errors. App/test APK assembly passed. Lint has zero
errors, 87 warnings and ten informational findings. After the deliberate baseline
failure, the same complete command passed again in eight seconds.

Confirmed disposable AVD `Ticket02_Verification`, serial `emulator-5560`, API 37,
x86_64 with ARM64 translation. Fingerprint
`google/sdk_gphone16k_x86_64/emu64xa16k:17/CP21.260330.005/15181570:user/dev-keys`.
Started only this AVD headlessly, preserving snapshots and Pixel_10 userdata.
Installed locally built app/test APKs with `adb install -r`.

Combined Android classes: `RecordingSessionFailureTest`,
`RecognitionModelNoticeTest`, `ManagedRecognitionModelCatalogTest`,
`DownloadPromptTest`, `DownloadRetryUiTest`, `RecognitionModelRequestTest`,
`SettingsNavigationTest`, and `RecognitionContentTest`.
Post-fix run: `OK (24 tests)`, 33.737 seconds, no failures. The prior integrated
run also passed 24 tests in 27.633 seconds. Notice/update checks use pinned payload
fixtures, not real-engine successor inference. No physical phone was connected.

Logs retained in the worktree: `integration-build.log`,
`integration-instrumentation-before-fix.log`, `integration-final-build.log`,
`integration-final-instrumentation.log`, `integration-regression-baseline.log` and
`integration-restored-build.log`. XML reports remain under `app/build/`.
Precommit debug APK SHA-256
`4191396b3b6a984e6f30525b85e2703d7309927c9c1c333e1443266d6143a661`.
Its embedded revision is the initial HEAD; signed CI will rebuild the committed
source and version 64 for immutable tag `v1.4.6-share-beta.3`.

## Signed publication checkpoint

Published immutable tag `v1.4.6-share-beta.3` from tested source
`123f4e783e609cb66a5f51350e8ed733570f911b`.
[Signed CI 36820430244](https://github.com/Today20092/voice-input/actions/runs/36820430244)
passed; [beta 3](https://github.com/Today20092/voice-input/releases/tag/v1.4.6-share-beta.3)
is public, a prerelease and not a draft. Latest stable remains v1.4.5.

Exact committed local app/test assembly passed in 14 seconds. Installing those
APKs and repeating the same combined classes passed `OK (24 tests)` in 30.973
seconds. Committed debug APK SHA-256
`054129c2270deb54d2d7c4319f2eeb37a51afce78c7e0a2bae34f5a77ee25abe`.
Additional logs: `integration-committed-build.log`,
`integration-committed-instrumentation.log`, `integration-ci-watch.log`.

CI reports contain 201 JVM tests, 200 passed, one existing skip, zero failures or
errors. Standalone release lint has zero errors, 87 warnings and nine informational
findings. Android test compilation passed. Nine pinned Harper native tests and
notice generation passed; combined release Gradle build passed in 8m 24s.

Downloaded APK `futo-voice-input-moonshine-v1.4.6-share-beta.3.apk` is 127,098,863
bytes. SHA-256
`9e54387fc3190cb7e5b38a0f3c19e0ccc70f601e68e89e005531d5f4d6722804`
matches both GitHub's asset digest and the separately downloaded CI APK artifact.
Android build-tools 35.0.0 independently verify APK v2 signing with one signer.
Certificate SHA-256 remains
`385efab077fd42b52288004a7f6f404190d2f97b9c50d43aefbfc7d53774e2c5`,
matching the previously verified stable v1.4.5 signer.

Package `org.futo.voiceinput.moonshine`, version code 64, version name
`1.4.6-share-beta.3-moonshine`, target SDK 35, `extractNativeLibs=true`.
ZIP inspection finds 21 ARM64 libraries, no other ABI and no duplicate entries
using case-sensitive names. Expected S1, Harper, llama, OpenCL and ggml libraries
and Harper notices are present. No complete model-license audit is inferred.

Downloaded signed APKs, CI unit/lint reports and full CI log are retained under
`build/ticket07/` in this integration worktree. Both workflow artifacts are
`release-check-reports` and `futo-voice-input-moonshine-apk`.
The worker releases its exclusive writer/build/device slot to the control tower
after this publication verification. Master was not merged; no ticket was closed.

## Outstanding final release criteria

- Ticket 01: original packaging root cause remains unproven despite successful
  signed reruns. A beta publication does not prove a root-cause fix.
- Ticket 02: physical phone/editor/FUTO Keyboard interoperability, real headset
  routing, spoken TalkBack and required predictive-back device scenarios.
- Ticket 03: interrupted real successor update, native successor reload/inference
  and notice clearance on a physical device. Production has no successor declared.
- Ticket 04: short, approximately 30-second and at least two-minute phone baselines
  for both Moonshine variants and Activity/IME, continuity, overhead/memory and
  Stop-to-final with S1 separate. No admitted native recoverable category exists;
  eligible injected-failure and canceled-recovery phone criteria remain deferred.
- Ticket 05: isolated adapter is no-go for production; actual JNI inference,
  legacy/S1 coexistence, complete notice provenance and phone benefit remain open.
  ASR4ALL Small and Parakeet 110M remain deferred.
- Ticket 06: symptom, entry point and reproduction input still required.
- Ticket 07: measured Nemotron first useful text and caught-up final targets,
  latency/real-time factor/peak memory/thermals/backlog for every English profile,
  slower-device safe handling, evidence-based performance labels, complete model
  and Sherpa attribution, and remaining full download/runtime/device regressions.

All incomplete criteria remain unchecked. Beta publication is an independent
authorized test milestone and does not complete the catalog release or merge master.

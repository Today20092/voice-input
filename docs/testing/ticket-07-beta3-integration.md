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

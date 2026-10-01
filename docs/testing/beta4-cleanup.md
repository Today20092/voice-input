# Beta 4 cleanup verification

October 1, 2026. User authorized applying the four ponytail-review findings and
publishing the next signed GitHub beta. Branch `codex/share-beta-integration`,
starting checkpoint `8ce09f7`; immutable release tag `v1.4.6-share-beta.4`,
version code 65. Application ID and release signing configuration are preserved.

Removed 20 production lines: unused staged-candidate readiness reporting,
unused runtime-release entry point, unreachable active-runtime sweep and obsolete
range-resume helper. Removed its two obsolete assertions and retained the
range-partition test. Activation and deletion retain their ownership checks under
the runtime mutex; warm Parakeet artifacts still close before replacement/deletion.

Independent Standards and Spec reviews found no actionable findings. No behavior,
default, recognizer, rollback or recovery expansion is intended.

Local Android Studio JBR/SDK validation used two Gradle workers,
`CMAKE_BUILD_PARALLEL_LEVEL=2`, `--no-parallel --offline`:

```powershell
.\gradlew.bat :app:testDevDebugUnitTest :app:assembleDevDebug :app:assembleDevDebugAndroidTest :app:lintDevDebug --max-workers=2 --no-parallel --offline
```

Passed in 1m 20s: 201 JVM tests, 200 passed, one existing skip, zero failures/errors;
app/test APK assembly passed; lint zero errors, 87 warnings, ten informational.
Installed both APKs on confirmed disposable `Ticket02_Verification`,
`emulator-5560`. `RecordingSessionFailureTest` and `RecognitionModelNoticeTest`
passed `OK (8 tests)` in 4.929 seconds, including OOM/cancellation lease cleanup
and staged activation/reload/notice transitions with injected payload fixtures.
Pixel_10 userdata was untouched. Logs: `beta4-local-build.log` and
`beta4-local-instrumentation.log` in the integration worktree.

All physical-device/native/catalog acceptance gaps from
[beta 3](ticket-07-beta3-integration.md#outstanding-final-release-criteria)
remain open. This cleanup does not close tickets or merge master.

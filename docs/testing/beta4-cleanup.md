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

## Published artifact

Immutable tag `v1.4.6-share-beta.4` points to
`c3a77bc429a1018fda0e7dd399d5730a012a7f37`.
[GitHub CI 36826790984](https://github.com/Today20092/voice-input/actions/runs/36826790984)
passed and published the [public prerelease](https://github.com/Today20092/voice-input/releases/tag/v1.4.6-share-beta.4).
CI: 200 JVM passes, one existing skip, zero failures/errors; release lint zero
errors, 87 warnings and nine informational findings; nine native Harper passes;
Android-test compilation, notice generation, signed assembly and package/signature
checks passed. Combined Gradle build completed in 9m 13s.

Downloaded release APK and separately downloaded CI APK are identical and match
GitHub's asset digest. APK size: 127,097,915 bytes. SHA-256:
`b4c1d0ebdb1669deb35149d1793489d18f7592af7dc948538ff0a3dabccd2721`.
Package `org.futo.voiceinput.moonshine`, version code 65, version name
`1.4.6-share-beta.4-moonshine`. Independent Android build-tools verification passes
APK v2 signing with the unchanged stable certificate SHA-256
`385efab077fd42b52288004a7f6f404190d2f97b9c50d43aefbfc7d53774e2c5`.
There are 21 ARM64 libraries, no other ABI and no duplicate ZIP entries.
CI reports, full log and both downloaded APKs are retained under `build/beta4/`.

The later microphone icon fix `cec0868` is outside this immutable beta 4 tag.
This worker's build/device/release-verification slot is released to the control
tower; its independent icon-fix work is preserved.

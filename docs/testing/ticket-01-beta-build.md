# Ticket 01 beta build evidence

## Original failure

[Run 36484940228](https://github.com/Today20092/voice-input/actions/runs/36484940228)
failed on 2026-09-28 at source `76844dae593c68f4d9f620e0d4eb8de823f04147`,
tag `v1.4.6-share-beta.1`. No GitHub release exists for that tag.

The exact failed command was:

```sh
./gradlew :app:testStandaloneReleaseUnitTest :app:lintStandaloneRelease :app:compileDevDebugAndroidTestKotlin :app:assembleStandaloneRelease
```

The recorded error was:

```text
> Task :app:packageStandaloneRelease FAILED
Execution failed for task ':app:packageStandaloneRelease'.
> A failure occurred while executing com.android.build.gradle.tasks.PackageAndroidArtifact$IncrementalSplitterRunnable
BUILD FAILED in 9m 49s
Process completed with exit code 1.
```

The log and check annotations contain no underlying exception. Signing setup,
native Harper tests, unit tests and UI-test compilation ran before this failure.
Publication and APK verification were skipped. Lint analysis ran, but the final
lint report task did not complete, so the original run does not prove lint passed.

## Diagnostic reproduction

Added `--stacktrace` to the same command and dispatched the existing release
workflow on `codex/share-beta-integration`.
[Run 36752666775](https://github.com/Today20092/voice-input/actions/runs/36752666775)
passed at `3b6e166` on 2026-09-30. Its build configuration and runtime source match
the failed beta; intervening changes were documentation and exception reporting.

The command exercises the real packager and repository signing setup. It takes
about twelve minutes including native/toolchain setup; a unit mock would bypass
the failing path. This replaces the diagnosing-bugs skill's seconds-long loop
with the actual CI release gate. The rerun did not reproduce the failure.

Memory pressure, signing and conflicting archive/native entries were considered.
All passed unchanged on the fresh runner. This excludes a deterministic failure
in those inputs, but does not identify the original underlying cause. No runtime,
heap-size, signing-secret or native-packaging workaround was applied.

Ticket 01's cause-establishment and root-cause-fix criteria remain unverified.
Successful publication will restore a testable beta without closing those criteria.
If packaging fails again, inspect the retained stack trace before changing code.

## Release preparation

Beta 2 uses a new tag, version code 63 and the existing standalone application ID
`org.futo.voiceinput.moonshine`. Beta 1's tag remains unchanged. The workflow now
retains unit/lint reports even after failure and prints public signing-certificate
fingerprints. Signing secrets remain in the existing repository workflow.

The published v1.4.5 baseline APK matched GitHub's digest
`62831b1849f82c9e1a8a9d84e1f8045aee069e0ccc15b30b5f3905e08b21fc07`.
Android build-tools 35.0.0 verified its APK v2 signature. Its signer certificate
SHA-256 is `385efab077fd42b52288004a7f6f404190d2f97b9c50d43aefbfc7d53774e2c5`.
Compare the published beta with this fingerprint after its workflow finishes.

## Skills and review

Read ask-matt and selected diagnosing-bugs, then implement for release
preparation/checks. Applied writing-for-agents to the ticket/coordination evidence
and unslop to prose. Existing release gates are the agreed verification boundary;
no implementation-mirroring test was introduced.

Code-review compared changes with checkpoint `3a6e97b` through separate read-only
Standards and Spec agents. Standards found no actionable violations. Spec found
one unmet requirement: the original packaging cause and its fix remain unproven.
Tagged CI and published-artifact verification are pending at this checkpoint.

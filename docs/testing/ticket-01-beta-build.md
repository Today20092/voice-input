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
Both jobs used Ubuntu 24.04 runner image `20260920.314.1`. The repository signing
secrets' update timestamps are all 2026-07-15; they were not changed between runs.

The command exercises the real packager and repository signing setup. It takes
about twelve minutes including native/toolchain setup; a unit mock would bypass
the failing path. This replaces the diagnosing-bugs skill's seconds-long loop
with the actual CI release gate. The rerun did not reproduce the failure.

Memory pressure, signing and conflicting archive/native entries were considered.
All passed unchanged on the fresh runner. This did not reproduce a failure with
those inputs, but does not identify the original underlying cause. No runtime,
heap-size, signing-secret or native-packaging workaround was applied.

Ticket 01's cause-establishment and root-cause-fix criteria remain unverified.
The published beta restores a testable APK without closing those criteria.
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
The published-beta fingerprint comparison is recorded below.

## Skills and review

Read ask-matt and selected diagnosing-bugs, then implement for release
preparation/checks. Applied writing-for-agents to the ticket/coordination evidence
and unslop to prose. Existing release gates are the agreed verification boundary;
no implementation-mirroring test was introduced.

Code-review compared changes with checkpoint `3a6e97b` through separate read-only
Standards and Spec agents. Standards found no actionable violations. Spec found
one unmet requirement: the original packaging cause and its fix remain unproven.
The closeout review of `3a6e97b...00d5e34` found no additional source defects.
Its wording correction, limiting the successful rerun claim to non-reproduction,
was applied to this evidence.
Both review axes also checked the final publication evidence and found no
additional findings; the Spec axis confirmed only the third ticket criterion is met.

## Published beta verification

On 2026-09-30,
[run 36754734286](https://github.com/Today20092/voice-input/actions/runs/36754734286)
completed successfully at `00d5e34b91f48df83b260bf2206706678e326989` and published
[v1.4.6-share-beta.2](https://github.com/Today20092/voice-input/releases/tag/v1.4.6-share-beta.2)
as a public prerelease, not a draft. The stable latest release remains v1.4.5.

| Gate | Verified result |
| --- | --- |
| Standalone release unit tests | 45 suites, 190 tests: 189 passed, one skipped, zero failures/errors |
| Standalone release lint | Zero errors/fatal issues, 85 warnings and 10 informational issues |
| UI-test compilation | `:app:compileDevDebugAndroidTestKotlin` passed |
| Native checks | Pinned Rust `cargo test --locked`: nine tests passed; notice generation passed |
| APK build | `:app:assembleStandaloneRelease` passed; combined Gradle build completed in 9m 2s |
| Signature | APK v2 verification passed; one RSA-4096 signer matching published v1.4.5 |
| Package | `org.futo.voiceinput.moonshine`, version code 63, `1.4.6-share-beta.2-moonshine`, target SDK 35 |
| Native package | 21 ARM64 libraries; expected recognition/cleanup libraries and Harper notices present, no other ABI or duplicate native entries; CI confirmed `extractNativeLibs=true` |

The workflow retains both `release-check-reports` and
`futo-voice-input-moonshine-apk` artifacts. The reports were downloaded and their
XML totals inspected; the published APK was downloaded and independently checked
with Android Studio's Java runtime and Android build-tools 35.0.0.

Published APK: `futo-voice-input-moonshine-v1.4.6-share-beta.2.apk`, 127,086,479 bytes.
SHA-256: `a1fca2805b79eb1ed7da85a7b0b2b98d5a541f68020e5b1a442e4e5ee1db2f8e`.
It matches GitHub's asset digest and the APK uploaded as the workflow artifact.
Signer certificate SHA-256 remains
`385efab077fd42b52288004a7f6f404190d2f97b9c50d43aefbfc7d53774e2c5`.
The second release asset, `arabic-transliteration.txt`, is 990 bytes with GitHub
digest `484f25aad988d74b5582fd1152514165b4c1f11a23640520107ac98b8726ba95`.

The remote beta 1 tag still points to `76844dae593c68f4d9f620e0d4eb8de823f04147`;
beta 2 points to the tested commit above. Neither tag was moved. Master was not
merged or changed. No physical-device, headset, TalkBack or instrumentation-run
acceptance was claimed. The original missing exception and non-reproduced failure
remain the blockers to closing ticket 01's first two criteria.

Local verification artifacts are under
`C:/Users/User/.codex/visualizations/2026/09/30/01a0f362-38db-7913-afe3-244a4d61c15e/ticket01/`.

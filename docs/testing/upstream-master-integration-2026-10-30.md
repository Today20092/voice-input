# Upstream master integration — October 30, 2026

## Scope and ancestry

Integration branch: `Today20092/bring-fork-up-to-upstream`.
Fork baseline: `5d52ccb` (`origin/master`).
Upstream baseline: `c4590aa` (`futo-org/voice-input` master).
The fetched branches diverged by 381 fork-only and 61 upstream-only commits.

The workspace originally started at `4d84a5c`, 260 commits behind the public fork
and one commit ahead. Merge `5cbdf8a` brings in the latest fork without rewriting
that local commit. Its nested-download fix is superseded by the fork's current
resumable downloader; the current nested-path regression test is retained.

The upstream merge reconciles ancestry, with explicit resolutions for the fork.
It does not turn this app into a feature-for-feature copy of upstream. The
[existing adoption specification](../specs/upstream-share-adoption.md) and
[architecture review](../research/upstream-share-adoption-review-2026-09-26.md)
remain the basis for runtime and UI decisions.

## Conflict resolutions

- Keep `RecordingSession`, `RecognizerView`, `RecognitionModelLifecycle`, and the
  backend interfaces. Preserve Orukeet as the new-install default, every saved
  backend selection, model installation/update ownership, audio history,
  retranscription, cleanup ordering, vocabulary corrections, and content-free
  standard diagnostics.
- Retain legacy Whisper/GGML sources, JNI, bundled model, and migration paths.
  Upstream's deletion of those paths is part of its runtime replacement, not
  permission to remove this fork's selectable fallback.
- Exclude upstream's `shared` module and `transcribe.cpp` submodule from this
  build. Their wholesale adoption conflicts with the existing specification and
  ADR 0001. The isolated public-engine experiment and its promotion gates remain
  unchanged; this merge does not claim those gates passed.
- Retain the managed catalog, explicit download confirmations, resumable and
  validated staging, atomic activation, and reactive readiness. Do not replace
  them with upstream's separate model catalog/update path or destructive dev
  model-downgrade controls.
- Retain the fork's tested FUTO Keyboard provider/session implementation, IME
  insertion ownership, accessible recognition UI, navigation, theme, language
  controls, version sequence, package IDs, updater, and GitHub release flow.
  Upstream's keyboard-provider flow and shared-module UI are alternative
  implementations of these responsibilities.
- Keep the pinned native runtimes and NDK `28.2.13676358`. In particular, the
  upstream transcribe.cpp NDK change is not applied to the fork's separate native
  stack. Keep target SDK 35 and minimum SDK 26 for this integration.

## Changes adopted in this integration

- Upstream Gradle 9.4.1 wrapper, AGP 9.2.1, Kotlin/Compose plugin 2.2.10,
  compile SDK 36, and JVM 21 toolchain configuration.
- Align the serialization plugin with Kotlin, replace removed `exec` APIs with
  provider-based execution, and resolve generated source directories to files.
  Existing `preBuild` dependencies still generate the assets and native runner.
- Retain the Kotlin-plugin and legacy-variant compatibility flags for AGP 9;
  these require migration before AGP 10. Preserve existing APK names and pinned
  payload verification. Retain release unit-test tasks with
  `android.onlyEnableUnitTestForTheTestedBuildType=false`, matching upstream's
  compatibility setting and the fork's existing release workflow.
- Remove the obsolete, unused `:dep:futopay:android:app` include, as upstream did.
  Gradle 9 rejects that nonexistent project directory.
- Update all three GitHub build workflows to provision SDK/build tools 36.
- Upstream's distinct development launcher icons.
- Disable IME fullscreen mode (`e87bd3c`) while retaining the fork's insertion
  lifecycle. List FlorisBoard in Help (`9caad8d`), explicitly attributing
  compatibility to upstream rather than claiming a local device test.

## Remaining upstream commits since the earlier review

The [September review](../research/upstream-share-adoption-review-2026-09-26.md)
already covers the first 35 upstream-only commits through `95e82ad`. Existing
SHARE tickets and their test records track the fork's adaptations. The remaining
26 commits are resolved as follows:

| Upstream commits | Resolution |
| --- | --- |
| `4952540`, `dfc7bba`, `ddf5f43` | Keep the pinned opt-in ASR4ALL S/M/L implementation and Orukeet default; do not swap the bundled legacy model or silently migrate saved model indexes. |
| `cc4fe1a`, `c48ce03`, `a5640cb` | Keep the fork's model catalog, capability-based language UI and streaming profiles. |
| `ce68a5e` | Keep audio permissions required by explicit microphone routing; do not copy a permission deletion for a different runtime. |
| `354fedb`, `ff02a28`, `59a56f8`, `1536d25`, `c4590aa` | Import the wrapper/toolchain baseline and adapt GitHub CI. Keep fork release versions and artifact naming; omit upstream GitLab publishing and transcribe model-download changes. |
| `e87bd3c` | Adopt the fullscreen-mode fix. |
| `53715ff`, `e93bdfe`, `c211677` | Keep the existing cross-backend personal-vocabulary corrections and explicit aliases. Do not add upstream's ASR4ALL-specific postprocessor or hardcode dictionary entries. |
| `15a1088`, `daa7721` | Keep the fork's cancellation/recording-session ownership and model-specific language handling; the changed shared-module call sites are absent here. Verify existing recovery and language tests. |
| `9caad8d` | Add the upstream FlorisBoard Help entry with a local-verification caveat. |
| `ebcd206`, `95b59a3` | Keep the fork's accessible UI, diagnostics copy, and existing warnings rather than mixing two theme/UI systems. |
| `ccfef1a` | Keep the fork's explicit model download/repair confirmation flow, which already avoids automatic downloads from settings. |
| `10c1b8d` | Keep fork-specific release notes and version sequence; upstream's what's-new content describes a different runtime/default. |
| `1a9bfa1` | Keep the existing provider setup/manual fallback guidance and remembered-provider caveat. |
| `4fd23c3` | Preserve the upstream merge commit in ancestry; its shared-architecture content is resolved as above. |
| `6f4d137` | Keep the fork README, installation instructions and product description. |

## Validation

Completed on Windows with JDK 21 and the pinned NDK/CMake toolchain:

| Check | Result |
| --- | --- |
| `:app:testDevDebugUnitTest` | 276 passed, 3 skipped, no failures/errors |
| `:app:testStandaloneReleaseUnitTest` | 276 passed, 3 skipped, no failures/errors |
| `:app:lintDevDebug` | No errors; 87 warnings |
| `:app:lintStandaloneRelease` | No errors; 86 warnings |
| `:app:compileDevDebugAndroidTestKotlin`, `:app:assembleDevDebugAndroidTest` | Passed, including the final test corrections |
| `:app:verifyDevDebugApk` | Passed; 29 ARM64 libraries and one copy of each required runtime |
| `:app:assembleStandaloneRelease` | Passed; local fallback signing, not production release signing |
| `tools/verify_native_packaging.py` on the release APK and symbols | Passed: all 29 ARM64 libraries, pinned runtimes/notices, S1 speculation off, stripping and matching symbols |
| `git -c core.whitespace=cr-at-eol diff --cached --check` | Passed; upstream's Windows wrapper retains CRLF line endings |

The selected emulator suite covers real editor insertion, recording-session
cancellation/OOM recovery, recognition UI, provider protocol handling, navigation,
every managed model's language/options UI, model update/repair transitions,
download confirmation/retry, nested downloads, stale-request refresh, Whisper
selection, and independent backend construction. Downloads and upgrades use
controlled fixtures rather than fetching all model packages.

Two existing assertions failed reproducibly in the initial 57-test run: the
navigation test searched for a selected descendant beneath an already merged
selectable row, and the ASR4ALL details test required an obsolete exact
description. The production model UI and descriptions match the fork baseline;
the assertions now check the selectable row and the description prefix. See
[Android's merged-semantics testing guidance](https://developer.android.com/develop/ui/compose/testing/apis).
Both corrected methods passed in a focused rerun. The added
`LegacyWhisperSmokeTest` also passed, loading the actual bundled fallback and
transcribing the existing JFK audio fixture through ARM64 JNI.

The full final emulator suite passed all 58 tests in 94.346 seconds on a fresh
API 37 x86_64 AVD with ARM64 translation. The original Pixel_10 AVD was full;
its app/model data was retained and a separate test AVD was created under
ignored `build/` storage.

Build logs and reports are under ignored `build/` and `app/build/`. The APKs were
built from the resolved merge tree based at `5cbdf8a`; their embedded revision
identifies that pre-merge parent. No APK or release was published.

## Remaining device gates

No physical phone was connected. Physical microphone/Bluetooth routing, FUTO
Keyboard interaction with another installed app, inference for downloaded models
such as Orukeet, performance/battery behavior, production-signed app upgrades,
and real interrupted model transfers still need device validation before a
release. Fixture tests and emulator ARM64 translation do not establish those
results. The default, catalog pins, saved setting keys, and package IDs remain
unchanged. This integration does not close the existing optional-engine gates.

# AGENTS.md field-study review

Historical review recovered from `31a3220` on October 30, 2026. This is the October 17 review record, not a new instruction audit. Current tracker, low-memory and Git-workflow guidance takes precedence.

Reviewed October 17, 2026. All five recommendations applied after the user requested implementation.

## Basis and scope

Applied the invoked `agents-md-field-study` skill and its supplied study notes. The Coldtea study describes common content in large repositories; its frequencies are not a score or evidence that adding rules improves outcomes. This review uses repository evidence rather than a target length or generic checklist.

Located root `AGENTS.md` and three nested files, all in vendored code: `app/src/main/cpp/third_party/parakeet.cpp/AGENTS.md`, its `third_party/voice-detect.cpp/AGENTS.md`, and `app/src/main/cpp/third_party/llama.cpp/AGENTS.md`. Their local build, performance, ABI, and contribution rules should retain their local scope. No new nested file is recommended. Exhaustive validation of upstream instructions and their transitive documentation is outside this root-guide review.

Read the root's three linked `docs/agents/` documents, `CONTEXT.md`, and the three ADRs. Also inspected development documentation, build configuration, release CI, and the current Parakeet artifact declarations. Graph and context-mode tools were unavailable in this session, so repository discovery used bounded shell inspection.

## Already useful

- The purpose, Android entry points, main recognition flow, and Kotlin/native separation provide useful orientation.
- Existing boundaries preserve legacy Whisper selection, handle the IME permission limitation, and protect unrelated local edits.
- Windows build, unit-test, and lint commands provide a practical starting point.
- Issue tracking, canonical labels, vocabulary, and architectural decisions already have dedicated documents. Preserve those sources rather than repeating their contents.
- Vendored instruction files provide subsystem-specific guidance without expanding the root document.

## Prioritized recommendations

### 1. Correct model-storage guidance (high)

**File/section:** root `AGENTS.md`, Model Storage And Downloads.

**Evidence:** `ParakeetModel.kt` now declares directory `sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8` and four artifacts: `encoder.int8.onnx`, `decoder.int8.onnx`, `joiner.int8.onnx`, and `tokens.txt`. All four have concrete hashes, also present in `app/build.gradle`. The guide still names Unified assets and placeholder hashes.

**Why:** following the guide could target obsolete assets or encourage unnecessary hash changes.

**Proposed change:** replace the obsolete file inventory and placeholder warning with pointers to `ParakeetModel.recognitionModel`, `RecognitionModelStore`, and Gradle's bundled artifact declarations. Retain the instruction to keep runtime and bundled artifact definitions consistent.

### 2. Align setup and release guidance with the build (high)

**File/section:** root Build And Test Commands and Product Flavors; `docs/development.md` setup prerequisites.

**Evidence:** release CI uses JDK 21, CMake 3.22.1, recursive submodules, Rust 1.98.1 and the `aarch64-linux-android` target. It runs `testStandaloneReleaseUnitTest`, `lintStandaloneRelease`, `compileDevDebugAndroidTestKotlin`, and `assembleStandaloneRelease`, then checks package contents and signatures. It discovers the APK under `standalone/release`; the root's fixed dev-debug filename claim is obsolete. `docs/third-party/Harper-NOTICE.md` documents the Rust prerequisite, which the main development setup omits.

**Why:** incomplete prerequisites cause avoidable build failures; inaccurate release guidance sends agents to the wrong artifact.

**Proposed change:** repair setup prerequisites in `docs/development.md`; link that canonical guide from AGENTS. Replace the stale APK warning with the current output directory and a link to `.github/workflows/release-apk.yml`. Identify local debug checks separately from CI release checks. Keep JDK 17+ as the documented local minimum unless execution establishes otherwise; report JDK 21 as CI's selected version.

### 3. Add focused testing and device-evidence pointers (medium)

**File/section:** root Build And Test Commands / Development Notes.

**Evidence:** unit and Android test trees exist, including `BenchmarkRunnerTest`; CI compiles Android tests but does not execute them. `docs/development.md` already requires device evidence for microphone routing, insertion, and inference. `docs/release/model-catalog-verification.md` documents `:app:verifyDevDebugApk`.

**Why:** agents need to distinguish local logic checks, packaging checks, and actual device validation.

**Proposed change:** add a focused example, `.\gradlew.bat :app:testDevDebugUnitTest --tests 'org.futo.voiceinput.benchmark.BenchmarkRunnerTest'`, and link the existing device and packaging requirements. State that compiling instrumentation tests is not device execution. Verify the example through Gradle before claiming it has passed.

### 4. Surface the established privacy boundary (medium)

**File/section:** root Development Notes, with links to `CONTEXT.md`, `docs/diagnostics.md`, and ADR 0003.

**Evidence:** those sources distinguish content-free standard diagnostics, explicit transcript captures, and retained audio history. The root guide does not expose this boundary. Development documentation already excludes signing keys, private recordings, and local SDK configuration from commits.

**Why:** recognition and troubleshooting changes can accidentally expose dictated content or conflate separate retention systems.

**Proposed change:** add a short directive to read the diagnostics contract before changing logging, capture, or export; preserve its content exclusions and explicit transcript-capture boundary. Link the existing contributor security rule rather than inventing a new policy.

### 5. Update orientation and sharpen documentation triggers (medium)

**File/section:** root High-Level Architecture, Key Files, Development Notes, and Agent skills.

**Evidence:** the current root map emphasizes Parakeet and legacy Whisper, while repository vocabulary and ADRs also cover model lifecycle, live transcription, S1-mini cleanup, audio history, and diagnostics. The blanket claim that Parakeet is final-only does not distinguish the TDT declaration from the buffered Unified behavior described in ADR 0001. `docs/development.md` already defines focused PRs, issue links, attribution, validation, and release requirements but is not linked from the root agent guide.

**Why:** a stale map misroutes work, and weak pointers hide already-written instructions.

**Proposed change:** add concise pointers for shared recognition lifecycle, cleanup, and diagnostics; replace blanket transcription claims with a pointer to each model's declared behavior. Make the domain pointer explicit before exploration and the development pointer explicit before contribution/release work. Keep triage details in their existing documents. Verify runtime ownership before rewriting detailed flow claims.

## Theme coverage and non-applicable additions

Overview and structure are useful but need freshness work (1, 5). Setup/build and CI need corrections (2). Full/focused testing and quality checks need clearer routing (3). Git/PR conventions already exist and need a pointer (5). Security boundaries have concrete local relevance (4). Links to deeper documentation already work and can replace duplicated inventories.

New monorepo domain hierarchies are not applicable: this repository explicitly declares a single context. A mandatory formatter, commit-message format, or pre-commit framework is not recommended: this review did not establish a project requirement for one. Upstream vendored policies should not become application-wide contribution rules.

## Implementation and verification

The user selected implementation of the review. All five recommendations are implemented in root `AGENTS.md`, with prerequisite corrections in `docs/development.md`. No nested instruction files or application code were changed. Existing benchmark-related working-tree edits were preserved.

The root guide now points to current model declarations, setup and release sources, focused tests and device-evidence requirements, privacy contracts, and shared recognition/cleanup/history/diagnostics components. Local setup now includes the pinned Rust toolchain, Android target, Python notice generation, and a preference for CI's JDK 21.

Graph and context-mode tools became available during implementation. Graph lookup and an inbound trace confirmed `orukeetBackend` is used by the shared recognition lifecycle; its snippet confirms the Orukeet-configured `ParakeetBackend`. All local Markdown link targets in the edited instruction and development files exist. Final diff review and `git diff --check` passed.

Attempted the focused `BenchmarkRunnerTest` command with `--max-workers=2` and the bundled Android Studio JDK (25.0.3). Gradle failed before executing tests: it could not create `AndroidUnitTest`, `DefaultTestTaskReports`, and `DefaultReportContainer`. This does not establish that JDK 25 caused the failure. The focused command remains unverified by execution and should be retried with CI's JDK 21. No build-tool configuration was changed to resolve this unrelated failure.

Skipped full builds, lint, native tests, and device tests for these documentation changes. The documented local JDK minimum was preserved, not independently validated. Existing diagnostics implementation gaps remain documented in `docs/diagnostics.md`; the new agent guidance states the required privacy boundary rather than claiming every existing logger satisfies it.

# Ticket 05 optional engines

## Decision, 2026-09-30

No-go for production promotion at this checkpoint. The public Parakeet candidate
has reproducible source and an isolated ARM64 build, but actual JNI inference,
same-process legacy/S1 coexistence, complete packaged notices and useful measured
phone benefit remain unverified. This is an inclusion decision, not a measured
claim that the engine or 110M model is inferior. Keep Orukeet, saved selections,
Sherpa-ONNX, legacy Whisper/GGML and S1 unchanged.

ASR4ALL Small is deferred because the public revision lacks the FUTO ASR4ALL API
and the exact FUTO pin remains anonymously unavailable. Parakeet 110M catalog
implementation is deferred because there is no positive adapter gate. Its pinned
artifact metadata is known, but the model was not downloaded or inferred here.
Neither dependent model is added to production. Their individual model,
lifecycle and phone criteria are unperformed, not passed.

## Scope and provenance

Worktree: `C:/Users/User/.codex/worktrees/f56d/futo-parakeet-voiceinput`.
Branch: `codex/ticket-05`, starting at tested integration checkpoint `bdedb17`.
Original `codex/share-09` remains at `ab10fa5`.

| Original commit | Imported commit | Scope |
| --- | --- | --- |
| `fc4635e` | `8481406` | Source feasibility evidence |
| `8316814` | `3816cf6` | Isolated adapter and host checks |
| `ab10fa5` | `f095f0c` | Pinned include-path build fix |

Imports used `git cherry-pick -x`. Each ticket conflict kept the current
consolidated queue; the imported evidence and implementation were retained.
Only ticket 05 and its linked SHARE-09/10/11 acceptance sections are updated.
Production source sets, Gradle settings and runtime defaults have no changes.

The orphaned retained directory
`C:/Users/User/.codex/worktrees/9e07/futo-parakeet-voiceinput/tools/transcribe_experiment`
was inspected read-only. It has the source archive and two extracted source trees;
no `.so`, native log or Git metadata was found in that retained experiment.
Its files were preserved. A copy of its archive was verified in this worktree
against SHA-256 `00c3bc4f2f505b4e6865c559a3c9aad30e98a48c9b6a304a2e73b50cb2f42522`.
The original September 28 build report is historical evidence, not a retained
binary verified during this pass.

## Local verification

All builds used the installed Android Studio toolchain: JBR 21.0.10,
Gradle wrapper 8.11.1, SDK platform 35, NDK `28.2.13676358`, Clang 19.0.1,
CMake 3.22.1 and Ninja. Native configuration is ARM64, API 26, Release,
static libc++, two workers. The actual cache has CPU-only static dependencies,
OpenCL/OpenMP/GPU/dynamic loading/KleidiAI disabled, and tinyBLAS enabled.
No NDK/AGP migration was required.

Commands from the repository root:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
$env:ANDROID_HOME = 'C:/Users/User/AppData/Local/Android/Sdk'
./gradlew.bat -p tools/transcribe_experiment test --max-workers=2 --no-daemon
./tools/transcribe_experiment/experiment.ps1
./tools/transcribe_experiment/experiment.ps1 -Build
```

The host suite passed all three tests, zero skips/failures/errors. It covers
canceled loading, invalid model/language/input rejection, a complete 121-second
audio handoff, repeated success/failure, cancellation before release, suppression
of canceled text and idempotent close. These use a controlled session double;
they do not load JNI or prove native lifecycle behavior.

The first full native script completed successfully with exactly five JNI
exports and only `libm.so`, `libdl.so` and `libc.so` dependencies. No shared GGML,
llama, transcribe, OpenCL or libc++ dependency appeared. This establishes link
isolation, not same-process runtime compatibility or APK packaging.

Research found the default build compiles tinyBLAS `sgemm.cpp`, whose full MIT
notice is missing from upstream `THIRD-PARTY-LICENSES.md`. The assertion below
failed after the original script completed:

```powershell
if (!(Test-Path tools/transcribe_experiment/build/arm64/TINYBLAS-LICENSE.txt)) {
    throw 'Built tinyBLAS notice is missing'
}
```

The copy step now retains the complete first 21 notice lines from the verified
pinned source. The complete script rerun exited 0 with the same five JNI exports
and dependency allowlist. The assertion then passed. The output notice has all
21 lines, beginning `Copyright 2024 Mozilla Foundation` and ending `SOFTWARE.`,
including the full permission and warranty/disclaimer text.

Final unstripped library: 44,034,408 bytes, SHA-256
`da4a57a18e843e557cfad41dc76b65340c09508895d82c0089021bc52126959d`.
This is one local build artifact, not APK/install size or bit-reproducibility
evidence. Retained logs and test XML are in this worktree's ignored
`tools/transcribe_experiment/build/`:

- `host-tests-2026-09-30.log` and `test-results/test/`;
- `native-build-2026-09-30.log`, the original script pass;
- `native-build-notices-2026-09-30.log`, the fixed script pass;
- `arm64/libshare09_parakeet.so`, `TRANSCRIBE-LICENSE.txt`,
  `THIRD-PARTY-LICENSES.md` and `TINYBLAS-LICENSE.txt`.

The control tower granted the build slot for both passes. It was explicitly
released after the fixed pass; no heavy build or device access remains claimed.

The known engine/GGML/miniz and tinyBLAS notices do not establish complete
distribution attribution. Always-compiled Unicode helpers/tables identify
llama.cpp/UCD origins without the donor revision/UCD version; complete provenance
and the final notice package remain unverified. See the [primary-source audit](../research/ticket-05-source-gates-2026-09-30.md).

The isolated JVM experiment has no Android lint task or packaged application.
Production app assembly/lint was not repeated for this tools-only change.
Android adapter packaging/lint and JNI/device checks remain open before promotion.

## Remaining checks and disposition

| Gate | Result |
| --- | --- |
| Source availability and difference from FUTO | Verified public pin; FUTO pin unavailable; ASR4ALL API absent |
| ARM64 compile/link and export/dependency isolation | Verified locally |
| CPU baseline | Verified in cache; no GPU support or benefit claimed |
| Host interface lifecycle and full audio | Three controlled-session tests pass |
| Actual JNI model validation/load/inference/finalization/cancel/release | Unverified |
| English selection and absence of partials | Source/host contract only; native behavior unverified |
| Legacy GGML/S1 runtime coexistence and APK symbols/packaging | Unverified |
| Complete dependency/model distribution notices | Unverified; known tinyBLAS omission fixed |
| Shared model-family comparison and useful measured benefit | Unverified |
| ASR4ALL Small / Parakeet 110M production evaluation | Deferred under no positive adapter gate |

ADB listed only `emulator-5560`, product/model `sdk_gphone16k_x86_64`. No physical
phone was attached. No emulator installation, inference or data mutation was
performed, and retained Pixel_10 data was untouched. Emulator execution would
not substitute for the specification's fixed-audio phone protocol.

Before revisiting inclusion, resolve notice provenance and package a test
adapter, then run JNI lifecycle/coexistence checks. On the user's ARM64 phone,
compare a shared model family and the existing small choices using the same
audio/reference transcripts, separating quantization/version differences and
raw output from cleanup. Include short phrases, numbers/names, silence,
approximately 30-second and two-minute recordings with pauses, repeated cold/warm
runs, first partial and revisions where supported, Stop-to-final p50/p95, total
processing time, accuracy, peak memory, APK/install/model bytes and sustained
thermal behavior. These measurements were not performed here.

## Standards

Independent review found no documented-standard breaches or actionable baseline
smells. It compared the imported commits and pending checkpoint changes with
`bdedb17`, using AGENTS.md, CONTEXT.md, tracker conventions and ADR 0001/0002.
The native session interface provides a useful ownership/testing seam; the
experiment stays outside production source sets and preserves defaults.
Acceptance updates check only verified criteria.

## Spec

Independent review found no actionable findings for this bounded no-go
checkpoint. The English final-only `SpeechBackend` contract, full PCM handoff,
validation and host cancellation/release checks match the isolated experiment.
Required actual JNI behavior, coexistence, packaging/lint, complete notices and
phone comparison remain partial and accurately disclosed. SHARE-10/11 explicitly
remain deferred without a positive adapter gate. No catalog/default/runtime
changes or false completion claims were found.

Standards: zero findings. Spec: zero actionable findings; disclosed promotion
gates remain open. Whitespace checks passed; new evidence links and SHARE-09/10/11
specification links resolve. Production files and unrelated ticket sections are
unchanged.

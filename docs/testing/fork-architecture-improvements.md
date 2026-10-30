# Fork architecture improvements: verification

Specification: [fork architecture improvements](../plans/fork-architecture-improvements.md). Parent [#30](https://github.com/Today20092/voice-input/issues/30), integration branch `codex/architecture-improvements`, review baseline `1fa5cda54b6445bb704fbc96bb1a1a4904829ce0` (1.4.8).

## Ticket evidence

- #31 native packaging: integrated. [Artifact checks and final signed integration measurements](architecture-31.md) passed; see integrated checks below.
- #32 final transcript processing: integrated. [Focused verification](architecture-32.md): 29 JVM tests and Android-test compilation passed; no device execution.
- #33 comparison audio lifetime: integrated. [Focused verification](architecture-33.md): 25 benchmark JVM tests passed, including the demonstrated eager-loading regression.
- #34 recognition construction: integrated. [Focused verification](architecture-34.md): 33 JVM tests and Android-test compilation passed; actual native construction/configuration tests still need device execution.
- #35 streaming finalization snapshot: integrated after #32. [Focused verification](architecture-35.md): eager-copy regression failed, lazy streaming preparation passed; 23 JVM tests and Android-test compilation passed. Final-only snapshot remains outside recognition timing.
- #36 asynchronous capture storage: integrated. [Focused verification](architecture-36.md): 30 S1 JVM tests, lower-memory Android-test compilation and lint passed. Initial host JVM allocation failure is recorded separately from code/test results.

## Integrated checks

The user requested no more local testing because of insufficient RAM. All remaining integrated builds/tests use remote CI. After that request, a process check found no local Java build processes running; no unrelated processes were terminated.

Final remote CI passed on source revision `b995256ec305f215b7a230514f18d1ce0d9b67d4`: [run 40108948096](https://github.com/Today20092/voice-input/actions/runs/40108948096). Subsequent evidence updates are documentation-only. The earlier pre-review-correction run 40108066898 also passed, but is not substituted for this final result.

- JVM tests: 260 passed, zero failures/errors/skips, counted from the uploaded standalone-release XML reports.
- Release lint, dev-debug Android-test Kotlin compilation and signed standalone ARM64 assembly passed. Android-test compilation did not execute instrumentation/Compose tests.
- Phonon, Orukeet and Whistle model package/reference checks passed. Packaged library/signature checks and the new native-symbol verifier passed for all 29 expected ARM64 libraries, pinned Whistle bytes, eligible debug stripping, matching separate symbols and checked alignment.
- Signed APK: 105,131,976 bytes (100.26 MiB), versus signed 1.4.8's 139,809,896 bytes (133.33 MiB): 34,677,920 bytes / 33.07 MiB / 24.80% smaller.
- Compressed native entries: 47,373,376 bytes versus 82,063,423 bytes; extracted native entries: 121,056,528 versus 265,485,744 bytes, a 137.74 MiB reduction. This is native disk-payload measurement, not measured inference RAM or speed.
- CI retained `futo-voice-input-moonshine-apk`, `native-debug-symbols-b995256ec305f215b7a230514f18d1ce0d9b67d4` and `release-check-reports` artifacts. No stable release/version bump was made.
- Two-axis standards/spec review against the pinned baseline: completed inline, with one retry correction; see the review limitations below.

## Review

The requested parallel review agents could not initialize because the session reached its agent-thread limit. Review and the following correction were therefore performed inline with lightweight source/Git reads only; this is not an independent-agent review. The review compared `1fa5cda...0c068f7`, then checked the correction below.

### Standards

No documented-standard breach or actionable baseline smell was identified in the reviewed implementation. Reviewed the changed Kotlin processing/construction/storage/UI paths, native artifact verifier, workflow and evidence against AGENTS.md, development guidance, CONTEXT.md and ADRs 0001-0003. No additional local builds/tests were run for this review.

### Spec

One new retry defect was found in `settings/pages/S1Mini.kt`: after a successful capture action, a failed follow-up list refresh was reported as an action failure, and Retry repeated the completed action. This violated #36's accurate error/retry behavior. The correction distinguishes action failure from refresh failure and retries only loading after a successful action. `TranscriptCaptureUiTest.refreshRetryDoesNotRepeatSuccessfulDeletion` covers a completed deletion, refresh failure, replacement capture and non-destructive retry. This Android test requires execution on a device; remote compilation alone does not establish a passing UI regression.

Summary: Standards 0 findings; Spec 1 finding corrected. Native/device/runtime and independent-review limitations remain explicit.

## Worktree cleanup

The six ticket branches are merged and their implementation/evidence commits are preserved. Managed archival was attempted for all six worktrees, but the app refused each with `Another active task still uses this worktree.` No forced filesystem/Git deletion was attempted. Worktrees therefore remain for app-managed cleanup after their ownership guard clears. This consumes disk space, not active test-process RAM; process checks after the user's constraint found no running local Java build processes.

## Device limitations

No Android device was connected at kickoff. Physical ARM64 model/runtime smoke tests, microphone/editor behavior, Android UI rendering/StrictMode execution, PSS, Stop-to-final timing, thermal and battery behavior remain pending until recorded as executed. Instrumentation compilation is not device execution. Calculated allocation reductions and temporary native stripping experiments are not physical-device performance results.

Existing broader validation/privacy issues #15, #17, #19, #21 and #23 are outside this implementation's closure scope. No stable release or version bump is included.

# Fork architecture improvements

Parent: [#30](https://github.com/Today20092/voice-input/issues/30). Baseline: master `1fa5cda54b6445bb704fbc96bb1a1a4904829ce0` (1.4.8). Integration branch: `codex/architecture-improvements`.

## Objective

Implement the six 2026-10-23 fork-only architecture recommendations. Keep every recognition model, existing user data/preferences, validated model downloads, native isolation, recording recovery and content exclusions. Do not rewrite unchanged upstream behavior, change the default model, add a runtime/plugin framework, bump the release version or publish a new stable release in this scope.

## Task graph

- [#31](https://github.com/Today20092/voice-input/issues/31): Ship stripped native code with separate debug symbols. No prerequisite child ticket.
- [#32](https://github.com/Today20092/voice-input/issues/32): Share final transcript processing between dictation and audio history. No prerequisite child ticket.
- [#33](https://github.com/Today20092/voice-input/issues/33): Bound comparison PCM memory to one canonical recording at a time. No prerequisite child ticket.
- [#34](https://github.com/Today20092/voice-input/issues/34): Use one managed recognition construction mapping across entry points. No prerequisite child ticket.
- [#35](https://github.com/Today20092/voice-input/issues/35): Avoid an unused complete audio snapshot when finishing streaming recognition. Blocked by #32 because both modify recording finalization.
- [#36](https://github.com/Today20092/voice-input/issues/36): Move transcript-capture startup and settings disk work off the UI thread. No prerequisite child ticket.

Initially ready: #31, #32, #33, #34, #36. #35 becomes ready after #32 is merged to the integration branch. Implementers use separate branches/worktrees based on the integration branch; integration merges are serialized. A draft integration PR becomes ready only after all implementations and the two-axis review are complete.

## Required behavior and test seams

1. **Native packaging (#31):** ship eligible native code stripped of debug information and retain a separate symbols artifact. Test the built APK and symbols; preserve pinned Whistle bytes, CPU variants, OpenCL, extraction, 16 KiB alignment, library set and signing. Measure APK/extracted size against 1.4.8. The prior controlled experiment showed ~31 MiB compressed-native reduction, not a validated new APK or device speed improvement.
2. **Final transcript processing (#32):** live and history share S1 -> Harper -> personal vocabulary, language gates, valid-empty handling and content-free stage diagnostics; history replacement/delivery/cancellation remain caller-owned. Regression tests exercise real shared processing and caller adoption. Keep comparison raw. Broader delivery/privacy issues #19/#21/#23 remain outside scope.
3. **Comparison PCM (#33):** retain one canonical recording at a time, read/validate outside decode timing, and preserve per-pass mutable-input isolation, first/warm timing/count/order, scoring, persistence and cancellation. Test through the runner and real loading seam. Maximum supported canonical retention falls from ~87.9 to ~11 MiB; this is an allocation calculation, not device PSS.
4. **Recognition construction (#34):** share managed-runtime mapping between lifecycle and comparison. Preserve production warm leasing/selection effects, comparison independent state and language overrides, and specialized Whisper behavior. Test catalog-wide configurations through construction/lifecycle/benchmark seams; no selection mutation or install invalidation in comparisons.
5. **Streaming snapshot (#35):** streaming finalization does not create a redundant complete PCM copy; final-only recognition receives complete normalized audio. Keep count/duration diagnostics, primary buffer/history recovery, debug statistics, raw comparison output and cancellation/session guards. Test recording finalization at the streaming/final-only adapter seam.
6. **Capture storage (#36):** startup/settings capture disk work is off-main-thread and exposes asynchronous loading/error/retry state. Load/retention avoids duplicate decoding and retains consent, ten-run/seven-day limits, corruption evidence, deletion and separate history retention. Test store behavior plus execution-policy/StrictMode where feasible. Keep errors nonfatal to dictation without masking failures as an empty list.

The user explicitly delegated test choices. Use focused vertical TDD cycles at these seams where behavior changes; no need to request the same testing authorization again. Do not assert private implementation structure or write source-string tests in place of behavior/output checks. A packaging task may use an assert-based artifact check as its red/green loop.

## Verification and evidence

The user reported insufficient host RAM after the ticket implementations. From that point onward, run all remaining builds/tests in remote CI; review/fix agents must not run local Gradle, compilation or test retries. Lightweight source/Git review is allowed. Previously completed local results remain evidence only for their recorded revisions.

- Focused regression evidence records pre-fix failure (or why the check is a preservation/refactor check) and post-fix success.
- Integrated JVM tests, lint, Android-test Kotlin compilation, and ARM64 APK assembly/contents/signature checks.
- Use signed CI when local full native assembly cannot run. Preserve the production key/application ID; no signing secrets in source or output.
- Record actual changes and checks in `docs/testing/fork-architecture-improvements.md`. Distinguish device execution from instrumentation compilation and allocation arithmetic from sampled PSS/timing.
- No physical Android device was connected at kickoff. Unavailable microphone/editor/inference/hardware/visual checks must be listed as pending, never marked passed. Preserve existing #15/#17 release/device gates.
- Run code-review against the pinned baseline after all tickets, fix findings, then mark the integration PR ready. Issue closing references resolve the map/children when that PR merges; do not claim unrelated issues completed.

## Existing decisions to retain

ADR 0001: maintained Sherpa runtime; separate native implementations where needed for compatibility. ADR 0002: optional S1 cleanup with CPU/OpenCL choices in a private process. ADR 0003 and CONTEXT.md: standard diagnostics stay content-free; explicit transcript captures and audio history have separate retention/consent.

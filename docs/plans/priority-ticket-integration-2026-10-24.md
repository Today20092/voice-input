# Priority ticket integration

Base: `23212c45f3112a09aa244f548eb816e69b34d55f` (master).
Integration branch: `codex/ticket-priority-integration`.

## Resource budget

Use at most three lightweight implementation agents at once. Each has an isolated
worktree, without native submodule initialization. No local Gradle, CMake, emulator,
APK assembly, model downloads or inference. Run Android/JVM/native packaging checks
through the existing manually dispatched release workflow, sequentially against
the integration branch. Branch builds upload artifacts without publishing a release.
Never infer physical-device success from instrumentation compilation.

## Frontier and ownership

| Order | Issue | Dependency / completion boundary |
| --- | --- | --- |
| 1 | #19 diagnostics privacy and retention | Independent first fix; owns sanitization and retained content lifetime. |
| 2 | #21 delivery and diagnostic isolation | Coordinate shared capture schema with #19; integrate after #19. |
| 3 | #41 Moonshine SIGILL | Investigate concurrently; runtime fix requires affected-phone confirmation. |
| 4 | #8 Orukeet no-speech | Inspect shared input fix and current flow; reporter-specific retest remains required. |
| 5 | #23 explanatory diagnostics | Base implementation on integrated #19/#21 schema and failure boundaries. |
| 6 | #15 release verification | Audit CI/package evidence after fixes; retain physical/accessibility gates. |
| 7 | #17 device benchmark evidence | Inspect tooling and report provenance; human live readings and device runs remain gates. |
| 8 | #40 S1 speculative decoding | Validate pinned runtime feasibility and correctness; avoid unsupported acceleration claims. |
| 9 | #39 short cleanup bypass | Optional and disabled by default; retain eligible cleanup/capture behavior. |
| 10 | #16 upstream keyboard setup | Verify a published compatible upstream contract before exposing setup. |

The original specs and comments remain authoritative in
<https://github.com/Today20092/voice-input/issues>. Existing implementation and
device records under `docs/` are context, not proof that this integration passes.
Close only issues whose criteria are met or explicitly dispositioned; use related
issue references for pending runtime/device work rather than automatic closure.

## Verification and integration

Implement behavior regressions at existing public boundaries using the TDD skill.
Record tests written versus actually executed separately, including CI revision
and URLs. Merge each implementer branch with a merger agent, then run independent
Standards and Spec reviews against the pinned base. Fix actionable findings before
marking the integration PR ready. Preserve the primary checkout's uncommitted work.

No release tag or production publication is part of this PR preparation.

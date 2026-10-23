# Fork architecture improvements: verification

Specification: [fork architecture improvements](../plans/fork-architecture-improvements.md). Parent [#30](https://github.com/Today20092/voice-input/issues/30), integration branch `codex/architecture-improvements`, review baseline `1fa5cda54b6445bb704fbc96bb1a1a4904829ce0` (1.4.8).

## Ticket evidence

- #31 native packaging: integrated. [Artifact checks and controlled measurements](architecture-31.md) pass; final signed build remains pending.
- #32 final transcript processing: integrated. [Focused verification](architecture-32.md): 29 JVM tests and Android-test compilation passed; no device execution.
- #33 comparison audio lifetime: integrated. [Focused verification](architecture-33.md): 25 benchmark JVM tests passed, including the demonstrated eager-loading regression.
- #34 recognition construction: integrated. [Focused verification](architecture-34.md): 33 JVM tests and Android-test compilation passed; actual native construction/configuration tests still need device execution.
- #35 streaming finalization snapshot: integrated after #32. [Focused verification](architecture-35.md): eager-copy regression failed, lazy streaming preparation passed; 23 JVM tests and Android-test compilation passed. Final-only snapshot remains outside recognition timing.
- #36 asynchronous capture storage: integrated. [Focused verification](architecture-36.md): 30 S1 JVM tests, lower-memory Android-test compilation and lint passed. Initial host JVM allocation failure is recorded separately from code/test results.

## Integrated checks

Not yet run on the combined implementation. Focused ticket results are not a substitute for these checks.

- JVM tests, lint and Android-test compilation: pending.
- Signed ARM64 assembly, native symbols, library/hash/signature validation and actual APK size comparison: pending.
- Two-axis standards/spec review against the pinned baseline: pending.

## Device limitations

No Android device was connected at kickoff. Physical ARM64 model/runtime smoke tests, microphone/editor behavior, Android UI rendering/StrictMode execution, PSS, Stop-to-final timing, thermal and battery behavior remain pending until recorded as executed. Instrumentation compilation is not device execution. Calculated allocation reductions and temporary native stripping experiments are not physical-device performance results.

Existing broader validation/privacy issues #15, #17, #19, #21 and #23 are outside this implementation's closure scope. No stable release or version bump is included.

# Download resumption verification

Branch: `codex/resume-downloads`. Prerequisite: `7e98997d25a6299c0b99043007908ed770dcd2d8`, including Nemotron artifact correction `a28fb96`.

Implementation checkpoints: `3a10e99` and `528ef4c`. The shared downloader retains and validates range checkpoints, retries failed ordinary files, skips validated targets, and falls back to a fresh transfer when ranges are refused. A per-file UI message explains restart. Archive transfers share the sequential response handling. Installation still requires every requested file to validate before a completion marker is written.

## Completed checks

Standalone Kotlin 2.1.0 compilation and JUnit 4.13.2 execution of production `Hashing.kt`, `DownloadRanges.kt`, `ModelFileDownload.kt`, and `ModelFileDownloadTest.kt`, using existing cached dependencies and Android Studio's JBR. Eight tests passed, zero failures:

- Interrupted 33 MiB transfer resumes saved ranges and skips a validated target.
- Ignored Range triggers an explanation and a full replacement response, never an append.
- Interrupted sequential fallback retains its partial bytes and resumes.
- Invalid range, body length, short/oversized body, and checksum do not replace an existing target.
- Changed artifact identity cannot reuse saved offsets.
- Concurrent retries use one writer and one request.
- HTTP 416 and incompatible ranges recover with one bounded fresh request.
- Free-space estimation counts saved bytes rather than a sparse file's apparent length.

The first resume test failed to compile before the new transfer helper existed, then passed. The ignored-range test failed before the restart callback/fallback existed, then passed. The range-refusal regression failed with an IOException before the bounded fallback and passed afterward.

Standards review found no actionable issues. Spec review found the range-refusal recovery gap; after correction, its second review found no remaining issues. Reviews compared against `7e98997`.

## Pending exclusive build slot

The coordinator requires all app Gradle work, including JVM tests and compilation, to wait until Model Options explicitly releases its slot because the shared host has experienced memory pressure. No Gradle, native builds, emulator, or model downloads have run for this ticket.

- Run focused downloader JVM tests, including the updated archive tests, with at most two Gradle workers.
- Run the full unit suite and app compilation/assembly with native parallelism at most two.
- Compile and run `DownloadRetryUiTest` for Retry visibility and restart explanation, plus the prerequisite stale-manifest checks where appropriate.
- Keep device-specific performance claims separate; synthetic transfer tests do not reproduce the beta phone's original stall.
- Update only verified ticket criteria, commit final evidence, and report the final SHA to the coordinator.

Source guidance: [OkHttp response lifecycle](https://square.github.io/okhttp/5.x/okhttp/okhttp3/-call/execute.html) and [cancellation recipes](https://square.github.io/okhttp/recipes), retrieved through Context7. Production remains on the repository's OkHttp 4.11.0 dependency.

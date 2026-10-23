# Comparison recording memory (#33)

`BenchmarkRunner.runCase` now accepts recording metadata and the canonical storage read operation. `DeviceBenchmark` no longer builds an eager list of all PCM arrays. Each recording is loaded and validated once per model in a separate suspend function; that function completes every pass and releases its canonical reference before the next recording is read. No prefetch queue, cache or new storage schema is introduced.

Model initialization still has its own timer. Canonical reads/validation, per-pass defensive copies, warmups, scoring and persistence remain outside measured decode time. The first recording receives the independent first decode, then its untimed warmup and configured warm repetitions; subsequent recordings receive the same warmup/repetition sequence. Each decode retains its own mutable copy. Report order, original/canonical hashes, scoring and raw outputs are unchanged. The lifecycle benchmark lease, wake lock and sampler remain caller-owned.

Later read errors now finish the active case as failed through the runner's existing failure path, close the recognizer and persist prior observations; cancellation still propagates after closing and persisting. Previously every recording was read before any case started, so later read failures could not retain completed observations from earlier recordings.

## TDD and checks

- Red: adapted the runner loading boundary while retaining eager reads, then ran `:app:testDevDebugUnitTest --tests org.futo.voiceinput.benchmark.BenchmarkRunnerTest --max-workers=2`. Seven tests ran and the new real-storage regression failed: expected `failed`, got `completed`. It deletes the second canonical recording during the first decode; an eager load incorrectly continues from retained audio.
- Green: moved reads into the per-recording suspend scope. The same regression now preserves exactly the first recording's first decode and two warm observations in the reopened report, and confirms recognizer closure.
- Existing runner checks preserve warm repetition counts/order, independent initialization/release timing, partial decode failure and model continuation. The fake clock now adds time during audio loading, proving it is excluded from initialization/decode timings.
- Cancellation checks confirm a later clip is never loaded after cancellation. Cross-model mutable-input isolation now runs through real `BenchmarkStore.saveAudio/readAudio`, including canonical hash validation. Existing persistence checks cover reopening, interruption, tampering and safe identifiers.
- Focused verification: `:app:testDevDebugUnitTest --tests 'org.futo.voiceinput.benchmark.*' --max-workers=2` passed. No native assembly was needed.

## Allocation calculation and limits

Eight maximum-length 180-second, 16 kHz mono float32 canonical recordings previously required 92,160,000 bytes (87.89 MiB). One requires 11,520,000 bytes (10.99 MiB): 76.90 MiB less retained canonical PCM. Per-pass input copies, temporary file bytes during loading, native model memory and runtime overhead remain additional allocations; this is not a measured process-PSS reduction.

No Android device inference, physical PSS/latency measurement or instrumentation execution was performed for this ticket. Integrated tests/lint/instrumentation compilation and APK verification belong to the integration run. No version bump or release publication is included.

# Avoid redundant streaming snapshots (#35)

Based on integration commit `94957f26d3205a6c8cdc4795c258a130c88ed448` after #32.

RecordingSession now uses the small `SpeechBackend.prepareRecordingFinalization(FloatBuffer)` adapter. Streaming backends flush their existing stream without accessing/copying the full captured PCM; final-only backends receive a complete independent array containing exactly the buffer's written prefix. The adapter prepares any required snapshot before the recognition timer/event and returns the recognition operation to execute inside that interval. DEBUG RMS/peak statistics scan that prefix directly with absolute reads without advancing its position or allocating an array. Standard audio-duration diagnostics still derive from the captured sample count at 16 kHz.

Primary capture, history writes, streaming replay/flush, raw live-comparison output, shared transcript finalization, and generation/cancellation/delivery guards are unchanged. Required final-only snapshot allocation remains outside recognition elapsed time, preserving existing duration diagnostics. No stop-to-final speed claim is made.

## Focused vertical verification

- Red: first moved the existing eager snapshot into the real production adapter. The streaming test supplied a read-only FloatBuffer with no accessible array. It failed with `ReadOnlyBufferException`, proving the old eager-snapshot behavior still required PCM access before flushing.
- Green: moved the snapshot exclusively into the final-only branch; the same streaming flush test passed with unchanged raw final text and buffer position.
- Preservation checks: a final-only backend receives exactly `[-1, 0, 32767/32768]`, without spare buffer capacity. Mutating its input cannot alter primary recovery audio. Streaming cancellation/failure propagates the original failure without attempting final-only replay.
- A preparation regression changes primary audio between preparing the final-only operation and starting recognition: the backend still receives the original samples. This exercises actual snapshot preparation before the timed operation without asserting private implementation structure.
- Passed 23 JVM tests: 4 RecordingFinalizationTest, 2 StreamingAudioReplayTest, 2 LiveBenchmarkTest, 10 AudioHistoryStoreTest, and 5 RecordingSessionPolicyTest.
- Passed `:app:compileDevDebugAndroidTestKotlin`; existing recording failure/cancellation instrumentation tests compile, but were not executed on a device.
- Successful command: `:app:testDevDebugUnitTest --tests org.futo.voiceinput.backend.RecordingFinalizationTest --tests org.futo.voiceinput.backend.StreamingAudioReplayTest --tests org.futo.voiceinput.RecordingSessionPolicyTest --tests org.futo.voiceinput.history.AudioHistoryStoreTest --tests org.futo.voiceinput.benchmark.LiveBenchmarkTest :app:compileDevDebugAndroidTestKotlin --max-workers=2 --console=plain`.
- `git diff --check` passed. One initial combined invocation placed `--tests` after the compile task and failed option parsing; the corrected invocation above passed.

At 180 seconds, the removed streaming float array is `180 * 16,000 * 4 = 11,520,000` bytes, approximately **10.99 MiB** plus array overhead. This is an allocation calculation, not sampled PSS or a measured latency improvement. DEBUG builds also avoid the array, while retaining their statistics scan.

No phone was connected: microphone/history/IME behavior, native inference, memory sampling and stop-to-final timing remain pending. Integrated lint/release APK checks are reserved for the integration gate. No version bump or publication.

# Moonshine runtime cleanup and segmentation

Researched 2026-09-28 for "Verify long Moonshine dictation and improve segmentation only if needed". This report inspects source and cached Java bytecode. It does not establish phone accuracy, memory use or latency.

## Evidence version

`app/build.gradle:304` pins `ai.moonshine:moonshine-voice:0.0.68`. The cached AAR has SHA-256 `21a8ab5f7b2e0b962b7ef6f0101a7e19fc716ae82d97c163bbbaa247c3ef4a80`. Its cache directory is `C:/Users/User/.gradle/caches/modules-2/files-2.1/ai.moonshine/moonshine-voice/0.0.68/4f109f8ac663252824db4aa9df37b278839e5172/`. The coordinator extracted its classes to `build/moonshine-check/moonshine.jar`; this research independently inspected them using `javap -p -c`.

Upstream v0.0.68 resolves to `0a99f2e31e6e2913c4c61e4c62f85ce57404ba60`. Links below pin that source revision. Java bytecode corroborates the cleanup implementation; this is not proof that native binaries reproduce the source commit. [Upstream tag tree](https://api.github.com/repos/moonshine-ai/moonshine/git/trees/v0.0.68)

Context7 `/moonshine-ai/moonshine` returned current README/API documentation for resource lifecycle. Its `MicTranscriber.close()` description concerns the current API, and must not be applied to the pinned `Transcriber`. [Current README](https://github.com/moonshine-ai/moonshine/blob/main/README.md)

## Native cleanup

The actual 0.0.68 Java class has no public `close()` or unload method. `removeAllListeners()` only clears the Java listener list. It does not free native resources. Its protected `finalize()` checks the transcriber handle, frees the default stream and resets that handle to -1, then frees the native transcriber and resets its handle to -1. Bytecode confirms this order and the guard. A later successful repeated call therefore does nothing. [Pinned Java implementation](https://github.com/moonshine-ai/moonshine/blob/0a99f2e31e6e2913c4c61e4c62f85ce57404ba60/android/java/main/java/ai/moonshine/voice/Transcriber.java)

The same class loads a native transcriber before creating its default stream. If initialization fails after obtaining the native handle, discarding the partially initialized Java object defers cleanup to finalization. Failed-load cleanup must retain access to that object long enough to release it. A second `loadFromFiles()` on an already-loaded object overwrites its native handle without first releasing it; use a fresh instance or release first. These conclusions follow from the pinned source and cached bytecode, not an Android execution test.

The smallest version-specific release bridge is a subclass method that invokes `super.finalize()` after the backend serializes all use of the transcriber. It avoids reflective access to private handles. This is an implementation recommendation, not an upstream public lifecycle contract. Do not race release against add/stop/load, do not independently free the default stream, and do not reuse the released object. Successful finalization resets both handles, preventing a subsequent GC finalizer from freeing them again. Exceptions during native release require explicit error handling because the assignments happen after the native calls.

The upstream C++ destructor deletes the loaded recognition models and stream objects. Releasing only a stream leaves the transcriber and models allocated. [Native destructor](https://github.com/moonshine-ai/moonshine/blob/0a99f2e31e6e2913c4c61e4c62f85ce57404ba60/core/transcriber.cpp)

## Existing segmentation

Pinned native options set `vad_max_segment_duration = 15.0f`, `vad_threshold = 0.5f` and `vad_look_behind_sample_count = 8192`. Stream creation passes these settings into the native VAD. The runtime already has segmentation; a multi-minute session is not necessarily one model segment. [Options](https://github.com/moonshine-ai/moonshine/blob/0a99f2e31e6e2913c4c61e4c62f85ce57404ba60/core/transcriber.h#L139-L145), [stream construction](https://github.com/moonshine-ai/moonshine/blob/0a99f2e31e6e2913c4c61e4c62f85ce57404ba60/core/transcriber.cpp#L353-L362)

There is a discrepancy between the VAD comment and arithmetic. The comment describes decreasing the score to zero at the maximum. The multiplier is `(buffer_size - fade_sample_count) / fade_sample_count`, where the fade threshold is two thirds of the maximum. Immediately after that threshold, the multiplier is near zero. With positive default threshold, this suggests segment completion around ten seconds of buffered audio. It is source analysis, not a measured boundary or a claim of transcription failure. Do not describe the pinned implementation as an exact 15-second splitter. [Pinned VAD calculation](https://github.com/moonshine-ai/moonshine/blob/0a99f2e31e6e2913c4c61e4c62f85ce57404ba60/core/voice-activity-detector.cpp#L158-L170)

Segment completion clears the active buffer; subsequent speech can create another segment with look-behind audio. This does not stop the stream. VAD `stop()` separately marks the stream inactive and completes any active segment. [State transitions](https://github.com/moonshine-ai/moonshine/blob/0a99f2e31e6e2913c4c61e4c62f85ce57404ba60/core/voice-activity-detector.cpp#L170-L225)

The pinned Android binding dispatches `LineCompleted` for completed updated lines while audio continues. `stopStream()` separately requests native stop and a forced transcription pass before dispatching trailing events. App stop handling must retain these events. A completed segment is not itself a completed dictation. [Android stop and dispatch](https://github.com/moonshine-ai/moonshine/blob/0a99f2e31e6e2913c4c61e4c62f85ce57404ba60/android/java/main/java/ai/moonshine/voice/Transcriber.java#L175-L248)

## Remaining verification

Upstream contains a multi-minute memory test with silence gaps and `return_audio_data = false`; this research did not run it. Its assertions address retained completed-segment PCM, not uninterrupted speech accuracy or this app's Android behavior. [Upstream test](https://github.com/moonshine-ai/moonshine/blob/0a99f2e31e6e2913c4c61e4c62f85ce57404ba60/core/transcriber-streaming-memory-test.cpp)

No inspected evidence establishes the user's safe total dictation duration. Device checks still need continuous speech across internal boundaries, ordinary pauses, a final phrase immediately before stop, transcript retention in UI/IME/history, repeated unload/reload and measured stop-to-final latency. Correct demonstrated cleanup or propagation defects separately. Leave segmentation controls unchanged until a reproducible measurement justifies changing them.

# Ticket 8: Orukeet no-speech investigation

Issue: https://github.com/Today20092/voice-input/issues/8

Reviewed October 24, 2026 against the app sources at integration base `e8bd6f0` (application baseline `23212c4` plus ticket 19 privacy fixes). The report and all three current comments were read. No new affected-device report, supplied failing audio, or reporter confirmation was present. Status remains **needs-info**, with no demonstrated additional root defect to fix safely.

## Source findings

1. The displayed no-speech message is selected by `RecognizerView.failed` for `NoSpeechRecognizedException`. `RecordingSession.runModelInner` raises that exception when the finalized text is blank and cleanup did not explicitly produce a valid empty result. A native decoder exception follows the generic recognition-failure path instead. The displayed message alone does not identify a capture, VAD, decoder, or cleanup cause.
2. `appendSamples` persists the current PCM chunk and appends the `nRead` samples before VAD is evaluated. WebRTC VAD controls automatic stopping; it does not filter individual silent frames out of Orukeet's waveform. Final-only recognition snapshots exactly `FloatBuffer.position()` samples through `prepareRecordingFinalization`, including the existing 200 ms final-silence pad. This inspection found no path that intentionally supplies only VAD-positive samples to Orukeet.
3. `orukeetBackend()` constructs the shared Sherpa adapter with Orukeet's pinned assets and **128** feature bins. The adapter uses 16 kHz samples, zero dither, CPU execution, `modelType = "nemo_transducer"` and greedy decoding. Every call creates a fresh stream and releases it in `finally`; a mutex serializes load/decode/release. Orukeet does not reuse the Parakeet TDT warm lease: managed construction creates its own adapter.
4. The native runtime setup matches the relevant [Sherpa Orukeet example](https://github.com/k2-fsa/sherpa-onnx/blob/master/python-api-examples/orukeet.md): its export uses the existing NeMo transducer implementation and 128 features. Context7 lookup used `/k2-fsa/sherpa-onnx` on the review date. This is a configuration comparison, not proof that every Poco recording decodes correctly.
5. History retranscription opens the saved PCM without microphone capture or VAD, uses the same managed Orukeet adapter, rejects blank raw results, and retains the previous saved transcript when the attempt fails. It therefore provides a useful discriminator between capture/automatic-stop issues and a reproducible decoder/input issue.

## Relationship to the Phonon fix

Commit `003576cf8b124ae131d1f4e3b8615ee7fd5c0a75`, shipped in v1.4.8, changed **both** live `appendSamples` and history `readSamples` from division by `Short.MAX_VALUE` to division by `32768f`. PCM -32768 now becomes exactly -1 rather than approximately -1.0000305. The shared conversion benefits Orukeet too.

The demonstrated Phonon failure rejected an out-of-range sample before inference. The Orukeet report instead describes an empty/no-speech outcome, and no source evidence establishes that Sherpa applies Phonon's strict input rejection. Consequently this investigation does not call #8 fixed, duplicate, or caused by clipping.

Existing checks cover the conversion through public recording/history boundaries:

- `AudioHistoryStoreTest.fullScalePcmRemainsValidForPhononDuringRetranscription` verifies exact full-scale sample values and successful history replacement. Despite its historical name, the sample conversion is shared by every history recognizer.
- `RecordingSessionFailureTest.fullScaleRecordingSamplesStayWithinRecognizerRange` verifies live full-scale conversion.
- `ParakeetBackendTest.transcribesThroughSpeechBackendAndReleasesDecoder` verifies waveform forwarding and release with a fake decoder.
- `ParakeetSherpaSmokeTest.downloadedOrukeetTranscribesKnownAudioSample` checks real installed Orukeet inference on the JFK fixture. It assumes validated installed assets and can skip when absent; compilation or a skip is not inference evidence.

These tests were inspected, **not executed locally**. No new test or code change is justified without a failing behavior beyond the existing conversion regression.

## Handy comparison

The reporter does not identify the Handy version or model artifact hashes, so its successful Windows result is not a controlled same-input/same-runtime comparison. A bounded source inspection pinned current Handy to `5a9451e70aedc90648ce2befd9d7e0304746f9c1`:

- Its [Cargo manifest](https://github.com/cjpais/Handy/blob/5a9451e70aedc90648ce2befd9d7e0304746f9c1/src-tauri/Cargo.toml) now routes models through `transcribe-cpp` 0.4.0 with platform-specific backend features.
- Its [transcription manager](https://github.com/cjpais/Handy/blob/5a9451e70aedc90648ce2befd9d7e0304746f9c1/src-tauri/src/managers/transcription.rs) has whole-clip batch and finished-recording paths, and explicitly notes that Parakeet can ignore language hints.

That current implementation differs from this app's Android Sherpa runtime, and may differ from the reporter's installed Handy. It supplies no demonstrated drop-in VAD/decoder correction for this report. Replacing the Android runtime, changing model defaults or adding automatic retries would be speculative and could change latency, memory use or output.

## Next evidence and release gate

Retest on v1.4.8 or a later build on the affected Poco X8 Pro/Android 16/HyperOS, keeping model files and audio history intact:

1. Reproduce normal Orukeet dictation and note app build, entry point, headset/routing, approximate failure time, and automatic versus manual stop.
2. Confirm that the failed history recording contains audible, complete speech. Retranscribe that **same** recording with Orukeet and Nemotron; record whether each returns text or fails. No initial audio upload is needed.
3. If history Orukeet succeeds but live capture fails, repeat a controlled manual-stop trial and collect capture/stop evidence. If history Orukeet repeatedly fails while Nemotron succeeds, investigate that exact input/model/runtime combination rather than assuming a microphone fault.
4. Export a user-reviewed standard detailed-mode bug report promptly. Keep transcript-inclusive capture off unless separately authorized. Correlate raw recognition character count, finalization/cleanup outcome, recording duration and stop reason where available; #23 improves these content-free distinctions.

No model download, native/Gradle build, emulator or device inference was started in this investigation, preserving the user's RAM constraint. CI can validate existing host regressions and instrumentation compilation; it cannot reproduce a Poco microphone/runtime issue without an affected device and input. Keep #8 open until reporter confirmation or reproducible affected-device evidence supports a disposition.

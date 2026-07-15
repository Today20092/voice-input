# Parakeet backend implementation research

Research date: 2026-07-15

Scope: primary-source review of NVIDIA Parakeet TDT 0.6B v3, the upstream `transcribe-rs` ONNX implementation, comparable open-source ONNX implementations, and ONNX Runtime guidance relevant to Android. This report intentionally separates sourced facts from implementation inferences. The local Android/JNI audit is left to the companion code review.

## Executive findings

1. **The most important decoder issue is that current upstream `transcribe-rs` does not use the TDT duration output.** Its Parakeet decoder truncates the joint output to vocabulary logits and advances one encoder frame on blank (or after ten emitted tokens). NVIDIA defines TDT specifically as jointly predicting a token and a duration so decoding can skip frames. `onnx-asr` and `parakeet-rs` both split the same exported joint output at `vocab_size`, choose the duration, and advance by it. This is evidence of a real implementation gap, not merely a tuning preference.
2. **Cold start is expected when sessions are built on demand.** Parakeet loading constructs three ONNX Runtime sessions (preprocessor, encoder, decoder/joint), and current upstream `transcribe-rs` enables Level 3 graph optimization while committing each model. ONNX Runtime says applying optimizations at every session initialization adds startup overhead, especially for complex models. Keeping sessions alive is the lowest-risk way to avoid paying that work on the first utterance.
3. **Do not assume NNAPI or XNNPACK will beat the CPU provider for this INT8 export.** ONNX Runtime's mobile guidance says to start with the CPU provider for quantized models. NNAPI gains are model- and device-specific; unsupported partitions and data transfers can make it slower. Benchmark representative phones before shipping an execution-provider change.
4. **This export path is final-utterance recognition, not native cache-aware streaming.** Current `transcribe-rs` explicitly declares Parakeet streaming unsupported. NVIDIA documents chunked inference for this checkpoint with left/right context, but that is not the same thing as preserving encoder/decoder caches across microphone chunks. A real streaming rewrite is a separate project and is not required to fix the reported rare missing-final-result failure.
5. **An empty transcript is a valid decoder outcome and must not be confused with a failed finalization path.** Greedy TDT/RNN-T can emit only blanks, and the reviewed libraries return an empty string for zero emitted tokens. The app therefore needs explicit observability and exactly-once completion for three distinct outcomes: non-empty transcript, no-speech/all-blank, and error/cancellation.

## Model and decoding semantics

### Evidence

- NVIDIA describes Parakeet TDT 0.6B v3 as a 600-million-parameter FastConformer/TDT model with punctuation, capitalization, timestamps, and automatic language detection across 25 European languages. The checkpoint accepts 16 kHz mono audio in the documented examples. [NVIDIA model card](https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3)
- TDT jointly predicts a token and the number of input frames covered by it. During inference, the predicted duration permits skipping frames; NVIDIA reports up to 2.82x faster ASR inference than a conventional transducer in the original TDT work. [NVIDIA Research: Efficient Sequence Transduction by Jointly Predicting Tokens and Durations](https://research.nvidia.com/publication/2023-04_efficient-sequence-transduction-jointly-predicting-tokens-and-durations)
- NVIDIA's greedy TDT API takes the blank index, the allowed duration list, and an optional maximum-symbols-per-step guard. The blank index must be the vocabulary length for the NeMo TDT representation. [NeMo TDT decoding API](https://docs.nvidia.com/nemo-framework/user-guide/25.02/nemotoolkit/asr/api.html#tdt-decoding)
- Current upstream `transcribe-rs` loads three sessions, then its greedy loop slices the joint output to the vocabulary portion, selects a token, and advances exactly one frame on blank or after ten symbols. It does not read or apply the appended duration logits. It also declares `supports_streaming: false`. [Current `transcribe-rs` Parakeet source](https://github.com/cjpais/transcribe-rs/blob/main/src/onnx/parakeet/mod.rs)
- `onnx-asr` implements the same exported NeMo TDT structure by returning `output[:vocab_size]` as token logits and `argmax(output[vocab_size:])` as the duration step. Its shared transducer loop advances by that positive duration; otherwise it advances on blank or the maximum-symbol guard. [NeMo model adapter](https://github.com/istupakov/onnx-asr/blob/main/src/onnx_asr/models/nemo.py), [shared transducer loop](https://github.com/istupakov/onnx-asr/blob/main/src/onnx_asr/asr.py)
- `parakeet-rs` independently splits token and duration logits and advances `t` by the selected duration. It also keeps the ten-symbol safety limit. [TDT model source](https://github.com/altunenes/parakeet-rs/blob/master/src/model_tdt.rs)
- `onnx-asr` reports Parakeet v2/v3 at 36x real time on a Ryzen 9800X3D but only 1.0x on Cortex-A53 CPU. These are project benchmarks, not Android guarantees, but they demonstrate the large hardware dependence. [onnx-asr repository benchmarks](https://github.com/istupakov/onnx-asr#benchmarks)

### Inference

Ignoring duration logits is likely a material performance loss because it forces a decoder/joint invocation at every encoder frame instead of taking the model's learned skips. It may also diverge from NeMo's intended greedy alignment. It is not enough, by itself, to prove the rare no-output report: an all-blank result, a lifecycle/callback failure, an exception, or an audio-buffer race can each produce the same user-visible symptom.

### Recommended action

Fix duration-aware greedy decoding before experimenting with more invasive execution providers. The minimum change is to:

1. split the joint output at the vocabulary boundary;
2. choose the token and duration argmax values independently;
3. preserve decoder state only for a non-blank token;
4. advance by a positive duration, otherwise use the existing blank/ten-symbol fallback; and
5. use the encoder-reported valid length rather than padded tensor capacity as the loop bound.

Verify against a fixed speech WAV by comparing text and token timing with `onnx-asr` or NeMo, and record decoder/joint invocation count before and after. This one test detects both a transcript regression and failure to use duration skips.

## Cold-start latency and model lifetime

### Evidence

- Current upstream `transcribe-rs` creates a separate ONNX Runtime session for the encoder, decoder/joint, and ONNX preprocessor. Its shared session builder requests Level 3 graph optimization and parallel graph execution. [Parakeet loader](https://github.com/cjpais/transcribe-rs/blob/main/src/onnx/parakeet/mod.rs), [`transcribe-rs` session builder](https://github.com/cjpais/transcribe-rs/blob/main/src/onnx/session.rs)
- ONNX Runtime states that online graph optimization occurs during inference-session initialization and can add important startup overhead for complex models. It supports serializing an optimized graph for later startup with optimizations disabled, but warns that offline output must match the target execution provider, options, and compatible hardware. [ONNX Runtime graph optimizations](https://onnxruntime.ai/docs/performance/model-optimizations/graph-optimizations.html)
- Current upstream `transcribe-rs` added 250 ms of **leading** silence for Parakeet in March 2026 because mel-spectrogram windowing could drop the beginning of audio. The change is about recognition quality at utterance start, not session warm-up and not trailing finalization. [Upstream padding commit](https://github.com/cjpais/transcribe-rs/commit/25bbda1291170a823f1fbb77634c91016d4f5706)

### Inference

- If the app starts loading only after recording begins or after Stop, session construction will be perceived as a slow start or slow final response. Reusing a successfully created engine is the smallest effective optimization.
- Pre-optimizing the model could reduce session construction time, but distributing one hardware-specific optimized graph across heterogeneous Android phones is risky. A per-device cache created after download is possible, yet it increases storage, first-run complexity, and invalidation work. It should follow measurement, not precede it.
- A small warm-up inference can move lazy kernel allocation out of the first real utterance, but it adds CPU/battery work. Measure load time separately from first inference before deciding whether it is needed.

### Recommended action

Instrument these boundaries with monotonic timestamps and a request ID:

- engine acquire requested;
- each of the three ORT sessions committed;
- engine ready;
- audio snapshot finalized (sample count and speech/VAD duration, not audio content);
- preprocessor start/end;
- encoder start/end;
- decoder start/end and joint-call count;
- final callback delivered with outcome `text`, `no_speech`, `error`, or `cancelled`.

Then keep the engine warm across ordinary dictation sessions. Tune idle eviction only after collecting memory-pressure and reload data. Offline optimized models or warm-up inference should be added only if session creation or first-run allocation remains a measured bottleneck.

## ONNX Runtime execution, threading, and Android

### Evidence

- ONNX Runtime defaults to sequential graph execution and all graph optimizations. It says parallel graph execution may help graphs with many branches but can hurt graphs without them. The default intra-op pool uses physical cores, and spinning trades power for lower inference latency. [ONNX Runtime thread management](https://onnxruntime.ai/docs/performance/tune-performance/threading.html)
- The current upstream `transcribe-rs` builder explicitly enables parallel execution. It leaves CPU intra-op thread count at the runtime default unless configured through another path. [`transcribe-rs` session builder](https://github.com/cjpais/transcribe-rs/blob/main/src/onnx/session.rs)
- ONNX Runtime's current mobile guide recommends starting with the CPU provider for a quantized model and XNNPACK for a non-quantized model. It warns that NNAPI/CoreML performance is device- and model-specific, and fragmented provider partitions can lose to CPU because of transfer overhead. [Deploy ONNX Runtime on mobile](https://onnxruntime.ai/docs/tutorials/mobile/)
- XNNPACK owns a separate thread pool. ONNX Runtime recommends one non-spinning ORT intra-op thread plus an XNNPACK pool sized around physical cores when XNNPACK owns the heavy operators; if unsupported heavy nodes fall back to CPU, other settings may win and must be measured. [XNNPACK execution provider](https://onnxruntime.ai/docs/execution-providers/Xnnpack-ExecutionProvider.html)
- NNAPI can use Android accelerators, but its CPU fallback may be slower than ORT's optimized CPU kernels. ONNX Runtime provides a flag to disable NNAPI CPU devices on Android 10+, allowing unsupported work to return to ORT. [NNAPI execution provider](https://onnxruntime.ai/docs/execution-providers/NNAPI-ExecutionProvider.html)
- ONNX Runtime notes that ARM processors with dot-product instructions can benefit from INT8, while quantization overhead can make older hardware slower. [ONNX Runtime quantization guide](https://onnxruntime.ai/docs/performance/model-optimizations/quantization.html)

### Inference and experiment order

1. Benchmark the existing INT8 CPU provider first.
2. Compare sequential versus parallel graph execution. The encoder is mostly a deep serial stack; parallel graph scheduling may add overhead.
3. Sweep a small set of intra-op counts (for example 2, 4, and runtime default) on low-, mid-, and high-tier ARM64 phones. Record end latency, CPU time, thermals, and battery—not only best-case speed.
4. Only then test NNAPI. Record provider node assignment and reject configurations that produce many small partitions.
5. Do not add XNNPACK merely because it is mobile-oriented; confirm that the INT8 model's expensive operators are actually claimed.

Avoid a broad device-specific tuning system until the measurements show one static configuration is insufficient.

## Streaming and stop/finalization semantics

### Evidence

- NVIDIA's model card gives a chunked-inference example for this checkpoint with 2-second chunks, 10 seconds of left context, and 2 seconds of right context. [NVIDIA model card, chunked inference](https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3#how-to-use-this-model)
- NeMo documents cache-aware streaming as a distinct FastConformer capability with configurable latency/accuracy tradeoffs. It is not automatically provided by any arbitrary full-context ONNX export. [NeMo ASR overview](https://docs.nvidia.com/nemo/speech/nightly/asr/intro.html)
- Current upstream `transcribe-rs` marks Parakeet streaming unsupported and performs preprocessing and full encoder inference over the submitted sample buffer. [Current `transcribe-rs` Parakeet source](https://github.com/cjpais/transcribe-rs/blob/main/src/onnx/parakeet/mod.rs)
- In `onnx-asr`, zero emitted tokens decode to an empty token list and empty string; there is no backend error simply because the result is blank. [onnx-asr decoding source](https://github.com/istupakov/onnx-asr/blob/main/src/onnx_asr/asr.py)

### Inference

The Stop button should be treated as a transaction boundary: freeze one immutable audio snapshot, run exactly one final decode, and deliver exactly one terminal result. A rare absence of typed text cannot be diagnosed from an empty UI alone because the following cases are observably identical without telemetry:

- the captured buffer is empty or too short;
- VAD removed all speech or the model emitted only blanks;
- stop raced with capture/buffer publication;
- engine acquisition/unload raced;
- ORT/JNI threw and the coroutine exited before completion dispatch;
- cancellation won after decode but before text commit; or
- a valid empty string was silently treated as success.

### Recommended action

Use an exactly-once completion path (typically `try/finally` around engine work) that always reports one typed terminal outcome. Keep empty text as `no_speech`, not success. Include request ID, state transition, audio sample count, decoder token count, and exception class in local logs. Do not log microphone content.

Adding arbitrary trailing silence is not yet supported by the reviewed primary sources as a fix for this symptom. It is a reasonable controlled experiment only if telemetry shows short tail clipping or all-blank results near Stop. NVIDIA's documented right context applies to chunked inference, while the upstream Rust padding change prepends silence to protect the beginning of speech.

## Local code-audit findings

- **High confidence: native failures can produce the reported silent no-output state.** JNI turns load/transcribe errors into Java exceptions, but `AudioRecognizer.runModel()` catches only `OutOfMemoryError`. Any ordinary ORT/JNI exception ends the coroutine before `recognitionCompleted` or `finished(text)`, leaving no typed result or error callback. See `parakeet-native/src/lib.rs` and `AudioRecognizer.kt` lines 690-732.
- **High confidence: the idle timer has an acquire/unload race.** `ParakeetEngineManager.acquire()` uses its Kotlin mutex, but the delayed job calls `ParakeetNative.unloadIfIdle()` before taking that mutex. Cancellation can arrive after the delay has completed, allowing a just-reacquired native engine to be closed before the next transcription. The resulting native "model is not loaded" exception then follows the silent path above. See `ParakeetEngineManager.kt` lines 17-44.
- **High confidence: cold loading overlaps recording and a quick Stop waits for it.** Parakeet model verification completes without loading in `create()`. `startRecording()` starts capture first and only then launches `loadModel()`; `runModel()` joins that job. Loading three ORT sessions can therefore compete with capture and appear as slow start or slow post-Stop processing. See `AudioRecognizer.kt` lines 311-345, 580-590, and 650-664.
- **High confidence: the local decoder deliberately uses frame-step TDT.** It slices away duration logits and advances one frame on blank/guard, so it does not obtain TDT duration-skipping speedups. Commit `cd89271` made this choice to preserve final transcripts and reduce cut-off endings. Do not simply flip the enum: the local duration path advances only on blank/guard and needs a reference-output regression test first.
- **Medium confidence: an input-connection loss can also look like no output.** The IME obtains `currentInputConnection` only when delivering the result and has no durable retry/fallback if it disappeared while decoding. This is separate from Parakeet and should be logged as a distinct terminal outcome.
- **Ruled down: Stop-buffer publication is reasonably ordered.** The model job joins `recorderJob` before snapshotting `floatSamples`, and the capture job drains the recorder tail, appends 200 ms final silence, then releases it. Tail tuning may affect clipped words, but it is not the first explanation for a completely missing callback.
- **Observability gap:** `PARAKEET_ENGINE_DIAGNOSTICS` is declared but unused. There are no request-correlated cold-load, decode, terminal-outcome, token-count, or exception measurements.

## Ranked implementation plan

1. **Add outcome/timing telemetry and exactly-once final completion.** This directly makes the rare failure diagnosable and prevents exceptions from becoming silent UI hangs.
2. **Implement TDT duration-aware decoding and a fixed-WAV regression/benchmark.** This aligns the decoder with NVIDIA's architecture and comparable implementations.
3. **Serialize idle unload with acquire/use and keep successful sessions warm.** This closes a verified rare-failure window and reduces repeated cold starts with a small lifecycle change.
4. **Upgrade or selectively port the upstream leading-padding change** if the local fork lacks it and clipped utterance beginnings are observed.
5. **Benchmark sequential execution and bounded intra-op thread counts on representative phones.** Ship only a measured improvement.
6. **Evaluate NNAPI or offline-optimized graphs only if CPU/session reuse still misses latency goals.** Both add device-specific complexity and are not justified by source evidence alone.
7. **Defer true cache-aware streaming.** It is a larger model/export/state-management change and is unnecessary for resolving a rare missing final callback.

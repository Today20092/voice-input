# Offline Streaming ASR Options for Android

Research date: 2026-07-15

## Recommendation

Add **Moonshine Medium Streaming** as an accuracy-focused option and retain Small Streaming as the balanced option. It is the lowest-risk improvement because the app's existing `moonshine-voice` 0.0.68 Android library already exposes `MOONSHINE_MODEL_ARCH_MEDIUM_STREAMING`; only model selection, download metadata, and settings UI need to change.

If Medium still performs poorly on the user's real speech, prototype **NVIDIA Nemotron ASR Streaming 0.6B through sherpa-onnx** as an experimental backend. Do not replace Moonshine with it without an on-device A/B test.

## Comparison

| Model | True streaming | Published accuracy | Download / footprint | Android practicality | Verdict |
|---|---:|---|---|---|---|
| Moonshine Small Streaming (current) | Yes | 7.84% OpenASR average; shipped INT8 3.03% LibriSpeech clean | About 245 MB | Already integrated | Balanced baseline |
| Moonshine Medium Streaming | Yes | 6.65% OpenASR average; shipped INT8 2.37% LibriSpeech clean | 428.6 MiB across official quantized ORT files | Same Android SDK and backend | Best next step |
| NVIDIA Nemotron ASR Streaming 0.6B | Yes, cache-aware RNNT | 7.07% OpenASR average at 560 ms; 6.93% at 1120 ms | About 632 MB for sherpa-onnx INT8 | Official sherpa-onnx Android APK/model exists; new runtime integration | Best experimental alternative |
| NVIDIA Parakeet Realtime EOU 120M | Yes | 9.30% OpenASR average at 160 ms | Roughly 120M parameters | NeMo-first; no punctuation/capitalization | Faster endpointing, not an accuracy upgrade |
| sherpa-onnx streaming Zipformer English | Yes | Competitive LibriSpeech results, but older/narrower evaluation | Around 181 MB INT8 for the 2023 English model | Mature Android/Kotlin support | Fast and small, not a demonstrated robustness upgrade |
| Vosk small English | Yes | 9.85% LibriSpeech clean | 40 MB; about 300 MB runtime RAM | Mature Android support | Clearly less accurate |
| Whisper / faster-whisper / whisper.cpp | Buffered or chunked pseudo-streaming | Strong offline models at larger sizes | Varies | Final re-decode is possible, but true low-latency streaming is not its strength | Use only as a second-pass finalizer |

## Why Medium Streaming

Moonshine reports a reduction from 7.84% to 6.65% average WER across the eight OpenASR datasets, about a 15% relative error reduction. On the shipped quantized models' LibriSpeech-clean test, Medium scores 2.37% versus Small's 3.03%, about a 22% relative reduction. The official CPU response-latency comparison reports 107 ms versus 73 ms on a MacBook Pro and 802 ms versus 527 ms on Raspberry Pi 5. These are endpoint-response measurements rather than Android end-to-end latency, so a real phone test is still required.

Medium uses the same streaming architecture and Android library already in the app. The official quantized files total 428.6 MiB, versus roughly 245 MB for Small. The likely tradeoff is approximately twice the model compute and substantially more RAM, but the integration risk is much lower than adding another inference runtime.

Sources:

- [Moonshine Voice benchmarks, quantized accuracy, Android support, and licensing](https://github.com/moonshine-ai/moonshine)
- [Moonshine Streaming Medium model card](https://huggingface.co/UsefulSensors/moonshine-streaming-medium)
- [Moonshine v2 paper](https://download.moonshine.ai/docs/moonshine_streaming_paper.pdf)

## Nemotron as the challenger

NVIDIA's 600M-parameter model is genuinely streaming: its cache-aware FastConformer-RNNT processes non-overlapping chunks and supports 80, 160, 560, and 1120 ms operating points. It includes punctuation and capitalization. NVIDIA reports 7.07% average WER at 560 ms and 6.93% at 1120 ms.

Sherpa-onnx provides an INT8 conversion, Kotlin/Java Android support, and a prebuilt arm64 Android APK. Its 560 ms model contains a 623 MB encoder plus small decoder/joiner files; the documented example has RTF 0.16 on the test host, but that is not an Android phone benchmark. This makes Nemotron credible, but larger and riskier than Moonshine Medium.

Sources:

- [NVIDIA Nemotron ASR Streaming model card and WER tables](https://huggingface.co/nvidia/nemotron-speech-streaming-en-0.6b)
- [Sherpa-onnx Nemotron streaming ONNX and Android documentation](https://k2-fsa.github.io/sherpa/onnx/nemo/nemotron-streaming.html)
- [Sherpa-onnx platform support](https://github.com/k2-fsa/sherpa-onnx)

## Other options

Parakeet Realtime EOU 120M is optimized for low-latency endpoint detection, but its published 9.30% average WER is worse than Moonshine Small's published 7.84%, and it omits punctuation and capitalization. Vosk's Android-sized English model reports 9.85% on LibriSpeech clean, far behind Moonshine Small's shipped 3.03% on that dataset. Picovoice Cheetah is a polished proprietary Android streaming SDK with real vocabulary boosting, but requires an account/access key and does not provide enough directly comparable current benchmark data to justify replacing an open offline backend.

Sources:

- [NVIDIA Parakeet Realtime EOU 120M model card](https://huggingface.co/nvidia/parakeet_realtime_eou_120m-v1)
- [Vosk model sizes and WER](https://alphacephei.com/vosk/models)
- [Picovoice Cheetah Android documentation](https://picovoice.ai/docs/quick-start/cheetah-android/)

## Proposed evaluation

Ship Small and Medium as selectable Moonshine quality levels. Record a small private test set on the target phone containing the phrases that currently fail, then calculate exact substitutions/deletions/insertions for both models. Only invest in Nemotron/sherpa-onnx if Medium does not materially improve those samples.

An optional two-pass mode can preserve live Moonshine text while replacing the final result with Parakeet after recording stops. That may produce the best final accuracy, but it is not true single-model streaming and adds finalization delay.

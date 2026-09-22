# Parakeet TDT v3 vs Parakeet Unified EN

Research date: 2026-07-25

## Short answer

They are different NVIDIA checkpoints, not two names for the same model.

| Model | What the name means | Best fit in this app |
|---|---|---|
| **Parakeet TDT 0.6B v3** | About 600M parameters; a FastConformer model with a **Token-and-Duration Transducer** decoder; third checkpoint generation | Accuracy-focused, multilingual transcription. The app's Sherpa-ONNX integration returns final text after recording stops. |
| **Parakeet Unified EN 0.6B** | About 600M parameters; an English FastConformer + RNN-T checkpoint jointly trained for both full-context offline and limited-context streaming inference | Live English partial text. The app installs the 560 ms buffered-streaming export. |

“Unified” means that one checkpoint shares parameters between **offline and streaming modes**. It does not mean multilingual. NVIDIA describes TDT v3 as covering 25 European languages with automatic language detection, while Unified EN is English-only. [NVIDIA TDT v3 model card](https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3), [NVIDIA Unified EN model card](https://huggingface.co/nvidia/parakeet-unified-en-0.6b)

## What TDT means

TDT stands for **Token-and-Duration Transducer**. It extends a conventional transducer by predicting both the next token and how many input frames that token covers. The decoder can then skip frames instead of repeatedly producing blank predictions, reducing decoding work. “v3” identifies the checkpoint generation; it is not a streaming mode. [NVIDIA TDT research](https://research.nvidia.com/labs/conv-ai/publications/2023/2023-token-duration-transducer/), [NeMo checkpoint glossary](https://docs.nvidia.com/nemo/speech/nightly/asr/asr_checkpoints.html)

TDT v3 can still be run over successive overlapping chunks—NVIDIA publishes a chunked inference example—but that is separate from the TDT decoder itself. In this app, the selected TDT ONNX package is deliberately exposed as final-only; that is an integration choice, not a claim that every possible TDT runtime must be batch-only. [NVIDIA TDT v3 streaming example](https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3#streaming-with-parakeet-models), [local TDT catalog metadata](../../app/src/main/java/org/futo/voiceinput/parakeet/ParakeetModel.kt)

## What “buffered live recognition that recomputes left context” means

Picture the audio as:

```text
[recent audio already heard] [new chunk] [short look-ahead]
          left context         chunk       right context
```

For every live update, buffered inference sends that whole window through the encoder again. The old left-context audio helps the model interpret the new words and may revise the partial transcript, but recomputing it repeats work.

A **cache-aware** streaming model instead retains neural state/features from earlier audio and mainly processes the new audio. That is generally more computationally efficient. NVIDIA explicitly says the current Unified inference pipeline is buffered streaming, recomputes left context for each chunk, and can have more latency than cache-aware streaming. [NVIDIA Unified EN model card](https://huggingface.co/nvidia/parakeet-unified-en-0.6b)

NVIDIA defines displayed latency as **chunk size + right context** and recommends a 5.6-second left context. Its published configurations range from 2.08 seconds down to 160 ms. The package selected in this app is the 560 ms export; “560 ms” is the 160 ms chunk plus 400 ms right context, not the total amount of audio processed on every update. [NVIDIA streaming configuration table](https://huggingface.co/nvidia/parakeet-unified-en-0.6b#setting-up-streaming-configuration), [local Unified catalog metadata](../../app/src/main/java/org/futo/voiceinput/recognition/RecognitionModelCatalog.kt)

## Practical choice

- Choose **TDT v3** when multilingual coverage and the app's accuracy-focused final result matter more than seeing words while speaking.
- Choose **Unified EN** when English live partials matter and some repeated computation is acceptable.
- Choose a **cache-aware streaming model such as Nemotron** when the quickest, most efficient live updates are the priority; that is why the app's catalog recommends Nemotron for lower-latency streaming.

Do not equate “live,” “buffered streaming,” and “cache-aware streaming.” All can display text before recording ends, but they reuse past computation differently.

# Liquid AI for on-device transcript rewriting

Research date: 2026-07-15

## Recommendation

Prototype the feature, but do not automatically rewrite every transcript. Start with **LFM2.5-350M Q4_K_M via llama.cpp**, make the model an optional download, and expose explicit actions such as Grammar, Shorter, Professional, Bullets, Email, and Text message. Preserve the original transcript and provide undo.

Do not begin with LFM2.5-230M as the sole production model: it is smaller and faster, but Liquid positions it primarily for extraction and lightweight agentic work. Do not begin with the 1.2B or 2.6B models: their extra quality is likely outweighed by download size and peak-memory pressure beside Parakeet until device benchmarks prove otherwise.

## Candidate models

| Model | Q4_K_M size | Fit |
|---|---:|---|
| LFM2.5-230M | 153 MB | Useful low-end experiment; Liquid reports 213 tok/s on a Galaxy S25 Ultra, but does not recommend it for creative writing. |
| LFM2.5-350M | 229 MB in the current official repository listing | Best first prototype: instruction-tuned, multilingual, 32K context, and Liquid reports under 1 GB memory plus 188 tok/s on Snapdragon Gen 4. |
| LFM2.5-1.2B-Instruct | 731 MB | Better instruction following, but much larger and likely to create RAM pressure when Parakeet remains resident. |
| LFM2-2.6B-Transcript | Not recommended | Specialized for English 30–60 minute meeting summarization and claims under 3 GB RAM; it is the wrong task and size for short IME rewrites. |

Sources: [230M model card](https://huggingface.co/LiquidAI/LFM2.5-230M), [230M GGUF files](https://huggingface.co/LiquidAI/LFM2.5-230M-GGUF), [350M model card](https://huggingface.co/LiquidAI/LFM2.5-350M), [350M GGUF files](https://huggingface.co/LiquidAI/LFM2.5-350M-GGUF/tree/main), [1.2B model card](https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct), [1.2B GGUF files](https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct-GGUF/tree/main), and [2.6B Transcript model card](https://huggingface.co/LiquidAI/LFM2-2.6B-Transcript).

## Runtime choice

Liquid officially supports GGUF/llama.cpp, ONNX Runtime, and its LEAP SDK. For this project, llama.cpp is the least risky prototype path because it is open source, supports the official Q4 models, and keeps Android 8–11 compatibility possible.

The current LEAP Android slogan example uses SDK 0.10.7 and requires Android 12/API 31, target API 36, and Kotlin 2.3. This app currently has minSdk 26 and targetSdk 35, so adopting that LEAP configuration would exclude Android 8–11 and require build upgrades. LEAP SDK licensing must also be reviewed separately from the MIT-licensed example repository. [Liquid Android example](https://docs.liquid.ai/examples/android/slogan-generator), [official LEAP examples](https://github.com/Liquid4All/LeapSDK-Examples), and [Liquid deployment FAQ](https://docs.liquid.ai/lfm/help/faqs).

The existing ONNX Runtime dependency is not sufficient by itself: text generation also needs tokenization, sampling, stopping, and KV-cache management. ONNX Runtime GenAI supplies those pieces, but its published architecture support list does not currently name LFM2, so compatibility should not be assumed. [ONNX Runtime GenAI](https://github.com/microsoft/onnxruntime-genai).

## Product constraints

- Run rewriting only after the user selects an action; never silently replace dictated text.
- Keep the raw transcript and one-tap undo because a language model can change names, numbers, negation, or intent.
- Use low-temperature generation, output-only prompts, strict token limits, and instructions to preserve names, numbers, and meaning.
- Avoid holding Parakeet and the rewrite model resident together on low-memory devices. The prototype should measure sequential unload/load versus concurrent residency.
- Make the rewrite model optional and separately downloadable; the base voice-input download should not grow by hundreds of megabytes.
- Benchmark exact transcript transformations on representative arm64 devices before choosing 230M, 350M, or 1.2B.

## License

The weights use the **LFM Open License v1.0**, not Apache or MIT. It permits redistribution with license/notice obligations, but commercial use by a legal entity with annual revenue of at least USD 10 million is outside the grant. Shipping therefore needs a license review, particularly if changes are intended for upstream FUTO distribution. [Official LFM license](https://huggingface.co/LiquidAI/LFM2.5-350M/blob/main/LICENSE).

# Cohere Transcribe beta

This beta adds Cohere Transcribe as an optional recognition model. Orukeet remains the default, and existing model selections are preserved.

## Install and try it

1. Download the APK attached to this prerelease and install it. It uses the existing Moonshine fork app ID and release signature, so it updates that app while retaining its settings and downloaded models.
2. Open **Model Options** and select **Cohere Transcribe (Beta)**. Confirm the separate model download, about **2.89 GB**. The APK does not contain the weights.
3. Under the Cohere model, choose **Recognition language**, such as Arabic or English. The Languages page also changes this same setting when Cohere is selected.
4. Dictate and stop recording. Cohere returns final text after recording stops. It does not provide live transcription.

The model supports Arabic, Mandarin Chinese, Dutch, English, French, German, Greek, Italian, Japanese, Korean, Polish, Portuguese, Spanish, and Vietnamese. Language selection is explicit, with English initially selected. There is no automatic language detection. Mixed-language speech can be inaccurate.

## Beta limitations and feedback

Phone speed, peak memory, and accuracy have not been measured locally. This 2B model uses substantial memory and may load slowly or be stopped by Android on devices with limited memory. It is unloaded when the recognition session releases it. Longer recordings are transcribed in consecutive chunks of up to 35 seconds, preferring quiet boundaries; words around boundaries need particular attention during testing.

Please report the phone model, Android version, RAM, selected language, approximate recording length, load/recognition delay, and whether any words were missing or incorrect. For Arabic, include the dialect and whether the recording mixed Arabic and English. Use nonsensitive examples when sharing transcripts.

Local builds and tests were intentionally skipped at the user's request. GitHub Actions runs the release unit tests and builds and verifies the signed ARM64 APK. Passing CI does not verify on-device inference.

## Model provenance

The model uses the existing Sherpa-ONNX 1.13.4 runtime. Model assets come from [the k2-fsa converter's pinned Hugging Face revision](https://huggingface.co/csukuangfj2/sherpa-onnx-cohere-transcribe-14-lang-int8-2026-04-01/tree/156a470cf08eefe706a0004f3c52d9ee567ca7a0), with size and SHA-256 validation for every file, including `encoder.int8.onnx.data`. The direct-file download totals 2,888,052,036 bytes; the earlier 1.70 GB research figure referred to the compressed archive.

Cohere Transcribe is licensed under Apache 2.0. See the [publisher's model card](https://huggingface.co/CohereLabs/cohere-transcribe-03-2026) and [research assessment](../research/cohere-transcribe-assessment.md).

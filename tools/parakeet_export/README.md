# Parakeet Unified English ONNX Export

This directory is the conversion gate for `nvidia/parakeet-unified-en-0.6b`.
The Android app consumes exported ONNX assets from:

```text
artifacts/parakeet-unified-en-0.6b-onnx/
```

Run with `uv`:

```powershell
cd tools/parakeet_export
uv run export_unified_en_to_onnx.py
```

Expected output:

```text
artifacts/parakeet-unified-en-0.6b-onnx/
  config.json
  vocab.txt
  encoder-model.onnx
  decoder_joint-model.onnx
  preprocessor.onnx
  manifest.json
  checksums.sha256
```

If int8 quantization is validated, rename or emit the quantized encoder and
decoder as:

```text
encoder-model.int8.onnx
decoder_joint-model.int8.onnx
```

After export, fill the SHA-256 values in `app/src/main/java/org/futo/voiceinput/parakeet/ParakeetModel.kt`
and `app/build.gradle`, then host the exact files at the configured artifact
URL. The app intentionally rejects unverified downloaded files.

Acceptance checks before Android integration:

- ONNX Runtime CPU EP loads all exported models.
- A short English sample transcribes successfully through `transcribe-rs`.
- Output preserves punctuation and capitalization.
- `manifest.json` records the source model, revision, NeMo version, opset,
  sample rate, vocabulary size, architecture, export timestamp, and hashes.
- Peak memory during load and decode is measured on desktop before testing on
  Android.

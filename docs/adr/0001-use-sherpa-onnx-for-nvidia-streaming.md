# Replace the custom NVIDIA runtime with Sherpa-ONNX

Use Sherpa-ONNX as the sole Android runtime for NVIDIA recognition models because it supports Parakeet TDT, buffered Parakeet Unified, cache-aware Nemotron English, and multilingual Nemotron 3.5 with maintained Kotlin APIs and compatible INT8 artifacts. Stage the replacement by proving Nemotron English live transcription, migrating TDT and Unified after parity checks, and then deleting the redundant Rust/JNI runtime and obsolete ONNX Runtime build wiring; this accepts a larger APK in exchange for one maintained NVIDIA inference stack.

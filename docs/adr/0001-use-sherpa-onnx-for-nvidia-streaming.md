# Replace the custom NVIDIA runtime with Sherpa-ONNX

October 30, 2026 update: the owner selected [full FUTO recognizer adoption](../specs/full-futo-recognizer-adoption.md),
including its native runtime path, as the migration target. This supersedes the
sole-Sherpa requirement for the intended architecture. The existing Sherpa
implementation remains the production baseline until source availability,
model compatibility and feature-parity gates pass; no runtime has yet been
replaced by this decision. The original decision below describes that baseline.

Use Sherpa-ONNX as the sole Android runtime for NVIDIA recognition models because it supports Parakeet TDT, buffered Parakeet Unified, cache-aware Nemotron English, and multilingual Nemotron 3.5 with maintained Kotlin APIs and compatible INT8 artifacts. Stage the replacement by proving Nemotron English live transcription, migrating TDT and Unified after parity checks, and then deleting the redundant Rust/JNI runtime and obsolete ONNX Runtime build wiring; this accepts a larger APK in exchange for one maintained NVIDIA inference stack.

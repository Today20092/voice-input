# Moonshine runtime in 1.4.11 beta

Normal builds use the tested Moonshine Voice 0.0.68 AAR with only its ARM64
ONNX Runtime library replaced. There is no Moonshine API or recognition-model
upgrade. ONNX Runtime 1.23.0 was rebuilt at
`be835efc56aca19b8e810538ec93c8e150e0fc61` with KleidiAI disabled, NNAPI exports
retained and 16 KiB LOAD alignment. Its original versioned ABI is preserved.

The [candidate build and validation](https://github.com/Today20092/voice-input/actions/runs/40140732558)
and [durable artifact/provenance release](https://github.com/Today20092/voice-input/releases/tag/experiment-issues-40-41-7a6c546)
record the source, build flags, native checks and licenses. The normal Gradle
dependency downloads the exact AAR and verifies SHA-256
`b43d0fb31657e1984b7c6513578dc4a71a2a8a4b581683dc9d0bb27b221d45c7`.
Its ARM64 `libonnxruntime.so` SHA-256 is
`cbe4d71990f1032bcc7b4b7baf3e0b18327af6c25ba4bf106b5ecd42ddf22d30`.
AGP preserves those already debug-stripped bytes; release packaging checks the
exact payload and its ELF/SME properties.

The AAR file dependency does not carry Maven transitive metadata. The existing
AppCompat 1.6.1 dependency is retained, and Material 1.10.0 preserves the original
Moonshine POM's runtime dependency floor.

The build also downloads and hash-checks these assets, which are packaged into
the APK without platform-dependent line-ending changes:

- `MOONSHINE-LICENSE.txt`: full license from Moonshine tag v0.0.68, commit
  `0a99f2e31e6e2913c4c61e4c62f85ce57404ba60`, SHA-256
  `6148d7574a6554b7379b633cfd4c4fe5840c3f548d13bc83e00b52dc6fa00abd`.
  Section 1's MIT license covers the runtime and English models used here;
  later sections describe other-language models not included by this change.
- `ONNX-RUNTIME-LICENSE.txt`: the exact rebuilt runtime's MIT license,
  SHA-256 `2f07c72751aed99790b8a4869cf2311df85a860b22ded05fa22803587a48922c`.
- `ONNX-RUNTIME-THIRD-PARTY-NOTICES.txt`: its complete third-party notices,
  SHA-256 `e9e90971a8e75a9a8ac0c6412e29c1202d079998389915aa485f46c816c3b4cc`.

Credits links to the pinned Moonshine license and ONNX Runtime notices. Runtime
benchmark identity identifies the no-KleidiAI build. The explicit experimental
AAR override remains available and is labeled as an override rather than as the
normal verified runtime.

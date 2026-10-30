# Upstream share branch compared with our fork

Follow-up: [the complete adoption review](upstream-share-adoption-review-2026-09-26.md) reviews all 35 commits, distinguishes keyboard provider switching from shared code, and links implementation/evaluation tickets. It recommends retaining our architecture. The deeper review also found that the previously completed manual-update implementation was deliberately removed in `47566e2`; the tracker has been corrected and that work reconsidered explicitly.

Compared on September 26, 2026, using freshly fetched Git history and source:

- FUTO `share`: `95e82ada5e2513484dccf68295a5ddde6d223730`, last commit September 22.
- Our `master`: `c7a28c4facf50f67942221d8185ca2ae4dfb4401`. Local HEAD matches the public fork.
- Upstream has 35 commits absent from ours; ours has 142 absent from upstream. These counts measure history, not unique features.

They have added a substantial recognition-engine and UI rewrite. Some capabilities overlap with work we implemented independently.

| Upstream addition | How it compares with ours |
| --- | --- |
| Shared recognition module used for FUTO Keyboard integration | Moves recognition, UI, model management, and common types into `shared/`. Our fork retains its app-centered backend architecture. This is a major structural change. |
| `transcribe.cpp` engine integration | Replaces their old bundled GGML/Whisper implementation with a native engine submodule and GGUF support. Our added models use separate backend integrations; we also retain legacy Whisper. |
| ASR4ALL small, medium, and large | Additional model choices beyond our documented model lineup. |
| Parakeet TDT/CTC 110M | Another smaller model option beyond our documented lineup. |
| Parakeet Unified 0.6B, Moonshine Streaming Small, Nemotron English streaming, and Nemotron 3.5 multilingual streaming | These families overlap with ours. Their implementation and model formats differ. Their catalog explicitly disables streaming for Parakeet Unified; ours offers buffered live updates. |
| Streaming tiers | Low, mid, high, and no streaming. Our fork already has live recognition and streaming profiles, so streaming itself is not a missing feature. |
| Streaming recovery and finishing | Their native wrapper falls back to full-recording inference when streaming cannot start or feeding fails. Their history also adds tail decoding to reduce finishing latency. No comparative performance testing was done. |
| Model-upgrade prompts | Distinguishes missing models from optional upgrades, links to downloads, unloads models after downloads that may replace them, and refreshes the prompt after completion. |
| Refreshed recognition/settings UI and keyboard integration | Adds model capability indicators and voice-input switching integration with FUTO Keyboard, plus settings/inset/navigation fixes. |
| IME text-insertion fixes | Adds automatic spacing, attempts recovery when the selection changes, and avoids repeated composing-text calls. These are useful candidates for comparison with our insertion behavior. |
| Maintenance | Fixes missing-model crashes and language-bail handling, updates the NDK and native engine, fixes x86/ARMv7 compilation, and adjusts nightly updates and development branding. |

Our fork still has its own additions, including Orukeet as the default, Moonshine Medium, Cohere, recording history with retranscription, a richer personal dictionary, diagnostic reports, and optional Harper English cleanup. The upstream additions above do not make our fork obsolete.

The most useful items to evaluate for adoption are the new model choices, model-upgrade handling, and IME insertion fixes. A wholesale merge would require reconciling two substantially different recognition architectures. Commit counts alone do not establish which implementation is better or faster.

Our README's upstream comparison describes an older Whisper-focused upstream state. It should not be used as a current comparison against `share`.

Sources: [upstream commit history](https://github.com/futo-org/voice-input/commits/95e82ada5e2513484dccf68295a5ddde6d223730/), [upstream model catalog](https://github.com/futo-org/voice-input/blob/95e82ada5e2513484dccf68295a5ddde6d223730/shared/src/main/java/org/futo/voiceinput/shared/Models.kt), [upstream streaming wrapper](https://github.com/futo-org/voice-input/blob/95e82ada5e2513484dccf68295a5ddde6d223730/shared/src/main/java/org/futo/voiceinput/shared/ggml/WhisperGGML.kt), [model-update UI](https://github.com/futo-org/voice-input/blob/95e82ada5e2513484dccf68295a5ddde6d223730/app/src/main/java/org/futo/voiceinput/settings/ModelUpdateCheck.kt), [our fork overview](https://github.com/Today20092/voice-input/blob/c7a28c4facf50f67942221d8185ca2ae4dfb4401/README.md).

This is a source/history comparison, not a runtime benchmark. The local knowledge graph was stale for changed files and did not contain upstream's shared module, so Git source was used for those checks. No application code was changed.

# Ticket #26: ASR4ALL integration status

Checked October 9, 2026 against [ticket #26](https://github.com/Today20092/voice-input/issues/26). The ticket remains incomplete. This change implements the expandable Model Options families for existing models. It does not add ASR4ALL recognition choices, a runtime dependency, or an inference adapter.

## Release blockers

All three pinned Plus repositories advertise `license: other`, `license_name: futo-ethical-use-1.0`, and `license_link: LICENSE`, but have no `LICENSE` file in their file inventories. The base repositories instead advertise `futo-model-weights-ethical-use-1.0` and contain the corresponding license. Do not silently apply the base license to Plus. Obtain the applicable Plus terms and verify the bundled third-party notices before distributing an integration.

The [Medium Plus card](https://huggingface.co/futo-org/asr4all-m-plus/blob/a0303285980b1c5afc2806e96a2fa42cee799958/README.md) identifies Alibaba FunASR FSMN VAD weights as Apache-2.0. That attribution is evidence to investigate, not a complete packaged notice audit. The bundled speaker heads and any runtime dependencies also need verification. The base weights license explicitly excludes inference source code from its terms; copying the reference decoder requires a separate source license check.

Initially `adb devices -l` returned no physical device. The user subsequently connected an authorized Samsung S25 Ultra, SM-S938U, running Android 16 / API 36 with `arm64-v8a`. UI verification on that phone is recorded below. ASR4ALL inference, IME/activity recognition, airplane-mode recognition, TalkBack inspection, performance measurements, and accuracy comparison remain unverified. These are explicit acceptance gates in the ticket, not optional follow-up checks.

## Pinned candidate artifacts

The candidate Android runtime is ExecuTorch XNNPACK INT8, as recommended by the current [Medium model card](https://huggingface.co/futo-org/asr4all-m/blob/86f67fdc60fecb4f9662ccd1d99948291db4d905/README.md). Runtime selection is provisional until the exported operators, runtime version, licenses, and actual ARM64 execution pass validation. The older transcribe.cpp experiment in #13 does not establish compatibility with these exports.

Each repository has `executorch/xnnpack_int8/asr_encoder.pte` and `executorch/xnnpack_int8/metadata.json`. Use the pinned revision in every download URL, replacing `main` with the revision below. Sizes are bytes. Bundle size includes those two files only; notices may add bytes. PTE SHA-256 values are publisher LFS object identifiers returned by the Hugging Face API, not independent hashes of locally downloaded model weights. Metadata was fetched and hashed locally as UTF-8. None of the PTE files was downloaded or executed.

| Variant | Repository revision | PTE bytes | Metadata bytes | Bundle bytes |
| --- | --- | ---: | ---: | ---: |
| [Small](https://huggingface.co/futo-org/asr4all-s/tree/735bdd8ae688094926068990ccf9b26a80cc43c3) | `735bdd8ae688094926068990ccf9b26a80cc43c3` | 38,489,024 | 90,364 | 38,579,388 |
| [Medium](https://huggingface.co/futo-org/asr4all-m/tree/86f67fdc60fecb4f9662ccd1d99948291db4d905) | `86f67fdc60fecb4f9662ccd1d99948291db4d905` | 72,395,968 | 90,363 | 72,486,331 |
| [Large](https://huggingface.co/futo-org/asr4all-l/tree/2da6534551f007201368514ec08b0b9a60ba39dc) | `2da6534551f007201368514ec08b0b9a60ba39dc` | 123,044,416 | 90,362 | 123,134,778 |
| [Small Plus](https://huggingface.co/futo-org/asr4all-s-plus/tree/26f3822f68d2c892296adef4cc2fd4a84df224a8) | `26f3822f68d2c892296adef4cc2fd4a84df224a8` | 75,716,140 | 91,138 | 75,807,278 |
| [Medium Plus](https://huggingface.co/futo-org/asr4all-m-plus/tree/a0303285980b1c5afc2806e96a2fa42cee799958) | `a0303285980b1c5afc2806e96a2fa42cee799958` | 127,166,764 | 91,130 | 127,257,894 |
| [Large Plus](https://huggingface.co/futo-org/asr4all-l-plus/tree/a40efdca11107ff8ed52c53cc87580b28c56ca9b) | `a40efdca11107ff8ed52c53cc87580b28c56ca9b` | 202,352,556 | 91,129 | 202,443,685 |

| Variant | PTE SHA-256 | Metadata SHA-256 |
| --- | --- | --- |
| Small | `a027754d46abdc7ffcdcc621a1430da7896e4504484657228a22d6eccf283381` | `025de70620e2e90fd0e3de947fea4783e0e637ec54d877a5d25c8ad3866e6f48` |
| Medium | `f50318acb5de7bfc75229f73edcc9276b3553efc9c3e34a7f3f1cf66f76067b4` | `321501d89e6249a76b8c0f896f9c3e90089f0f9d6135ab82802e5752990cab41` |
| Large | `50113d5aa74c916cce33828a3ec69c86aa64b58513a437ecf84fecfae098d351` | `05c6fd156f1fa99ea44457ab0d0a8d23b954c272fb614a034476123874e6d125` |
| Small Plus | `da04949d37e86a9c8f07dcba220e6932f74f0ecb74a4fec4b65a709a53c45568` | `55911214eb48a06107aafd6456c7b6b93d5ec3cd745aafe9eb868f1f7215cf8d` |
| Medium Plus | `3a83dba148eba2f4978fbc14105746fc8053968f49d3026292e44d2cddd50600` | `26ceee9021d423d18a2f3b8caa72247f863c8ad45b93bad5707bfa8c0a99d3a2` |
| Large Plus | `57533207c73aebbceb1f2dac3fc596645fe9f0bf302b55c4df6ff36c33884511` | `92de7d18d270f176ec06346b4bbeb76d6362f5383bc89f2bac2b46711846a49d` |

Reproduce the inventory with `GET https://huggingface.co/api/models/futo-org/asr4all-{variant}` and `GET https://huggingface.co/api/models/futo-org/asr4all-{variant}/tree/{revision}/executorch/xnnpack_int8`. Check the pinned root file list for licenses. A future revision must be audited separately.

## Decoder contract and Android gap

All six fetched metadata files have schema version 1, 16 kHz mono input, 640 samples per acoustic frame, default tier `stream_c16r4`, PCEC `keep_input: true`, and two PCEC cache tensors. The Plus model cards describe the ASR/PCEC path as equivalent to their base model, with extra speaker and FSMN components. This is not evidence that the tensor shapes are interchangeable across sizes.

The pinned [ExecuTorch reference decoder](https://huggingface.co/futo-org/asr4all-m/blob/86f67fdc60fecb4f9662ccd1d99948291db4d905/executorch/asr4all_executorch.py) establishes the following behavior:

- Acoustic input positions are PCM, filter cache, and attention cache. Output positions are logits, updated filter cache, updated attention cache, and acoustic hidden features. Copy arena outputs before another method call.
- Initialize caches from the exported method's shapes and scalar types. PCEC's seen counter is INT64, not FLOAT32.
- Retain CTC repeat/blank state across chunks. Pool hidden features into acoustic tokens and bucket leading blank runs for PCEC.
- Run complete PCEC commits, discard warm-up slots according to lookahead and consumed tokens, and preserve its caches. Feed zero `keep` features without a vocabulary-biasing decoder.
- At the endpoint, zero-pad the remaining acoustic window, close the pooled token, drain PCEC commits, execute the PCEC flush with `n_real`, and render capitalization/punctuation control tokens. Silence padding must not introduce extra committed frames.
- Emit partial transcripts through `StreamingSpeechBackend`; use the existing final dispatch and IME insertion path. Provisional text must never be appended as an additional final transcript.

The reference obtains tensor geometry through Python method metadata. The Android Java `MethodMetadata` API in inspected [ExecuTorch v1.0.0](https://github.com/pytorch/executorch/blob/v1.0.0/extension/android/executorch_android/src/main/java/org/pytorch/executorch/MethodMetadata.java) and [v1.2.0](https://github.com/pytorch/executorch/blob/v1.2.0/extension/android/executorch_android/src/main/java/org/pytorch/executorch/MethodMetadata.java) exposes method names/backends, not input/output shapes or types. The model metadata deliberately omits those shapes. A maintainable Android adapter needs verified generated geometry tied to the artifact hashes, or a native metadata bridge. Do not guess cache dimensions or infer runtime compatibility from a successful APK build. The [Android runtime documentation](https://github.com/pytorch/executorch/blob/v1.2.0/docs/source/using-executorch-android.md) documents the Maven AAR integration; no AAR was added by this change.

## Completed UI portion

Existing models are grouped into expandable families. Parakeet includes TDT, Unified, and Redux; Nemotron includes English profiles and multilingual. The selected family's section starts open after the saved runtime selection loads. Other families start collapsed, and multiple families can stay open. A header identifies the selected variant even when collapsed. Show/Hide buttons expose heading and Expanded/Collapsed semantics and retain their state through activity recreation. Expanding a family only changes local UI state.

The existing current-model summary remains above the family list. Descriptions, variant controls, download actions, deletion, details, and recognition-language controls remain inside their respective sections. ASR4ALL's six variants must join one family once a verified runtime and licensed packages are ready.

## Remaining acceptance work

Resolve the Plus license and notice gaps, then verify the runtime version, operators, geometry, and successful ARM64 inference for every pinned variant. Implement and test the streaming decoder/PCEC, six catalog entries and saved selections, validated shared downloads and lifecycle integration, cancellation and failure cleanup, and correct IME final insertion. Verify both app entry points on a physical device in airplane mode. Measure cold/warm load, first partial/final latency, peak RAM, sustained CPU/battery use, and accuracy against Orukeet for short utterances, noise, names, and accented English. Finish installation/compatibility documentation from those measurements. No acceptance item should be marked complete solely from this report.

## Verification

Completed October 10, 2026 on an isolated checkout of task-start commit `145e514e4d6126f6ab902501f8a2ac1aedf42131` plus this task's five changed Kotlin files. Other concurrent workspace edits were excluded. Gradle 8.11.1 used temporary Temurin JDK 17.0.20.1 and a separate transform cache because the machine's default JDK 25 and existing Gradle cache failed before app compilation.

| Check | Result |
| --- | --- |
| `:app:compileDevDebugKotlin` | Passed |
| `:app:compileDevDebugAndroidTestKotlin` | Passed |
| `:app:testDevDebugUnitTest --tests org.futo.voiceinput.settings.pages.ModelPresentationTest` | 8 passed |
| `:app:assembleDevDebug` and `:app:assembleDevDebugAndroidTest` | Passed, ARM64 development APK and test APK built |
| `:app:lintDevDebug` | Passed |
| Full `:app:testDevDebugUnitTest` | 206 tests, 2 failures, 1 skipped |
| `ManagedRecognitionModelCatalogTest` on S25 Ultra | 9 passed |
| `ModelReleaseScreenshotsTest` on S25 Ultra | 1 passed; actual Compose screenshots inspected |
| `git diff --check` for task files | Passed |

The full suite failures are the unchanged `DiagnosticStoreTest` cases `detailed collection expires and can be stopped early` and `opting out survives restart and clear does not reenable collection`. Both throw `Unable to save diagnostic preferences` at the existing `DiagnosticStore.savePolicy` call to `File.renameTo` when replacing the policy file on Windows. No diagnostics implementation or test was changed for #26. The full suite is not green.

The catalog tests use isolated model storage, wait for saved settings to render, and restore preferences through the application context. They verify the selected family starts open, collapsed headers retain the selected variant, multiple sections stay open without changing selection, downloads remain reachable, all existing families retain their controls/details, and details scroll at a 2x font scale. Model-presentation tests verify grouping retains every catalog entry. TalkBack, wide-layout inspection, installed-model deletion on the phone, and ASR4ALL inference were not exercised.

The development APK was updated on the phone with `adb install -r`; no package was uninstalled. The production package was not replaced. Local build evidence is preserved under ignored `build/ticket26-verification/`, including the development APK, full unit-test report, lint report, phone-test logs, and three screenshots. These build artifacts are not part of the source commit.

## Standards review

No hard documented-standard violations or remaining concrete findings. The review identified a download test that depended on whether a model was already installed; isolated test storage fixes it. The phone run identified asynchronous settings initialization; waiting for rendered family headers fixes it. Existing Compose/settings patterns and domain terms are retained. Heading and Expanded/Collapsed semantics were checked by the UI tests; TalkBack behavior remains unchecked.

## Spec review

No concrete regressions or unrequested scope were found in the implemented UI subset. It matches the requested independent expandable family sections, selected-family initial expansion, collapsed selected-variant labels, visible current-model summary, and preserved management controls. Phone tests verify this subset.

Four requirement groups remain incomplete: all six ASR4ALL choices and decoder/download/lifecycle integration; final runtime selection, licenses/notices, and successful ARM64 inference; physical IME/activity recognition and performance/accuracy measurements; complete accessibility and installed-model management validation. The absence of the ASR4ALL family is intentional pending integration, not evidence of completion.

Review totals: zero remaining Standards findings; four acknowledged incomplete Spec requirement groups. The ticket remains open and incomplete.

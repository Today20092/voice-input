# Ticket 26: ASR4ALL implementation and phone verification

Updated 2026-10-10. This replaces the earlier feasibility assessment. ExecuTorch is now integrated and tested on the connected S25 Ultra.

## Delivered scope

Small, Medium and Large base models are available in Model Options. The user removed Plus from scope: its ASR and PCEC are identical, while its extra heads serve speaker identification and diarization. Orukeet remains the default. Saved selections are preserved.

The implementation uses ExecuTorch Android 1.2.0, XNNPACK INT8 and one CPU thread. Downloads use pinned revisions, sizes and SHA-256 verification. Installed files are verified before loading. Shared catalog and lifecycle interfaces handle selection, downloads, deletion, history retranscription and diagnostic model identity. Full model and runtime license notices are included and linked in the app.

Summaries use plain English, with runtime details below. Built-in PCEC adds punctuation, capitalization and corrections. S1-mini is skipped for current and saved ASR4ALL recordings; its preference is preserved for other models.

## Model-card guidance

Sources: [Small](https://huggingface.co/futo-org/asr4all-s), [Medium](https://huggingface.co/futo-org/asr4all-m), [Large](https://huggingface.co/futo-org/asr4all-l), their metadata and packaged reference decoder.

The adapter follows exported tensor layouts and caches, 16 kHz mono audio, 25 Hz encoder frames, CTC blanks/repeats, pause buckets, acoustic hidden-state pooling, PCEC keep masks, warm-up trimming and final flush. Retained tensors are copied before subsequent runtime calls. tools/asr4all_geometry.py extracts ET12 program geometry; packaged geometry carries matching model hashes.

The app uses stream_c16r4, pcec_c16 and pcec_c16_flush. c16r4 is the publisher's low-latency dictation tier. Each frame is 40 ms. Commit controls result cadence; right context controls lookahead. Larger tiers trade delayed first results for accuracy or long-file throughput. No tier picker was requested. Published WER at another operating point does not establish this app's accuracy.

| Model | Pinned revision | PTE bytes |
| --- | --- | ---: |
| Small | 735bdd8ae688094926068990ccf9b26a80cc43c3 | 38,489,024 |
| Medium | 86f67fdc60fecb4f9662ccd1d99948291db4d905 | 72,395,968 |
| Large | 2da6534551f007201368514ec08b0b9a60ba39dc | 123,044,416 |

Exact PTE and metadata hashes are in Asr4allModels.kt. Commercial use remains subject to the model license's ethical-use restrictions.

## S25 Ultra verification

Device: SM-S938U, Android 16/API 36, ARM64. Tests used the separate debug package org.futo.voiceinput.dev; the production app was not replaced. The 11.001-second JFK fixture was delivered in 200 ms chunks in airplane mode. Networking was restored afterward.

| Model | Load ms | First partial ms | Process CPU ms | Sampled peak PSS MiB | Whole-clip decode ms |
| --- | ---: | ---: | ---: | ---: | ---: |
| Small | 171 | 2,426 | 3,381 | 273 | 523 |
| Medium | 180 | 2,522 | 4,229 | 324 | 920 |
| Large | 298 | 2,858 | 5,046 | 422 | 1,544 |

All three produced the country sentence with capitalization and punctuation. Paced and whole-clip transcripts matched per model. Small used a sentence boundary where Medium and Large used a comma. Fresh-module warm loads were 119/196/289 ms.

Load includes hashing. Warm means cached file pages, not a resident engine. First-result and process CPU measurements include instrumentation overhead. PSS includes UI and the test process and is sampled, not a continuous peak. USB charging prevents meaningful battery measurement.

The offline suite exercises native decoding, S1 bypass and its UI, catalog options, download requests, composition replacement through an actual EditText input connection, and a single recognition-activity result. The activity result is injected from native fixture decoding; this is not a spoken microphone trial.

## Checks and review

ARM64 dev app and test APK builds and Android lint passed. Python extraction-tool lint, formatting and type checks passed. The 211-test unit run had two pre-existing Windows DiagnosticStoreTest failures and one skipped test. ASR4ALL contract, catalog and presentation regressions passed.

The first 22-test offline device run passed 21 tests. Its sole failure was screenshot capture selecting two Compose roots when a dialog was open. Capture now uses Android UI automation to include the dialog window; all 10 catalog tests passed on rerun. Native decoding, cleanup and result delivery all passed. The final dev and test APK rebuild also passed.

Independent Standards review found no remaining concrete issue after quadratic PCM buffer copying was removed. Independent Spec review found no decoder defect against the packaged reference. It identified this obsolete report and missing broader release evidence.

Remaining ticket acceptance work: representative accents, noise and names; controlled accuracy comparison against Orukeet; actual microphone/keyboard dictation; cancellation, accessibility and sustained thermal/battery checks. One clean fixture does not establish general accuracy or energy use. Ticket closure and release publication remain separate.

# Full FUTO recognizer adoption

Owner decision: adopt FUTO's complete shared recognizer implementation for
long-term upstream maintenance reuse. This supersedes the view-only and
model-runner-injection proposals as the intended final architecture for #48.

Status: deferred at the owner's request. The migration is fully specified for
future agent work, conditional on obtaining reproducible native source. Do not
continue authentication attempts or production migration now.

Parked branch: `codex/issue-48-full-futo-adoption`. Keep its proof and decision
records separate from `Today20092/bring-fork-up-to-upstream`, which is reserved
for the completed upstream synchronization and bundled-ASR4ALL work. When the
source gate clears, fetch current `development` and start a fresh implementation
branch from it; reuse this proof deliberately rather than merging its older
snapshot wholesale. Finished work targets `development`, then a reviewed
`development` → `master` promotion. No release or keyboard fork is authorized.

## Baselines and immediate source gate

- Fork source: `cf83820`, plus assessment `e2863c5`.
- Voice Input: `c4590aaebb8d4ba6bfb9ac11e802e6c0519daf9b`.
- Native transcribe.cpp gitlink: `26d75933c16b2cec228e34c69e92a6c3c154b97c`.
- android-libs gitlink: `792a06e2dfd6924d0d2b8131654486b0fafe4d55`.

The native source gate is currently blocked. Fresh checks on October 30:

| Retrieval | Result |
| --- | --- |
| Configured Git credential helper, noninteractive clone probe | Authentication required; no usable credential |
| GitLab source archive at the exact pin | Redirects to sign-in |
| GitLab archive API at the exact pin | HTTP 404 |
| Exact commit in handy-computer/transcribe.cpp | GitHub HTTP 422, commit absent |
| futo-org/transcribe.cpp GitHub repository | HTTP 404 |
| Known local Git module stores | No transcribe.cpp checkout found |

An exact source checkout/archive or authorized repository access is required.
The older public SHARE-09 candidate is a different implementation and cannot
stand in for FUTO's source. Do not claim a reproducible full build using it.
The separate source question is pending with the owner; no credentials should
be pasted into chat or committed.

## Intended implementation

Use shared `RecognizerView`, `AudioRecognizer`, `MultiModelRunner`, `ModelManager`
and the native transcribe.cpp path as the recognition foundation. Port the
fork's additions onto that foundation. The current factory that substitutes the
entire fork RecordingSession is a baseline experiment, not the final design.

Prefer upstream interfaces and supported model formats. The fork's existing
runtime adapters are reference implementations and rollback support during
development, not the assumed final runtime architecture. Retain additional
runtime integrations only if source-backed compatibility findings establish
that a required model cannot be supported through FUTO's implementation; record
that as an explicit deviation rather than silently reverting the chosen design.

The app must still preserve saved model selections, installed-model compatibility,
included offline startup, model comparison, audio history/retranscription,
cleanup/vocabulary ordering, diagnostics privacy, and Activity/IME contracts.
Selecting this architecture does not authorize silently dropping those features.
Any unsupported model or necessary format conversion is an explicit migration
gap. Do not overwrite or delete working model installations while evaluating it.

## Compatibility investigation

The pinned upstream catalog advertises ASR4ALL Small/Medium/Large, selected
Parakeet and Nemotron models, Moonshine streaming Small, and Whisper. This is
catalog evidence, not proof that its runtime supports every fork variant.

| Fork family | Required investigation before migration |
| --- | --- |
| ASR4ALL S/M/L | ExecuTorch PTE installations versus upstream GGUF; streaming tiers, formatting, included Medium and offline recovery |
| Orukeet, Parakeet TDT/Unified | Verify exact architecture/languages, quantization, final-only/live capability and available conversions |
| Nemotron English/Multilingual | Verify supported variants, languages, streaming state and memory at the exact native pin |
| Moonshine variants | Upstream's Small entry does not establish parity for all current fork variants or the patched runtime |
| Cohere, Whistle, Parakeet Redux | No matching entry was observed in the inspected upstream catalog; audit native source and conversion feasibility |
| Whisper | Confirm legacy model-file compatibility and saved selection mapping; keep optional downloads working |

Keep model comparison as an app feature using upstream model-loading/inference
operations. Its results must still identify exact models, formats and settings;
different weights or quantization cannot be presented as a pure engine comparison.

## Implementation slices and gates

1. Obtain exact native source and nested dependencies, inspect licenses/API/model
   support, and reproduce an unmodified upstream ARM64 build. Use its supported
   toolchain first; the pinned app requests NDK `30.0.16248370`, whereas the fork
   currently builds with `28.2.13676358`.
2. Build an isolated full-path APK with shared recording, runner, manager and
   actual native inference. Resolve VAD/GGML/S1 library collisions deliberately.
   The earlier compile-only VAD workaround disables the upstream recorder and
   therefore cannot be reused as evidence for this slice.
3. Map every current model/selection and installation. Prove included Medium live
   transcription and a current final-only model through this full path. Gate
   each additional model on validated assets, language/capability correctness,
   load/finalize/cancel/release and device tests.
4. Port model management and comparison onto upstream operations. Preserve
   readiness, version staging, safe activation, deletion and session ownership;
   provide one authoritative installation state instead of competing catalogs.
5. Port history, retranscription and final processing. There must be one capture
   owner and one final-processing owner. Preserve complete/tail audio and failed
   or canceled attempts, and keep previous transcripts after failed retranscription.
6. Port routing, permission/recovery, UI accessibility and diagnostics. Reuse
   Activity/IME delivery contracts; test cancellation, late callbacks, recording
   during load and exactly-once delivery with native and controlled fixtures.
7. Run unit/lint/build/packaging and targeted device suites. Compare load,
   finalization and sampled memory on identical public recordings/settings against
   the retained baseline. Resolve or separately disposition baseline UI failures.
8. Merge verified migration work into the named integration branch. Remove
   superseded implementations only after parity gates pass. Production activation,
   signing/upgrade checks and publication remain separate release gates.

Rollback during development is the unchanged production source graph without
`sharedRecognizerProof`. The existing successful view/native/delivery proofs are
recorded in [the evidence report](../research/shared-recognizer-reproducibility-2026-10-30.md);
they do not validate this full runtime path.

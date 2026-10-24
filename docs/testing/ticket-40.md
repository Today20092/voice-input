# Ticket 40: bounded S1 ngram-mod experiment

Issue: <https://github.com/Today20092/voice-input/issues/40>

## Status and scope

This is an experimental build path, disabled by default. It is not yet an
Android speedup claim or a verified production change. Normal builds retain the
existing single-token greedy loop byte-for-byte within its preprocessor branch.
No additional model, user setting, JNI argument, or cleanup bypass is introduced.
Keep #40 open until the remote proof and affected-phone checks have evidence.

### October 24 follow-up

The [corrected remote proof](https://github.com/Today20092/voice-input/actions/runs/40114736456)
passed at the earlier candidate revision, including Android compilation and APK
creation. Its five short CPU observations did not demonstrate a speedup.
The [reporter's correction](https://github.com/Today20092/voice-input/issues/40#issuecomment-6388543625)
invalidates the original 8.8-to-3.3-second desktop comparison: warm-up had seeded
the shared server table with the exact outputs. Fresh-server measurements were
9.7 seconds plain versus 6.9 seconds ngram-mod across twelve clips, with four
slightly slower clips and one comma mismatch. Short clips had no overall gain.
Android candidate runs exercised drafts on longer inputs but used different
inputs from the baseline and did not check output parity. They establish neither
a paired phone speedup nor identical output. The app default remains disabled.

The follow-up harness below removes cross-request table reuse and fixed ordering
from a same-input CPU comparison. New harness/Android executable compilation and
device execution are pending; previous CI evidence does not validate these edits.

The pinned runtime already provides a small standalone rolling-hash table in
`common/ngram-mod.cpp`. Linking that file avoids the full common/speculation,
server, draft-model and command-line infrastructure. The default server allocates
16 MiB shared across requests; this single-request implementation allocates a
64 KiB table and discards it at request completion. The existing 2048-token model
context is retained. Drafts use match length 24, minimum 2 and maximum 16, matching
the issue's min/max proposal and the upstream default match length.

## Algorithm and limits

`s1mini_ngram.cpp` is shared by the optional JNI path and the real-model host
proof. It samples the next token greedily from the target, looks up possible
continuations from already accepted prompt/output tokens, and decodes that token
plus at most 16 guesses in one batch. It only accepts guesses that match the
target's greedy logits at the corresponding position. The first mismatching
target token becomes the next pending token. Rejected KV positions are removed
before continuing; an unsupported removal fails cleanup rather than delivering
unverified output.

Batch size is bounded by remaining context and output space as well as 17 tokens.
The hash table is fixed at 64 KiB; history grows only within the existing context
limit. Additional logits and backend scratch memory are not included in the
64 KiB figure and still need Android PSS measurements. No draft model or second
model context is allocated.

EOS is never emitted as text. Zero/one-token limits, full-context rejection,
cancellation before/during decode, native decode failure, and subsequent fresh
request recovery are covered by the host proof. Decode failures remain
`decode_failed`, cancellation remains `cancelled`, and full input remains
`input_too_long`; existing Kotlin cleanup fallback remains responsible for
preserving the raw transcript. All batch allocations use RAII.

Verification of every guessed token establishes the intended greedy algorithm,
but does not establish bit-identical output across batch shapes/backends. Floating
point differences can alter argmax. That is why exact token/EOS equality is a
release gate, and why this path stays disabled until measured. A host pass is
not proof of Android CPU/OpenCL equality or latency.

## Remote proof and installable candidate

The manually dispatched reusable workflow `s1-speculation-proof.yml`:

1. Checks the exact llama.cpp commit and downloads the pinned S1 GGUF on GitHub's
   runner, verifying its catalog SHA-256. No local model download is required.
2. Replays the original single-token implementation preserved in
   `tools/s1_speculation/greedy_baseline.cpp`, independent of temporary branch
   ancestry or squash merges. The positive speculative-acceptance
   assertion must fail with `no_speculative_tokens_were_verified`.
3. Rebuilds only the changed generator against the same llama libraries and
   requires exact output-token/EOS parity on five synthetic public transcripts,
   positive accepted drafts, rejected drafts, the table/batch bound, token limits,
   cancellation, context/decode errors, and recovery. No private captures or
   personal transcripts are used or printed.
4. If the proof passes, compiles the actual enabled JNI path with
   `-Ps1NgramSpeculation=true`, runs Android unit/lint/instrumentation-compilation
   and dev native-packaging checks, and uploads an installable dev APK, SHA-256,
   provenance and technical proof logs. It does not run device instrumentation.

The workflow additionally runs the paired host mode, then cross-compiles the same
harness as `s1-speculation-arm64` using the pinned NDK, static C++ runtime and a
single ARM64 CPU backend. The executable and its SHA-256 join the existing
artifact; the GGUF is not included. These builds run sequentially. See
[the paired phone protocol](s1-speculation-device-protocol.md) before interpreting
its timings. Building the executable does not run it on Android.

The paired host mode uses two threads, matching the existing host proof; the
Android executable uses six, matching the reporter's phone settings. Every row
prints its actual thread count. An initial remote follow-up run spent over
thirteen minutes in the new six-thread host comparison. Runner oversubscription
was suspected but not established; this duration is not performance evidence.

After pushing the reviewed changes, manually dispatch the proof workflow on that
exact ref (replace `REVIEWED_REF`):

```powershell
gh workflow run s1-speculation-proof.yml --repo Today20092/voice-input `
  --ref REVIEWED_REF
```

Native compilation uses two workers; the Android stage uses one Gradle worker,
a 2.5 GiB heap and two active Java processors. The normal release build is
independent, and no tag or release is published. The artifact installs as the
dev app alongside production. The production default remains off.

The host log's `plainTotalMs` and `candidateTotalMs` include fresh context
creation, prefill and generation against an already loaded CPU model, in fixed
plain-then-candidate order. They are individual observations, not controlled
Android benchmarks or a promised speedup. Native candidate metrics separately
count drafted/accepted tokens, decode calls and table bytes without content.

## Checks and remaining gates

For this follow-up, source review, `git diff --check` and NDK Clang's ARM64
`-fsyntax-only` check of `proof.cpp` against the pinned llama headers passed.
No local Gradle/native build, model inference/download, or emulator was started.
Rerun the remote proof, paired host checks, cross-compilation and APK build at the
new revision before distributing the executable or claiming it passed.

Before enabling production: retain the CI run/artifact hashes; compare exact
output and termination on Android CPU and any supported OpenCL configuration;
exercise short/long transcripts, corrections, names, Unicode, context/token
boundaries, repeated/canceled/timed-out runs and model unloading; measure actual
load/prefill/decode/total latency and PSS on representative phones. Verify that
failed candidates preserve the raw transcript and standard diagnostics contain
no dictated text. Expand fixtures if the public corpus does not exercise both
acceptance and rejection. Any parity mismatch or regression keeps this experiment
disabled and must be investigated rather than weakening the equality check.

Primary references:

- [Pinned ngram-mod implementation](https://github.com/ggml-org/llama.cpp/blob/b19cbe925be361d229f0fe03435affe4a2717f37/common/ngram-mod.cpp)
- [Pinned server wrapper and default pool](https://github.com/ggml-org/llama.cpp/blob/b19cbe925be361d229f0fe03435affe4a2717f37/common/speculative.cpp#L1903-L2029)
- [Pinned target verification and KV rollback example](https://github.com/ggml-org/llama.cpp/blob/b19cbe925be361d229f0fe03435affe4a2717f37/examples/lookup/lookup.cpp#L122-L215)
- [Upstream speculative decoding documentation](https://github.com/ggml-org/llama.cpp/blob/b19cbe925be361d229f0fe03435affe4a2717f37/docs/speculative.md)

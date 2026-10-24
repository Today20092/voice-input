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
from a same-input CPU comparison. Its new remote proof/build evidence is recorded
below; Android device execution remains pending.

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

The [initial follow-up run 40140733977](https://github.com/Today20092/voice-input/actions/runs/40140733977)
at `380b24d` passed the intended negative control and the original candidate
semantic proof. It was deliberately canceled during the new paired benchmark:
completed rows retained exact parity, but six-thread host decoding was unusually
slow (about 13.9 seconds for six output tokens). The run was progressing, not
stuck. It is an incomplete benchmark and neither a semantic failure nor a
completed speed result. Revision `7a6c546` changes only the host benchmark thread
count to two, retaining six on Android and every equality assertion.

### Follow-up verification at 7a6c546

[Run 40141896253](https://github.com/Today20092/voice-input/actions/runs/40141896253)
passed the intended negative control, original real-model proof and complete
paired host mode: all 24 measured pairs across six fixtures had exact token/EOS
parity. Drafts were accepted. The host observations do not demonstrate an overall
speed improvement: the new longest fixture's median candidate-minus-plain context/
prefill/generation time was +134.5 ms (range +125 to +143 ms), while the other
fixtures' median differences ranged from -4.5 to +40.5 ms. These are synthetic
two-thread host observations, not phone performance or universal parity evidence.

The same run cross-compiled the ARM64 executable and passed enabled-path Android
unit testing, lint, instrumentation compilation and APK verification. The console
did not expose an exact unit-test count; none is inferred. No instrumentation or
paired executable was run on a device.

Artifact `12579961194`, named
`s1-speculation-proof-7a6c546f3cd1da2b7fa5347e38354cffbd68a2b4`, has GitHub archive
digest `sha256:a6bcb2941ec64a837f6abf19d004961bd16b8ecd7d778c1fd1b07aa830a376c1`.
Its manifest and downloaded file hashes agree:

| File | SHA-256 |
| --- | --- |
| `voice-input-dev-s1-ngram-candidate.apk` | `792bb883aafd658eaf0d79116227ae2b184af5eae0ece563d75ae1e576d6d351` |
| `s1-speculation-arm64` | `6e98619e9cb2157c057a73cb5a8952ad962a7a6941467a00712a41053156157d` |

Provenance records source `7a6c546f3cd1da2b7fa5347e38354cffbd68a2b4`, pinned
llama.cpp `b19cbe925be361d229f0fe03435affe4a2717f37` and the catalog GGUF hash.
NDK `llvm-readelf` inspection of the downloaded executable confirmed ELF64
AArch64 PIE with `/system/bin/linker64`, depending only on Android system
`libm.so`, `libdl.so` and `libc.so`: no separate llama, ggml, C++ or OpenMP library
must be copied. This is package inspection, not device execution. CI artifacts
expire after 14 days. The verified APK, executable, logs and combined manifest
are preserved in the
[experimental prerelease](https://github.com/Today20092/voice-input/releases/tag/experiment-issues-40-41-7a6c546).
GitHub's uploaded asset digests match the payload hashes above. This is a
separate Dev experiment, not a production beta or a speedup claim.

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
The remote checks above passed at `7a6c546`; subsequent source changes require
their own verification. Affected-phone evidence remains required before enabling
the app experiment.

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

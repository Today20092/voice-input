# Paired S1 CPU experiment for issue 40

This experiment compares the existing plain greedy loop with the app's shared
ngram generator on identical public synthetic inputs. It does not enable
speculation in the app. Use the `s1-speculation-arm64` executable, `SHA256SUMS` and
`provenance.txt` from one successful `s1-speculation-proof.yml` run. Retain its URL
and source revision with the results. Verify the executable checksum locally.

The workflow uses the [NDK's CMake toolchain](https://developer.android.com/ndk/guides/cmake)
to build for ARM64 Android 26 or newer. No local Android build is necessary. The
small executable needs an existing local copy of the pinned S1 GGUF whose SHA-256
is `3b41ebe2502cbd03e811d5d16b022f5ab551eda58d62597d152f89535003c634`.
Do not replace or delete the app's private model files or uninstall either app.

## Run on the affected phone

Close Voice Input and other inference apps first so they do not hold additional
models in RAM. Let the phone cool, keep power/battery conditions consistent, and
record the device, OS, battery state and whether it is charging. From the folder
containing the downloaded artifact and an already available verified model:

```powershell
Get-FileHash .\s1-speculation-arm64 -Algorithm SHA256
Get-FileHash .\s1-mini-q4_k_m.gguf -Algorithm SHA256
adb shell mkdir -p /data/local/tmp/s1-paired
adb push .\s1-speculation-arm64 /data/local/tmp/s1-paired/
adb push .\s1-mini-q4_k_m.gguf /data/local/tmp/s1-paired/
adb shell chmod 755 /data/local/tmp/s1-paired/s1-speculation-arm64
adb shell '/data/local/tmp/s1-paired/s1-speculation-arm64 /data/local/tmp/s1-paired/s1-mini-q4_k_m.gguf --benchmark' > paired-cpu.txt
$LASTEXITCODE
```

Require exit code zero and the final `PASS` line. A mismatch prints a numeric
`parity=0` row and exits unsuccessfully; preserve it as failure evidence. Do not
discard mismatches or count a partial run as passing. The log contains fixture
IDs, timings, counts and equality flags, without input/output text or tokens.
The executable suppresses llama logging and never accepts private transcripts.

## Read the results

There are six fixtures, including short text, corrections, repeated phrases and
a roughly hundred-word note. Each has an excluded warm-up pair (`pair=-1`), then
four measured pairs (`pair=0..3`). Each pair runs plain and candidate on the same
tokenized prompt, alternating which goes first. Every request creates and frees
its own context and speculative table; earlier outputs cannot seed later tables.
One CPU model is loaded once and only one context is alive at a time. Both modes
use six threads and a 2048-token context. The ordinary proof remains available
without `--benchmark`, including cancellation/error/recovery checks.
The workflow's paired host comparison uses two threads to match its existing
proof; compare modes within each platform, not host timings against phone timings.

Compare paired `candidateTotalMs`/`plainTotalMs` differences per fixture, report
the median and range of the four differences, and keep short and long cases
separate. Also report decode timings, decode calls and drafted/accepted counts.
At least one measured case must accept drafts; the harness fails otherwise.
Run a second cooled session to see whether the result repeats. Record slowdowns
as well as improvements; there is no speed assertion.

`TotalMs` includes context creation, sampler creation, prefill and generation.
It excludes model loading, prompt tokenization, context teardown, output text
conversion, service IPC and app cleanup/delivery work. Prefill and decode are
separately timed. This is a **warm-model standalone CPU comparison**, not a cold
startup or end-to-end app benchmark, and not OpenCL validation. The executable
uses one generic CPU build rather than the app's dynamically selected optimized
CPU variants; phone results therefore do not establish production JNI parity or
speed. Follow up with paired app/device evidence before promotion, including
actual total cleanup latency and PSS. Exact token/EOS checks here cover these
fixtures and settings only; a comma difference remains a correctness failure.

The app's normal speculation default stays off. Keep issue 40 open until the
Android output and performance gates in [ticket-40.md](ticket-40.md) are met.
No build, model download/inference, or device run was performed locally while
preparing this protocol. ARM64 NDK Clang syntax checking of the harness passed;
remote linking and packaging passed at `7a6c546`, as recorded in
[ticket-40.md](ticket-40.md). Phone evidence remains required.

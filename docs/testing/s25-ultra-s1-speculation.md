# S25 Ultra paired S1 CPU evidence

Recorded October 24, 2026 for [issue 40](https://github.com/Today20092/voice-input/issues/40).
The standalone experiment passed exact output-token/EOS comparisons on both
initial sessions, but did not establish a speedup. The longest fixture in that
six-fixture corpus was slower in all eight measured pairs. A later seven-fixture
session, including a new 290-word note, also passed exact parity and showed a
slowdown on the new longest fixture. Across the two revisions there were 76
measured pairs with zero mismatches; their corpus/output limits differ as
recorded below. Keep app speculation disabled by default.

## Provenance and scope

The phone was a Samsung Galaxy S25 Ultra, SM-S938U, Android 17 (SDK 37), SM8750.
Testing followed [the paired CPU protocol](s1-speculation-device-protocol.md),
using the executable from [successful CI run 40141896253](https://github.com/Today20092/voice-input/actions/runs/40141896253)
at source `7a6c546f3cd1da2b7fa5347e38354cffbd68a2b4`.

| Input | Verified SHA-256 |
| --- | --- |
| ARM64 executable | `6e98619e9cb2157c057a73cb5a8952ad962a7a6941467a00712a41053156157d` |
| Pinned S1 GGUF | `3b41ebe2502cbd03e811d5d16b022f5ab551eda58d62597d152f89535003c634` |

The six public synthetic fixtures, prompt and greedy generation settings were
identical in both modes. Each session used six CPU threads, one excluded warm-up
pair per fixture, and four measured pairs per fixture with alternating execution
order. Every request created a fresh context and speculative table. No previous
output seeded a later request. Both sessions exited with code zero and their
logs end with the harness's final `PASS`;
each contains 24 complete measured pairs with zero token/EOS mismatches.

This tests the shared generator through a standalone generic CPU executable. It
does not execute app JNI, optimized dynamically selected CPU variants, OpenCL,
service IPC or final text insertion. `TotalMs` measures context/sampler creation,
prefill and generation, excluding model loading, prompt tokenization, context
teardown, text conversion and app cleanup/delivery work. These are warm-model
measurements, not cold-start or end-to-end cleanup timings. No host build, model
loading or inference was performed; inference ran only on the connected phone.

## Conditions

| Session | Start | End |
| --- | --- | --- |
| 1 | USB charging, battery 9%, battery temperature 31.3°C, thermal status 0, low-power mode 0 | USB charging, battery 8%, battery temperature 36.3°C, thermal status 1 (light) |
| 2 | USB charging, battery 8%, battery temperature 35.2°C, thermal status 0, low-power mode 0; cooled after session 1 | USB charging, battery 7%, battery temperature 36.6°C, thermal status 1 (light) |

Battery temperatures are device-reported battery readings, not CPU temperatures.
Low battery, USB charging and warming limit timing interpretation; session 2
started warmer than session 1. These two sessions on one phone are provisional
performance observations. Exact parity here covers only this corpus and mode.

## Paired results

Values below are **candidate minus plain**, in milliseconds. Positive values mean
the candidate was slower. Each cell gives the median followed by the minimum and
maximum of four paired differences; warm-up pairs are excluded.

| Fixture | Session 1 total | Session 1 decode | Session 2 total | Session 2 decode |
| --- | --- | --- | --- | --- |
| 0: short response | -10 (-68 to +24) | +3 (-16 to +60) | +1.5 (-323 to +46) | -4 (-32 to +14) |
| 1: correction | +172.5 (-23 to +514) | -5.5 (-14 to +385) | +4 (-48 to +115) | +7.5 (-2 to +46) |
| 2: review note | +191 (-300 to +1021) | +249 (-310 to +984) | +174.5 (+109 to +397) | +174 (+107 to +254) |
| 3: ordered tasks | +13 (-130 to +123) | +22 (-19 to +52) | -38 (-107 to +135) | -12.5 (-74 to -5) |
| 4: repeated phrase | -82.5 (-257 to +116) | -40.5 (-124 to +98) | -253 (-304 to -5) | -70 (-126 to +8) |
| 5: longest release note | +293.5 (+202 to +343) | +287 (+229 to +318) | +304 (+256 to +715) | +314 (+260 to +481) |

The counts below were constant across the measured pairs of both sessions:

| Fixture | Output tokens | Plain decode calls | Candidate decode calls | Drafted / accepted |
| --- | --- | --- | --- | --- |
| 0 | 6 | 6 | 6 | 0 / 0 |
| 1 | 10 | 10 | 10 | 0 / 0 |
| 2 | 47 | 47 | 27 | 36 / 20 |
| 3 | 20 | 20 | 20 | 0 / 0 |
| 4 | 29 | 29 | 29 | 0 / 0 |
| 5 | 109 | 109 | 99 | 32 / 10 |

Draft verification reduced decode calls on fixtures 2 and 5, but fewer calls did
not translate into lower measured latency. Fixture 5 was slower in every pair
in both sessions; fixture 2 was slower in every session-2 pair and noisy in
session 1. Cases with no drafting also varied. These results do not support
enabling the experiment or promising a phone speed improvement.

## Logs and remaining gates

The local logs contain only fixture IDs, timing/count metrics and parity flags,
with no dictated audio, private transcripts or token sequences. Their hashes are:

| Log | SHA-256 |
| --- | --- |
| `s25-paired-1.log` | `dcbc34114428575b266fd12b8ff6fee5247ef8c1fd978e8b65684dc0eb1ec720` |
| `s25-paired-2.log` | `024b316e2f74bf60db14cad7dc2cf38acdcf8cd4e08e05731f0ebe430408a350` |

The original logs were retained in the task's local temporary artifact folder
`voiceinput-s1-40141896253`; their hashes are listed above. Reviewed copies with
normalized line endings are retained as
[session 1](evidence/s25-s1-session-1.txt) and
[session 2](evidence/s25-s1-session-2.txt). These copies preserve every numeric
row and final result without private content.

This does not test the OnePlus 15 crash from issue 41, nor resolve the reporter's
desktop punctuation mismatch. Production promotion still needs representative
paired app/JNI performance and exact-output evidence under suitable device
conditions, including supported OpenCL paths and actual cleanup latency/PSS.
The S25 standalone results alone justify keeping issue 40 open and disabled.

## Longer-note follow-up

To directly test whether a longer transcript benefits, source
`0c46c76d074952daf10ed9d2e42bb51ceb10c2b0` adds one original public synthetic
290-word spoken note about a community library event as fixture 6. It retains
all six original cases and raises only the standalone benchmark output limit
from 256 to 768 tokens. A new assertion requires tokenized prompt size plus the
full output allowance to stay below the unchanged 2048-token context. The
ordinary semantic proof, JNI and product defaults are unchanged.

[CI run 40154134233](https://github.com/Today20092/voice-input/actions/runs/40154134233)
passed the original semantic proof, all 28 measured host pairs, Android unit/lint/
instrumentation-compilation checks, executable compilation and APK verification.
The new executable SHA-256 is
`c0bdea92dd97fd2eafd1d79678f57f3509f3f9b455ff11a79a629bdaa13a6fc9`;
the GGUF hash is unchanged. The downloaded manifest and file hashes agreed.

One further physical S25 session used that executable with six CPU threads and
the same excluded-warm-up/four-alternating-pair procedure. The recognition app
was force-stopped to isolate the standalone run. It started USB charging at 19%
battery, 30.2°C battery temperature, thermal status 0 and low-power mode 0. It
ended at 18%, 36.0°C and thermal status 0, still charging. The process exited
zero with final `PASS`: all 28 measured pairs reached the required output/EOS
checks with no mismatch. This is one seven-fixture session, separate from the
48 measured pairs in the two earlier six-fixture sessions.

For the new 290-word input, both modes produced 304 tokens. Every measured pair
drafted 95 tokens, accepted 38, and reduced decode calls from 304 to 266. Despite
fewer calls, the candidate was slower in all four pairs:

| Pair | First mode | Plain total ms | Candidate total ms | Plain decode ms | Candidate decode ms |
| --- | --- | --- | --- | --- | --- |
| 0 | Plain | 10884 | 11632 | 7485 | 8240 |
| 1 | Candidate | 10870 | 11529 | 7468 | 8143 |
| 2 | Plain | 11476 | 12944 | 8094 | 9108 |
| 3 | Candidate | 12140 | 12982 | 8309 | 9140 |

The median **paired** candidate-minus-plain total was **+795 ms**, with a range
of +659 to +1468 ms. The median of the four candidate/plain total ratios was
1.0690 (range 1.0606–1.1279), approximately **6.9% slower**. Decode alone was a
median +793 ms slower (range +675 to +1014 ms), with median ratio 1.1004,
approximately 10.0% slower. The raw total medians were 11180 ms plain and 12288 ms
candidate; their difference is not the same statistic as the paired median.

This longer input did not reveal a benefit on this phone/settings. It supports
keeping speculation disabled, not a universal claim about every transcript,
device or execution backend. The phone warmed during this single session even
without a reported thermal-status change. The standalone warm-model CPU limits
above still apply: no production JNI/OpenCL, model-load, app cleanup latency or
PSS result is established.

The original local `s25-paired-long-1.log` SHA-256 is
`a8999a7d80a11f5c14a452912e79e6f4f1c80c2718ccba03b1425855d0871ab0`.
A reviewed numeric-only copy with normalized line endings is retained as
[longer-note session 1](evidence/s25-s1-long-session-1.txt).

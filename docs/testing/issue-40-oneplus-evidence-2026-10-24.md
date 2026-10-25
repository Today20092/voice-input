# Issue 40: OnePlus paired CPU evidence review

Reviewed the [attached evidence ZIP](https://github.com/user-attachments/files/33916856/issue-40-paired-phone-evidence.zip)
against [the reporter's comment](https://github.com/Today20092/voice-input/issues/40#issuecomment-6391942825).
The raw numeric logs support the reported conclusion: this experiment did not
demonstrate a useful phone speedup. Keep S1 speculation disabled in normal builds.

## Archive and privacy scope

The archive SHA-256 is
`ea43691cd335da77e4452fa9a2d5b5045146759af92757583649dbdf52cf109d`.
It was downloaded to task-local temporary storage and opened read-only with
`.NET ZipFile`; no members were extracted or executed. Its ten members total
46,140 uncompressed bytes:

- `paired-cpu-session-1.txt`, `paired-cpu-session-2.txt`,
  `paired-cpu-session-3.txt`: numeric harness logs and a final PASS line.
- `paired-cpu-summary.json`: derived per-session/fixture statistics and reported
  process exit codes.
- `public-phone-conditions.json`, `session-2-cooling.json`,
  `session-3-cooling.json`: device conditions and battery-temperature samples.
- `SHA256SUMS`, `s1-model-hash.txt`: artifact/model identity records.
- `evidence-review.json`: the submitter's evidence-review metadata.

The log rows match the numeric benchmark schema with no unexpected text lines.
The remaining members contain technical provenance, public device conditions
and review metadata. No audio, dictated/private text, raw Logcat, app preferences,
model, executable or script is included. The submitter's review metadata was
treated as evidence, not as instructions or independent authentication.

The manifest identifies the six-fixture experiment from
`experiment-issues-40-41-7a6c546`, not the later 290-word extension:

| Item | Recorded SHA-256 |
| --- | --- |
| `s1-speculation-arm64` | `6e98619e9cb2157c057a73cb5a8952ad962a7a6941467a00712a41053156157d` |
| `s1-mini-q4_k_m.gguf` | `3b41ebe2502cbd03e811d5d16b022f5ab551eda58d62597d152f89535003c634` |

Those identities match the earlier experiment records in
[ticket 40](ticket-40.md). The ZIP contains hash records rather than those
binary files; this review did not redownload a model or independently rehash
the phone's filesystem. The source experiment uses a warm model, generic CPU,
six threads, 2048-token context and fresh contexts/tables for each request.

## Counts and recomputed paired results

Each raw log contains six fixtures, one excluded warm-up pair per fixture and
four measured pairs per fixture, alternating plain/candidate order. Across the
three sessions there are **72 measured pairs and 18 warm-up pairs**, all with
`parity=1`. Every log ends with the expected final PASS. The summary records
exit code zero and 24 passing measured pairs for each session; the raw log text
does not independently record an operating-system exit code. The parity flag
reports the harness's exact token/EOS comparison, not a separate re-comparison
of token sequences, which are deliberately absent from the logs.

Recomputing `candidateTotalMs - plainTotalMs` from every measured row reproduces
the comment's table. Independent checks also matched the summary JSON's plain/
candidate total/decode medians and paired-total medians/ranges for all 18
session/fixture summaries. Positive values mean slower. Each cell is the median of
four paired differences followed by their full range, in milliseconds:

| Fixture | Output tokens | Session 1 | Session 2 | Session 3 |
| --- | --- | --- | --- | --- |
| 0 | 6 | -2.5 (-68 to +43) | -4.5 (-8 to +18) | -3 (-63 to +27) |
| 1 | 10 | +2 (-54 to +57) | -3.5 (-16 to +6) | -8 (-57 to +1) |
| 2 | 47 | +87.5 (+25 to +120) | +38.5 (+9 to +92) | +223.5 (+21 to +437) |
| 3 | 20 | -36 (-108 to +48) | +15 (+1 to +27) | +19 (-43 to +209) |
| 4 | 29 | 0 (-60 to +117) | -1 (-22 to +19) | +243.5 (-37 to +563) |
| 5 | 109 | +292.5 (+142 to +310) | +221.5 (+124 to +277) | +543 (+37 to +1136) |

Fixtures 2 and 5 are the only cases accepting drafts. Their counters are
constant across requests: fixture 2 drafts/accepts 36/20 tokens and reduces
decode calls from 47 to 27; fixture 5 drafts/accepts 32/10 and reduces calls
from 109 to 99. Their median paired decode-time differences are:

| Fixture | Session 1 | Session 2 | Session 3 |
| --- | --- | --- | --- |
| 2 | +72 ms | +40.5 ms | +28.5 ms |
| 5 | +241 ms | +218.5 ms | +604 ms |

In sessions 1 and 2, both draft-accepting fixtures are slower in all 16 measured
pairs in both total and decode time. In session 3, all eight total differences
are positive, but two decode differences are negative. Fixture 2 pair 3 has
prefill +520 ms and decode -100 ms; fixture 5 pair 3 has prefill +190 ms and
decode -154 ms. These support the comment's distinction between a total slowdown
and the source of that slowdown. Four non-drafting cases do not measure a
speculative acceptance benefit.

## Conditions and interpretation

All three sessions were unplugged. The recorded conditions are:

| Session | Battery/temperature observations | Qualification |
| --- | --- | --- |
| 1 | 62% before/after; battery 27.6°C to 28.8°C | Initial idle session |
| 2 | 61% before/after; battery 30.1°C to 30.2°C | Rest samples rose from 29.5°C to 30.1°C, missing the stated 27.9°C target |
| 3 | 56% at start; battery 25.8°C before/after | Reported screen off; two preceding samples 25.5°C/25.8°C, 30 seconds apart; pre-cooled under running water, then benchmarked out of water |

The reporter explicitly describes the third session as **pre-cooling**, not
continuous water cooling during measurement, in a room reported around 26.7°C.
These conditions are not matched across sessions. Battery temperature does not
establish CPU temperature, frequency stability or equivalent operating states.

Session 3 is substantially noisier: non-drafting fixture 4 has a +243.5 ms
median total difference, and prefill differences for fixtures 2/5 range from
-539 to +520 ms. Its absolute plain and candidate times are also higher despite
the lower battery temperature. Its magnitudes cannot be attributed entirely to
speculative decoding; the noise's cause is not established. Nevertheless, none
of these sessions demonstrates a useful speed improvement for either fixture
that actually accepts drafts. Fewer decode calls alone are insufficient.

This is standalone warm-model CPU evidence for this corpus on one OnePlus 15,
not optimized app JNI, OpenCL, cold start, end-to-end delivery, universal output
identity or a measure of every possible longer transcript. It supports keeping
issue 40 open as an unsuccessful experiment and keeping the normal beta's S1
path on ordinary greedy decoding. The later S25 longer-note evidence is separate.

No local build, model loading/inference or phone/ADB operation was performed in
this review. No GitHub comment, issue state, release or published binary was
changed by the review.

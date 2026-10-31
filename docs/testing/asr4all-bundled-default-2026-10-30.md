# Bundled ASR4ALL default and S25 verification

## Change

The owner requested replacing bundled English Whisper with ASR4ALL. This branch
includes the existing pinned ASR4ALL Medium ExecuTorch package and uses `asr4all`
with variant `m` when no backend setting is saved. Existing saved choices and
setting keys are preserved. Orukeet remains selectable, and English/multilingual
Whisper remain optional downloads through the existing runtime.

The old 43,550,795-byte `tiny_en_acft_q8_0.bin.not.tflite` source asset is removed.
English-39 now points to the checksum-pinned downloadable
`tiny_en_acft_q8_0.bin`; the original download endpoint returned HTTP 200 during
verification. An existing selection of bundled English-39 stays selected and
offers its download rather than silently switching recognizers. Included Medium
is immediately selectable as an alternative.

Gradle downloads Medium's two pinned artifacts into generated APK assets,
verifying sizes and hashes before installation. Release verification independently
checks their hashes and rejects the old bundled Whisper asset. Existing ASR4ALL
license texts remain in the APK, with the distribution notice updated to describe
the included weights.

ExecuTorch requires filesystem paths. The first load validates and copies the
included package into a private code-cache installation using the existing
staging/activation store. Later loads verify that copy. Corruption or Android cache
reclamation triggers reconstruction from APK assets without a network transfer.
This needs approximately 72.5 MB of additional private cache storage; copy/IO or
space failures propagate through existing recognition error handling. The included
package cannot be deleted through Model Options or comparison cleanup. Existing
downloaded model files are retained on upgrade.

## Shared module decision

[ADR 0004](../adr/0004-keep-external-keyboard-integration.md) keeps the external-app
integration. Importing upstream's shared module into this APK cannot change an
installed keyboard's embedded recognizer. Extraction becomes useful when we also
maintain a second embedding application, such as a keyboard fork. Replacing
recognition runtimes with transcribe.cpp remains a separate evaluation.

Upstream keyboard source was checked at
`31e720ddcc2df6259069b2ae7cf0f1defaf2cb74`. The connected phone has FUTO Keyboard
0.1.30 selected as its input method. A package-manager resolution of
`org.futo.inputmethod.latin.action.VoiceInputSwitch` returned **No activity found**.
Automatic provider selection therefore cannot be exercised on that installation;
our existing explicit/manual fallback remains necessary. The user's keyboard
installation, selected IME and provider settings were not changed.

## Validation

Phone: Samsung SM-S938U (S25 Ultra), **Android 17 / API 37**, ARM64. The separate
`org.futo.voiceinput.dev` package was installed; the release package
`org.futo.voiceinput.moonshine` and its data were not replaced. The dev build and
downloaded comparison models remain available on the phone.

| Check | Result |
| --- | --- |
| Dev and standalone release unit tests | Each: 277 passed, 3 skipped, no failures/errors |
| Dev and standalone release lint | No errors; 87/86 warnings respectively |
| Dev app, instrumentation APK and standalone release builds | Passed |
| Final physical-phone regression suite | All 60 passed, 81.258 seconds |
| Additional microphone/platform and route-control suite | All 4 passed, 2.123 seconds |
| Packaging guard unit tests | All 4 passed |
| Packaging verifier on final release APK and FULL symbols | Passed: 29 ARM64 libraries, pinned model/runtime/notices, S1 speculation off, stripping and matching symbols |
| Ruff on changed packaging verifier | Passed |

The phone suite covers included-model recognition from fresh isolated storage,
cache corruption and reclamation recovery, native transcript delivery through
actual EditText composition and recognition-activity results, settings and language
navigation, manual-provider recovery UI, model readiness/updates, fixture download
confirmation/retry, nested downloads, model-selection preservation, cancellation,
OOM recovery, and independent backend construction. The microphone test opens an
actual AudioRecord and checks phone preference/cleanup without retaining audio.
Bluetooth choices in the route-control tests are simulated; no Bluetooth headset
was exercised.

The catalog ordering test was updated because downloadable English-39 no longer
has zero transfer size. The stale-backend language test now expects the included
default, ASR4ALL Medium. The former bundled-Whisper smoke test was replaced with
included-ASR4ALL and cache recovery tests. Source, build tasks, benchmark cleanup,
model presentation, developer orientation and user guides were updated together.

The final locally built standalone APK is 121,353,153 bytes. It uses local fallback
signing and the pre-change parent `4c5bfad` in its embedded revision. No production
signing key, release publication, remote push or master merge was used.

## Repeatable model comparison

Run `111741b6-5d71-4040-a2dd-aca1671ac382` used the repository's 11.001-second public
JFK recording. Small and Orukeet were downloaded and validated through the app's
existing downloader in the dev package; Medium came from the APK. Each model ran
in a fresh app process, with a first whole-clip decode and three warm repetitions.

| Model | Initialization ms | First whole-clip decode ms | Median warm decode ms | Sampled peak app/test PSS MiB |
| --- | ---: | ---: | ---: | ---: |
| ASR4ALL Small | 124 | 526 | 495 | 500 |
| ASR4ALL Medium | 215 | 960 | 950 | 588 |
| Orukeet | 2,433 | 401 | 340 | 1,025 |

Small returned "my fellow Americas"; Medium and Orukeet returned "my fellow
Americans". All returned the country sentence, with different punctuation. These
are observations on one clean fixture, not an accuracy ranking. The spoken
reference was not independently verified in this run, so automated WER/CER are
reported as unavailable rather than asserted. Whole-clip decoding does not measure
live first-partial cadence or the wait after Stop. Initialization includes runtime
setup and integrity checks, not a cold filesystem-cache flush. PSS includes app and
instrumentation memory, not just model weights. USB charging prevents useful
battery conclusions.

Raw logs and the JSON comparison report are under ignored `build/`; the comparison
report is also retained in the dev app's Model comparison history. No user voice
recording was read or exported for this comparison.

## Remaining checks

Broader accents, names, noise and the user's own dictation remain untested here.
The owner was offered a live speech check; the repeatable comparison uses only
the public fixture. End-to-end dictation from the installed FUTO Keyboard into an
ordinary app still needs manual provider configuration and a spoken trial. No
keyboard update or provider switch was performed. Production-signed upgrade,
sustained heat, energy use and real Bluetooth routing remain release gates.

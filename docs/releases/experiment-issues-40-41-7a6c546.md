# Experimental tests for issues 40 and 41

These are manually installed experimental builds for phone testing. Both APKs
use the separate **Voice Input Dev** app and are alternatives for the same app;
installing one replaces the other. Keep the regular Voice Input app installed.
They are CI debug-signed candidates, not a production beta or stable update.
If an existing Dev installation has a signing conflict, preserve its data and
report the conflict rather than uninstalling it.

## Moonshine native-crash candidate (#41)

Install `voice-input-dev-moonshine-ort-candidate.apk` to test the rebuilt
Moonshine ORT 1.23.0 runtime with KleidiAI disabled. S1 speculation is off in
this APK. The previous candidate worked on the affected OnePlus 15, but the
new APK still needs physical-device confirmation and broader ARM64 coverage.

- Source: `380b24de10c90c6a39f70a92f1f8150f57c7f02c`.
- [Successful runtime and APK CI](https://github.com/Today20092/voice-input/actions/runs/40140732558).
- Passed versioned-symbol fixtures, original-runtime rejection, runtime build,
  ELF/SME checks, unit tests, lint, Android-test compilation, APK checks and
  byte-for-byte verification of the packaged replacement runtime.
- Test Moonshine Small and Medium, live dictation, history retranscription,
  long recordings, cancellation/retry, final results and switching models on
  another ARM64 phone. Repeat affected-OnePlus checks. Standard diagnostics
  suffice initially; no audio or transcripts are requested.
- The AAR, ORT license, third-party notices, CMake cache, provenance and validator
  logs are retained here alongside the APK. Production promotion remains gated.
  The attached `provenance.txt` describes this Moonshine build; S1 provenance is
  recorded in the section below.

## S1 speculative-decoding experiment (#40)

`voice-input-dev-s1-ngram-candidate.apk` enables the experimental ngram path.
It uses the normal Moonshine runtime, so it is not the Moonshine crash-workaround
build. `s1-speculation-arm64` is a standalone paired CPU benchmark executable,
not an APK; follow the
[phone protocol](https://github.com/Today20092/voice-input/blob/7a6c546f3cd1da2b7fa5347e38354cffbd68a2b4/docs/testing/s1-speculation-device-protocol.md).

- Source: `7a6c546f3cd1da2b7fa5347e38354cffbd68a2b4` (also the release tag target).
- [Successful proof, benchmark and Android CI](https://github.com/Today20092/voice-input/actions/runs/40141896253).
- Passed the negative control, semantic proof, all 24 measured host pairs with
  exact token/EOS parity, ARM64 executable compilation, unit tests, lint,
  Android-test compilation and APK checks.
- The paired host comparison did **not demonstrate a clear speed improvement**.
  Android timing, output parity, production JNI/OpenCL behavior and memory
  measurements remain untested. Speculation stays disabled in normal builds.
- The executable uses only Android system libraries. It requires the separately
  obtained pinned S1 GGUF, SHA-256
  `3b41ebe2502cbd03e811d5d16b022f5ab551eda58d62597d152f89535003c634`.
  No models are included. Pinned llama.cpp:
  `b19cbe925be361d229f0fe03435affe4a2717f37`.
- The initial six-thread host run was deliberately canceled for excessive
  duration. The successful host rerun uses two threads; Android uses six.
  Every equality assertion and fixture was retained.

## Artifact hashes and remaining checks

Verify the attached `SHA256SUMS` before testing. The downloaded artifacts were
checked against their CI manifests. This release preserves them beyond the
14-day Actions artifact retention period.

No local Gradle/native build, model inference, emulator or connected-phone test
was run while preparing these candidates. All builds ran in GitHub CI.
Compiling Android tests does not execute them on a device. Issues #40 and #41
remain open, and the production runtime and speculation defaults are unchanged.

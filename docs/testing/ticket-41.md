# Ticket 41: Moonshine ARM64 SIGILL candidate

Issue: <https://github.com/Today20092/voice-input/issues/41>

## Status

Investigation and a reproducible CI candidate, **not a verified production fix**.
The normal `ai.moonshine:moonshine-voice:0.0.68` dependency is unchanged. No release
tag, runtime release asset, or model change is part of this work. Keep #41 open.

The reporter's standard diagnostic export identifies a native SIGILL after model
load, but does not identify the faulting instruction/library. The upstream
KleidiAI defect is a plausible cause, not an established diagnosis of this app.

## Source and artifact evidence

- [ORT #26377](https://github.com/microsoft/onnxruntime/issues/26377) documents
  enabling SME2 kernels on CPUs that expose SME without SME2.
- [Pinned ORT 1.23.0 platform dispatch](https://github.com/microsoft/onnxruntime/blob/be835efc56aca19b8e810538ec93c8e150e0fc61/onnxruntime/core/mlas/lib/platform.cpp)
  checks `HasArm_SME()` before selecting KleidiAI. Its build supports
  `--no_kleidiai`. The candidate pins that exact commit, preserving the ABI.
- [Upstream fix #27403](https://github.com/microsoft/onnxruntime/pull/27403)
  introduces kernel selection for DynamicQGemm. This is evidence of the upstream
  defect, not evidence that the candidate works on the reported phone.
- Cached Moonshine 0.0.68 AAR SHA-256:
  `21a8ab5f7b2e0b962b7ef6f0101a7e19fc716ae82d97c163bbbaa247c3ef4a80`.
  Its ARM64 `libmoonshine.so` imports `OrtGetApiBase`, CPU provider and NNAPI
  provider functions with the **`VERS_1.23.0`** ELF version requirement. An
  arbitrary newer ORT library is therefore not a drop-in fix even though the C
  API is backward compatible; the ELF symbol versions also have to match.
- The [official Microsoft 1.23.0 AAR](https://repo.maven.apache.org/maven2/com/microsoft/onnxruntime/onnxruntime-android/1.23.0/onnxruntime-android-1.23.0.aar)
  has SHA-256 `2b7e4ed3c3028a1b2afac8dc324442c70b8983fc1a7cb6adda4133030d53f20a`.
  Its ARM64 library contains KleidiAI source strings, `smstart`, and SME2
  `ptrue pn9.b` at offset `0x760e98`. Replacing Moonshine's library with this
  same-version stock binary is not an established workaround.
- Simply upgrading to Moonshine 0.1.5 is insufficient evidence: its
  [ORT pin](https://github.com/moonshine-ai/moonshine/blob/v0.1.5/scripts/ort-build-common.sh)
  is 1.23.2 and its
  [Android build script](https://github.com/moonshine-ai/moonshine/blob/v0.1.5/scripts/build-ort-android.sh)
  still permits KleidiAI for ARM64. No unrelated Moonshine API upgrade is made.

Evidence was obtained with small metadata/source requests, a 30 MB official AAR,
and NDK LLVM tools. No speech models, Android emulator, Gradle build, or native
compilation ran on the maintainer's computer.

## CI candidate

`.github/workflows/moonshine-runtime-candidate.yml` runs on relevant pull requests
or manual dispatch. It builds exact ORT 1.23.0 for ARM64/API 26 with KleidiAI off,
CPU and NNAPI exports retained, NDK 28.2.13676358 and 16 KiB LOAD alignment. Native
compilation is limited to two workers on GitHub's runner. Only the ARM64
`libonnxruntime.so` entry is replaced in the pinned Moonshine AAR. The Java API,
Moonshine native libraries, models, Sherpa's statically linked runtime, and normal
production dependency remain unchanged.

The workflow first requires the original AAR to fail the SME instruction guard.
It then checks the candidate's ELF class/machine, SONAME, version namespace,
all three actual versioned imports, LOAD alignment, and absence of SME entry
instructions. It uploads the AAR, SHA-256, CMake cache, MIT license, third-party
notices, baseline/verification logs and build provenance for 14 days. This is
packaging evidence; it does not execute ARM64 inference.

Use that artifact only in a clearly identified test build, preferably in CI:

```powershell
.\gradlew.bat :app:assembleDevDebug `
  -PmoonshineCandidateAar=C:/path/moonshine-voice-0.0.68-ort-no-kleidiai.aar `
  -PmoonshineCandidateAarSha256=<lowercase-hash-from-reviewed-CI-SHA256SUMS>
```

The optional file override checks the complete AAR hash and fails closed on a
missing/mismatched artifact. Do not run this build alongside other local builds;
the candidate workflow is intended to avoid local native compilation entirely.

## Acceptance still required

- Execute the candidate CI workflow and retain its run URL, artifact/hash and
  successful validator output. A script or workflow definition is not a pass.
- Build an APK with that candidate in CI, run unit/lint/packaging checks, and
  verify the resulting APK contains exactly the candidate ARM64 ORT library.
- On the affected OnePlus 15, exercise both Moonshine Medium live dictation and
  audio-history retranscription, repeated cancel/retry, and a subsequent working
  model. Retain version, APK hash and content-free diagnostics. Obtain a native
  tombstone/backtrace if SIGILL persists.
- Check Moonshine Small and Medium on another physical ARM64 phone, old Android
  compatibility where available, long dictation, final flushing, and native
  runtime coexistence. Disabling kernels may affect speed; measure rather than
  claim a performance improvement.
- Only after those checks select a reproducible pinned distribution mechanism
  for a production runtime and update notices/version reporting as necessary.
  Do not replace the default with an unverified CI artifact.

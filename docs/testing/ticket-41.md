# Ticket 41: Moonshine ARM64 SIGILL candidate

Issue: <https://github.com/Today20092/voice-input/issues/41>

## Status

The candidate passed CI and the reporter's affected-OnePlus test on October 24,
2026. **Production promotion is still gated on broader device coverage and a
pinned runtime distribution.** The normal
`ai.moonshine:moonshine-voice:0.0.68` dependency is unchanged; normal 1.4.10 builds
do not include this workaround. Keep #41 open.

The original standard diagnostic export identifies a native SIGILL after model
load, but does not identify the faulting instruction/library. The successful
candidate test supports the workaround; it does not establish the exact upstream
KleidiAI defect as the cause of this app's crash.

## Completed evidence

- [Candidate CI run 40113425674](https://github.com/Today20092/voice-input/actions/runs/40113425674)
  succeeded at source `eaa25dd8f2dca2713c0fae55cadc55d6adb49eac`, including
  the candidate build/validator and installable APK job. The job ran unit tests,
  lint, instrumentation compilation, native packaging checks and an exact
  comparison of packaged ORT bytes against the replacement AAR. Instrumentation
  compilation is not a device test.
- The separate Dev APK is attached to
  [1.4.10-beta.1](https://github.com/Today20092/voice-input/releases/tag/v1.4.10-beta.1)
  as `voice-input-dev-moonshine-ort-candidate.apk`, version `1.4.9-dev`,
  versionCode 80, source `eaa25dd`. APK SHA-256:
  `685702ceed4650305b1c404192a492a7a0824c8fed1cc3dab96efed8a76f942e`.
  The normal beta APK keeps the published runtime.
- The [reporter's device results](https://github.com/Today20092/voice-input/issues/41#issuecomment-6388555102)
  identify OnePlus 15 (CPH2749 / SM8850), Android 16: five completed Moonshine
  Medium dictations (two with S1-mini), one cancel followed by retry, three
  history retranscriptions, and switching to ASR4ALL-L and back without crashes.
- Reviewed the attached
  [standard diagnostic ZIP](https://github.com/user-attachments/files/33912241/voice-input-diagnostics-1792867857776.zip)
  directly, without extracting or retaining its contents in the repository.
  Environment metadata matches `eaa25dd`, `1.4.9-dev`, versionCode 80, CPH2749,
  SDK 36. Structured events contain eight Moonshine recognition completions
  (including three history retranscriptions), one Moonshine cancellation and
  three ASR4ALL-L completions. Its only recorded process exit is reason 10,
  status 0, which the reporter attributes to a deliberate force-stop; no native
  crash exit (reason 5) is recorded. ZIP SHA-256:
  `b3e8e2c90568f30726d31ce17e33a53ab60fba7064e22afd9d39f6c00e6e8bb2`.
  The archive contains no audio or transcript capture entries. This bounded
  export cannot prove that no unrecorded crash occurred. The reporter also notes
  incomplete Logcat capture.

The original three production attempts all crashed. The candidate results are
stronger than the prior build-only evidence, but cover one affected phone and
one cancellation cycle, not the complete regression matrix below.

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

`.github/workflows/moonshine-runtime-candidate.yml` runs only by manual dispatch
or an explicit reusable-workflow call. It builds exact ORT 1.23.0 for ARM64/API 26 with KleidiAI off,
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

After pushing the reviewed changes, rebuild a candidate on that exact ref
(replace `REVIEWED_REF`) on a remote runner:

```powershell
gh workflow run moonshine-runtime-candidate.yml --repo Today20092/voice-input --ref REVIEWED_REF
```

The separate candidate job also builds the dev package alongside production,
runs dev unit tests/lint/instrumentation compilation/native packaging checks,
and compares the APK's ARM64 ORT bytes with the candidate AAR before uploading
an installable APK and its hash. It uses a single Gradle worker, a 2.5 GiB heap,
two active Java processors and two native build workers on the remote runner.
It neither executes device instrumentation nor publishes a release. The normal
release job is independent and unchanged.

Use that artifact only in a clearly identified test build, preferably in CI:

```powershell
.\gradlew.bat :app:assembleDevDebug `
  -PmoonshineCandidateAar=C:/path/moonshine-voice-0.0.68-ort-no-kleidiai.aar `
  -PmoonshineCandidateAarSha256=<lowercase-hash-from-reviewed-CI-SHA256SUMS>
```

The optional file override checks the complete AAR hash and fails closed on a
missing/mismatched artifact. Do not run this build alongside other local builds;
the candidate workflow is intended to avoid local native compilation entirely.

The validator now requires every Moonshine import to be exported with its exact
`VERS_1.23.0` symbol version. A matching namespace elsewhere in the library and
an unversioned or differently versioned symbol of the same name do not satisfy
that requirement. Lightweight fixtures run before candidate compilation:

```bash
bash tools/test-moonshine-ort-validator.sh
```

Those fixtures passed locally without downloads, Gradle, native compilation or
model loading. The historical CI pass above predates this stricter validator;
the updated workflow has not been rerun yet.

## Acceptance still required

- Repeat cancellation/retry cycles on the affected phone with the final
  candidate. Retain its APK hash and standard diagnostics. Obtain a native
  tombstone/backtrace if SIGILL returns.
- Check Moonshine Small and Medium on another physical ARM64 phone, old Android
  compatibility where available, long dictation, final flushing, and native
  runtime coexistence. Disabling kernels may affect speed; measure rather than
  claim a performance improvement.
- Retain the same candidate AAR, its hash, source/build provenance and license
  notices in durable release storage; the CI artifact expires after 14 days.
  Rebuilds need their own validation and APK hash, even with the same source.
- After the device checks pass, select a reproducible pinned distribution
  mechanism for the production runtime and update notices/version reporting as
  necessary. Run the stricter validator and normal release checks on that exact
  artifact, verify the final APK's ORT bytes, then confirm the ordinary beta on
  the affected phone before closing #41. Do not promote the default from the
  historical candidate APK's success alone.

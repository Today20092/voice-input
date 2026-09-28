# Public Parakeet adapter experiment

SHARE-09 uses handy-computer/transcribe.cpp commit
`4807edaf210d0d7e8a6f7fb2a44b65966a2797f0`, not FUTO's unavailable engine.
This directory is outside the app's source sets and Gradle settings. It adds no
catalog entry, download, default change or ASR4ALL support.

The Kotlin adapter implements the existing `SpeechBackend` interface, compiled
directly from the app source. It does not implement `StreamingSpeechBackend`:
the pinned public 110M model is English and final-only. It accepts complete
16 kHz mono float PCM, validates finite samples in [-1, 1], and returns text
only after successful native finalization. The engine uses two CPU threads.
No cleanup or personal vocabulary stage runs in this isolated experiment.

## Reproduce

Run from the repository root on Windows with JDK 17 or 21 and Android SDK 35.
The SDK defaults to `%LOCALAPPDATA%/Android/Sdk`; `ANDROID_HOME` overrides it.
The host checks compile Kotlin and use a controlled session double. They do not
load native code, download a model or prove Android behavior.

```powershell
./gradlew.bat -p tools/transcribe_experiment test --max-workers=2 --no-daemon
./tools/transcribe_experiment/experiment.ps1
```

The second command verifies the public source archive against the recorded
SHA-256. It fails on a mismatch. It does not compile native code.

After the coordinator grants the exclusive native build slot:

```powershell
./tools/transcribe_experiment/experiment.ps1 -Build
```

This uses the installed NDK `28.2.13676358`, CMake `3.22.1`, Ninja, API 26,
ARM64, Release and static libc++. It freshly extracts the verified archive,
configures separately from S1/legacy GGML and builds with two workers. No NDK,
AGP or production build migration is required by this experiment's setup.
Successful execution requires exactly the five JNI exports and rejects shared
GGML, transcribe, llama, OpenCL and libc++ dependencies. It copies the engine
and third-party notices beside `build/arm64/libshare09_parakeet.so`.
These checks reduce collision risks; loading the runtimes together on Android
is still required. The public engine compiles its full architecture registry,
so this is not a size-optimized Parakeet-only distribution.

The Kotlin JAR is under `build/libs/`. It is a preparation artifact, not an APK.
An Android test caller must supply Kotlin/coroutines, package the ARM64 library,
and instantiate `PublicParakeetBackend(modelFile)`. Call `load(context)` before
`transcribe(samples)` and `close()` in `finally`. `loadForExperiment()` runs the
same loading path without a Context. `close()` is terminal and idempotent;
create another backend to reload. Nothing installs or invokes this adapter in
the production app.

## Model and ownership

Only the [pinned public Q8_0 model](https://huggingface.co/handy-computer/parakeet-tdt_ctc-110m-gguf/resolve/766f172fe70eb66785e3371664f53762e0fbafaa/parakeet-tdt_ctc-110m-Q8_0.gguf)
is accepted, SHA-256
`7dd44c74a331d788a4e5f8b16913b3feb29ced22cf5613aad0e0f6cd30516296`.
Place it in private storage under caller ownership. This experiment does not
download or activate models, and the caller must not modify the file while it
is being validated or loaded. The model is NVIDIA's Parakeet TDT/CTC 110M,
converted by handy-computer, under CC-BY-4.0. See the pinned
[model documentation](https://github.com/handy-computer/transcribe.cpp/blob/4807edaf210d0d7e8a6f7fb2a44b65966a2797f0/docs/models/parakeet-tdt_ctc-110m.md)
and [source findings](../../docs/research/transcribe-cpp-source-feasibility-2026-09-26.md).
No model is redistributed here. Packaging a model requires its attribution and
license notices in addition to the engine notices.

`transcribe_open` owns the model and session. The adapter serializes compute,
sets an atomic abort flag on coroutine cancellation or close, and joins the
worker before freeing that session. It does not replace callbacks during
inference. A queued cancellation cannot be erased by the worker starting late.
An aborted or incomplete decode throws; readable partial text is not a final
result. UTF-8 output crosses JNI as bytes. The engine log sink is disabled
before loading, and the adapter does not log audio, model paths or text.
Model loading has no public abort callback in this implementation: cancellation
waits for loading to return, then frees the acquired session.

## Validation limits

Host tests cover cancellation versus release, suppression of canceled results,
repeated successful and failed runs, full two-minute audio handoff, input
validation and idempotent release. They do not measure native behavior.
ARM64 compilation and manual ELF export/dependency inspection passed at the
[safe-pause checkpoint](../../docs/research/transcribe-cpp-source-feasibility-2026-09-26.md#native-build-and-safe-pause-2026-09-28).
The full script has not been rerun after the include-path fix. Actual JNI
loading/inference, same-process legacy/S1 coexistence, Android packaging/notices,
failure injection and phone benchmarks remain pending. Do not mark SHARE-09 or
its dependent model tickets complete from these host checks.

Reference contracts used: [coroutine child lifetime](https://github.com/Kotlin/kotlinx.coroutines/blob/master/docs/topics/coroutine-context-and-dispatchers.md),
[CMake position-independent code](https://cmake.org/cmake/help/latest/prop_tgt/POSITION_INDEPENDENT_CODE.html),
and the [pinned transcribe API](https://github.com/handy-computer/transcribe.cpp/blob/4807edaf210d0d7e8a6f7fb2a44b65966a2797f0/include/transcribe.h).

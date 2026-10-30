# transcribe.cpp source feasibility

Research date: 2026-09-26. Scope: SHARE-09 source gate, before Android integration or hardware measurements. This note supplements the [adoption review](upstream-share-adoption-review-2026-09-26.md).

## Decision

The exact FUTO engine remains unavailable anonymously. Reproducing FUTO's integration at its pinned revision is blocked. A different public revision is fetchable and supports a bounded Parakeet experiment, so it would be incorrect to report that all transcribe.cpp source is unavailable. ASR4ALL remains blocked by a concrete API gap. Neither result establishes a working Android adapter or unblocks SHARE-10/11 implementation. Their gate still requires SHARE-09 build, coexistence, lifecycle and device verification.

Use public commit `4807edaf210d0d7e8a6f7fb2a44b65966a2797f0` only as an explicitly separate candidate. Do not silently substitute it for FUTO's dependency. This is a go for further source/build investigation of Parakeet and a no-go for adopting FUTO's exact ASR4ALL integration with the available source.

## Exact FUTO dependency

FUTO Voice Input commit `35255631e8d53a597a4055810989a8dec4c31069` points `app/src/main/cpp/transcribe.cpp` to `b9e8a8e348db9a34052b72f8643bbe4597522181`. Its CMake adds that directory and links the `transcribe` target. The JNI wrapper selects `TRANSCRIBE_BACKEND_CPU`. These establish what the app requests, not what the unavailable engine implements. Sources: [immutable tree](https://api.github.com/repos/futo-org/voice-input/git/trees/35255631e8d53a597a4055810989a8dec4c31069?recursive=1), [CMake](https://github.com/futo-org/voice-input/blob/35255631e8d53a597a4055810989a8dec4c31069/app/src/main/cpp/CMakeLists.txt), [JNI](https://github.com/futo-org/voice-input/blob/35255631e8d53a597a4055810989a8dec4c31069/app/src/main/cpp/voiceinput.cpp).

Anonymous live checks returned:

| Request | Observed result |
| --- | --- |
| [FUTO raw pinned header](https://gitlab.futo.org/keyboard/transcribe.cpp/-/raw/b9e8a8e348db9a34052b72f8643bbe4597522181/include/transcribe.h) | Redirected to `/users/sign_in`; final HTTP 200 contained the sign-in HTML, not source. |
| [FUTO GitLab commit API](https://gitlab.futo.org/api/v4/projects/keyboard%2Ftranscribe.cpp/repository/commits/b9e8a8e348db9a34052b72f8643bbe4597522181) | HTTP 404, `Project Not Found`. |
| [Public repository commit API](https://api.github.com/repos/handy-computer/transcribe.cpp/commits/b9e8a8e348db9a34052b72f8643bbe4597522181) | HTTP 422, `No commit found for SHA`. |

These observations do not prove that the FUTO repository or commit does not exist. They prove that these anonymous routes did not supply it. The internal FUTO changes, merge base, ancestry and license delta cannot be audited from the app gitlink.

## Public candidate and reproducibility

The public `v0.2.4` annotated tag object is `c100a8f905af43eb20a1a07af2f718e3dbc40eed`; it resolves to commit `4807edaf210d0d7e8a6f7fb2a44b65966a2797f0`. Sources: [tag reference](https://api.github.com/repos/handy-computer/transcribe.cpp/git/ref/tags/v0.2.4), [annotated tag object](https://api.github.com/repos/handy-computer/transcribe.cpp/git/tags/c100a8f905af43eb20a1a07af2f718e3dbc40eed).

An anonymous GET of the [commit archive](https://codeload.github.com/handy-computer/transcribe.cpp/tar.gz/4807edaf210d0d7e8a6f7fb2a44b65966a2797f0) returned HTTP 200 and 43,775,342 bytes. SHA-256 of the downloaded response was `00c3bc4f2f505b4e6865c559a3c9aad30e98a48c9b6a304a2e73b50cb2f42522`. This verifies source retrieval only, not a reproducible binary. GitHub-generated archive bytes should not replace the commit identifier as the source identity.

The candidate's [CMake](https://github.com/handy-computer/transcribe.cpp/blob/4807edaf210d0d7e8a6f7fb2a44b65966a2797f0/CMakeLists.txt) requires CMake 3.16 and C++17. [GGML provenance](https://github.com/handy-computer/transcribe.cpp/blob/4807edaf210d0d7e8a6f7fb2a44b65966a2797f0/ggml/UPSTREAM) pins `353b63b439f27ab2cc19dac97ab1681ba6d2d084` plus `0001-fix-threadpool-oversubscription.patch`. These facts do not establish Android NDK compatibility, target-name coexistence or absence of exported-symbol collisions with this fork's existing GGML and llama.cpp. No NDK/AGP upgrade is justified by source availability alone.

## Relevant differences

FUTO JNI uses `transcribe_asr4all_stream_ext` and `transcribe_asr4all_stream_ext_init`. The public revision has no ASR4ALL-named path, no ASR4ALL registration in `src/transcribe-arch.cpp`, and no ASR4ALL text in any of its seven public headers. Public Parakeet and Whisper extension initializers do exist in their family headers, so searching only `include/transcribe.h` would incorrectly identify them as missing. Sources: [FUTO JNI](https://github.com/futo-org/voice-input/blob/35255631e8d53a597a4055810989a8dec4c31069/app/src/main/cpp/voiceinput.cpp), [public tree](https://api.github.com/repos/handy-computer/transcribe.cpp/git/trees/4807edaf210d0d7e8a6f7fb2a44b65966a2797f0?recursive=1), [architecture registry](https://github.com/handy-computer/transcribe.cpp/blob/4807edaf210d0d7e8a6f7fb2a44b65966a2797f0/src/transcribe-arch.cpp), [Parakeet header](https://github.com/handy-computer/transcribe.cpp/blob/4807edaf210d0d7e8a6f7fb2a44b65966a2797f0/include/transcribe/parakeet.h).

The public Parakeet 110M model documentation describes offline English transcription at 16 kHz. Its converter drops the auxiliary CTC head and uses the TDT/RNNT decoder. It explicitly does not support streaming. Therefore a future adapter must advertise final-only behavior for this model, unless separately proven otherwise. A generic streaming C API does not confer streaming capability on every model. Source: [pinned model documentation](https://github.com/handy-computer/transcribe.cpp/blob/4807edaf210d0d7e8a6f7fb2a44b65966a2797f0/docs/models/parakeet-tdt_ctc-110m.md).

FUTO's catalog names `parakeet-tdt_ctc-110m-Q8_0.gguf` and `asr4all-s-Q8_0-v2.gguf`. Matching the Parakeet filename alone does not establish identical model bytes or conversion metadata. Source: [FUTO catalog](https://github.com/futo-org/voice-input/blob/35255631e8d53a597a4055810989a8dec4c31069/shared/src/main/java/org/futo/voiceinput/shared/Models.kt).

The public Hugging Face model API returned revision `766f172fe70eb66785e3371664f53762e0fbafaa`, with Q8_0 size 135,373,280 bytes and LFS SHA-256 `7dd44c74a331d788a4e5f8b16913b3feb29ced22cf5613aad0e0f6cd30516296`. These are server metadata, not a local download/hash verification. A future experiment can use the [immutable model URL](https://huggingface.co/handy-computer/parakeet-tdt_ctc-110m-gguf/resolve/766f172fe70eb66785e3371664f53762e0fbafaa/parakeet-tdt_ctc-110m-Q8_0.gguf) and verify that digest. Source: [model API](https://huggingface.co/api/models/handy-computer/parakeet-tdt_ctc-110m-gguf?blobs=true).

## Build and lifecycle compatibility findings

The local [native CMake file](../../app/src/main/cpp/CMakeLists.txt) compiles legacy GGML directly into `voiceinput`, then adds S1's llama.cpp and links `s1mini` to `llama` and `ggml`. S1 forces shared libraries, dynamic GGML backends, CPU variants and OpenCL through cache variables. The candidate independently adds its vendored GGML, whose CMake declares `ggml`, `ggml-base` and `ggml::ggml`, and forces `BUILD_SHARED_LIBS` from its own option. A direct second `add_subdirectory` therefore has concrete target-name and shared-cache conflicts to resolve; separate source directories alone do not isolate CMake targets. Sources: [candidate CMake](https://github.com/handy-computer/transcribe.cpp/blob/4807edaf210d0d7e8a6f7fb2a44b65966a2797f0/CMakeLists.txt), [candidate GGML targets](https://github.com/handy-computer/transcribe.cpp/blob/4807edaf210d0d7e8a6f7fb2a44b65966a2797f0/ggml/src/CMakeLists.txt).

The candidate sets hidden C/C++ visibility, but this is not evidence that its final ELF exports, GGML shared-library names or backend discovery are isolated from legacy GGML and S1. The local [OpenCL discovery override](../../app/src/main/cpp/cmake/FindOpenCL.cmake) supplies S1's headers and `OpenCL` target. A CPU-only experiment must verify its actual configure cache and linkage rather than inherit S1's forced `GGML_OPENCL=ON` accidentally. Do not reuse either GGML version across engines without evidence of API/ABI compatibility. Separate build configuration and final library inspection remain necessary.

The [public API contract](https://github.com/handy-computer/transcribe.cpp/blob/4807edaf210d0d7e8a6f7fb2a44b65966a2797f0/include/transcribe.h) prohibits concurrent compute across sessions sharing one model. A session supports one thread at a time; its model must outlive it. Install cancellation before inference, then change atomic callback state to request cancellation from another thread. Replacing the callback during inference is unsafe. `transcribe_open` creates a session that owns its model, and `transcribe_session_free` releases both. Two-step initialization instead borrows the model. These rules require serialized inference and joining active work before unload.

FUTO's JNI already uses `transcribe_open`, an abort callback, status checks, capability queries and `transcribe_session_free`, so the public API provides corresponding basic operations. That overlap does not validate copying FUTO's ownership or cancellation implementation. The public API distinguishes aborted, unsupported-language, over-length and truncated-output errors; partial results may remain after cancellation/truncation. A fork adapter must preserve these distinctions and suppress canceled output, rather than treating readable text as successful completion. Language selection must follow the selected model's capabilities; the presence of `transcribe_detected_language` does not make English-only Parakeet multilingual. Sources: [FUTO JNI](https://github.com/futo-org/voice-input/blob/35255631e8d53a597a4055810989a8dec4c31069/app/src/main/cpp/voiceinput.cpp), [public API](https://github.com/handy-computer/transcribe.cpp/blob/4807edaf210d0d7e8a6f7fb2a44b65966a2797f0/include/transcribe.h).

The candidate's [license](https://github.com/handy-computer/transcribe.cpp/blob/4807edaf210d0d7e8a6f7fb2a44b65966a2797f0/LICENSE) is MIT. Its [dependency notices](https://github.com/handy-computer/transcribe.cpp/blob/4807edaf210d0d7e8a6f7fb2a44b65966a2797f0/THIRD-PARTY-LICENSES.md) identify GGML and miniz as MIT and include their notices. Preserve those notices in any distribution. Model licensing is separate: the pinned Parakeet model documentation identifies CC-BY-4.0. This is a source-level notice check, not a full audit of every optional backend or unavailable FUTO change.

To repeat only the source retrieval check in PowerShell, download the commit archive to a temporary file and compare its checksum with the observation above:

```powershell
$share09Archive = Join-Path $env:TEMP 'transcribe-4807edaf.tar.gz'
Invoke-WebRequest 'https://codeload.github.com/handy-computer/transcribe.cpp/tar.gz/4807edaf210d0d7e8a6f7fb2a44b65966a2797f0' -OutFile $share09Archive
Get-FileHash -Algorithm SHA256 -LiteralPath $share09Archive
```

## Remaining gate

No Android native build, JNI adapter, model inference, GGML/OpenCL coexistence test, cancellation/unload stress test or phone benchmark was performed in this source investigation. The public project's model validation claims are not measurements of this fork or the user's phone. Cold/warm load, first text, finalization, memory, sustained performance and quality against existing backends remain unverified. GPU support remains outside the CPU baseline.

Next work may evaluate the pinned public candidate in an isolated ARM64 build, with the coordinator's shared-host build slot. ASR4ALL needs anonymously reproducible FUTO source or an explicitly scoped public implementation first. Keep Orukeet, saved selections and production adapters unchanged until the complete SHARE-09 gate passes.

## Public adapter preparation, 2026-09-28

The [isolated experiment](../../tools/transcribe_experiment/README.md) now implements final-only `SpeechBackend` calls using the pinned public source. Its own CMake configure tree uses static CPU dependencies and a distinct JNI library with an export allowlist. It does not enter the app source sets, change model selection, share S1's GGML targets or claim ASR4ALL compatibility.

The source archive SHA-256 was independently rechecked against the observation above. Three host tests passed using JDK 21.0.10, Gradle 8.11.1, Kotlin 2.1.0 and coroutines 1.9.0 via `gradlew.bat -p tools/transcribe_experiment test --max-workers=2 --no-daemon`. They cover canceled loading, invalid model/language rejection, full 121-second audio handoff, repeated success/failure, cancellation before release, suppression of canceled text and idempotent close. The controlled session double never loads native code. The initial cancellation test failed to compile before the adapter existed and passed after implementation.

`javap` confirmed that the compiled Kotlin class declares the five JNI methods expected by the wrapper/export check. `experiment.ps1` without `-Build` passed source verification. Android/native compilation is queued with the coordinator, and has not run at this checkpoint. The ELF export checks in that script are prepared assertions, not observed passes. Actual JNI inference, APK integration, same-process runtime coexistence and phone measurements remain unverified. The production promotion decision remains no-go pending those results.

## Native build and safe pause, 2026-09-28

After SHARE-06 released the native build slot, the ARM64 build reached a successful link with NDK 28.2.13676358, Clang 19.0.1, CMake 3.22.1, Android API 26, Release and two workers. Initial attempts failed because upstream `src/CMakeLists.txt` exports `${CMAKE_SOURCE_DIR}/include`, which points at this experiment when embedded. Adding the pinned source include directory to the `transcribe` target fixes both its own compilation and its JNI consumer without changing the downloaded source.

The successful command was `cmake --build tools/transcribe_experiment/build/arm64 --target share09_parakeet --parallel 2`, exit 0. Read-only inspection with NDK `llvm-nm --dynamic --defined-only` found exactly the five expected JNI exports. `llvm-readelf --dynamic` found only `libm.so`, `libdl.so` and `libc.so` dependencies. No shared GGML, llama, transcribe, OpenCL or libc++ dependency appeared. This establishes build/link isolation, not successful same-process coexistence.

The unstripped `build/arm64/libshare09_parakeet.so` is 44,034,408 bytes with SHA-256 `f649faa0487b8a31ea3663e9d866207205bfc340105c7418823005e5dbbc82ac`. This is one local output, not a bit-reproducibility or installed-size claim. Archive builds print transcribe commit `unknown`, and GGML's Git detection reports the enclosing app commit; the verified archive hash and pinned source revision above remain the source identity.

The user requested a safe pause as this build finished. No fresh end-to-end `experiment.ps1 -Build` rerun, notice-copy step, device/emulator operation or inference test followed. The native log and library remain under the ignored `tools/transcribe_experiment/build/` directory. No native process remained running at checkpoint, and the build slot was not handed to another chat. Resume under the new serial integration coordination. Source/host evidence and the ELF inspection do not complete SHARE-09 or unblock its dependent models.

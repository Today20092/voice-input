# Native release packaging (#31)

Date: 2026-10-23. Parent #30. Baseline: signed v1.4.8 APK, 139,809,896 bytes.

## Change

Release builds use AGP's `ndk.debugSymbolLevel 'FULL'` and ordinary native stripping. Only `libwhistle_runner.so` retains its exact executable bytes: changing those would violate its pinned SHA-256. `useLegacyPackaging true`, the ARM64 ABI, all CPU variants, OpenCL, signing and model runtimes remain configured as before.

`tools/verify_native_packaging.py` checks the APK and separate symbol ZIP, rather than Gradle source text. It requires the exact 29-library v1.4.8 set, compressed/extracted packaging, pinned Whistle bytes, absence of DWARF debug sections in eligible shipped libraries, at least 16 KiB ELF LOAD alignment, matching symbol build IDs and native symbol tables. Project JNI boundaries must retain FULL source/line debug sections. Already-stripped vendor dependencies cannot provide symbols they never shipped.

Release CI runs the checker after its existing package, notice, reference-transcription, extraction and signature gates. It uploads `native-debug-symbols-<commit SHA>` separately from the APK, with missing symbols treated as an error. Retain/download that CI artifact for the exact build being diagnosed; it follows GitHub Actions artifact retention, not permanent release-asset retention.

## Red/green evidence

- Red: the checker rejected the actual v1.4.8 APK with `Debug sections still shipped in libOpenCL.so`.
- Green: ran the real AGP 8.9.2 `stripPlayStoreReleaseDebugSymbols`, `extractPlayStoreReleaseNativeSymbolTables` and `mergePlayStoreReleaseNativeDebugMetadata` tasks using the signed v1.4.8 native payload as controlled input. Native merge/build prerequisites were excluded; these are focused packaging checks, not a fresh full native compilation. The release policy applies to every release flavor.
- Replaced only native entries with these actual AGP outputs in a controlled unsigned repack. The checker passed all 29 libraries and the actual AGP-generated FULL symbol archive (34,670,487 bytes), including the exact Whistle hash and matching debug build IDs.
- Negative checks rejected a missing Harper symbol file and a removed native runtime.
- Ruff format/lint, ty and `git diff --check` passed for the verification script/change. No Python dependency was added; CI uses its existing Python interpreter and the pinned NDK's LLVM readelf.
- The baseline APK's v2 signature independently verified with Android build-tools 35.0.0 apksigner. The controlled repack is deliberately unsigned and must never be installed or published as a release.

## Controlled size measurement

| Native payload | Signed v1.4.8 | Actual AGP stripped outputs | Difference |
| --- | ---: | ---: | ---: |
| ZIP compressed bytes | 82,063,423 | 48,843,531 | -33,219,892 (~31.68 MiB) |
| Extracted bytes | 265,485,744 | 121,056,528 | -144,429,216 (~137.74 MiB) |

The unsigned controlled repack is 100,544,826 bytes, but its overall APK difference is **not a release-size result**: repacking changes compression of unrelated resources and invalidates APK signing/alignment. Compare the final integrated signed standalone APK against v1.4.8 with the checker's `--baseline` option. Stripping debug data does not establish lower inference RAM use or faster inference.

## Remaining gates

Final integrated signed CI [40108948096](https://github.com/Today20092/voice-input/actions/runs/40108948096), source `b995256`, passed all build/reference/test/lint/instrumentation-compilation/signature/native-symbol checks. Its APK is 105,131,976 bytes (100.26 MiB), versus 139,809,896 bytes (133.33 MiB) for signed 1.4.8: 33.07 MiB / 24.80% smaller. Compressed native entries are 47,373,376 bytes and extracted native entries 121,056,528 bytes. The separate matching symbol archive is retained in CI. These final build measurements supersede the controlled unsigned repack estimate above; physical-device gates below remain pending.

The integration run must perform full signed standalone assembly, JVM tests, lint, instrumentation compilation, existing package/reference/signature gates and this artifact checker. Report final APK size from that build. No physical ARM64 device was available: model loading/inference (including every CPU/OpenCL path), Whistle execution, IME, microphone, and installation/runtime smoke tests remain pending under existing #15/#17 gates. LLVM may warn that separate `.dbg` files have no dynamic table; these AGP debug-only files are not executable libraries, and their sections/build IDs are still checked.

Reference: [Android native symbols documentation](https://developer.android.com/build/include-native-symbols), fetched through Context7 for this implementation.

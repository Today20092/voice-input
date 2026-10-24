# Ticket 19: diagnostics privacy and transcript retention

Issue: https://github.com/Today20092/voice-input/issues/19

## Implemented

- Ordinary S1 records, copied reports, both export types and retained legacy records use the same allowlisted projection. Raw loader errors, native device strings, arbitrary exception text and unknown JSON fields are discarded. Technical native timing/token counts, configuration categories, validated report UUIDs and pinned model/version metadata survive. Benchmark output hashes and free-form validation metadata are not ordinary diagnostic content.
- Native Whisper no longer logs its vocabulary prompt or model path, and its internal logging follows the configured silent callback. Native S1 loader logging emits fixed categories and JNI exceptions use an explicit category allowlist. Kotlin recognition/download/migration/payment sinks no longer print exception messages, stack traces or payment payloads.
- Capture access and saving purge abandoned `.tmp` files. Captures over 2 MiB are removed before decoding; valid and unreadable files share the latest-ten cap. Expired/corrupt files use the existing seven-day bound, and failed deletion raises a content-free error instead of claiming success. Atomic replacement preserves an existing record on replacement failure.
- Transcript ZIP modification time records the oldest included capture expiry. Startup, capture access and export purge expired cached transcript ZIPs; legacy cached ZIPs expire immediately on access. Recording/deleting/clearing captures invalidates prepared transcript ZIPs. Snapshot/export and capture mutations use the same monitor to prevent a deletion/export race recreating removed evidence. Standard and audio-history retention remain separate.
- Partial transcript exports are deleted if ZIP creation fails. The archive README distinguishes legacy unverified delivery from schema 2 Android handoff acceptance; acceptance does not prove another app displayed text.

## Verification

The user requested minimal local RAM usage. No Gradle, APK/native build, emulator, model download or Android test was launched locally.

Test-first commit `c9495b2` adds existing-public-boundary regressions for realistic sensitive metadata in exported reports, abandoned writes, oversized corruption and corrupt-file count bounds. The parent dispatched it against the old implementation in remote CI: https://github.com/Today20092/voice-input/actions/runs/40112697133 . This document does not claim that job passed or failed; use its actual result.

`9bf5e96` adds cache-expiry coverage before the corresponding implementation. `S1MiniDiagnosticArchiveTest.cachedTranscriptArchiveExpiresWithItsOldestEvidenceAndLegacyArchivesAreRemoved` exercises expiry at the exact boundary and preservation of ordinary cached reports. Archive/privacy and capture-store tests require CI execution against the integrated branch.

Local `git diff --check` passes. A source search finds no remaining `printStackTrace`, `stackTraceToString`, or Throwable-argument `Log.e` calls in app Kotlin code, apart from the diagnostic failure policy comment. This is a source check, not a packaged global no-leak proof.

## Required before declaring all acceptance gates passed

- CI JVM tests, lint, native compilation, instrumentation compilation and APK content/signature checks.
- Packaged synthetic vocabulary/path/exception/native-loader sentinels across Logcat/stdout/stderr, persisted ordinary records, copied reports and both export types. Inspect only locally; retain content-free pass/fail evidence.
- Packaged explicit consent, process interruption, startup purge, seven-day/latest-ten retention, individual deletion, blocked deletion and cache invalidation. Include concurrent export versus deletion and unexpected clock changes.
- Verify the packaged ACRA setting and its explicit consent behavior separately. Source still gates the existing uploader with `BuildConfig.ENABLE_ACRA`; this change neither enables uploads nor proves which configuration a signed APK contains.

Failures to delete a private file remain observable failures and cannot guarantee physical removal from a failing filesystem. Audio history, benchmark recordings and intentionally captured transcript stages remain separately authorized content stores.

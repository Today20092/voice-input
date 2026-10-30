# Transcript capture storage execution (#36)

Date: 2026-10-23. Parent #30. Scope: capture startup/settings execution and existing store retention/loading.

## Change

- Startup capture expiry runs in the existing IO coroutine alongside separate audio-history expiry. Its failure remains nonfatal to startup and does not skip audio-history cleanup.
- Context-based capture load/delete/clear/purge APIs are suspend functions that move filesystem work to `Dispatchers.IO`. Transcript export preparation, URI preparation, report copying and combined S1 clearing also move their disk work off the settings thread; clipboard/share UI stays on the caller's UI coroutine.
- The settings screen loads asynchronously on entry/resume, disables conflicting operations while busy and displays loading, safe error text and Retry. Retry reruns the failed operation. It does not replace a failed scan with an empty result; successful delete/clear reloads the list. Cancellation is rethrown, and store exceptions reach callers unchanged.
- The store decodes each file once during a retention/load pass and reuses those decoded captures. Delete also reuses its retention pass. Unreadable recent records remain counted; unreadable old records expire by file timestamp; valid records retain seven-day/latest-ten behavior and newest-first order. Scan and deletion/clear failures are reported instead of silently claiming an empty list or success.
- Explicit capture consent, stage length limits, private paths, archive labeling and standard-diagnostic exclusions remain intact. No dependency or generic repository/state framework was added.

## Regression evidence

The real-filesystem red test `inaccessibleCaptureDirectoryIsAnErrorInsteadOfAnEmptyList` failed before implementation because an invalid capture-directory path returned an empty result. It passes after the store reports an `IOException`. The seven public-store tests cover actual stages/truncation, newest-ten order, expiry, retained/expired corruption, reopen/delete/clear and writing again after clearing. All 30 S1 JVM tests passed with:

```powershell
.\gradlew.bat --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8' :app:testDevDebugUnitTest --tests 'org.futo.voiceinput.s1.*'
```

Two Android execution-policy tests call the actual context-based APIs from `Dispatchers.Main` using an isolated filesystem context that rejects main-thread file access. They cover startup purge, load, delete, clear and original-error propagation. Two Compose tests exercise the real diagnostics screen: storage error followed by repair/Retry, and resume/viewing/deleting/clearing captures through the background APIs. Their filesystem/exports are isolated from normal app data. These tests are compiled, not executed on a device.

An initial Android-test compile found an incorrect test import, which was removed. A subsequent combined lint/compile attempt hit a host JVM native-allocation failure, recorded in `hs_err_pid50416.log`, rather than a test failure. The lower-memory retry passed both `:app:compileDevDebugAndroidTestKotlin` and `:app:lintDevDebug` using one worker, no parallel Gradle execution, 1536 MiB heap and two active processors. `git diff --check` also passed.

## Limits and remaining gates

No device was connected. Instrumentation/Compose execution, rendering, resume/rotation interaction, real StrictMode enforcement, startup timing and capture/export failure scenarios on Android remain pending. JVM tests and code inspection do not establish phone responsiveness measurements.

This does not resolve broader #19/#21 issues: cached transcript-export retention/invalidation, abandoned temporary capture retention, broader content/logging concerns, truthful delivery acceptance and all diagnostic failure paths remain separately tracked. The startup purge remains best effort; opening settings exposes a persistent storage problem and Retry. Capture-store error messages add no transcript/path content to ordinary diagnostics, and this change adds no logging.

The Coroutine `withContext` execution/cancellation behavior was checked using Context7 against the [official Kotlin API](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/with-context.html).

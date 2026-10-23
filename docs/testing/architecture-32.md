# Shared final transcript processing (#32)

Based on integration commit `47b553630d276ba89f7bca225f585ee1c792236c`.

`TranscriptFinalizer.clean` now prepares both dictated and retranscribed text using S1, Harper, then personal vocabulary. The shared entry owns current settings/language gates and content-free stage metrics. It returns text and S1 valid-empty status, without claiming that prepared text has been accepted by another application. History still rejects unrecognized raw speech, replaces a saved transcript only after successful processing, and releases its model in the existing cancellation-safe finally block. Live dictation keeps its raw backup and generation/delivery checks. Model comparison still exits with raw output before finalization.

The former Harper Context wrapper moved into this entry. Native Harper guards remain in the existing cleaner. S1's process isolation, model fallback and English gate are unchanged. Separate transcript capture still requires an S1 diagnostic report and the explicit capture setting, retains existing private storage/retention, and cannot fail text processing. Capture writes now explicitly use the IO dispatcher. A coroutine activity check after S1 prevents a swallowed runtime cancellation from continuing into final text preparation.

## Focused verification

- Red: the initial `TranscriptFinalizerTest.cleanupPrecedesVocabularyForBothS1SuccessAndFallback` failed compilation because the shared entry did not exist. Green after implementing the shared processing entry and caller adoption. The expected output is a fixed example; only native Harper bytes and the external S1 runtime result are substituted. Actual Harper policy and actual personal vocabulary processing execute.
- Expanded preservation checks cover Harper disabled/non-English bypass, native unavailability, valid-empty S1 output, invalid-empty status, successful history replacement, and failure/cancellation preserving prior text and audio. These are preservation checks, not claims of a separate preexisting bug in each case.
- Passed 29 JVM tests: 5 shared finalizer, 8 Harper policy/English gate, 10 history store, 6 S1 English gate.
- Command: `:app:testDevDebugUnitTest --tests org.futo.voiceinput.TranscriptFinalizerTest --tests org.futo.voiceinput.harper.* --tests org.futo.voiceinput.history.AudioHistoryStoreTest --tests org.futo.voiceinput.s1.S1MiniEnglishGateTest --max-workers=2 --console=plain`.
- Passed `:app:compileDevDebugAndroidTestKotlin --max-workers=2 --console=plain`. Includes a shared Context-entry device regression using actual Harper native cleanup and changed preferences/detected language. Compilation does not execute that test.
- `git diff --check` passed.

No connected Android device: actual microphone/IME/history UI parity, native S1/Harper inference, capture IO failure/consent on-device and performance checks remain pending. Full integrated JVM/lint/APK verification belongs to the integration branch gate. No version bump or release publication.

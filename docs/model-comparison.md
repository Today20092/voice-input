# Model comparison

ADB runs accept `-WarmRepetitions 2` for an initial comparison (default five, range one to five). The saved JSON and Markdown record the count; existing reports without the field retain the five-repeat interpretation. With three clips, two repeats produce seven timed observations and three untimed warmups per model. Resuming a run keeps its saved protocol. To collect the deferred three repeats later, start a separate run with the same suite and model IDs and `-WarmRepetitions 3`; retain both reports and compare their build/device conditions before combining timings. Completed five-repeat models need no supplemental run.

Open Advanced settings, then Model comparison. Record one or more passages or import audio, listen back, and edit each reference to match the words actually spoken. Choose installed or downloadable models. The confirmation shows the additional download and installed storage. Model downloads use the existing downloader with daily-model selection disabled. Models run sequentially against the same saved canonical audio.

## In-app comparison on beta 3

The integration branch `codex/model-comparison-beta3` starts from released `v1.4.7-beta.3` (`9312d1c`) and includes its Phonon 2 backend. This is a local test build, not a published beta release.

1. Open **Advanced → Model comparison** in **Voice Input Dev**.
2. Choose a passage or enter your own prompt. Tap **Record**, read it, then **Stop recording**. You can leave the reference blank for an output-and-speed comparison without accuracy scores.
3. Tap **Listen** and correct **Words actually spoken** if needed. **Remove recording** lets you discard a take and record again.
4. Check two or more models. Installed models are selected initially; missing models show their download and storage requirements. Unsupported recording languages are skipped without downloading those models.
5. Tap **Compare selected models**, review the download totals, and confirm. Keep the screen open while each model processes the same audio. Each model makes a first pass, one untimed warm-up, and five timed passes; large models can take several minutes.
6. Read each transcript, word/character error rate, model loading time and warm timing range. Expand individual measurements if outputs differ. Reports remain on the phone and can be exported as JSON or Markdown. **Use as daily model** applies a choice explicitly.

Recording and comparison keep the screen awake. Rotating during a model download retains its pending result. Leaving the screen while recording stops and saves the take; native decoding may finish its current call before cancellation takes effect. The in-app screen exposes the fixed-recording comparison; guided live readings and the comprehensive USB/ADB suite are deferred.

The dev APK installs alongside the regular app and has separate models, settings and recordings. Existing compatible Voice Input Dev installations can be updated in place; the regular release's model downloads are not shared with it.

Input is limited to 180 seconds and 64 MiB per clip. The import path supports AAC in M4A containers and 16-bit little-endian PCM WAV, mono or stereo at 8 to 48 kHz. It rejects unsupported codecs, changing formats, corrupt/empty samples, oversized files and overlong recordings. Stereo is averaged before conversion. A 32-tap windowed-sinc low-pass filter with 1024 fractional phases produces 16 kHz mono float32 little-endian samples. Conversion, defensive input copying, scoring and report writes are excluded from measured decode time. Originals and canonical inputs remain in private benchmark storage.

The comparison defaults to English. Choose a recording language before adding clips; every clip in a comparison uses that language. Explicit-language models receive it without changing ordinary dictation settings. English-only models and unsupported explicit-language configurations are skipped with a reason. Orukeet, Parakeet TDT and Redux retain their automatic language detection, disclosed in each case identity. Orukeet, Parakeet TDT, Cohere, Redux and Whisper return final text; streaming models are run through the same final-result interface. ASR4ALL includes inseparable built-in processing, disclosed in reports. Configurable S1/Harper cleanup and personal vocabulary are excluded.

Each independent recognizer measures construction/loading, one first decode on the first clip, one untimed warm-up per clip and five measured warm decodes per clip. The recognizer is released before another model loads. Release time measures the API call, not proof that Android reclaimed its memory. Warm results preserve all transcripts and timings, including variations. Reports include minimum, median and maximum, startup-inclusive loading plus first decode, and real-time factor, defined as decode seconds divided by recording seconds. Model-unloaded app runs and fresh-process ADB runs may both have warm filesystem caches.

The scoring policy `nfkc-root-lower-punctuation-v1` uses Unicode NFKC, locale-independent upper/lower case folding with dotless-i preserved and final sigma folded, removes straight/curly apostrophes, converts other Unicode punctuation/symbols to word boundaries and collapses whitespace. CER counts Unicode code points including normalized spaces. Word and character edit paths prefer substitution, then deletion, then insertion on ties. Numbers remain literal, fillers remain present and punctuation/capitalization are not spoken-word errors. WER can exceed 100% because of insertions. Empty or missing reference has unavailable accuracy; empty successful output is scored as deletions. Recognition failure is never scored as empty successful output. Unverified supplied references have unavailable accuracy until explicitly reviewed.

Reference text, audio hashes, actual sample counts, conversion settings, model/artifact identities, runtime versions, execution order and device/build metadata accompany results. Memory is sampled process PSS in KiB at a requested 250 ms interval. Scheduling and native work can delay sampling, so it is not an exact allocation peak. Thermal, battery and power state are captured before and after each case where the Android API supports them. Startup overhead and energy consumption are unavailable. The app revision identifies the checkout commit; local edits require retaining the tested APK and its hash with published evidence.

Downloads, cancellation and load failures retain partial results. A model-load failure does not invalidate the installed marker or change the daily model. Completed/failed cases are immutable within a saved run. Start a new comparison to repeat a case. A process interruption marks in-progress results as interrupted on recovery. Missing models produce skipped rows and do not block installed models. Benchmark ownership blocks simultaneous dictation, duplicate fixed-audio runs, model deletion and replacement.

JSON and Markdown reports contain reference text and recognized transcripts, visibly labeled in the screen, report and export filename. Audio is excluded from report exports. Nothing uploads automatically. Benchmark content is separate from audio history and standard diagnostics. Delete a benchmark and its audio independently of model removal. Optional model removal checks new downloads by default, protects the selected daily model and bundled assets, and uses lifecycle deletion safeguards for managed models. Legacy Whisper removal requires first switching the daily model away from Whisper. Shared directories are counted once.

## USB/ADB runner

Deferred at the maintainer's request on October 16, 2026. The runner, supplied recordings and earlier results are retained for later; no comprehensive ADB benchmark is part of the beta 3 in-app integration.

The control interface exists only in the Android instrumentation APK. It checks that the target is debuggable. Neither benchmark activity is exported. No third-party application can invoke downloads or read/export recordings through a benchmark component.

Use JDK 17 and build both APKs:

```powershell
.\gradlew.bat :app:assembleDevDebug :app:assembleDevDebugAndroidTest
.\tools\test-model-benchmark.ps1
```

Once the phone is connected and USB debugging authorized, run the short pilot. It takes the first clip and the first two installed catalog entries, including bundled Whisper where available:

```powershell
.\tools\run-model-benchmark.ps1 -Install
```

Select exact model IDs if desired. The runner retrieves the current managed catalog plus legacy Whisper variants from the app. It does not maintain a separate list. The thorough run uses all variants and all supplied clips:

```powershell
.\tools\run-model-benchmark.ps1 -Mode Full -DownloadMissing -CoolingSeconds 30
```

Downloads/storage totals are printed before downloads start. Without `-DownloadMissing`, missing models become skipped rows. Download failures, timeouts and process crashes preserve evidence and continue other cases. ADB force-stops the application before each model and shares the same runner/schema/scoring code as the screen. It retrieves reports with `run-as`, never by parsing transcripts from Logcat. No root or filesystem cache clearing is used. Case waits default to 1800 seconds, are bounded and configurable; downloads are bounded to 20 minutes per case.

Use `-Serial` when multiple devices are connected. `-ResumeRunId <UUID>` reloads the persisted suite and attempts pending cases without overwriting completed/failed/interrupted rows. Create a new run to repeat those rows. `-ReferencesVerified` is an explicit assertion that every selected reference has been checked against the supplied recording. Until then accuracy is unavailable. Do not use that flag just to populate a table.

Reports are retrieved under `benchmarks/results/<run-id>/`. `host-run.json` records cooling intervals, timeout and host-run date. `report.json` and `report.md` contain personal dictated text. Keep them private unless reviewed for publication. Originals stay on the device in private benchmark storage. The source suite is `benchmarks/audio/phone-room/suite.json`; its hashes are checked on both host and phone. The host temporarily stages supplied source files in `/data/local/tmp` during import, then removes them. Only use the supplied authorized recordings for public evidence.

Inspect the pilot before the full suite. Confirm canonical durations/hashes, model identity, transcripts, all six measured observations per clip, independent release, and readable reports. Device evidence is pending until this has actually run on the phone.

## Guided live readings

This experimental harness is retained for later work and is not linked from the in-app comparison screen. Each selected variant has keyboard and recognition-activity buttons. Keyboard readings use a controlled editor in the app; choose Voice Input using Android's keyboard picker. Activity readings use an explicit controlled caller for the existing recognition activity. English configuration is overridden only for that reading without modifying saved daily settings. Audio history and optional transcript diagnostics do not receive benchmark content.

The keyboard test first shows the controlled editor's keyboard before opening the picker: Android hides auxiliary voice-input entries when the soft keyboard is hidden. The development flavor is labeled `Voice Input Dev` so it can be distinguished from the regular app even when the picker truncates long names. Both behaviors were checked on the physical phone; the rotation device test also checks that a voice-input entry appears after starting with the keyboard hidden.

Recordings use the existing microphone/VAD/streaming/final-recognition/delivery paths. Saved milestones use `System.nanoTime`: recording started, latest VAD-positive frame, first nonblank partial, capture stopped, manual Stop, raw final ready, delivery returned and controlled receiver observed. Last VAD-positive frame is a capture-loop approximation of speech end, not an independently measured acoustic endpoint. Stop-to-final is unavailable when Stop was not used. First partial is unavailable for final-only models. Edit and save the actual spoken reference after each reading. Delivery verification compares trimmed delivered text with the controlled receiver to expose missing/duplicated words while allowing surrounding insertion spacing.

Live readings appear separately in JSON and Markdown. Each reading differs, so their accuracy is not an identical-input model ranking. A guided full pass requires human readings for both entry points for every selected variant. The ADB fixed-audio command does not pretend to automate a human reading. Hardware microphone, keyboard insertion and activity delivery validation must be performed on the physical phone.

## Verification status

In-app beta 3 integration on October 16, 2026:

- `:app:assembleDevDebug` and `:app:assembleDevDebugAndroidTest` pass with JDK 17. The test app is `Voice Input Dev`, version `1.4.7-beta.3-dev`.
- `:app:testDevDebugUnitTest` passes: 233 tests, zero failures, two skipped optional published-archive tests. All 18 benchmark unit tests pass. A new repeated-streaming test checks that seven passes reset decoder state rather than accumulating earlier audio.
- The two earlier diagnostics failures were caused by Windows refusing to replace an existing preferences file through `File.renameTo`. Preferences now use atomic replacement, and both tests pass.
- New Android tests compile for catalog/backend coverage including Phonon, recording removal and unique recording IDs, retained download completion after recreation, and saved per-model transcript results. No phone is attached, so these tests and the microphone/download/device UI flow have not been executed for this integration.
- The app and test APKs build with limited Java processors/workers. Earlier unrestricted attempts exhausted Windows committed memory; this was a host resource failure rather than a Kotlin compile error.
- `:app:lintDevDebug` passes with one worker, a 768 MiB Gradle heap, and `JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=2 -XX:+UseSerialGC -Xss512k'`. Earlier attempts exhausted Windows committed memory during analysis.
- Test APK: `build/model-comparison/voice-input-beta3-model-comparison-dev.apk`, SHA-256 `4a8e98630e6017806e1d812d7253bec57884a6830f715a0234efc0cdbdb1b86e`. This APK was packaged from the integration sources before committing them; its embedded revision is the upstream beta 3 base, `9312d1c`. Retain the APK/hash when reporting results rather than treating that base revision as the full source identity.
- The comprehensive ADB benchmark and guided live-reading validation are explicitly deferred. Existing source, original recordings and ignored local result files are retained. No new model performance claims are made.

Local verification on October 10, 2026:

- Dev debug application and Android test APKs build successfully with JDK 17.
- `:app:lintDevDebug` passes.
- All 17 tests in `org.futo.voiceinput.benchmark.*` pass, covering normalization, edit counts, empty reference/output, WER over 100%, timing boundaries, untimed warm-up, defensive input copies, failures, cancellation, release, ownership, catalog coverage, persistence, reference review and controlled delivery.
- The full JVM suite executes 226 tests: two failures and one skip. The failures are in unchanged `DiagnosticStoreTest` tests: `detailed collection expires and can be stopped early` and `opting out survives restart and clear does not reenable collection`, both reporting `Unable to save diagnostic preferences`. The full suite is not passing. This full-suite execution predates the two latest added benchmark tests; the focused suite was rerun afterward.
- PowerShell syntax and the three supplied original recording hashes pass `tools/test-model-benchmark.ps1`.
- `git diff --check` passes.

Five Android device tests pass on a Samsung SM-S938U running Android 16: canonical WAV conversion/storage, changed-audio rejection, failed-import cleanup, Whisper selection and guided-reading recreation. The recreation test initially had an invalid JUnit return type; it was corrected and passed on the phone.

The three supplied AAC recordings import successfully. Pilot run `b5f44de1-7e7d-4f87-aadc-a46750a39a1a` completed with bundled Whisper Tiny English; its reference was reviewed by the recording owner and its report rescored without repeating inference. Full run `6945d405-1e91-4942-8cfb-6fa20fc363c9` is in progress; each completed model case has 16 measured observations across three clips. The host runner stopped after the first seven cases; persisted results remain intact and the run has resumed without repeating them. The full pass must finish and be reviewed before claiming catalog-wide performance coverage. APK updates occurred between segments; `build-evidence.json` records the resumed build hash and this limitation.

Human microphone readings through both the recognition activity and keyboard completed with bundled Whisper Tiny English, with matching delivered and observed text. The recording owner confirmed that the keyboard sentence appeared once in the controlled editor. The usual FUTO keyboard was restored afterward. Live readings for other variants remain pending; these two readings do not establish catalog-wide live coverage. Native-inference cancellation and the complete user workflow still require review. Issue #17 should remain open until the remaining phone checks and report review are complete.

## UI and Whistle review build — October 21, 2026

The comparison page now uses the settings header, spacing and themed surfaces.
Audio, individual recordings, models, results and saved comparisons have separate
sections. Model families expand like Model Options; each choice shows installation
state, size and languages. Technical instructions and individual measurements
remain available in expandable details. Results keep transcripts and useful
timings visible, and saved comparisons show dates instead of opaque run IDs.
Actions wrap onto separate rows with enlarged text; reference fields show at most
six lines while preserving their full editable contents.

Whistle is included in the same recorded/imported-audio flow. Its seven supported
language codes are enforced, and its benchmark identity reports automatic
detection. Every pass includes worker startup and model loading. Sampled app PSS
excludes that worker; both limitations appear in the report metadata and results.
The daily model remains unchanged unless explicitly selected from results.

Verification:

- 71 focused JVM tests: zero failures, one optional host-runtime test skipped.
- Dev debug assembly, Android-test APK assembly and dev debug lint pass.
- All 11 Android comparison/UI tests pass on an API 37 emulator, including import,
  recreation, catalog/backend coverage, Whistle language/readiness rules,
  transcripts, exports, keyboard focus, back navigation, and error/progress states.
- The actual page was rendered at 320 dp with 200% text and at 640 dp using local
  Compose density overrides. Single-line checks cover short action labels to
  prevent splitting words. Dark and light screenshots were inspected. Text and
  control contrast checks cover the production Voice Input theme, Dev Yellow and
  Classic Material Light.
- Earlier emulator attempts encountered graphics-buffer errors, startup timeouts,
  and low-memory process kills. The final successful run used software rendering
  and 4 GB of emulator RAM.
- The ARM64 Whistle runner in the APK matches its pinned SHA-256, and its notice
  is packaged. No non-ARM64 libraries were found.

Review artifacts are local and ignored by Git under `build/model-comparison/`:
`voice-input-beta5-comparison-ui-dev.apk`, `ui-audio-dark.png`,
`ui-whistle-selected.png`, `ui-results-light-sample.png`,
`ui-large-text.png`, `ui-large-text-editing.png` and `ui-wide-light.png`.
The results preview uses synthetic test data, not model accuracy measurements.

The debug APK reports `1.4.7-beta.5-dev`, version code 74, application ID
`org.futo.voiceinput.dev`. It includes current local changes; its embedded
`5c74eb3` revision is not the complete source identity. APK SHA-256:
`e5fe81d8762843d98ebae2496df269e7fc1937c218ebad2acb4002fca1f6199d`.

This is a review build, not a published beta. Physical-phone Whistle inference,
microphone recording, speed, memory and thermal comparisons remain unverified.
The full Windows JVM suite was not repeated; the two previously documented
DiagnosticStore failures are outside these focused checks.

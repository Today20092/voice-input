# Upstream master integration — November 27, 2026

## Scope and ancestry

Integration branch: `Today20092/merge-upstream-2026-11`.
Fork baseline: `1c8aeef` (`origin/development`).
Upstream tip: `cfdd409` (`futo-org/voice-input` master), 16 commits after the
`c4590aa` baseline of the [October 30 integration](upstream-master-integration-2026-10-30.md).
The branches diverged by 398 fork-only and 16 upstream-only commits.

Merge `00ad682` has `1c8aeef` and `cfdd409` as its parents. Its tree is identical
to its first parent, so the merge records ancestry and changes no file. The
adopted changes are separate commits after it.

## Conflict resolutions

- Keep the fork's version of every conflicting file under `app/`.
- Revert the one cleanly merged hunk, in `SettingsActivity.kt`: a
  `performMigrationsIfNecessary()` call for upstream's model-option migration,
  which the fork does not have.
- Exclude upstream's `shared` module, including the newly added
  `MicPermissionActivity.kt` and `themes.xml`.
- Exclude the `app/src/main/cpp/transcribe.cpp` submodule. The merge adds no
  gitlink and no `.gitmodules` entry.

[ADR 0001](../adr/0001-use-sherpa-onnx-for-nvidia-streaming.md) and
[ADR 0004](../adr/0004-keep-external-keyboard-integration.md) still apply.
The integration commits left defaults, saved setting keys, the model catalog,
package IDs, version numbers and release notes unchanged. The follow-up for
`fb66c08` and `2424b6b` changes one default and adds two setting keys.

## Upstream commits

Source paths are under `app/src/main/java/org/futo/voiceinput/` unless shown in full.

| Commit | Upstream change | Outcome | Reason |
| --- | --- | --- | --- |
| `5170ff4` | Update credits | Ported in part | Added the NVIDIA entry with the per-model licenses in [fork background](../fork-background.md). The `transcribe.cpp` entry is omitted because the fork does not ship it, and the fork's other entries are unchanged. Upstream replaced its TensorFlow Lite entry; the fork keeps that entry pending a separate attribution check, although no TensorFlow dependency is declared in `app/build.gradle`. |
| `c0bbb05` | Model option refactor, ASR4ALL Spanish | Partly adopted in a follow-up | The Spanish model was added as an optional download through the publisher's ExecuTorch XNNPACK INT8 export (`futo-org/asr4all-m-es` at `0290ab5`), not upstream's format. The model-options refactor was not ported: it replaces saved model settings and the catalog. Android device behavior is unverified. |
| `74fe3eb` | Empty samples return empty text | Ported | The fork did not throw, but `ggml/WhisperGGML.kt` passed an empty array to native code, and `app/src/main/cpp/ggml/whisper.cpp` (`log_mel_spectrogram`) then reads past it while reflect-padding. A recorder failure before the first read skips the final silence pad, so empty audio can reach finalization. The guard is in `backend/SpeechBackend.kt` (`prepareRecordingFinalization`) and covers all final-only backends. Streaming backends only flush. Saved-recording retranscription already rejects empty audio (`history/AudioHistoryStore.kt`, `readSamples`) but accepts very short audio, so `WhisperGGML.infer` also returns empty text for 200 samples or fewer. Blank transcripts now skip S1 cleanup (`s1/S1MiniTranscriptCleaner.kt`). |
| `6dbd5bf` | Batch inference cancellation | Not applicable | The fork has no native cancel request and no `InferenceCancelledException`. `app/src/main/cpp/voiceinput.cpp` aborts only for a bail language and returns only the `lang=` sentinel, which `ggml/WhisperGGML.kt` handles. `RecordingSession.runModel` discards failures from a superseded session. The `contains` to `startsWith` change only differs if dictated text contains the sentinel. |
| `f21f80e` | `MicPermissionActivity`, mic-error UI | Not applicable | `RecognizeMicError` in `RecognizerView.kt` already uses `fillMaxWidth` with scrolling. Its settings icon is a separate `IconButton` that needs its own description, unlike upstream's icon inside a clickable box. The activity and its string belong to the excluded keyboard flow (ADR 0004). |
| `5d64434` | Missing model in language picker | Not applicable | The fork has no `startPending` or nullable per-language recognizer. The picker in `RecognizerView.init` calls `create()`, which checks readiness and asks for the download before recording. |
| `0f49cb6` | No cleanup while recognizer in use | Not applicable | The fork has no cleanup-all call. `recognition/RecognitionModelLifecycle.kt` refuses to delete or replace a model with an active session or runtime. `RecordingSession.reset` joins the recorder, model and load jobs before releasing, and `parakeet/ParakeetEngineManager.kt` cancels the idle unload on acquire. |
| `65eed42` | Do not log the full prompt | Not applicable | `voiceinput.cpp` has no log call that includes the prompt. |
| `dba0374` | Move `lang_token` out of loop | Not applicable | Only updates the excluded `transcribe.cpp` submodule. |
| `c235ee8` | Throw on `InvalidModelException` | Not applicable | The fork has no such exception or swallowing catch. `RecordingSession.loadModelInner` sends every load failure to `failRecognition`, which shows the error. Upstream's rethrow would bypass that. |
| `fb66c08` | Manual language selection by default | Ported in a follow-up | `MANUALLY_SELECT_LANGUAGE` now defaults to on. A saved value, including off, is kept. The fork passes the chosen language to Whisper/GGML only for decoding, so when the toggle was never saved only that recognizer asks (`manualLanguageSelectionDefault` in `RecognizerView.kt`). Other recognizers still ask when the toggle was saved on, because the S1 and Basic text cleanup language gates use the pick. `language_tip_1` describes the new default. |
| `afd4b50` | News entry | Not applicable (reviewed in a follow-up) | The fork has no dismissible "What's new" card: `settings/pages/Home.kt` has no news list or viewed counter. Upstream's text also announces a Spanish model. |
| `2424b6b` | Bluetooth and audio focus options | Ported in a follow-up | Added **Prefer Bluetooth** (default off) and **Pause videos/music** (default on) with upstream's keys. The preference is applied and saved in the shared recording flow (`AudioRecognizer.kt`, `MicrophoneRouting.kt`), covering the activity and the keyboard. Only a switch the user makes and the recorder confirms is saved; fallbacks and automatic selection are not. It remembers Bluetooth or phone, not a particular headset. The second setting gates `focusAudio()`, the app's only audio-focus request. |
| `89d30dc` | Remove `ModelManager` debug trace | Not applicable | The fork has no `ModelManager`. The traced calls were not ported. |
| `25f1450` | Remove unused activity | Not applicable | Already removed by `5dfb4b1`; no fork manifest declares `BillingActivity`. |
| `cfdd409` | Guard saved model options | Not applicable | The crash path needs upstream's string-set `MODEL_OPTIONS`, which the fork does not have. The fork reads integer indices written from fixed model lists, and `Util.kt` and `WhisperGGMLBackend.kt` fall back to an English model when no language maps to one. Out-of-range indices from damaged preferences remain unguarded on the recognition path, as before. |

## Validation

The table and notes below cover the integration only. The follow-up is covered
under [Follow-up validation](#follow-up-validation).

Run on Windows with JDK 21, SDK platform and build tools 36, NDK
`28.2.13676358`, CMake `3.22.1` and Rust `1.98.1`, on source `2ee0571`, which
includes `development` through `ba593d8` (this
record was added afterwards). Gradle ran with `--max-workers=2`, a 4 GB heap and
`-Ps1NgramSpeculation=false`.

| Check | Result |
| --- | --- |
| `:app:testDevDebugUnitTest` | 278 passed, 4 skipped, no failures or errors |
| `:app:testStandaloneReleaseUnitTest` | 278 passed, 4 skipped, no failures or errors |
| `:app:lintDevDebug` | No errors; 87 warnings |
| `:app:lintStandaloneRelease` | No errors; 86 warnings |
| `:app:compileDevDebugAndroidTestKotlin`, `:app:assembleDevDebugAndroidTest` | Passed (compiled, not executed) |
| `:app:assembleDevDebug`, `:app:verifyDevDebugApk` | Passed; 29 ARM64 libraries |
| `:app:assembleStandaloneRelease` | Passed; local debug-key signing, not production release signing |
| `tools/verify_native_packaging.py` on the release APK and symbols | Passed: 29 ARM64 libraries, pinned runtimes and notices, S1 speculation off, stripping and matching symbols |
| `apksigner verify` on the release APK | Passed (v2 scheme, Android debug certificate) |
| `cargo +1.98.1 test --locked` for Harper | 9 passed (on `faa2b27`; no Rust change since) |
| `python -m unittest discover -s tools -p test_native_packaging.py` | 4 passed |

The four skipped tests need the published Orukeet, Phonon, Parakeet 110M and
Whistle packages.
`tools/harper_notices.py` needs `PYTHONUTF8=1` on this Windows host.

## Follow-up validation

Run on the same Windows host and with the same Gradle flags and tasks (without
the separate packaging, signature, Cargo and Python checks), on the source of
`205b8b9`.

| Check | Result |
| --- | --- |
| `:app:testDevDebugUnitTest`, `:app:testStandaloneReleaseUnitTest` | Each 285 passed, 4 skipped, no failures or errors |
| `:app:lintDevDebug`, `:app:lintStandaloneRelease` | No errors; 87 and 86 warnings |
| `:app:compileDevDebugAndroidTestKotlin`, `:app:assembleDevDebugAndroidTest` | Passed (compiled, not executed) |
| `:app:assembleDevDebug`, `:app:verifyDevDebugApk`, `:app:assembleStandaloneRelease` | Passed |

No emulator or phone was used. These checks remain, on a phone:

1. With **Prefer Bluetooth** off, recording starts on the same microphone as before.
2. With it on and a headset connected, both the keyboard and the speech popup
   switch to the headset. Check how much opening speech is lost while it connects,
   and that silence during the switch does not auto-stop the recording or report
   a blocked microphone.
3. Switch to the phone, then to Bluetooth, in the recording window; confirm each
   choice appears in settings and applies to the next recording. Stop before a
   switch completes and confirm the setting is unchanged.
4. With it on, test no headset, a headset disconnected mid-recording, Bluetooth
   turned off, and a connection that times out. Audio is still captured and the
   setting stays on.
5. Play music and video with **Pause videos/music** on and off, using the phone
   microphone and a Bluetooth microphone.
6. With Whisper/GGML and two languages, the picker appears before recording;
   with ASR4ALL and the same languages it does not unless the toggle was saved on.
7. Read both new switches with TalkBack.
8. Download and select **ASR4ALL Medium Spanish**, dictate in the keyboard and
   popup, retranscribe a saved recording, run a model comparison and a live
   comparison in Spanish, and confirm S1 and Basic text cleanup are skipped.

The Spanish model was added after `205b8b9`. Its publisher export ran on the
ExecuTorch 1.2.0 desktop runtime and produced Spanish text; accuracy was not
measured, and the app's Android decoder has not run it.

## Not verified

No emulator or phone was used, so instrumentation tests did not run. The new
Credits entry has not been viewed on a screen. The empty-recording guard is
covered by a unit test; the recorder failure that produces empty audio was not
reproduced on a device. The short-audio Whisper guard and the blank-transcript
S1 bypass have no dedicated test. The Linux-only CI steps (Phonon and Whistle reference
transcriptions, the Moonshine ONNX Runtime validator) did not run.

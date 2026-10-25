# S25 Ultra Moonshine candidate checks

Recorded October 24, 2026 for [issue 41](https://github.com/Today20092/voice-input/issues/41).
Moonshine Small and Medium completed native batch recognition on a second
physical ARM64 phone. Subsequent audio-history UI checks passed cancellation,
retry and same-process switching with Whisper. Positive live dictation and
production release gates remain outstanding.

## Device and candidate

- Samsung Galaxy S25 Ultra, SM-S938U, SM8750, Android 17 / SDK 37.
- App source `380b24de10c90c6a39f70a92f1f8150f57c7f02c`, version
  `1.4.10-dev`, versionCode 82, package `org.futo.voiceinput.dev`.
- [Verified candidate CI](https://github.com/Today20092/voice-input/actions/runs/40140732558)
  and [published experimental artifacts](https://github.com/Today20092/voice-input/releases/tag/experiment-issues-40-41-7a6c546).
- Original CI APK SHA-256:
  `5abf8b1c29725ba81a3795834e7eb8166b8f2902201754dc9bc69776e40bad8a`.
- The installed older test APK used a different signing key from the CI APK.
  A temporary copy of the candidate was signed with the existing local test
  key, without compilation. All 1,105 non-`META-INF` ZIP entries were compared
  by SHA-256 and were identical before and after signing, including native
  libraries, DEX, manifest and resources. Installed candidate SHA-256:
  `65f3151d8d5aefc434b039b73ffd433f38092e8ea789d40d434f2891f192dbda`.
- Existing test APK SHA-256:
  `cc50cb239bd9ee26a6985af1ef53fe2a11ff4801cd9be580c3ff5c417a458245`.
  The local signing-certificate SHA-256 was
  `acf360f0fa27545f40b9daa32e846864091ec0f76d5671b0900a79b6a31655da`.
- Only the newly installed Dev candidate was replaced for signing compatibility.
  The regular app and its data were preserved; the pre-existing test APK was
  not replaced. No host Gradle/native build or emulator ran. Signing used a
  64 MiB Java heap.

## Inputs and setup limits

The public upstream `app/src/androidTest/assets/audio.floats.bin` fixture was
converted to a 16 kHz mono PCM16 WAV (5.855 seconds). A second WAV repeats it ten
times (58.55 seconds); it is not a natural minute-long dictation. No private
recording was used. Audio accuracy was not scored because a spoken reference
was not reviewed.

| Clip | Original WAV SHA-256 | Android-decoded float32 SHA-256 |
| --- | --- | --- |
| Short | `6ba71ddda339c469a52fa48a375e17e2487a336f4434ae24ef5118637bbcb925` | `22d472b40f913206b6917115d136306be88df3b278e77f68be5910423250fa4d` |
| Repeated ten times | `ac711d61aa091f3299f89136347dc58c0b155b65d15c1be91ab75364bd4c9d6d` | `22eac9bab38d44fd5fc85006f9b375cc6d749e702162c6ed88e261eff51f3abc` |

`BenchmarkAdbTest` catalog and model downloads worked, but its older `prepare`
operation failed with `NoSuchMethodError` for the changed `BenchmarkReport`
constructor. This was a harness mismatch, not a native recognition crash.
Preparation had already decoded and saved both hash-checked clips. A minimal
report in the current JSON schema was placed alongside those clips, then the
existing runner resumed it. App code and model assets were not modified.

Run ID: `5c8f09e1-5122-4e40-a0fa-337d36bb6a60`. The current app's runner performed
the actual model loading, decoding, release and report serialization. It uses
the non-streaming transcription path, with a fresh app process between models.
Each Moonshine model performed five decodes: short first, short warm-up, short
measured warm, long warm-up and long measured warm. Untimed warm-ups are not
included in the observation table.

## Completed batch results

| Model | Load ms | Short first ms | Short warm ms | Repeated long warm ms | Release-call ms | Sampled peak process PSS KiB |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Small | 578.2 | 568.6 | 494.6 | 7,329.8 | 62.0 | 752,187 |
| Medium | 1,431.9 | 1,074.6 | 865.8 | 9,618.2 | 91.8 | 1,164,951 |

Both cases have status `completed`, no failure reason, and nonempty output in
all six recorded observations. They completed real native inference without
the process ending in a crash. This does not identify the original OnePlus
SIGILL instruction or establish absence of unrecorded failures.

The phone was USB charging at low battery after the S1 sessions (7%, 36.6°C
battery reading, thermal status 1 at the start of benchmark preparation).
These single warm observations are functional checks, not performance rankings.
PSS is sampled whole-process memory, not native-model allocation size.

The optional Whisper fallback completed two short observations, then was
deliberately stopped when the maintainer needed to unplug. Its case was still
marked `running` in the saved snapshot and must not be counted as completed or
as a model failure. Snapshot SHA-256:
`3efa3b4c6ff181b69014550f8c3d517f499bc71e3ceb2ecd79adeebdf266506a`.
The raw local report contains only the public sample's recognized text and is
retained in the task's temporary artifact folder; it is not standard diagnostics.

## Resumed audio-history UI checks

After a replacement USB cable stabilized ADB, the resumed checks used a cool
phone at 17% battery (USB charging, 29.1°C battery reading, thermal status 0).
The installed candidate and app data were retained. No rebuild or reinstall
was needed. Temporary TCP ADB was attempted but unreachable; it was returned
to USB mode (`service.adb.tcp.port=0`) before testing continued.

Public PCM16 fixtures were seeded into the Dev app's private audio-history
directory, using its normal timestamp/UUID naming and 16 kHz format. The long
fixture was the same 58.55-second repeated public sample; a second entry used
the original 5.855-second sample. These are synthetic test entries, not proof
that microphone capture produced the saved audio. The UI's actual Retranscribe
action, current model selection, lifecycle loading and transcript persistence
were used without altering app code.

| Check | Result |
| --- | --- |
| Small, long public history entry | Completed; saved nonempty transcript |
| Medium, same long entry | Completed; saved nonempty transcript |
| Medium cancellation and successful retry, twice | Both canceled by navigating back while processing; both retries completed |
| Small cancellation and successful retry | Canceled by navigating back; retry completed |
| Switch to built-in Whisper English-39 and retranscribe short entry | Completed; saved nonempty transcript |
| Switch back to Medium and retranscribe short entry | Completed |

The prior transcript SHA-256 was
`a52e4e4912aa2b618a13f4b8291ef36bb1d2d8be951d88056e2bf2d7d8ca4838`
before each canceled attempt and was unchanged afterward. Standard diagnostics
record all three `SESSION_CANCELLED` events and their subsequent successful
retranscriptions. There are seven successful retranscriptions in this sequence:
Small twice, Medium four times and Whisper once. The PID stayed `29675`
throughout Small → Medium → Small → Whisper → Medium, with no force-stop or
reinstall during that sequence. This is same-process evidence for those
runtimes, not Sherpa/OpenCL or every available model combination.

The [content-free event extract](evidence/s25-history-events.json) retains model,
session, event, timestamps and numeric metrics, including the reported audio
durations. It excludes recognized text, audio and application names outside
the tested app. Nonempty stored text and transcript hashes were checked
directly; the text itself is not included in this evidence.

### Live recording cancellation without verified speech

Three Medium recognition-activity sessions opened the microphone, loaded the
model and were closed with the actual Cancel button. Each recorded
`SESSION_CANCELLED`; recording could be reopened afterward and the PID remained
`29675`. The [event extract](evidence/s25-live-cancel-events.json) retains these
technical events. It does not contain captured audio or recognized text.

The public sample played on the computer was not detected as usable speech by
the phone. The first resumed attempt was canceled after playback; two further
short sessions checked reopening and cancellation. An earlier Small live attempt
ended with a no-speech failure. None of these proves a successful spoken retry,
first partial, final text or delivery. Positive live speech needs a sample the
phone's microphone can actually hear.

## Remaining checks

- Actual microphone/live streaming, first partial and final flushing in the
  recognition activity; delivery to a controlled activity/IME receiver.
- Live-recording cancellation followed by successful spoken retry. The history
  cancellation/retry tests above exercise a separate path.
- Retranscription of an actual microphone-captured successful recording; the
  seeded public history entries above validate the history UI/inference path.
- Older Android compatibility, final affected-OnePlus confirmation and normal
  beta validation remain separate release gates.

Initial resumption was interrupted by repeated USB disconnects at 4% battery.
The new cable later held and enabled the checks above. No disconnect is counted
as an application or native recognition failure.

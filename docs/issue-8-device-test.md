# Issue 8: Orukeet no-speech device test

Reference: https://github.com/Today20092/voice-input/issues/8

Goal: capture a failed live recognition and compare Orukeet and Nemotron on the exact same saved audio. A successful S25+ run does not rule out the reported Poco/HyperOS failure.

Allow 30 minutes after downloading both models. Keep the installed app version unchanged for this baseline. Record its full version, Android/One UI version, model variants, language, keyboard or speech-activity entry point, and microphone route.

## Preparation

- Use a quiet room, fixed phone position, and the phone microphone. Keep charging state and battery-saver setting constant.
- Enable recording backups/audio history. Use only invented, non-sensitive test speech.
- Turn transcript cleanup off. Keep model warming and personal dictionary settings unchanged, and record them.
- Start detailed mode in Settings > Support > Diagnostics. It expires after 30 minutes. Leave standard collection enabled and do not clear existing diagnostics.
- Initially preserve your automatic-stop and recording-limit settings. Note both.

## Minutes 0–10: Orukeet baseline

Use your usual dictation entry point in the same notes app. Label the first attempt after reopening separately from subsequent attempts. Do not force-stop after starting detailed mode.

Run three rounds of these clips, nine attempts total:

1. Short, about 5 seconds: "Please remind me to call the dentist tomorrow morning."
2. Medium, about 20 seconds: describe an invented meeting and its agenda continuously.
3. Long, about 45–60 seconds: describe an invented shopping trip. Include two natural pauses of about 2 seconds. If the current duration limit ends it early, record that outcome.

For each attempt record its start time, approximate duration, success or exact error, whether it stopped before you tapped Stop, and whether the waveform moved while speaking. Preserve every failure. Export a standard bug-report ZIP at the end of this block.

## Minutes 10–18: controlled stopping

With Orukeet still selected, disable automatic stopping on silence and the optional 30-second limit. Keep everything else constant. Repeat the same nine-attempt sequence, tapping Stop after the last word. Record the same observations. Export a second ZIP before changing models.

If both settings were already disabled, use this block to repeat the baseline, including a few immediate-start attempts and a few attempts that wait 1 second before speaking. Label which timing you used.

## Minutes 18–25: replay the same audio

If any live attempt failed:

1. Find it in audio history and play it. Record whether speech is audible and whether the beginning and end are present.
2. Retranscribe that saved recording with Orukeet twice. Record each outcome and time.
3. Select Nemotron and retranscribe the exact same saved recording. Record its full model variant and outcome.
4. Also compare one successful saved recording with both models.

If there were no failures, compare one short and one long saved recording with both models. Do not record a fresh utterance as a substitute for replaying the same audio. If Nemotron is unavailable, report that and finish the Orukeet replays.

## Minutes 25–30: export and report

Export another bug-report ZIP before detailed mode expires. Review the reports before sharing. Standard diagnostics are sufficient initially; leave transcript inclusion off.

Return the ZIPs and this small attempt log:

```text
Device: Galaxy S25+
App version:
Android / One UI:
Model variants / language:
Entry point / notes app / keyboard:
Microphone route:
Cleanup / warming / dictionary:
Initial automatic stop / duration limit:
Battery saver / charging / phone warmth:
Test date and timezone:

Attempt | start time | clip length | settings block | outcome / exact error | early stop? | waveform?

Failed history item / attempt:
Playback audible and complete?:
Orukeet replay 1 / replay 2:
Nemotron replay:
Successful control replay:
```

Keep failed test recordings locally. If a failure reproduces, a single non-sensitive audio sample may be useful later to build an automated regression test. Logs alone do not establish whether the cause is recording, stopping, inference, or result handling.

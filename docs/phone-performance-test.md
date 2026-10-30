# Phone dictation test

Use this sheet to compare recognition models on your Samsung Galaxy S25 Ultra. These are test instructions, not measured performance results. Allow about 5 to 8 minutes per model, plus downloads and cooling breaks. Test only the models you want compared. Start with Orukeet, then repeat the same procedure for the others.

## Before each model

1. Use the same app version, microphone, quiet room, phone position, and speaking style. Download the model first. Use the same dictation entry point throughout, such as the voice keyboard in one notes app.
2. Select the model in Model Options. Record its full name, variant, and streaming profile. Select English where available. Keep personal dictionary settings unchanged and note any rules that affect the test passages.
3. Turn transcript cleanup off for the recognition comparison. Keep model warming on where available. Disable automatic stopping on silence and the optional 30-second limit so you can finish each passage and tap Stop yourself. Keep the recording backup setting the same for all models.
4. Keep battery saver off and charging state consistent. Prefer testing unplugged after the phone cools. Note battery percentage, charging state, and whether the phone feels warm. Pause between models if it heats up.
5. In Settings, open Support, Diagnostics. Leave standard collection enabled and start detailed mode. It expires after 30 minutes. Check that it is still active before each model block. Do not clear diagnostic history unless you have already exported anything you want to keep.

## What to say

Read naturally. The times below assume roughly 130 to 170 words per minute; do not rush to hit them. Do not read the clip labels aloud. Tap Stop after the last word. Diagnostics will provide the actual audio duration.

### A. Short message, about 5 to 7 seconds

> Please remind me to call the dentist tomorrow morning and bring my insurance card.

### B. Everyday paragraph, about 14 to 19 seconds

> I moved our meeting to Thursday afternoon because the delivery arrived late. Please send the updated agenda before lunch, and ask Jordan whether the new microphone is working. If the room is busy, we can meet downstairs near the window instead.

### C. Longer dictation, about 36 to 47 seconds

> This morning I tested the voice input app while planning a small weekend trip. First, I checked the weather and made a list of things to pack. Then I compared two train routes, but I have not booked either one yet. Please remind me to leave home at seven thirty on Saturday and bring a charger, a water bottle, and the blue notebook. The hotel reservation is under Morgan Taylor, and the reference number is four eight two nine. Before we leave, I still need to review the GitHub pull request and confirm that the Android recording screen shows the complete transcript.

## Run order for each model

1. After selecting the model and starting detailed mode, use Android App info to force-stop Voice Input. Reopen it without running another transcription. This makes the first run an app-cold run; it does not clear Android's filesystem cache.
2. Dictate A once. Label this run `cold-A` in your notes. Keep it separate from the warm results.
3. Without force-stopping or changing settings, dictate `A, B, C`, then `A, B, C`, then `A, B, C` again. Leave only enough time to save the result and start the next run. These nine attempts are the warm test. Record interruptions or long gaps rather than silently treating the model as warm.
4. Keep every attempt, including errors, automatic early stops, and empty output. Label accidental interruptions and record any replacement run separately. Do not fix the generated text before saving it.
5. Export immediately after this model block, before changing the model or settings. In Diagnostics, choose Export bug report, add the notes below, review the report, and share the ZIP. Send the ZIP with your unedited transcripts. The export does not upload itself.

Use one ZIP per model block. Reports can overlap because export includes retained history; session IDs let us deduplicate them. A separate export preserves that model's settings snapshot. If a block approaches 30 minutes, export it and restart detailed mode for the next block.

Copy this into the report notes and fill it in:

```text
Phone dictation test, protocol docs/phone-performance-test.md
Model / variant / profile:
Language:
App version:
Entry point, keyboard or speech activity:
Cleanup off; model warming setting:
Automatic stop / duration limit / recording backups:
Dictionary rules affecting the passages, if any:
Battery start / end; charging; battery saver; phone warmth:
Start and end time with timezone:
Run order: cold-A, A1, B1, C1, A2, B2, C2, A3, B3, C3
Mistakes, interrupted attempts, retries, or changed settings:
```

Save the unedited output under each run label in a separate note. If you misspoke, note what you actually said. These passages contain invented details, so you can share their output without dictating personal information.

## What the diagnostics can tell us

Use fresh dictation for the everyday-use timing comparison above. Builds with the history timing additions also support a separate, identical-audio comparison through Retranscribe. Older builds, including 1.4.3, do not record that path's recognition-session timings.

For each model and clip, report the three warm attempts individually and their median and range. Report the cold attempt separately, plus failures and interrupted attempts. Three repetitions are a small personal-device sample, not a general model ranking. Speaking the passage again changes the audio slightly; this is a repeatable everyday-use comparison, not an identical-audio laboratory benchmark.

The ZIP's `events.jsonl` groups events by `sessionId`. Durations are milliseconds. Useful measurements are:

| Measurement | Evidence and interpretation |
| --- | --- |
| Audio duration | `RECOGNITION_STARTED.AUDIO_MS`, the audio passed to recognition. |
| Model load time | `MODEL_LOAD_FINISHED.DURATION_MS`. A warm load may reuse an existing model. |
| Wait after Stop | Difference between `RESULT_READY.ELAPSED_MS` and `RECORDING_STOPPED.ELAPSED_MS` in the same session. This includes post-stop processing through the ready result, not display time in the receiving app. |
| Final recognition stage | `RECOGNITION_FINISHED.DURATION_MS`. For final-only models this covers transcription; for streaming models it covers finalization because earlier recognition happened while you spoke. Do not rank these as equivalent full-clip processing times. |
| First live text | Difference between `FIRST_PARTIAL.ELAPSED_MS` and `FIRST_AUDIO.ELAPSED_MS`, when both exist. Final-only models have no live partial result. |
| Cleanup time | `CLEANUP_FINISHED.DURATION_MS` and `APPLIED`; baseline cleanup should be off. |
| Sampled memory and heat | `RESOURCE_SAMPLE` includes `PSS_KB`, heap bytes, and supported thermal status. Report the highest observed sample, not an exact peak. Samples can miss short spikes and main-process memory excludes the separate cleanup process. |
| Failures | Session failures, cancellation/reset, missing terminal evidence, and available process-exit records. An unfinished session alone does not prove a crash. |
| Waveform startup | `WAVEFORM_FIRST_FRAME.ELAPSED_MS` minus `FIRST_AUDIO.ELAPSED_MS` in new builds. This records the first draw with audio bars, which may represent silence; it does not prove visible movement or uninterrupted rendering. The export also records the popup setting. |

For final-only runs, recognition milliseconds divided by audio milliseconds gives a recognition-stage real-time factor. Do not apply that formula to streaming finalization and call it full-model speed. Detailed-mode sampling adds overhead, so label published figures as measured with detailed diagnostics enabled.

Standard and detailed diagnostics deliberately exclude transcript text and audio. They cannot establish recognition accuracy by themselves. The separately supplied, unedited test outputs let us compare errors against the passages. Count substitutions, deletions, and insertions using a documented normalization rule, treating punctuation/case and equivalent number formatting consistently. Report number and name mistakes separately when useful. Missing outputs or a spoken departure from the script must be accounted for before quoting an error rate.

If you also want to measure S1-mini cleanup, do a separate block with cleanup enabled after completing the baseline. Label its first-run setup/benchmark separately. Do not mix those timings with recognition-only results.

## Optional identical-audio comparison

After installing a build with the new history timing events, record A, B, and C once with recording backups enabled. Note each recording's timestamp and keep its retention long enough to finish testing. For each model, select it and follow the same diagnostics and settings preparation above. Force-stop and reopen the app, then retranscribe saved clip A once as the cold attempt. Retranscribe the same saved clips in the order `A, B, C` three times for warm attempts. Keep Audio history open until each attempt finishes, and copy each result before the next attempt replaces its saved transcript. Export one ZIP per model block and label it `history comparison`.

History sessions contain `SESSION_STARTED` with `RETRANSCRIPTION=1`, and successful attempts end in `RETRANSCRIPTION_FINISHED` after the transcript is saved. They have no microphone Stop event or text-delivery event. Use recognition-stage duration and completion elapsed time for this comparison. History calls the model's full-clip transcription path, so keep its results separate from live streaming behavior. If these events are missing, the installed build cannot support this comparison through diagnostics.

For the intermittent waveform problem, enable detailed mode, note the time and entry point, and reproduce it with the popup setting both on and off using the same model. Record whether the waveform is late, flat, missing, or clipped. Export after each setting so the snapshot identifies it correctly. These comparisons provide evidence; the popup setting alone does not establish the cause.

## Publishing results

Once the ZIPs and transcripts have been checked, add a compact README table with phone, Android/app version, model/profile, clip duration, cold load, warm wait-after-Stop median/range, sampled memory, and failures. Link a fuller results document with the protocol, individual runs, and limitations. Accuracy requires the separate transcripts. Until then, keep the README's existing statement that comparative performance has not been measured.

Publish aggregate measurements and the agreed test passages/results. Keep raw diagnostic archives out of the repository; they can contain device details and report notes unrelated to the benchmark.

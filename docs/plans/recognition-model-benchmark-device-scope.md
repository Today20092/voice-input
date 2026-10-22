# Issue #17 scope amendment: real-device benchmark suite

Agreed with the maintainer on October 10, 2026. Implementation is deferred at the maintainer's request. This amendment extends [issue #17](https://github.com/Today20092/voice-input/issues/17) and `docs/plans/recognition-model-benchmark.md`. It supersedes the earlier exclusion of live-transcription latency measurement. Other existing requirements remain in force.

## Outcome

Compare every selectable recognition model and variant on the maintainer's real Android phone using both fixed recorded audio and guided live dictation. Use USB/ADB to control device runs and collect reports. Recognition runs on the phone. Start with a short pilot, validate the reports, then run the full suite during an uninterrupted phone session.

## Supplied recordings

The original recordings, metadata, SHA-256 checksums, reuse permission, and exact reference scripts are under `benchmarks/audio/phone-room/`:

- `benchmark-01.m4a`: everyday dictation, 25.279604 seconds.
- `benchmark-02.m4a`: names, numbers, and similar sounds, 38.847396 seconds.
- `benchmark-03.m4a`: longer technical dictation, 75.796146 seconds.

All contain stereo AAC at 48,000 Hz and approximately 256 kbps. The maintainer recorded them with their normal phone microphone in a room with air conditioning and background noise, and explicitly permitted repository storage, public reuse, and future benchmarking. This permission applies only to these recordings.

Verify spoken words against the supplied scripts before final accuracy scoring. Keep originals unchanged. Decode/resample once into canonical 16 kHz mono samples outside measured model timings, record conversion settings and hashes, and reuse identical samples across models. Follow the existing versioned scoring policy; retain raw references and transcripts.

## Model coverage and downloads

The full pass covers every selectable recognition model and variant, including downloads as needed. The current inventory is 20 cases: 14 managed entries and six legacy Whisper selections. Derive the actual inventory from the current catalog and legacy selections at execution time; do not hard-code this count.

Use appropriate English settings for these English recordings and record effective settings. Use and disclose auto-detection where required. Do not multiply the suite across unrelated language choices. Include size variants and latency/accuracy profiles even when they share assets.

Show total additional download and storage requirements before downloading. Reuse installed valid assets and count shared files once. Record failed, unsupported, or unavailable cases with reasons and continue unrelated cases. Never represent incomplete coverage as a passing full suite.

## Fixed-audio device protocol

Keep the existing fresh-process ADB protocol and shared UI/ADB runner. For each model/variant, measure initialization separately and record the first decode immediately after loading. For each clip, perform one untimed warm-up followed by five measured warm decodes. Record individual transcripts, timings, warm minimum/median/maximum, and real-time factor.

Also measure the close/unload call duration separately. Label this as API release-call duration; it does not prove Android has reclaimed all model memory. Cold means a fresh process with the model unloaded, not a cleared filesystem cache. Record startup overhead separately if measured.

Use raw recognition with configurable transcript cleanup and personal-vocabulary transformations disabled consistently. If cleanup is inseparable from a model, disclose it per case rather than claiming identical raw-output behavior. Cleanup-model comparison remains excluded.

Run sequentially, record execution order, thermal and battery/power state, and available named process-memory measurements. Permit cooling intervals and record them. Persist results before each restart; bounded waits, cancellation, failures, and interruptions must preserve understandable partial results.

## Guided live device protocol

Add a guided live-reading pass for every selectable model/variant through both the voice keyboard/IME and the speech-recognition activity. Use the supplied everyday passage as the common short prompt. The user reads it through the actual phone microphone for each case. Use normal app capture, VAD, recognition, and delivery paths, with configurable cleanup disabled.

Record the model/configuration, entry point, spoken reference or deviations, raw/final delivered text, errors, and capture/recognition/delivery milestones. Report speech-end-to-final latency, Stop-to-final latency when Stop is used, and final-text delivery latency. Where a model emits partials, record first-partial timing with a named clock origin; final-only models have partial timing unavailable. Define timing boundaries explicitly and use monotonic clocks.

Verify final insertion through a controlled editor for the IME and result delivery through a controlled caller for the recognition activity. Record loss, duplication, or delivery failure. Capture microphone/VAD behavior and relevant user-visible errors without exposing text in standard diagnostics or Logcat.

Keep live results separately identified from fixed-audio results. Each reading differs, so do not treat live accuracy as an identical-input model ranking. Allow the user to correct the reference for actual spoken deviations. Live microphone capture, silence detection, partial timing, and text delivery are additional scope beyond the original final-only comparison.

## Reports and optional model removal

Export versioned JSON and readable Markdown containing raw transcripts, references, accuracy edit counts, timing boundaries/results, input hashes, app revision/build, device/Android identity, model/runtime/configuration identities, isolation mode, run order, and available memory/thermal/power data. Record unavailable measurements with reasons.

After completion or cancellation, offer optional removal of newly downloaded models using explicit checkboxes and existing deletion protections. Models installed before the run are unchecked by default. Protect shared/bundled assets and release model ownership before deletion. Do not automatically delete on failure or cancellation. Keep report/audio deletion independent from model cleanup.

Preserve existing privacy/export rules for other users, daily-model settings, bounded input validation, safe ownership, download verification, and meaningful scoring/runner tests. No automatic model selection, report upload, README publication, or root cache clearing.

## Deliverables and acceptance additions

- [ ] A PowerShell-friendly USB/ADB command supports a short pilot and the full catalog-derived suite on the real phone, with bounded waits and resumable results.
- [ ] Supplied originals and checked references produce a versioned canonical input manifest shared by every fixed-audio case.
- [ ] All currently selectable model/variant cases are attempted, with required downloads and explicit per-case failures or exclusions.
- [ ] Initialization, first decode, one untimed warm-up, five warm decodes per clip, and close/unload-call duration have verified separate timing boundaries.
- [ ] Configurable cleanup is disabled in both protocols; inseparable model processing is disclosed.
- [ ] Guided live readings cover both IME insertion and recognition-activity result delivery, with documented capture/VAD/partial/final/delivery measurements where available.
- [ ] Fixed-audio and live results are separate in JSON and Markdown, with actual spoken references and limitations visible.
- [ ] Optional removal targets newly downloaded models by default, protects retained/shared assets, and requires explicit user selection.
- [ ] Meaningful automated tests cover new timing boundaries and live delivery behavior, alongside the existing issue's scoring, failure, ownership, and persistence checks.
- [ ] Applicable build, unit, lint, and device checks are recorded. Run and inspect the pilot before full-suite evidence is accepted.

## Readiness

The recordings are supplied. Canonical conversion, listening/reference verification, implementation, download/storage audit, and physical-device execution remain pending. ADB was available on the host during planning, but no phone was attached. Connect and authorize the phone when implementation/device validation resumes. No performance or accuracy result has been measured from this handoff.

# Personal recognition-model comparison and reproducible device benchmarks

Status: agreed design, implementation pending. Decisions settled with the maintainer on 2026-10-04.

Tracking issue: [#17](https://github.com/Today20092/voice-input/issues/17).

## Outcome

Let a user record one passage once and compare final-only transcription across selected recognition models on their own Android device. Provide a thorough, reproducible suite and an ADB runner for device-specific README evidence, initially using the maintainer's Galaxy S25 Ultra.

The in-app comparison helps users choose a recognition model for their voice, accent, accuracy preferences, device speed, and storage budget. ADB automates the same runner and adds fresh-process isolation; it does not imply faster model decoding or universally better accuracy.

## Agreed scope

- Advanced settings contains a Model comparison screen, available to ordinary users rather than only development builds.
- Record or import audio; reuse exactly the same canonical samples for every selected recognition model.
- Offer a short everyday passage by default, custom reference text, and the four thorough-test passages in `docs/benchmark-recording-scripts.md`.
- List all supported recognition models and variants, including legacy Whisper selections. Installed variants are selected initially; users can change checkboxes and select missing variants.
- Show the total additional download/storage requirement before starting downloads. Download selected missing assets through the existing downloader, validate installation, and then benchmark. Count shared artifacts once where applicable.
- Measure final transcripts, model initialization, first decode, warm decode, accuracy, and available process-memory/thermal metadata.
- Export versioned JSON and readable Markdown suitable for a reviewed README update.
- Offer optional checkbox cleanup after completion or cancellation. Models installed before the benchmark are unchecked by default. Show recoverable storage and protect assets shared with retained models.
- Provide an ADB-driven thorough run with a fresh app process per model/variant and durable results between invocations.

## User flow

1. Choose the default passage, another supplied passage, or custom reference text. Alternatively import a recording.
2. Record once or import an audio file. Preview the recording and the reference text before starting. Imported audio may omit reference text.
3. Explain that the reference must describe the words actually spoken. If the user deviated from a supplied passage, let them re-record or edit the reference. Do not derive the reference from a model transcript automatically.
4. Select models and variants. Show installed/download status, language support, and additional storage. Use the existing language controls where a model requires them; record the effective configuration.
5. Download missing selected assets and run models sequentially. Show progress, cancellation, and per-model failures. Failed downloads/loads produce a failed or skipped row with a reason while other models continue.
6. Compare transcripts alongside accuracy and timings. Explain measures in plain language. Do not silently change the daily recognition model or claim a universal winner.
7. Offer explicit selection of a preferred daily recognition model, report export, and optional deletion of models. Selection and deletion use the existing model-management protections.

Provide a bounded custom recording duration and size consistent with supported audio/recognition limits, and communicate the actual bound in the UI. Reject unsupported, empty, corrupt, or oversized input with an actionable message. The implementation must document the formats supported by the import path; do not promise arbitrary media support.

## Canonical input and accuracy

Keep originals for the reproducible suite. Decode/resample once into canonical 16 kHz mono audio accepted by the common recognition interface, outside measured model timings. Every variant receives identical immutable samples or equivalent defensive copies.

The suite manifest contains a stable clip identifier, original and canonical audio hashes, conversion settings, actual sample count/duration, reference text/hash, and language. Hashes identify exact inputs; reference text changes invalidate the previous accuracy comparison.

Preserve raw output and reference text. Compute word error rate and character error rate using a documented, versioned normalization policy: Unicode normalization, case folding, punctuation treatment, and whitespace normalization. Report substitutions, deletions, insertions, and denominators. Do not silently equate digit notation with spelled-out numbers or discard spoken fillers; disclose formatting differences and retain raw text for review. Reference punctuation and capitalization are not scored as spoken-word accuracy.

Missing or empty normalized reference produces unavailable accuracy with a reason, not zero error or division by zero. A successfully produced empty transcript against a nonempty reference is scored as deletions. A failed recognition attempt has no accuracy score. WER may exceed 100 percent because of insertions.

Exclude transcript cleanup and personal-vocabulary transformations from measured recognition and accuracy. Report this configuration explicitly. Language/decoding settings and model identities must be explicit so runs can be compared.

## Timing protocol

Use a monotonic clock and run one recognition model at a time. Prevent simultaneous dictation, duplicate benchmark runs, and model replacement/deletion during active ownership. Keep work off the UI thread. Record cancellations, interruptions, and incomplete results.

For each selected model/variant:

1. Construct an independent recognizer with no already-loaded model. Measure initialization separately.
2. Transcribe the first clip once immediately after loading. Record this as the first decode, with the clip ID and duration. It is separate from warm measurements.
3. Perform one explicitly untimed warm-up for each clip, then five measured warm transcriptions of that clip using the same loaded recognizer. Preserve every measured transcript and timing, including nondeterministic differences.
4. Close/release the recognizer before advancing. Record actual execution order.

Report initialization and first decode as single observations, rather than inventing a distribution. For warm runs show individual observations, median, minimum, and maximum per clip. Report decode real-time factor as decode seconds divided by audio seconds, and define it in the report. Also show initialization plus first decode as a startup-inclusive measure for that first clip.

The in-app mode is model-unloaded. The ADB mode force-stops and launches a fresh app process for each selected variant, reloading the persisted suite configuration and saving each result before the next restart. Capture process/session identifiers and isolation mode. Neither mode is disk-cache-cold; Android filesystem caches may remain warm. Report startup overhead separately if measured, rather than hiding it inside initialization.

Record thermal status and relevant power/battery state before and after each model where available. Sample process memory with named metrics and sample intervals. Label sampled peaks as sampled process measurements, not exact model allocations. Unsupported counters are unavailable with a reason. Retain model-order and thermal information to expose possible order effects; do not automatically infer energy consumption or performance class from this small test.

## ADB and reproducibility

Use the same runner, input manifest, accuracy code, and JSON schema as the UI. Provide a documented PowerShell-friendly host command to install/import the suite, select variants, download selected models, run fresh-process cases, wait for completion with bounded timeouts, and retrieve reports. Avoid parsing transcripts from Logcat.

The control entry point must be guarded for intended developer/ADB use and must not let arbitrary third-party apps trigger downloads, read recordings, or export reports. Prefer the project's established debug/instrumentation conventions after inspecting them. Do not require root. Record interruptions and resume completed model results without overwriting them silently; a new run receives a new ID.

For published evidence document app revision/build, actual device model, Android version, chip information when available, selected settings, exact recognition model/artifact versions or hashes, runtime version, audio suite identity, model order, isolation mode, and environmental conditions. Validate on the maintainer's S25 Ultra once recordings and a connected device are available.

## Storage, exports, and cleanup

Benchmark audio, references, and transcripts stay in dedicated private storage until the user explicitly exports or deletes them. Keep them separate from standard diagnostics and audio history. Do not write transcript text or audio into Logcat, normal diagnostics, or crash/error messages. This follows the content boundary in ADR 0003.

JSON and Markdown exports visibly state that they contain reference text and recognized transcripts. Report export does not include audio by default; separately exporting or publishing the reproducible audio suite is explicit. The maintainer has authorized public release of their benchmark recordings once supplied; this does not imply publication of other users' recordings.

Record which models were installed before the run and which were downloaded for it. Cleanup presents explicit checkboxes, defaulting only newly downloaded models for removal, and recalculates reclaimable bytes considering shared/bundled assets. Do not auto-delete on errors or cancellation. Close owned recognizers before deletion and use existing safe deletion paths. Allow deleting benchmark recordings/reports independently of recognition models.

## Implementation constraints and exclusions

Use the common recognition load/transcribe/close interface with an explicit configuration factory. Discovery found the ordinary recognition lifecycle loader can mutate saved model settings and invalidate installation state on load failure; inspect and isolate those effects rather than calling it blindly. Reuse lifecycle ownership to protect assets. Do not reuse a dictation warm cache for cold measurements.

Model enumeration must derive from the current supported catalog plus the legacy selectable models rather than a new hard-coded list. Include variant/configuration identities in rows even when variants share artifacts.

No live-transcription latency measurement, cleanup-model comparison, automatic model choice, automatic uploads, root cache clearing, general claims about model accuracy, or automatic README publication. The benchmark may inform catalog-calibration issue #15, but does not satisfy that issue's broader release gates.

## Blockers and deliverables

Implementation has no known external blocker. Public S25 Ultra evidence requires the maintainer's original recordings with confirmed references, a connected device, and completed selected-model downloads. Record unavailable device evidence as pending, never as a passing test.

Deliverables are the Advanced settings comparison flow, shared benchmark runner and schema, ADB host runner/documentation, supplied prompt/reference manifest, export/cleanup controls, automated verification, and a device report when the inputs/device are available.

## Acceptance checklist

- [ ] Record and import flows produce identical canonical audio for all selected variants.
- [ ] Supplied/custom references are reviewable and editable; missing references show accuracy unavailable.
- [ ] Current installed and downloadable recognition models/variants, including legacy paths, appear with selectable checkboxes.
- [ ] Missing selected models download with a visible total size; failures do not block unrelated cases.
- [ ] Daily model settings and installation readiness remain unchanged by benchmark failures.
- [ ] Initialization, first decode, one untimed warm-up, and five timed warm runs follow the documented protocol.
- [ ] ADB fresh-process runs persist results across restarts and use the same scoring/timing implementation.
- [ ] Raw transcripts, WER/CER edit counts, all run timings, model/config/audio identities, and available device/memory/thermal metadata export to JSON and Markdown.
- [ ] Cancellation, background/process interruption, model-load failure, and resource exhaustion leave understandable partial results and release ownership safely.
- [ ] Cleanup tracks newly installed models, protects shared assets, closes loaded instances, and requires explicit user selection.
- [ ] Standard diagnostics and Logcat remain free of audio/reference/transcript content.
- [ ] Meaningful scoring tests cover exact match, substitutions/deletions/insertions, empty reference/output, normalization, and WER above 100 percent.
- [ ] Runner tests verify timing boundaries, warm-up exclusion, identical input, sequential ownership, per-case failure handling, and restart persistence.
- [ ] Applicable Android unit tests, lint, and build checks pass; record actual device verification separately.
- [ ] Once supplied, the public recordings and S25 Ultra report include provenance and a reviewed README-ready table with limitations.

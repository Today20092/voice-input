# Audit standard diagnostics and detailed diagnostic mode

Status: future audit, implementation gaps not yet established. Requested on 2026-10-04.

Tracking issue: [#18](https://github.com/Today20092/voice-input/issues/18).

## Outcome

Review the app's existing diagnostic modes and exported support artifacts so maintainers can distinguish plausible causes of failures across the supported app. Identify missing evidence, excessive or redundant capture, and misleading fields. Produce a coverage matrix and prioritized, actionable follow-up tickets rather than asserting that diagnostics can cover every possible future failure.

The audit includes standard diagnostics, temporary detailed diagnostic mode, transcript-inclusive diagnostics, and their exports. Preserve the boundaries in `CONTEXT.md` and ADR 0003. Existing collection stays local; standard diagnostics remain content-free. Audio, dictated/reference text, vocabulary, clipboard content, surrounding application text, secrets, and identifying paths must not leak into ordinary logs or reports.

## Scope of investigation

Inventory the actual capture points, fields, mode controls, persistence, retention, export paths, tests, and user-facing instructions. Inspect both successful and failed paths, rather than judging coverage from a settings screen or list of events alone.

Map each relevant failure surface:

- Installation/update/startup, migrations, missing native libraries, unsupported ABI/runtime, and configuration changes.
- Recognition activity and IME entry points, lifecycle/session transitions, cancellation, backgrounding, interruption, and text delivery/switch-back outcomes.
- Microphone permission and audio acquisition: route changes, initialization/read errors, sample/buffer configuration, silence/VAD decisions, stopped recording, truncation, and no-speech outcomes. Capture technical counters rather than audio.
- Recognition model/variant/language configuration, download/validation/extraction, storage availability, readiness, updates, deletion, shared assets, and model ownership.
- Every supported recognition runtime, including legacy paths: load/unload, first/warm decode, streaming/final-only behavior, queue/backlog, native failures, resource exhaustion, and recovery.
- Device/build/runtime identity, relevant settings, CPU/thread configuration, named memory metrics, thermal/power conditions, and timing needed to separate performance causes. Unsupported metrics must be explicit rather than recorded as zero.
- Transcript cleanup and personal-vocabulary stages: eligibility, bypass/failure, execution time, and delivery status without recording content in standard diagnostics.
- Audio history and retranscription lifecycle failures, using content-free metadata; these are distinct from transcript-inclusive diagnostics.
- Persistence/export failures, malformed or partially written records, rotation/expiry/caps, low storage, interrupted exports, unavailable dependencies, and recovery.
- Recognition-model benchmarks from #17: technical run metadata may inform support, but reference/audio/transcript-bearing benchmark exports remain separate from standard diagnostics.

Only include integrations and features that actually exist. Explain exclusions and known limitations.

## Required coverage matrix

For each surface document:

1. A concrete symptom and the plausible causes a maintainer needs to distinguish.
2. The next triage decision and the smallest signals that support it.
3. Capture source, event/field names, timing and units, applicable mode, and whether evidence is available before failure.
4. Session/run correlation and ordering across asynchronous/native work, using local non-identifying IDs.
5. Persistence and export behavior, field classification/redaction, and degraded-state behavior.
6. Current coverage backed by source/test references, remaining gaps, and priority.

Do not add a field unless it changes a triage decision. Separate verified observations from proposed capture. Do not assume the current detailed mode is incomplete until the audit establishes a gap.

## Mode and artifact checks

Confirm standard diagnostics retain evidence of failures before a user enables detailed mode. Verify collection controls, disabled collection versus deletion, the documented seven-day/10 MB retention cap, detailed-mode expiry after 30 minutes, stop behavior, and app restart/process-death semantics. Verify the export accurately identifies which modes/time ranges were captured, missing evidence, dropped/truncated events, schema version, and units.

Detailed mode should add justified technical detail with bounded overhead and storage, not expand content permissions. Determine whether it materially helps distinguish failures while preserving the same content exclusions.

Verify transcript-inclusive capture stays separately enabled and exported, obeys its dedicated storage/retention limits and explicit export confirmation, and never enters standard diagnostics. Check that failures in diagnostics do not interrupt recognition, cleanup, or text delivery.

Review the user-triggered bug report flow, preview, issue-template instructions, and startup-failure fallback. A fresh install, missing model, broken runtime, denied microphone permission, and export failure should have an understandable way to supply available evidence without hidden setup. Do not introduce automatic uploads or broadly collect crash dumps without a demonstrated triage need.

## Verification and deliverables

Create a reproducible scenario set with source-backed expected signals. Exercise normal dictation and representative degraded cases, including permission denial, no speech, failed/corrupt model installation, model-load/decode failure, cancellation/interruption, cleanup bypass/failure, delivery failure, storage/persistence problems, and export failures. Use injected failures where necessary and label them; perform packaged-device checks where practical.

Add or identify tests for required evidence and privacy using realistic sensitive fixtures. Check every persisted/exported/logged path, including exception text and native logs, for content leakage. Measure detailed-mode overhead on representative cases and document the measurement method and device conditions. Where tests cannot reproduce native process death or device-specific failures, provide a manual protocol and mark verification pending.

Deliver an audit document under `docs/` with the coverage matrix, evidence, limitations, prioritized gaps, and recommendations. Create focused follow-up issues for justified changes, including acceptance criteria and dependencies. Record no-change findings where current coverage is sufficient. This ticket requests an audit; it does not authorize speculative instrumentation expansion or relaxation of existing privacy boundaries.

## Blockers and relationship to existing work

No known blocker to source/test review. Real-device checks depend on an available Android device and reproducible scenarios. #17 is related but does not block the audit; review its planned diagnostic/report boundary and recheck integration when implemented. The audit does not change existing catalog release gates.

## Acceptance checklist

- [ ] Inventory all diagnostic modes, collection/export controls, persistence/retention, and capture sources against the current code.
- [ ] Produce the symptom -> triage decision -> signal -> capture source/mode matrix for all actual failure surfaces.
- [ ] Verify event correlation, timing units, schema/version metadata, unavailable fields, and missing/truncated evidence behavior.
- [ ] Verify standard-mode evidence and detailed-mode activation, expiry, stop, restart, caps, and measured overhead.
- [ ] Verify standard, transcript-inclusive, benchmark, and audio-history content boundaries and exports remain distinct.
- [ ] Verify sensitive fixtures cannot leak through persisted records, exports, Logcat, exceptions, or native logging.
- [ ] Exercise representative success/failure/export scenarios and record automated versus packaged-device evidence separately.
- [ ] Review report preview, issue-intake instructions, and degraded/startup-failure fallback.
- [ ] Publish the evidence-backed audit and focused follow-up issues; record adequate coverage without unnecessary new fields.
- [ ] Update diagnostic documentation where it diverges from actual behavior, and disclose unresolved limitations.

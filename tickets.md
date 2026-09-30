# Tickets: Voice Input

Seven outcome tickets replace the 18 open tickets. All previous acceptance criteria, statuses and completed-work evidence remain in [ticket detail and history](docs/plans/ticket-detail-2026-09-30.md). Organization changes; scope and completion status do not.

Work on `codex/share-beta-integration`, then merge the tested release into `master`. Keep the unmerged `codex/share-09` experiment isolated until its feasibility decision. Start one new chat per numbered ticket and work one runnable slice at a time. Read the current integration ledger and linked details at the start; record evidence and the commit/checkpoint before handing off to the next chat. Run only one integration writer/build at a time. Numbers indicate recommended order, not additional blockers; skip a blocked ticket and return when its missing input is available.

Before working a ticket, read its linked sections. Their unchecked criteria are required detail; checked criteria and resolutions are existing evidence. Record verification there and check the summary only when its requirements are met. The detail file is not a second queue; do not reopen completed work. Historical claims/blockers are superseded by this queue.

The frontier is 01. The last checked release run failed at Build APK and publication was unverified; recheck live CI. See [finish plan](docs/plans/share-beta-finish.md).

## 01: Restore the integrated beta build

**What to build:** Fix the failed release build, pass the combined release gates and publish a signed test beta.

**Blocked by:** None.

**Triage:** ready-for-agent

- [ ] Record the actual failed CI command/error and reproduce or establish its cause.
- [ ] Fix the cause; pass unit tests, lint, UI-test compilation, native checks, APK build and signature/package verification.
- [ ] Record successful CI, tested commit, tag, prerelease URL and artifacts.

## 02: Verify the integrated voice-input experience

**What to build:** Finish integrated keyboard/editor, settings, download and microphone validation and fix demonstrated failures.

**Blocked by:** 01: Restore the integrated beta build.

**Triage:** ready-for-agent

- [ ] Verify every remaining criterion in the linked sections, including phone/editor interoperability, TalkBack, Bluetooth hardware and predictive-back animation.
- [ ] Record device/software/headset versions, settings and results; compilation or emulator checks do not substitute for required phone/headset evidence.
- [ ] Refresh help to describe verified behavior; rerun affected checks after fixes.

**Required detail:**

- [Add predictive-back transitions to settings navigation](docs/plans/ticket-detail-2026-09-30.md#add-predictive-back-transitions-to-settings-navigation)
- [Resume interrupted model downloads across retries](docs/plans/ticket-detail-2026-09-30.md#resume-interrupted-model-downloads-across-retries)
- [Clarify and reorganize Model Options](docs/plans/ticket-detail-2026-09-30.md#clarify-and-reorganize-model-options)
- [SHARE-01: Integrate FUTO Keyboard external-provider setup](docs/plans/ticket-detail-2026-09-30.md#share-01-integrate-futo-keyboard-external-provider-setup)
- [SHARE-02: Make IME text insertion session-safe](docs/plans/ticket-detail-2026-09-30.md#share-02-make-ime-text-insertion-session-safe)
- [SHARE-05: Make language settings follow the selected model](docs/plans/ticket-detail-2026-09-30.md#share-05-make-language-settings-follow-the-selected-model)
- [SHARE-06: Add explicit Bluetooth microphone selection](docs/plans/ticket-detail-2026-09-30.md#share-06-add-explicit-bluetooth-microphone-selection)
- [SHARE-07: Keep waveform and live transcript visible together](docs/plans/ticket-detail-2026-09-30.md#share-07-keep-waveform-and-live-transcript-visible-together)
- [SHARE-12: Refresh keyboard help and upstream comparison documentation](docs/plans/ticket-detail-2026-09-30.md#share-12-refresh-keyboard-help-and-upstream-comparison-documentation)

## 03: Finish safe model updates and readiness

**What to build:** Finish explicit atomic pinned updates, then reactive readiness and optional upgrade notices.

**Blocked by:** None.

**Triage:** ready-for-agent

- [ ] Verify every remaining manual-update criterion first, including active-session coordination, staged validation, rollback and reload.
- [ ] Then verify every reactive-readiness criterion through the update lifecycle contract.
- [ ] Preserve the usable installation, explicit confirmation and cheap startup checks. No automatic transfer or fabricated successor.

**Required detail:**

- [Add safe manual model updates](docs/plans/ticket-detail-2026-09-30.md#add-safe-manual-model-updates)
- [SHARE-04: Show reactive model readiness and upgrade notices](docs/plans/ticket-detail-2026-09-30.md#share-04-show-reactive-model-readiness-and-upgrade-notices)

## 04: Verify long dictation and bounded recovery

**What to build:** Measure long Moonshine dictation and add only demonstrated corrections and supported bounded replay recovery.

**Blocked by:** None.

**Triage:** ready-for-agent

- [ ] Verify all linked long-dictation criteria with phone baselines, continuity, memory and Stop-to-final measurements.
- [ ] Classify recoverable failures before implementation; verify every recovery criterion including injected failure and cancellation on-device.
- [ ] Preserve complete audio, session guards, one final result, cleanup and defaults. New modes or VAD models require the agreement specified in the original criteria.

**Required detail:**

- [Verify long Moonshine dictation and improve segmentation only if needed](docs/plans/ticket-detail-2026-09-30.md#verify-long-moonshine-dictation-and-improve-segmentation-only-if-needed)
- [SHARE-08: Recover once from a recoverable streaming failure](docs/plans/ticket-detail-2026-09-30.md#share-08-recover-once-from-a-recoverable-streaming-failure)

## 05: Decide whether optional engines earn inclusion

**What to build:** Finish the isolated transcribe.cpp experiment, then evaluate ASR4ALL Small and Parakeet 110M only if justified.

**Blocked by:** None for adapter feasibility; optional-model work requires a positive adapter feasibility decision.

**Triage:** ready-for-agent

- [ ] Resolve every adapter source/build/coexistence/inference/notice/measurement criterion; record a go/no-go decision.
- [ ] On a positive decision, verify every optional-model criterion before production promotion.
- [ ] A negative decision may defer dependent work with evidence and explicit disposition; unperformed checks are not passed. Preserve existing defaults and runtimes.

**Required detail:**

- [SHARE-09: Evaluate a reproducible transcribe.cpp adapter](docs/plans/ticket-detail-2026-09-30.md#share-09-evaluate-a-reproducible-transcribecpp-adapter)
- [SHARE-10: Evaluate ASR4ALL Small as an optional model](docs/plans/ticket-detail-2026-09-30.md#share-10-evaluate-asr4all-small-as-an-optional-model)
- [SHARE-11: Evaluate Parakeet 110M as an optional model](docs/plans/ticket-detail-2026-09-30.md#share-11-evaluate-parakeet-110m-as-an-optional-model)

## 06: Resolve the intermittent waveform report

**What to build:** Reproduce the reported waveform failure, fix its cause and verify the reported phone scenario.

**Blocked by:** User description of the symptom, entry point and reproducible scenario.

**Triage:** needs-info

- [ ] Obtain the missing symptom and compare popup enabled/disabled in the affected entry point.
- [ ] Verify every remaining source criterion with a failing regression check and phone evidence. General layout checks do not close the report.

**Required detail:**

- [Diagnose intermittent waveform behavior when opening voice input](docs/plans/ticket-detail-2026-09-30.md#diagnose-intermittent-waveform-behavior-when-opening-voice-input)

## 07: Calibrate and release the tested catalog

**What to build:** Complete catalog performance/regression evidence and release the verified scope.

**Blocked by:** 02: Verify the integrated voice-input experience; 03: Finish safe model updates and readiness.

**Triage:** ready-for-agent

- [ ] Verify every remaining calibration criterion, including measured latency/memory/thermals/backlog, attribution, regression checks and APK inspection.
- [ ] Record disposition of 04, 05 and 06; explicitly disclose or defer unfinished work.
- [ ] Publish from the tested commit and merge codex/share-beta-integration into master after applicable release checks pass.

**Required detail:**

- [Calibrate and release the complete model catalog](docs/plans/ticket-detail-2026-09-30.md#calibrate-and-release-the-complete-model-catalog)

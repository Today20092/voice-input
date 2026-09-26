# SHARE adoption and beta coordination

Baseline: `eb5e1dadfd9d6ceb2ebb78ca4f403d2718bddcf8` from `origin/master`.
Coordinator chat: `01a0df68-5245-78d1-9e16-f31f778c85fb`.
Integration branch: `codex/share-beta-integration`.
Integration worktree: `C:/Users/User/.codex/worktrees/share-beta-integration/futo-parakeet-voiceinput`.

The user authorized separate chats and isolated worktrees for every unfinished ticket, parallel work, ongoing coordination, then integration and a beta after all tickets are resolved and verified. Each child was created from origin/master. Creation returned client IDs; resolve real thread IDs through list_threads before sending follow-ups. Never pass client IDs to thread tools.

## Flow

Ready tickets follow Ask Matt -> implement -> TDD -> code-review -> local commit. Research tickets follow primary-source research and feasibility gates. The existing tickets and adoption spec supply scope; no repeated specification interview is needed.

Start SHARE-01, SHARE-02 and SHARE-05 implementation in parallel. SHARE-09 and long-Moonshine inspection can research concurrently. Other chats have preparation-only first turns; explicitly dispatch implementation after prerequisites and overlapping changes are ready. Cap concurrent expensive Android/native builds at one; focused checks may run independently. User/device evidence remains required where tickets say so.

## Dependencies and dispatch

- Nemotron assets -> download resumption -> SHARE-03 manual updates -> SHARE-04 reactive readiness.
- SHARE-05 -> Model Options -> predictive-back navigation validation.
- SHARE-09 positive feasibility -> SHARE-10 and SHARE-11 evaluation. A negative feasibility finding is a documented outcome, not permission to implement a different runtime or falsify downstream acceptance.
- SHARE-06 and SHARE-08 both touch recording; sequence their integration and regression checks.
- SHARE-07 does not resolve the separate intermittent waveform report.
- SHARE-12 can correct current facts immediately; new-feature instructions follow verified code.
- Catalog calibration/release is last. Keep hardware blockers and missing reproduction explicit.

## Task registry

Each task owns only its ticket changes and feature files in its own worktree. Before edits, add Working branch and Claimed by to that ticket. Branch names below were assigned in the prompts. These creation IDs are pending handles, not usable thread IDs.

| Key | Ticket | Flow | Branch | Creation handle |
| --- | --- | --- | --- | --- |
| share-01 | SHARE-01: Integrate FUTO Keyboard external-provider setup | implement | codex/share-01 | client-new-thread:1c557417-94b2-4478-9672-bc7c06976f22 |
| share-02 | SHARE-02: Make IME text insertion session-safe | implement | codex/share-02 | client-new-thread:03dd7cf9-4e2e-4a9b-8158-4b73f890d794 |
| share-05 | SHARE-05: Make language settings follow the selected model | implement | codex/share-05 | client-new-thread:25ccb30b-2c09-4bd9-adce-535426198968 |
| share-09 | SHARE-09: Evaluate a reproducible transcribe.cpp adapter | research | codex/share-09 | client-new-thread:e8812795-806a-4a7c-a6d0-bc19b4be060d |
| share-03 | Add safe manual model updates | prepare | codex/share-03 | client-new-thread:2337f904-12af-4334-88eb-e3f0b82d1a04 |
| share-04 | SHARE-04: Show reactive model readiness and upgrade notices | prepare | codex/share-04 | client-new-thread:5df5836a-e49a-451a-af9d-2245c4b6c348 |
| share-06 | SHARE-06: Add explicit Bluetooth microphone selection | prepare | codex/share-06 | client-new-thread:f271d8de-efec-4b36-969d-f56206531f98 |
| share-07 | SHARE-07: Keep waveform and live transcript visible together | prepare | codex/share-07 | client-new-thread:0aec9fc1-a8b3-43a1-9d09-b19935797597 |
| share-08 | SHARE-08: Recover once from a recoverable streaming failure | prepare | codex/share-08 | client-new-thread:61c00b44-f7cc-4fb3-821f-446010b07f31 |
| share-10 | SHARE-10: Evaluate ASR4ALL Small as an optional model | prepare | codex/share-10 | client-new-thread:a9836515-3407-4f7a-bbfb-91b57d601ccd |
| share-11 | SHARE-11: Evaluate Parakeet 110M as an optional model | prepare | codex/share-11 | client-new-thread:66c9c760-a39f-48ef-a391-bc29dd0f5d9a |
| share-12 | SHARE-12: Refresh keyboard help and upstream comparison documentation | prepare | codex/share-12 | client-new-thread:f5e4c3d3-23d5-4ac5-9634-7b5240e4e2fb |
| nemotron-assets | Exclude test audio from the Nemotron 3.5 Multilingual installation | prepare | codex/nemotron-assets | client-new-thread:e0436531-b09a-4a60-b270-1696b2198753 |
| resume-downloads | Resume interrupted model downloads across retries | prepare | codex/resume-downloads | client-new-thread:ce89e09a-d4f3-4f08-856d-6cbc82fb494b |
| model-options | Clarify and reorganize Model Options | prepare | codex/model-options | client-new-thread:bdde943c-0b49-41ee-9255-11af34cf18bf |
| predictive-back | Add predictive-back transitions to settings navigation | prepare | codex/predictive-back | client-new-thread:89c9830e-d97f-4c9c-90db-fb017a6e3cfb |
| moonshine-long | Verify long Moonshine dictation and improve segmentation only if needed | research | codex/moonshine-long | client-new-thread:68ea11db-14d4-482d-b298-db2df0b2e19d |
| waveform-bug | Diagnose intermittent waveform behavior when opening voice input | prepare | codex/waveform-bug | client-new-thread:4d3e7fbd-7ab6-4e19-996b-63ac31cfe79f |
| catalog-release | Calibrate and release the complete model catalog | prepare | codex/catalog-release | client-new-thread:2289b8ba-a2f8-4c12-88e3-600a56f61e66 |

## Integration and release gate

Read child reports and verify their commits and checks. After all tickets have verified outcomes, merge their commits into the integration branch, resolving overlapping ticket metadata by preserving both outcomes. Before dependent implementation, supply prerequisite commits to the relevant child and have it incorporate them into its own branch. Run the combined unit, instrumentation, assembly and lint checks; keep missing device measurements open. Review signing, versioning and existing release automation before producing a beta. Do not tag, publish, or describe the beta as ready while required acceptance criteria remain unverified. Research may conclude no-go where the ticket permits it; record that decision explicitly rather than pretending an integration shipped.

This file is the coordinator's durable ledger. Update real thread IDs, commit SHAs, build-slot owner, outcomes and blockers as they become available.


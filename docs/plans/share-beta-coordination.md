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

## Runtime coordination status

Heartbeat automation `voice-input-ticket-coordination` checks progress every ten minutes in the coordinator chat. Creation was confirmed active.

Confirmed real task IDs from child messages:

- SHARE-02: `01a0df6d-6c8e-7973-9def-e478a790a2bd`, worktree `a44e`, implementation active; editor-contract TDD seam confirmed.
- SHARE-05: `01a0df6d-7306-7ea1-a675-a0f15aab1ca2`, worktree `96da`, implementation active; language/navigation/settings-effect seams confirmed.
- SHARE-04: `01a0df6d-a8b0-74f0-8c10-fb16e0b5739d`, worktree `c425`, preparation complete, awaiting SHARE-03.

SHARE-04 requests this contract from SHARE-03: usable installed version, missing/incompatible state, optional pinned successor, explicit update-confirmation entry point, observable invalidation across lifecycle instances after installation/deletion/activation/failure. Relay to SHARE-03 before implementation.

The user confirmed Android Studio's emulated Pixel is available. Verified `Pixel_10` with `%LOCALAPPDATA%/Android/Sdk/emulator/emulator.exe -list-avds`; adb showed no running device. Allocate the emulator exclusively, verify its ABI/API before using it for this arm64 app, and use it for UI/navigation/lifecycle/instrumentation where supported. It does not replace phone-specific performance/thermal measurements. No full-build or emulator slot is currently allocated.

All 19 ticket worktrees were observed in `git worktree list` at the baseline. Initial `list_threads` snapshots omitted the newly created chats despite valid child messages; use those messages as authoritative real IDs and recheck listing later. Do not duplicate the creation requests.

## Final gate

Read child reports and verify their commits and checks. After all tickets have verified outcomes, merge their commits into the integration branch, resolving overlapping ticket metadata by preserving both outcomes. Before dependent implementation, supply prerequisite commits to the relevant child and have it incorporate them into its own branch. Run the combined unit, instrumentation, assembly and lint checks; keep missing device measurements open. Review signing, versioning and existing release automation before producing a beta. Do not tag, publish, or describe the beta as ready while required acceptance criteria remain unverified. Research may conclude no-go where the ticket permits it; record that decision explicitly rather than pretending an integration shipped.

This file is the coordinator's durable ledger. Update real thread IDs, commit SHAs, build-slot owner, outcomes and blockers as they become available.

## Current checkpoint — 2026-09-28

This section supersedes the earlier creation-handle registry and runtime status above. All 19 chats and worktrees exist; do not recreate them. No ticket is yet fully verified and closed. No feature merge or beta publication has occurred. The prior coordinator turn was interrupted before permission refresh and ledger updates finished. The heartbeat is active with notifications enabled; bring missing user input and the eventual consolidated completion report to this chat.

| Key | Actual thread ID | Worktree | Latest checkpoint |
| --- | --- | --- | --- |
| share-01 | 01a0df6d-6b0e-7ce0-9706-ea24fae95dde | 2e21 | Resumed Sep 28; code and focused tests exist, full validation and commit pending |
| share-02 | 01a0df6d-6c8e-7973-9def-e478a790a2bd | a44e | Resumed Sep 28; implementation commit 98666a4, native dependency fetch/review/full validation pending |
| share-05 | 01a0df6d-7306-7ea1-a675-a0f15aab1ca2 | 96da | Resumed Sep 28; JVM suite passed and expanded UI tests compile, review/device checks/commit pending |
| share-09 | 01a0df6d-74af-78a2-851c-ff6880aa9a19 | 9e07 | Research commit fc4635e; adapter experiment and six acceptance criteria remain open |
| share-03 | 01a0df6d-a4d3-7b23-aff9-326ddb941334 | e875 | Readiness contract committed as 4b60157; awaits download prerequisites |
| share-04 | 01a0df6d-a8b0-74f0-8c10-fb16e0b5739d | c425 | Prepared; SHARE-03 contract relayed, production prerequisite pending |
| share-06 | 01a0df6d-b800-7a42-bb07-d8851c9d7fac | 5abf | Routing plan committed as b3fa405; implementation queued |
| share-07 | 01a0df6d-b8be-7343-839a-6b7221efc9c3 | 5220 | Prepared; reuse SHARE-02 generation guards before implementation |
| share-08 | 01a0df6d-b8bd-7c93-8cef-6adf7cfa148e | 5db2 | Prepared; Moonshine failure propagation and recording boundaries need coordination |
| share-10 | 01a0df6d-b8d7-7d90-9dea-ebe690f8057c | c72a | Prepared; compatible adapter and phone evidence still gated |
| share-11 | 01a0df6d-db91-7253-94a6-033d8dd0fc7f | 7e06 | Source preparation; compatible adapter and phone evidence still gated |
| share-12 | 01a0df6d-db72-7aa1-8cfb-13d7fa2c54fa | eb0e | Factual corrections authorized; interrupted; new behavior docs await verified features |
| nemotron-assets | 01a0df6d-fbe4-76f0-b278-331242777e01 | 0b2b | Implementation authorized Sep 26 but interrupted near start |
| model-options | 01a0df6e-16f5-7d22-b916-56c53222e516 | e80d | Prepared; awaits SHARE-05; permission refresh pending |
| resume-downloads | 01a0df6e-1708-7302-93b9-d09642142788 | 8687 | Preparation; awaits Nemotron asset fix; permission refresh pending |
| predictive-back | 01a0df6e-1d75-7371-b7fe-92ceaaebaf01 | 0d54 | Preparation; awaits Model Options; permission refresh pending |
| moonshine-long | 01a0df6e-1dc8-7681-bf3e-d6f700140855 | 3f10 | Research checkpoint; permission refresh pending |
| waveform-bug | 01a0df6e-2f09-7c73-9b43-80593221d7c4 | 0245 | Preparation; permission refresh pending |
| catalog-release | 01a0df6e-3ed8-7622-b381-354cbe5dd4d6 | b6e6 | Final hardware/release gate; permission refresh pending |

Permission audit of latest session turn contexts at 18:24 UTC Sep 28: first 13 rows use approval_policy=never and sandbox=danger-full-access. Last six still show on-request/workspace-write. The three resumed implementation turns were freshly verified as Full access. Do not claim all 19 refreshed yet. The proven refresh is a new continuation through the app's Full access composer; subsequent send_message_to_thread preserves that setting. Preserve existing checkpoints.

Build/emulator queue: SHARE-02 owns the slot, then SHARE-01, then SHARE-05. Direct handoff instructions sent Sep 28. Only one expensive build/emulator driver, max two Gradle workers. SHARE-02 previously started Pixel_10 as emulator-5554 with ARM64 native bridge; recheck actual current device state before use. Both SHARE-01/02 encountered failed native submodule initialization; SHARE-02 is assigned diagnosis without changing pinned revisions.

Additional contracts: SHARE-02 owns stale legacy/streaming partial and final Main dispatch generation guards. SHARE-07 reuses these and checks any remaining catch-up callback. SHARE-08 must suppress callbacks from failed runtimes within the same generation and coordinate Moonshine worker error propagation/native cleanup. SHARE-05 introduces shared RecognitionModelLanguageOptions(model) and ModelPresentation language guidance; Model Options should reuse these after handoff.

Research gate: fc4635e documents inaccessible exact FUTO engine revision and a fetchable distinct public Parakeet candidate, not successful integration. ASR4ALL API compatibility is absent. SHARE-10/11 remain blocked on a working compatible adapter; source research completion alone is insufficient. Phone performance, thermals, real headset routing and other physical-device acceptance remain unverified.

### Recovery completed — Sep 28 follow-up

- Latest session-context audit confirms all 19 chats now use `never` / `danger-full-access` (19 full, zero restricted). Last six were resumed through the Full access composer. Subsequent coordinator messages preserve these settings.
- Thirteen ticket checkout directories had disappeared since Sep 26. Recreated only absent directories at their original paths using saved branches where present and baseline branches otherwise; no existing checkout was overwritten. Committed research/contracts survived in Git. Owners of resumed tasks were told to reconstruct any missing uncommitted preparation from their chats. All original task IDs remain valid.
- SHARE-02 reports full unit suite, app APK, test APK and lint passed, plus 13 focused JVM and 5 actual Android EditText instrumentation tests on emulator-5554. Final review/ticket commit pending; actual runtime rotation, WebView and FUTO Keyboard interoperability remain open. Expensive slot released directly to SHARE-01, then SHARE-05, then Nemotron assets.
- SHARE-01: seven focused JVM tests pass and both review axes have no implementation blockers; native checkout long-path issue repaired without changing pinned revision. Full checks now have the slot.
- SHARE-05: reviewed commit `0d394e8`, clean tree; 142 JVM tests passed, one skipped, zero failed; UI tests compile. Assembly, lint, five instrumentation tests and physical-device checks remain open. Model Options has inspected the shared language contract and awaits verified handoff.
- SHARE-12 factual documentation commit `3479da9`, clean tree, both reviews passed. XML/resource/link/diff checks passed. New setup/update behavior and Help device rendering/accessibility remain open.
- Nemotron assets resumed after checkout recovery. Catalog regression demonstrated red/green; focused catalog/retry JVM tests and Android test compilation passed. Full JVM suite/review/local commit underway; intent instrumentation remains queued. No downstream implementation handoff yet.
- Download resumption and predictive-back preparation restored and ready behind dependencies. Moonshine research and demonstrable worker-error/native-cleanup fixes resumed, coordinating directly with SHARE-08. Waveform diagnosis resumed separately from SHARE-07.
- Main-chat input requested for waveform symptom, whether transcription works, entry point, app version/model, and cold/repeated-open reproduction. Await user reply and forward it to waveform owner. Other independent work continues.
- Waveform source/evidence review committed as `87b8763`, clean checkout 0245, in `docs/research/waveform-diagnosis-2026-09-28.md`. Existing first-frame telemetry records nonempty bars, including silence; it does not prove visible movement or continuous rendering. Ticket remains needs-info with acceptance unchanged. No production changes or device tests. Do not treat SHARE-07 layout or ring-buffer tests as closure of this defect.
- SHARE-02 final reviewed commit is `2c8518f415f8d8e15bcbeb70583fde705025c413`; it amends/replaces `98666a4`. Use only `2c8518f` for handoffs. Clean tree, both reviews cleared, JVM 154 reported with zero failures/errors and one existing skip; 13 new tests and 5 Android editor tests passed. Full unit/assembly/test-APK/lint passed on final production code; APK revision label predates the amendment. End-to-end dictation rotation, WebView/real-IME delayed callbacks and FUTO Keyboard interoperability remain open. No release/merge.

### Dispatch — Sep 28 18:45 UTC

- SHARE-01 reports full unit suite, app/test APK assembly and lint passed; emulator tests and real-keyboard fallback check active. It retains the exclusive slot, then SHARE-05, Nemotron assets, and Moonshine.
- Nemotron assets reviewed commit is `a28fb96e1dff513bf7e4855fc1c3519636f93b56`; full JVM suite 143 tests, zero failures/errors, one skipped. Android request tests compile but have not executed. Download resumption remains queued until that regression validation.
- SHARE-07 implementation dispatched, incorporating verified `2c8518f` before unified waveform/transcript/status work. SHARE-06 implementation dispatched from routing plan `b3fa405`, also incorporating `2c8518f`; both must coordinate recording overlap and keep unverified physical-device criteria open. Focused tests allowed; expensive builds/emulator require slot.
- SHARE-09 dispatched for isolated public Parakeet adapter experiment from `fc4635e`, with no production default change or ASR4ALL compatibility claim. Native builds require slot. SHARE-10/11 gates remain closed.
- Moonshine actively testing confirmed worker-error loss and ignored closed-channel feeds using injected runtime. Segmentation still requires evidence; physical-phone timing remains unmeasured.
- Exclusive queue extended and relayed: SHARE-01 (current) -> SHARE-05 -> Nemotron -> Moonshine -> SHARE-07 -> SHARE-06 -> SHARE-09. Explicit handoff required; skip an owner that does not need the slot. SHARE-07 owns the remaining queued onCatchingUp generation guard. SHARE-06 owns recorder creation/release/reset routing hooks and a control outside changing recognition content, without appendSamples/backend/tail-drain edits. Boundaries relayed to SHARE-07/08; owners may coordinate directly.
- Latest slot handoff: SHARE-01 released to SHARE-05, which is running assembly/test APK/lint with two workers and native parallelism two, then LanguagesScreenTest on emulator-5554. Nemotron is next. Await SHARE-01's final evidence report before changing its acceptance status.

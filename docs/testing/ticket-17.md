# Ticket 17: benchmark evidence and remote APK preparation

Reviewed October 24, 2026 against integration `bf31d24`. [Issue #17](https://github.com/Today20092/voice-input/issues/17), including its October 10 comment amendment, requires a full current-catalog fixed-audio pass and guided human readings through both entry points. The shipped UI/runner is not the missing full-device evidence.

## Existing evidence, not a fresh benchmark

Read-only inspection of ignored local reports under `benchmarks/results/` exposed only metadata/counts during this audit. Reports contain private references/transcripts and are not copied into this document.

| Saved run | Observed state | What it establishes |
| --- | --- | --- |
| `b5f44de1-7e7d-4f87-aadc-a46750a39a1a` | Completed fixed-audio pilot: 1 selected/completed model, 1 verified clip, 6 observations. Two completed delivery-verified live readings; 11 interrupted live attempts. | Limited bundled Whisper pilot and both live entry paths. Existing documentation records owner review of its reference and one keyboard insertion. Interrupted attempts are not successful readings. |
| `6945d405-1e91-4942-8cfb-6fa20fc363c9` | Interrupted full run: 20 selected, 12 completed cases and 1 interrupted case, 200 retained observations, 0/3 verified references, no live readings. | Durable partial timing/output evidence, not a full-catalog or scored-accuracy result. `build-evidence.json` records resumed APK changes: retain that limitation rather than pooling it as one unchanged-build ranking. |
| `520fadaa-edbd-4e46-8679-04d06bba4466` / `a0ef79a1-24b4-4258-b38e-a07d6a539b99` | Newer partial reports assert 3/3 verified references; neither is a completed full suite. | Per-run assertions must be retained. They do not authorize silently changing supplied `suite.json`, whose three flags remain false, or claiming universal reference review. |

The [comparison guide](../model-comparison.md#verification-status) records earlier S25 tests and the owner-reviewed pilot. This audit did not listen to recordings, confirm additional references, rescore results or infer phone measurements from synthetic UI charts.

## Concrete tooling change

The ADB runner's `-Install` requires **both** a dev-debug app and matching instrumentation APK. Existing release CI only built the standalone app and compiled Android-test Kotlin, leaving no installable pair for the requested no-local-build workflow. The release workflow now has an optional `build_benchmark_apks` manual input, default false. When enabled, it builds the pair remotely after release checks and uploads `benchmark-device-apks-<full commit SHA>` with SHA-256 checksums. It does not start an emulator or publish a release.

Coordinator command after the final integration SHA is pushed:

```powershell
gh workflow run release-apk.yml --repo Today20092/voice-input --ref codex/ticket-priority-integration -f build_benchmark_apks=true
```

Download the successful run's `benchmark-device-apks-<full commit SHA>` artifact into `app/build/outputs/apk/`; its layout preserves `dev/debug/` and `androidTest/dev/debug/` for the runner. Retain the workflow URL, exact SHA, artifact checksums and signing identity with results. Debug signing must be compatible with any existing developer install. If Android rejects an update for a signing mismatch, preserve/export the old developer package's benchmark evidence and decide on migration explicitly; never work around it by automatically uninstalling or clearing data. The production package is separate.

## Next device session

The following commands are documented for a later connected, authorized physical phone. They were **not run** in this audit:

```powershell
.\tools\test-model-benchmark.ps1
.\tools\run-model-benchmark.ps1 -Install
.\tools\run-model-benchmark.ps1 -Mode Full -DownloadMissing -CoolingSeconds 30
```

Inspect the new pilot before the full pass. Confirm canonical/original/reference hashes, actual sample counts and durations, app/build/model identities, initialization, first decode, one excluded warm-up and five measured warm repeats per clip, close-call time and independent ownership. One first decode exists for the first clip only: a three-clip/five-repeat case has 16 measured observations, not 18. Every model receives a defensive copy of the same canonical clip, loaded one clip at a time. Downloads/conversion/scoring/persistence remain outside decode timing.

Derive coverage from the current catalog; the historical count of 20 is not a fixed current inventory. Without `-DownloadMissing`, unavailable cases are skipped. Review storage totals, per-case failures, unavailable metrics and the final coverage count. A saved run resumes only still-unattempted cases: `-ResumeRunId <UUID>` preserves completed/failed/interrupted cases. A fresh current-build comparison needs a new run; resuming the mixed-build historical run is not a replacement.

Use `-ReferencesVerified` only after the owner has checked **every selected** spoken reference. It is an explicit assertion and can rescore saved observations without inference; do not use it merely to fill accuracy columns. Existing pilot review remains valid for that pilot, not automatically every selected clip in a new run.

Guided live readings require a person for each selected variant through both the controlled IME editor and recognition-activity caller. Review actual spoken deviations, raw/final/observed text, once-only delivery and timing milestones; save the corrected reference. The retained experimental live harness is not linked from ordinary comparison UI. Fixed-audio ADB automation cannot supply human microphone readings. Include separately identified long-Moonshine continuity/backlog/Stop-to-final evidence inherited from #12.

Startup process overhead is explicitly unavailable; model-unloaded does not mean filesystem-cache-cold. PSS is sampled app-process memory at a requested 250 ms interval, not an exact model allocation peak. Whistle's separate worker memory and separate already-loaded timing are unavailable; each pass includes worker loading. Battery state is not energy consumption. Speech end is the last VAD-positive capture-loop frame, final-only models have no partial timing, and automatic-stop readings have no manual Stop latency.

## Verification/disposition

Local checks are limited to PowerShell parsing/original-file hashes and `git diff --check`; no Gradle, native build, emulator, model download, phone inference or guided reading ran. The new optional CI path still requires a successful dispatch/artifact and phone installation. Keep #17 open for reviewed full-catalog reports and guided evidence, distinct from #15's broader integration/release safety checks. Export JSON/Markdown only after reviewing their references/transcripts; audio is not included in report export and nothing uploads automatically.

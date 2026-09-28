# Share beta finish plan

Updated 2026-09-28. This plan supersedes the parallel dispatch instructions in
`share-beta-coordination.md`; that file remains historical evidence.

## Ownership and checkpoint

- Integration owner: chat `01a0e9d0-a7c3-73e0-8aa8-e14d9d1d84de`.
- Branch: `codex/share-beta-integration`, based on `eb5e1da` from master.
- The previous coordinator stopped dispatches and deleted the
  `voice-input-ticket-coordination` heartbeat.
- SHARE-06 paused at `e99f37f`; SHARE-09 paused at `ab10fa5`.
- Other ticket chats stay idle. No automatic slot handoffs or new parallel builds.
- Run one integration/build at a time. Only the integration owner edits the beta.

## First test beta

Publish `v1.4.6-share-beta.1` as a GitHub prerelease from this branch after the
combined unit tests, lint, APK build and signing/package checks pass. The beta
collects completed source changes for testing; it does not close acceptance
criteria that require real devices or unfinished research.

Merge existing branches one at a time in this order, preserving their history:

1. SHARE-01 keyboard provider setup (`5351c2e`).
2. SHARE-02 session-safe insertion (`2c8518f`).
3. SHARE-05 model language controls (`daef16e`).
4. Nemotron asset filtering (`7e98997`).
5. Download resumption (`be5d7d3`), after Nemotron.
6. Moonshine failure/lifetime fixes (`5c3a409`).
7. Model Options (`76df21c`), after SHARE-05.
8. SHARE-07 waveform/transcript UI (`50f3813`), after SHARE-02.
9. SHARE-06 microphone routing (`e99f37f`), after SHARE-02/07.
10. SHARE-12 help and comparison docs (`788cb24`), after SHARE-01.

Inspect overlapping changes before committing conflict resolutions. In particular,
SHARE-06 routing must wrap SHARE-07's shared recognition window, and duplicated
dependency commits must not reintroduce old implementations. Check whitespace and
conflict markers after every merge; run combined Gradle checks once the source set
is assembled, then rerun affected checks for any fixes.

## Finish remaining tickets in order

| Order | Work | Exit condition |
| --- | --- | --- |
| 1 | Test the integrated beta's provider setup, insertion, language settings, Model Options, waveform/transcript and microphone selection | Record each ticket's real keyboard, TalkBack, phone and headset results; fix failures before a follow-up beta |
| 2 | Finish download-resumption device checks, then SHARE-03 safe manual updates | Preserve the active usable model across interrupted/failed updates; expose the installed/update readiness contract |
| 3 | SHARE-04 reactive readiness | Observe lifecycle invalidation and explicit update confirmation using SHARE-03's contract |
| 4 | Predictive-back navigation | Validate against the integrated Model Options navigation and Android versions required by the ticket |
| 5 | Long Moonshine dictation | Run the specified real-device long-dictation/segmentation checks; change segmentation only if evidence requires it |
| 6 | SHARE-08 streaming recovery | First demonstrate a supported recoverable failure category; implement one bounded retry only if the backend contract supports it |
| 7 | SHARE-09 adapter experiment | Complete full build/notice script, real JNI inference, runtime coexistence, packaging and phone measurements |
| 8 | SHARE-10/11 optional models | Proceed only after SHARE-09's positive feasibility gate; record a justified negative outcome if infeasible |
| 9 | Intermittent waveform report | Obtain the still-missing reproduction/entry-point description, then reproduce and fix |
| 10 | Complete model catalog calibration/release | Resolve manual updates and all required model/device/performance evidence, refresh docs, publish the final tested release |

`tickets.md` remains the acceptance checklist. Merging source, publishing a beta,
or archiving a checkout does not by itself complete a ticket.

## Device checkpoint

The Pixel_10/API37 emulator is running, but `org.futo.voiceinput.dev` is currently
uninstalled with data retained. Both prior and new APK installation failed with
`INSTALL_FAILED_INSUFFICIENT_STORAGE`; approximately 739 MiB remained on `/data`.
Do not wipe or resize the original AVD as part of branch cleanup. No AVD disk
backup exists yet. A future recovery step must preserve userdata and snapshots.

The prior APK is preserved outside the disposable checkout at:
`C:/Users/User/.codex/visualizations/2026/09/26/01a0df6d-b800-7a42-bb07-d8851c9d7fac/share07-prior.apk`.
SHA256: `031e2ba9cf2bff2ff6b30d109c6b502a16d3b9a90c5a43eea64423dd39c72e9c`.

The first published beta must disclose that integrated device checks remain open.
Use a physical arm64 device for the beta, or a separate suitable test AVD without
altering the retained-data AVD.

## Cleanup policy

- Keep the primary checkout and beta integration worktree.
- Preserve SHARE-09's unfinished experiment, including ignored native artifacts.
- Archive idle, integrated feature worktrees through their owning chats' managed
  worktree tools after verification. Preserve any necessary ignored evidence first.
- Preserve unique unmerged research/contract commits before retiring their checkout.
- Delete local feature branches only after their commits are reachable from the
  integration branch or an explicit archival ref. Never force-delete unknown work.
- Empty preparation branches at `eb5e1da` can be deleted once no checkout uses them;
  recreate from the current beta when their ticket starts.
- Keep chats available as evidence. Do not close PRs or delete remote branches merely
  because their local checkout is no longer needed.

## Completion record

- All ten feature branches merged with history preserved. Release source commit:
  `76844dae593c68f4d9f620e0d4eb8de823f04147`.
- Tag `v1.4.6-share-beta.1` pushed; publication is gated by
  [release CI](https://github.com/Today20092/voice-input/actions/runs/36484940228).
- SHARE-03, SHARE-08 and waveform investigation documentation subsequently merged
  into the integration branch; no runtime changes were added after the release tag.
- Deleted empty local preparation branches `codex/catalog-release`,
  `codex/predictive-back`, `codex/share-04`, `codex/share-10`, `codex/share-11`.
- Pending: CI result, prerelease URL and final managed-worktree cleanup inventory.

# Branch recovery and workflow migration

October 30, 2026. The owner requested recovery of useful work, retirement of four old remote branches, and new work on `development` before promotion to `master`.

Development starts from remote master `5d52ccb`. The remote default remains `master`; promotion is a separate reviewed merge. The existing upstream-integration worktree contains unfinished changes and is outside this cleanup.

| Retired branch | Original tip | Disposition |
| --- | --- | --- |
| `codex/whistle-beta4` | `004dcf378a587dd1c23ec722ffde798a429bf50d` | Archive only. Current master already contains Whistle support from the later beta 5 integration; retain its newer implementation. |
| `codex/share-beta-integration` | `307c783173c5af265e05a5b7882716cbe4d56041` | Recover the missing published-artifact verification section in `docs/testing/beta4-cleanup.md`. Cleanup implementation is already in master. |
| `codex/issues-40-41-s25-testing` | `6d7ba6a33ccc511cecc01284572abd35fd23b96d` | Merge all three commits into development: S25 evidence, dated reports and the longer S1 benchmark. Preserve the existing output-parity, completion, memory and context-budget assertions. Production S1 speculation stays disabled. |
| `codex/release-1.4.9` | `31a322016efa847a4b5a9ed2195c83c99c47bbd1` | Recover documentation and instruction corrections. Preserve current GitHub tracker and low-memory rules; add the development workflow. Keep the field-study and Voxtral documents as dated historical records. |

Original tips are retained under `archive/<branch>` tags before deleting the remote branches. These are recovery references, not release tags.

Local master/cleanup commit `4d84a5c` is reconciled by an ancestry-preserving merge. The current downloader uses adjacent staging and creates nested parent directories in `ModelFileDownload.kt`; it no longer uses the broken temporary-file prefix. Keep the current `NestedDownloadTest`, which uses explicit historical nested filenames because current Nemotron no longer downloads test audio.

## Deferred recognizer migration and upstream synchronization

The owner subsequently deferred full shared-recognizer adoption in #48. Preserve
its disabled proof and implementation plan on
`origin/codex/issue-48-full-futo-adoption` at `27dc002`. It supersedes the local
`Today20092/assess-shared-recognizer-migration-with-fork-fea` branch name; the
worktree remains available and is marked todo in Orca.

`Today20092/bring-fork-up-to-upstream` retains upstream merge `4c5bfad` and bundled
ASR4ALL change `cf83820`. The local experimental checkpoint `652db38` was removed
from that branch only after preservation on the separate remote branch. Current
`origin/development` was merged into the upstream-sync branch, preserving its
workflow, recovered evidence and S1 benchmark changes. Prepare a draft PR into
`development`; do not promote this branch directly to `master` or include #48's
experimental source sets. Unrelated local branches and worktrees are retained.

Future #48 work starts from then-current `development`, reuses the parked
evidence deliberately and first checks exact native-source availability. It must
not repeat unsuccessful account setup: the owner has no FUTO-hosted account and
FUTO's internal GitLab does not offer public registration.

## Original cleanup verification scope

- Check recovery against the exact remote branch tips and current master, including duplicated Whistle files and missing release evidence.
- Check JSON/JSONL evidence, local documentation links, conflict markers and whitespace.
- Run the existing release build and S1 proof workflows on development; record their source revision and outcome when available.
- Treat the recovered phone evidence as historical. No new phone tests, release version, stable tag or APK publication is part of this cleanup.

The Git workflow is maintained in [development and releases](development.md#git-workflow). Tracker and vocabulary homes remain unchanged.

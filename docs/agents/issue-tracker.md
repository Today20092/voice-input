# Issue tracker: GitHub

Active tickets live in [Today20092/voice-input GitHub Issues](https://github.com/Today20092/voice-input/issues). Use the authenticated `gh` CLI. On this Windows machine it is at `C:\Program Files\GitHub CLI\gh.exe` if absent from PATH.

Read bodies, current labels and comments with `gh issue view <number> --repo Today20092/voice-input --comments`. Create issues with an outcome, blockers and acceptance checklist using `gh issue create --repo Today20092/voice-input --title "..." --body-file <path>`. Apply the canonical labels in `triage-labels.md`; current labels supersede migrated Triage lines. Claim with an assignee after checking blockers. Record verification in the issue and close only after completing the required criteria or recording an explicit disposition.

Local tickets 01–08 migrated on October 4, 2026 to GitHub #9–#16 respectively. `tickets.md` is a historical snapshot, not an active queue. GitHub #7 and #8 remain separate existing bug reports. Keep longer specifications and evidence under `docs/` and link them from issues. Old local numbers in evidence use the mapping in `tickets.md`.

When a skill says "publish to the issue tracker", create a GitHub issue. When it says "fetch the relevant ticket", read the issue and comments. For map workflows, use a map issue linking child issues. Record blockers as `Blocked by: #<number>` and explain milestone exceptions. The frontier is the first incomplete, unclaimed issue whose required blockers are satisfied. A ready label alone does not establish that work is unblocked.

**PRs as a request surface: no.** Review implementation PRs normally; external PRs are not feature-request tickets. Preserve ticket-specific release gates. Historical queue instructions do not grant fresh permission to publish or merge.

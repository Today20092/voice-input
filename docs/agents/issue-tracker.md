# Issue tracker: Local Markdown

Tickets for this repo live in `tickets.md`. PRDs and supporting specifications live under `docs/specs/`.

## Conventions

- Each second-level heading in `tickets.md` is one ticket.
- `**What to build:**` defines the outcome.
- `**Blocked by:**` names prerequisite tickets; `None` means it can start immediately.
- Checklist items are acceptance criteria.
- A ticket is completed when all its acceptance criteria are checked.
- An incomplete ticket whose blockers are completed is on the frontier.
- Optional triage state is recorded as `**Triage:** <role>` using `triage-labels.md`.
- External pull requests are not a request or triage surface.

## When a skill says “publish to the issue tracker”

Append a ticket section to `tickets.md`, including its outcome, blockers, and acceptance criteria. Put longer specifications under `docs/specs/` and link them from the tracker.

## When a skill says “fetch the relevant ticket”

Read the matching heading and its contents from `tickets.md`.

## Wayfinding operations

- **Map:** `tickets.md`.
- **Child ticket:** a second-level heading.
- **Blocking:** the `**Blocked by:**` line.
- **Frontier:** the first incomplete ticket whose named blockers are completed.
- **Claim:** add `**Claimed by:**` below the blocker line.
- **Resolve:** check verified acceptance criteria and add a short `### Resolution` note.

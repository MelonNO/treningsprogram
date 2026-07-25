# Brief 06 — Settings: App Settings row moves to the top

**Type:** Feature (reorder)
**Cluster:** T (Settings: 6 + 8) — one trivial worker.

> Outcome-only brief. No confirmation round was possible (user asleep); assumptions are labelled.

## Context
The top-level Settings list (`fragment_settings.xml`, order set in v1.18.0) currently runs: Training Profile → AI & Program → Exercise Library → Backup & Data → App Settings → About.

## What the user wants (end result)
**App Settings is the first row.** Resulting order: App Settings → Training Profile → AI & Program → Exercise Library → Backup & Data → About.

## Acceptance criteria
- Done when the list shows App Settings first and every row still navigates correctly.
- Done when nothing else about the screen changes.

## Assumptions (user may veto)
- **A-06a:** the relative order of the remaining rows is unchanged — only App Settings moves.

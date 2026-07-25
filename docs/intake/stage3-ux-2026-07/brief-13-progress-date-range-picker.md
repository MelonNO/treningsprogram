# Brief 13 — Progress tab: same calendar date-range filter

**Type:** Feature
**Cluster:** H2 (12 + 13 + 1) — one worker with item 12 (same pattern) and item 1 (same file).

> Outcome-only brief. No confirmation round was possible (user asleep); assumptions are labelled.

## Context
The Progress sub-tab (`HistoryProgressFragment`) windows its per-exercise charts with chips: 1M / 3M / 6M / All (`timeWindowMonths` feeding the strength history and PR timeline).

## What the user wants (end result)
Exactly the item-12 treatment here: the chips are replaced by a start/end **calendar range picker**, default **All**, and the selected range windows everything the chips windowed — the strength/reps charts (incl. item 1's), the PR timeline, and anything else keyed to the time window.

## Acceptance criteria
- Done when the chips are gone, the range control matches item 12's look and behavior (consistency between the two tabs), default All, one-action reset to All.
- Done when a chosen range restricts the charts/PR timeline to sessions inside it (inclusive, logical day boundary), and chart edge cases (0–1 points in range) degrade as the existing sparse-data handling does.
- Done when switching exercises keeps the chosen range applied.
- Windowing logic unit-tested off-device.

## Scope and constraints
- **In scope:** the Progress sub-tab's time window UI + plumbing.
- **Out of scope:** the Trends drill-in screen (keeps its own behavior); e1RM/stall logic beyond receiving the windowed data it already receives.

## Assumptions (user may veto)
- **A-13a:** same non-persistence as item 12 — fresh visits start at All.

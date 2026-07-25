# Brief 12 — History tab: calendar date-range filter replaces the boxes

**Type:** Feature
**Cluster:** H2 (12 + 13 + 1) — one worker (13 is the same pattern; 1 shares 13's file).

> Outcome-only brief. No confirmation round was possible (user asleep); assumptions are labelled.

## Context
The History sub-tab (`HistoryLogFragment`) filters the session list with four chips ("boxes"): Week / Month / 3 Months / All (`HistoryViewModel.DateFilter`).

## What the user wants (end result)
The chips are replaced by a **select-time control**: tapping it opens a **calendar where the user picks a start and an end date**; the session list then shows only sessions in that range. **Default is All** — before any selection, everything shows.

## Acceptance criteria
- Done when the chip row is gone and a single range control stands in its place, opening a start+end calendar picker (Material date-range pattern).
- Done when a chosen range filters the list inclusively (sessions on the start and end dates included, using the app's logical day boundary) and the control displays the active range.
- Done when the default state is All, and the user can return to All with one obvious action (clear/reset) after picking a range.
- Done when an empty result range shows a sensible empty state, not a broken list.
- Range-filter logic unit-tested off-device.

## Scope and constraints
- **In scope:** the History sub-tab's time filter UI + filtering.
- **Out of scope:** the session cards themselves; other tabs (item 13 handles Progress).

## Assumptions (user may veto)
- **A-12a:** the selected range does not persist across app restarts — each visit starts at All (a stale forgotten filter hiding sessions is worse than re-picking).
- **A-12b:** the old quick presets (Week/Month/…) are simply gone, per "instead of the boxes" — no hybrid.

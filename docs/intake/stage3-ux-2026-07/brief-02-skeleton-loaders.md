# Brief 02 — Skeleton loaders on the data-heavy screens

**Type:** Feature (polish)
**Cluster:** H5 — build LAST on the History surface (after items 3/9/10/11/12/13/14/15 settle those layouts).

> Outcome-only brief. No confirmation round was possible (user asleep); assumptions are labelled.

## Context
The History group's four sub-tabs (Recap, Stats, Progress, History) all load their content asynchronously and sit blank (or partially blank) until data arrives; the Progress sub-tab additionally shows nothing but the picker until an exercise is selected. The user asked for skeleton loaders "in the appropriate places, such as history tab when nothing is selected."

## What the user wants (end result)
Instead of blank regions, the app shows **skeleton placeholders** (shimmering content-shaped blocks in the Auros style) wherever content is pending:
- History group sub-tabs while their data loads (Recap session content, Stats cards, History session list, Progress chart area).
- The Progress sub-tab's "nothing selected yet" state shows a skeleton hinting at the chart/cards that will appear, instead of empty space.
- Genuine no-data empty states (first run, zero sessions) keep their existing friendly copy — a skeleton must never imply data is coming when none exists.

## Acceptance criteria
- Done when opening the History tab shows structured skeletons, not blank space, until real content replaces them.
- Done when Progress with no exercise selected shows the placeholder skeleton; picking an exercise swaps it for real content.
- Done when true empty states (no sessions ever) still show their copy, not an infinite skeleton.
- Done when skeletons match the rough shape of the content they precede and never flicker for imperceptibly fast loads.
- No regression to the existing empty-state logic (`DataScreenEmptyState`).

## Scope and constraints
- **In scope:** the History group's four sub-tabs; a reusable skeleton style consistent with Auros.
- **Out of scope:** Home/Program/Profile (their loads are near-instant today); blocking spinners; changing what loads when.

## Assumptions (user may veto)
- **A-02a:** "appropriate places" = the History group named above; other screens only if the worker finds an actually-visible blank-while-loading state there.
- **A-02b:** skeletons are the shimmer-block pattern (no progress bars/spinners), themed dark-teal like the rest of Auros.

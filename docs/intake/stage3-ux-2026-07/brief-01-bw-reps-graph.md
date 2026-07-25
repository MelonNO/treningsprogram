# Brief 01 — Progress tab: reps graph for bodyweight exercises

**Type:** Feature
**Cluster:** H2 (Progress/History filters + Progress chart) — same worker as items 12/13 (item 13 edits the same fragment).

> Outcome-only brief. No confirmation round was possible (user asleep); assumptions are labelled.

## Context
The Progress sub-tab (`HistoryProgressFragment`) plots one strength chart per selected exercise: max **weight** per session (`StrengthChartView`, fed by `StrengthPoint(dateMs, maxWeight, bestReps)` — reps are already in the data). For a bodyweight exercise like Pull Ups (working sets at 0 kg) the weight chart is a flat zero line and the real progression — reps — is invisible.

## What the user wants (end result)
When the selected exercise is bodyweight (e.g. pull-ups): a graph of **the reps performed over time**. If the user has also added weight to that exercise (weighted pull-ups), the **weight chart appears in addition** — both series visible, clearly labelled.

## Acceptance criteria
- Done when selecting an exercise whose history is all-bodyweight shows a reps progression chart (no useless flat 0-kg line).
- Done when an exercise with mixed history (bodyweight + added weight) shows both a reps graph and the weight graph, each labelled unambiguously.
- Done when ordinary loaded lifts (squat, bench…) look exactly as today — weight chart, e1RM line, PR timeline unchanged.
- Done when the charts respect the tab's time filtering (item 13's range picker once it lands).
- Chart-data derivation unit-tested off-device.

## Scope and constraints
- **In scope:** the per-exercise chart area on the Progress sub-tab.
- **Out of scope:** e1RM math for weighted-bodyweight work (body weight isn't in the strength history); the Recap/Trends drill-in screen; PR definitions.

## Assumptions (user may veto)
- **A-01a:** "amount of reps" = **best working-set reps per session** (the progression metric matching the chart's per-session granularity), not the session's total reps.
- **A-01b:** "bodyweight exercise" is determined from the logged data (sessions with 0-kg working sets), so it works for any exercise the user performs unloaded — no hand-maintained list.

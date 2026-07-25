# Brief 01 — Delete a logged set mid-workout (with confirm)

Type: Feature (small UX)
Cluster: A (with 07 and 10 — all touch the log-workout screen; one worker or strict sequence)

> Outcome-only: this brief describes the end result and user experience. The implementation approach belongs to the orchestrator/worker.

## Context

Log-workout screen (`ui/log/LogWorkoutFragment.kt` + `LogWorkoutViewModel.kt`). Today, once a set is logged during an active workout it is displayed as a plain, non-interactive row ("S1: 8 reps @ 60kg") — no tap, long-press, swipe, or undo. The only way to remove a wrong set is *after* completing the workout, via the red × in the Stats-tab History list. (A `deleteSet` function already exists on the ViewModel but nothing in the log screen calls it — current fact, not a prescription.)

## What the user wants (end result)

During an active workout, a misslogged set can be **deleted** on the spot. Deleting asks a short **"are you sure?" confirmation** first. After confirming, the set is gone and the user simply logs the correct one.

## Current (incorrect/missing) vs correct behavior

- Current: a wrong set cannot be removed or corrected until after the workout, and only from the History list.
- Correct: each logged set in the active workout has a visible, obvious way to delete it; confirm → the set disappears immediately.

## Acceptance criteria

- Done when a set logged during an active workout can be deleted from within that workout, guarded by a confirmation step.
- Done when, after deletion, everything the session shows updates coherently: set counter/numbering (S1/S2…, W1/W2…), session totals, warm-up ramp visibility, beat-last-time targets — no stale numbers.
- Done when completing the workout afterwards stores only the remaining sets, and no downstream stat/XP/PR reflects the deleted set.
- Done when deleting the only set of an exercise returns that exercise to its "no sets yet" state without errors.
- Done when normal logging flow (log set → rest timer, etc.) is unchanged when no delete happens.

## Scope and constraints

- **Delete only** — the user explicitly declined in-place editing of reps/weight (delete + re-log is the correction path).
- Active-workout screen only; the post-completion History behavior is NOT changed by this item (item 04 handles History separately).
- The running rest timer must not be disturbed by a deletion.

## Decisions baked in

- Confirmation dialog before delete (user chose "are you sure" over instant delete/undo).

## Considerations for whoever builds it (surfaced, not decided)

- Set numbering after a mid-list deletion should stay coherent (renumber vs keep gaps — builder's choice, must just look sane).
- Deleting all working sets of an exercise may legitimately make the warm-up ramp card reappear — acceptable.
- Standing constraints: build via `./build.sh`; no commits/releases unless asked; no on-device UI tests — verify via unit tests.

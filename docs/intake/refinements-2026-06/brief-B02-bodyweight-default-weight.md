# B02 — New bodyweight exercise must default to 0/"BW", not the previous exercise's weight

**Type:** Bug
**Cluster:** Active workout / logging (with B01)
**Outcome-only:** describes the desired end result, not the implementation.

## Context
On the active workout screen, when the user moves to a new exercise, the weight field is pre-filled. For a bodyweight exercise that has no prior weight of its own, the field is currently being pre-filled with a weight carried over from the previously viewed exercise instead of starting empty / at zero ("BW").

## What the user wants (end result)
- A **bodyweight exercise with no prior history** must default to **0 / empty, shown as "BW"** — it must never inherit the weight of another exercise.
- A bodyweight exercise that has been **previously logged with added weight** (e.g. weighted pull-ups, dips with a belt) should still pre-fill **its own** last logged weight. That behavior is correct and must be preserved.
- Only the **cross-exercise weight bleed** (one exercise's weight showing up as the default for a different, fresh bodyweight exercise) is the bug.

## Current vs correct behavior
- **Current:** switching to a fresh bodyweight exercise can pre-fill the weight from the previously viewed exercise.
- **Correct:** a fresh bodyweight exercise starts at 0/"BW"; an exercise's own prior logged weight (including legitimate added weight) still pre-fills for that exercise.

**Diagnose first:** confirm the exact path by which the previous exercise's weight reaches the new exercise's weight field (e.g. a shared/last-entered value vs the per-exercise last-logged value), so the fix removes the cross-exercise bleed without wiping legitimately saved per-exercise weights.

## Acceptance criteria
- Done when selecting a bodyweight exercise that has no prior logged weight shows the weight as 0/"BW", regardless of what exercise was viewed immediately before.
- Done when a bodyweight exercise previously logged with added weight still pre-fills its own last weight.
- Done when in-progress, not-yet-logged values the user has typed for an exercise are still preserved as they are today (no regression to draft restore).

## Scope and constraints
- In scope: the weight-field default/pre-fill logic when switching exercises during an active workout.
- Out of scope: changing reps pre-fill, AI target weights, or how sets are stored.

## Considerations for whoever builds it
- Be careful not to over-correct into "always blank for bodyweight" — added-weight bodyweight work is real and its own history must survive.
- Standard cross-cutting constraints apply (build via `./build.sh`; no commits/releases or UI tests unless asked).

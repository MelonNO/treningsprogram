# fix2-04 · Remove the redundant Skip button

**Type:** Change.

## Context
Native Android app. During a workout, the per-exercise controls include Back / Skip / Next. **Next can already be pressed without logging any sets.**

## Current behavior
Skip duplicates Next — since Next advances without requiring a logged set, Skip is redundant.

## Correct behavior
- The **Skip** control is removed from the workout controls.
- Advancing to the next exercise without logging is done via **Next** (the "skip this exercise" case is preserved through Next).

## Acceptance
- [ ] Skip is gone from the active-workout controls.
- [ ] Next still advances to the next exercise without requiring any logged set.
- [ ] The control row looks intentional after removal (no gap/orphaned spacing).

## Constraints
- Confirm Next's no-log advance works before removing Skip.

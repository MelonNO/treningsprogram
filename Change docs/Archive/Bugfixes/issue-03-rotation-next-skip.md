# Issue 03 — Screen rotation advances to the next exercise

**One-liner:** Rotating the screen behaves as if "Next" was pressed and moves to the next exercise.

## App context
The active "Log" session shows one exercise at a time; a "Next" action advances to the next exercise.

## Current (incorrect) behavior
- Rotating the device portrait↔landscape advances the session to the next exercise as if "Next" had been pressed. No logged data is lost, but the unwanted advance happens.

## Correct behavior (target)
- Rotation does not change the current exercise or trigger any navigation. The user stays on the exercise they were on, in the same state.

## Acceptance criteria
- [ ] On any exercise, rotate the device → still on the same exercise, same set, same entered values.
- [ ] Rotation never triggers "Next", "Skip", or "Back".

## Coordination / related issues
- **Shares a root cause with Issue 02** (rotation also resets the rest timer). Both point to the active-session screen being recreated on rotation and re-running setup/advance logic. **Recommend Issues 02 and 03 be handled together by the same agent**, since one correct fix — preserve active-session state across configuration changes — resolves both, and separate edits to the same code risk conflicting.

## Constraints & scope
- Hypothesis to verify: a lifecycle/recreation event on rotation re-invokes the advance path. Confirm the actual trigger before changing.
- Do not change the intended behavior of the real "Next" action.

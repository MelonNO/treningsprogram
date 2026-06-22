# Issue 15 — App crashes when pressing "Awesome!" on the completion modal

**One-liner:** Dismissing the Workout Complete modal via "Awesome!" crashes the app.

## App context
After finishing a workout, a "Workout Complete!" modal shows XP, streak, session summary, PRs, and achievements, with an "Awesome!" button to dismiss it.

## Current (incorrect) behavior
- Pressing "Awesome!" crashes the app.

## Correct behavior (target)
- Pressing "Awesome!" closes the completion modal, ensures the completed session is saved, and returns the user to a normal screen (e.g. Home) without crashing.

## Acceptance criteria
- [ ] Complete a workout and press "Awesome!" → modal closes, no crash.
- [ ] The completed session is persisted: after restart it appears in history with correct totals.
- [ ] Repeating across several workouts is stable.

## Coordination / related issues
- **Compare with Issue 08** (crash when the rest timer finishes) for a possible shared root cause — e.g. a common completion/notification/teardown path. If they share a cause, coordinate the fixes.

## Constraints & scope
- **Capture the crash log / stack trace on the "Awesome!" press first** — diagnose before patching. Likely candidates: the modal-dismiss path, the session-finalize/save step, or the post-dismiss navigation/teardown.
- Ensure the fix does not skip saving the session in order to avoid the crash — saving must still happen.

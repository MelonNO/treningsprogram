# Issue 02 — Rest timer resets to suggested time on screen rotation

**One-liner:** Rotating the screen resets a running rest timer back to the AI-suggested duration.

## App context
During the active "Log" session, after a set the app shows a rest timer that counts down from an AI-suggested (and user-adjustable) duration.

## Current (incorrect) behavior
- While the rest timer is mid-countdown, rotating the device portrait↔landscape resets it back to the AI-suggested duration. The elapsed countdown is lost.

## Correct behavior (target)
- Rotation does not affect the timer. The remaining time and running state are preserved across orientation changes; the countdown continues uninterrupted.

## Acceptance criteria
- [ ] Start a rest timer, rotate the device mid-countdown → remaining time and running/paused state are unchanged.
- [ ] Rotating repeatedly does not drift, reset, or duplicate the timer.

## Coordination / related issues
- **Shares a root cause with Issue 03** (rotation also triggers a "Next" advance). Both are almost certainly the active-session screen being recreated on configuration change and re-initializing from defaults instead of preserving state. **Recommend Issues 02 and 03 be handled by the same agent / same fix** (preserve all active-session state across rotation) to avoid conflicting changes to the same code.

## Constraints & scope
- Hypothesis to verify: the screen/view is recreated on rotation and timer state is rebuilt from the suggested value rather than retained. Confirm before changing.
- Do not regress the timer's adjustability or the AI-suggested default.

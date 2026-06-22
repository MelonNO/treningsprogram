# Issue 08 — App crashes when the rest timer finishes

**One-liner:** The app crashes every time a rest timer reaches zero, whether open or closed.

## App context
A rest timer counts down after a set and fires some completion behavior at zero.

## Current (incorrect) behavior
- When the rest timer reaches zero, the app **always** crashes — both when the app is open (foreground) and when it is closed/backgrounded.

## Correct behavior (target)
- When the timer reaches zero, it fires its completion (sound/vibration/notification) and the app keeps running normally, in foreground or background. No crash.

## Acceptance criteria
- [ ] Let a timer run to zero in the foreground → completion alert fires, no crash.
- [ ] Repeat with the app backgrounded/closed → alert fires, no crash.
- [ ] Repeat several times → stable, no crash.

## Coordination / related issues
- **Part of the rest-timer lifecycle cluster (05, 06, 07, 08).** This crash is most likely in the timer's finish/completion handler — the same code Issue 07 formalizes into a proper notification-backed timer. Fixing the timer subsystem correctly should resolve this; coordinate with the 07 work.
- **Also compare with Issue 15** (crash on "Awesome!") for a possible shared root cause (e.g. a common completion/notification/teardown path).

## Constraints & scope
- **Capture the actual crash log / stack trace at timer completion first** — diagnose before patching. Do not mask the crash by suppressing the completion behavior.

# Bug 03 — Rest timer: persistent notification timer

## Context
Native Android app. The rest timer currently runs only in-app. (The earlier crash-on-timer-finish is already fixed — don't reintroduce it.)

## Current (incorrect) behavior
The timer does not run as a notification; outside the app it doesn't show or reliably alert.

## Correct behavior
- While a rest timer runs, a **persistent system notification shows a live countdown** (ticking), like a standalone timer app.
- The countdown continues and completes while the app is **backgrounded or closed**.
- On completion it alerts with **sound + vibration**.
- **Tapping the notification returns to the app.**

## Acceptance
- [ ] Start a timer, leave the app → notification shows the countdown ticking.
- [ ] At zero → sound + vibration fire, even with the app backgrounded/closed and the screen off.
- [ ] Tapping the notification returns to the app.
- [ ] No crash on completion (regression check on the previously-fixed crash).

## Coordination
- **Backbone of the timer cluster (Bugs 01 + 02 + 03).** A correct background/notification timer makes Bug 01 (keep counting while hidden) and the outside-app behavior fall out, and Bug 02 sits on top. One agent owns all three.

## Constraints
- Native Android — use the standard ongoing-notification + background mechanism (e.g. foreground service / alarm) so it survives backgrounding; request notification permission gracefully.
- Verify specifically with the app **backgrounded and fully closed**, screen off.

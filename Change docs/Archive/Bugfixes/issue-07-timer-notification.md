# Issue 07 — Rest timer should run as a live countdown notification

**One-liner:** The rest timer should behave like a standalone timer app — a system notification that counts down and works outside the app.

## App context
The rest timer currently exists only as an in-app overlay and does not function when the user leaves the app.

## Current (incorrect) behavior
- When the app is backgrounded or closed, the rest timer does not surface as a notification and does not reliably alert on completion.

## Correct behavior (target)
- While a rest timer is running, an ongoing system notification shows the live countdown, like a dedicated timer app.
- The countdown continues and completes correctly while the app is backgrounded or closed, and alerts the user on completion (sound and/or vibration) via the notification.

## Acceptance criteria
- [ ] Start a rest timer and leave the app → a notification shows the time counting down.
- [ ] At zero, the user is alerted (sound/vibration) even when not in the app.
- [ ] Returning to the app shows a consistent timer state (in sync with the notification).

## Coordination / related issues
- **Backbone of the rest-timer lifecycle cluster (Issues 05, 06, 07, 08).** A correct background-capable timer + notification here is what makes Issue 05 (hide but keep counting) work, fixes Issue 08 (crash on completion) if that crash is in the finish path, and supports Issue 06 (manual start). **Strongly recommend one agent owns the whole timer subsystem.**

## Constraints & scope
- Requires the target platform's ongoing-notification + background-execution mechanism and notification permission. **Confirm the target platform** (the requirement implies a mobile OS) and request the permission gracefully.
- Flag the exact notification style/sound as a decision.

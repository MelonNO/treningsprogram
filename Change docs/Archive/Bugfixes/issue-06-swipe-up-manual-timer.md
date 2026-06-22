# Issue 06 — Manually start a rest timer with a swipe-up gesture

**One-liner:** There's no way to start a rest timer on demand; a swipe-up should start one.

## App context
Rest timers currently appear automatically after a set. There is no manual way to start one.

## Current (incorrect) behavior
- The user cannot manually start/bring up a rest timer.

## Correct behavior (target)
- A swipe-up gesture starts (and shows) a rest timer on demand — the inverse of the swipe-down-to-hide in Issue 05. The manually started timer uses a sensible default (e.g. the AI-suggested duration) and is adjustable.

## Acceptance criteria
- [ ] Swiping up starts a rest timer at any appropriate point in the session.
- [ ] The manually started timer behaves like an auto-started one (adjustable, hide-able per Issue 05, completes/notifies per Issues 07/08).

## Coordination / related issues
- **Rest-timer lifecycle cluster: Issues 05, 06, 07, 08.** Implement these as one coordinated timer design, not independent parallel agents. The swipe-up start and swipe-down hide (Issue 05) are two gestures on the same timer surface.

## Constraints & scope
- Pair the swipe-up (start/show) and swipe-down (hide) gestures consistently. Flag any gesture conflict with existing scroll behavior as a decision.

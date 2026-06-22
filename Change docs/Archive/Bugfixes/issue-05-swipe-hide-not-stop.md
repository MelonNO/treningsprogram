# Issue 05 — Swiping the rest timer stops it (should only hide)

**One-liner:** Swiping the rest timer away stops it; it should only hide while continuing to count.

## App context
The active session shows a rest timer overlay after a set. The user can swipe it away.

## Current (incorrect) behavior
- Swiping the rest timer away stops/cancels it.

## Correct behavior (target)
- Swiping the timer only hides the overlay. The timer keeps counting in the background and still reaches zero (and fires its completion — see Issue 07/08).

## Acceptance criteria
- [ ] Start a rest timer, swipe to hide → the timer is still running (verify it continues and completes/alerts at zero).
- [ ] Hidden timer can be brought back into view (see Issue 06).

## Coordination / related issues
- **Part of the rest-timer lifecycle cluster: Issues 05, 06, 07, 08.** These four all touch the timer subsystem and **must not be implemented as independent parallel agents** — high conflict risk. Recommend a single coherent timer implementation (a background-capable countdown surfaced as a notification, per Issue 07) that satisfies all four; this hide-but-keep-counting behavior falls out of that design naturally.

## Constraints & scope
- The hide gesture must decouple "dismiss the view" from "cancel the timer". Confirm the current handler conflates them before fixing.

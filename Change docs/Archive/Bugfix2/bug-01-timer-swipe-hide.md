# Bug 01 — Rest timer: swiping down destroys it (should only hide)

## Context
Native Android app. The active workout "Log" flow shows a rest-timer overlay after a set.

## Current (incorrect) behavior
Swiping the rest timer down removes it entirely — it disappears and stops counting.

## Correct behavior
Swiping the timer down only **hides** the overlay. The timer keeps counting in the background and still completes (and alerts per Bug 03). It can be brought back via Bug 02.

## Acceptance
- [ ] Start a rest timer, swipe down → overlay hidden, timer still running.
- [ ] The hidden timer reaches zero and fires its completion as normal.
- [ ] Hiding never cancels or resets the timer.

## Coordination
- **Timer cluster (Bugs 01 + 02 + 03) — same agent.** Bug 03's notification-backed timer is what makes "keep counting while hidden" work; implement the three together.

## Constraints
- Decouple "dismiss the view" from "cancel the timer".
- Verify on-device (emulator / Maestro), not just on the JVM.

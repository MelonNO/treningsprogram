# Bug 02 — Rest timer: recall / manual start with a visible affordance

## Context
Native Android app. Rest timers appear after a set. Once hidden (Bug 01) there's no way to get one back, and no way to start one manually.

## Current (incorrect) behavior
- No way to recall a hidden timer.
- No way to start a rest timer manually.
- No visible control indicating the option exists.

## Correct behavior
- A persistent, **visible** control (a bar or button) is always present so the option is discoverable to the user.
- **Swiping up** on it brings a hidden timer back into view.
- If no timer is running, swiping up **starts** a rest timer manually (default / AI-suggested duration, adjustable).

## Acceptance
- [ ] A visible bar/button is always shown so the user can see they can get/start the timer.
- [ ] Swiping up recalls a hidden, still-running timer.
- [ ] With no timer running, swiping up starts one.

## Coordination
- **Timer cluster (Bugs 01 + 02 + 03) — same agent.** Pairs directly with Bug 01 (hide ↔ recall).

## Constraints
- Pair the swipe-up (recall/start) with Bug 01's swipe-down (hide) consistently.
- Avoid conflicts with existing scroll gestures.
- Verify on-device (emulator / Maestro).

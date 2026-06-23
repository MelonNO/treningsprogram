# Bug 05 — Intermittent navigation mismatch

## Context
Native Android app with bottom navigation: Home, Program, Stats, User.

## Current (incorrect) behavior
Intermittently, the **pressed tab, the screen shown, and the highlighted indicator disagree.** Observed examples: pressing Home navigated to User; entering the User tab left Stats highlighted. Intermittent, with no reliable repro yet.

## Correct behavior
- Pressing a nav tab always navigates to that tab's screen.
- The highlighted indicator always matches the screen currently shown.

## Diagnose first
- **Reproduce** the issue and find the root cause.
- Determine whether it is **wrong destination**, **wrong highlight**, or both.
- Check for nav index/state desync (selected-tab state vs the navigated route), off-by-one errors, and correlation with prior actions (returning from a workout, screen rotation, rapid taps).

## Acceptance
- [ ] Across repeated navigation — including after a workout and after rotation, and under rapid taps — the pressed tab, shown screen, and highlight always agree.
- [ ] The previously-observed mismatches can no longer be reproduced.

## Constraints
- Capture a reliable repro as part of the fix. **Do not paper over with a hack** — fix the desync source.
- Verify on-device (Maestro on AVD), exercising rapid and edge-case navigation.

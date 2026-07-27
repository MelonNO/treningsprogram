# Brief 03 — History week swipe must change the week, not the sub-tab (zone-based)

**Type:** Bug (the v1.27.0 feature does not work as shipped)
**Cluster:** A (swipe gestures)
**Outcome-only:** this brief describes the end result and user experience, never how to build it.

## Context

v1.27.0 (commit `b49bef0` / `02b06eb`) shipped "swipe between weeks" inside the History
tab's opened week view: swipe right = older week, swipe left = newer week, same weekday
carried over. The History screen also hosts sub-tabs (Sessions/Log, Stats, Progress,
Recap) in a horizontally swipeable pager. On-device, the pager wins the gesture: a
horizontal swipe on the opened week view flips to another sub-tab instead of changing
the week. The shipped feature is effectively unusable.

## Current (incorrect) behavior

- Swiping horizontally on the opened week view in History switches to a different
  History sub-tab; the displayed week never changes.

## Correct behavior (confirmed — zone-based)

- A horizontal swipe **on the opened week view itself** (the area showing the week and
  its days) changes the **week**: right = older, left = newer, exactly per the v1.27.0
  spec (same-weekday carry-over, bounds at earliest logged week and current week,
  end-of-range nudge, filter-respecting). It must **never** switch sub-tabs.
- A horizontal swipe **anywhere else** on the History screen — the Stats, Progress, and
  Recap sub-tabs, and the month/week browser when no week is opened — still switches
  between the sub-tabs, as it does today.
- Both gestures coexist; **where the finger swipes decides which one wins.**

## Diagnose first

The cause is presumed to be the sub-tab pager intercepting the horizontal gesture
before the week view sees it, but this is unverified — confirm the actual interception
behavior before changing anything.

## Acceptance criteria

- Done when swiping right/left on the opened week view reliably changes the displayed
  week (older/newer) and never changes the sub-tab.
- Done when all v1.27.0 week-swipe behaviors work as originally specified: same weekday
  stays selected, cannot go past the current week or before the earliest logged week,
  subtle nudge at the ends, active filters respected.
- Done when swiping on Stats/Progress/Recap and on the closed (month-browser) state of
  the Sessions list still switches sub-tabs exactly as before.
- Done when tapping the sub-tab names still switches sub-tabs from anywhere.
- Done when vertical scrolling inside the week view is unaffected.

## Scope and constraints

- **In scope:** gesture routing on the History screen only.
- **Out of scope:** the week-swipe feature's semantics (already settled in
  `docs/intake/history-week-swipe-2026-07-25/`); the Program tab (Brief 04).
- Standing constraints: build via `./build.sh` (not `./gradlew`); no commits/releases
  unless the user asks; no on-device/automated UI tests unless the user asks.

## Decisions baked in

- User explicitly **keeps** swipe-between-sub-tabs everywhere outside the opened week
  view ("keep it, but if the swiping is done on the specific part of the screen where
  the week is do not change tab").
- App-wide swipe direction convention stands: right = older, left = newer.

## Considerations for whoever builds it

- Verification is unit/build-level only (standing rule: no Waydroid/on-device runs);
  the user does the live device check. Given this bug shipped despite green tests,
  whoever builds it should think hard about what test evidence can actually
  demonstrate the gesture routing, and flag clearly that final proof is the user's
  device check.
- Brief 01 removes the skeleton on this same surface — see INDEX for sequencing.

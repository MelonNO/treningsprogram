# B01 — Enlarge in-session progress-bar tap target

**Type:** Bug / usability
**Cluster:** Active workout / logging (with B02)
**Outcome-only:** describes the desired end result, not the implementation.

## Context
During an active workout, the logging screen shows an "Exercise X / Y" session progress bar. Tapping that progress bar (and/or its counter label) opens the quick-jump menu used to move between the session's exercises. The user reports the tappable area is too small/finicky to hit reliably.

## What the user wants (end result)
The progress bar (and its "Exercise X / Y" label) should be **easy to tap** to open the exercise-jump menu — a comfortable, forgiving touch target rather than a thin, hard-to-hit strip.

## Current vs correct behavior
- **Current:** the tap target for opening the jump menu is small/finicky; taps frequently miss.
- **Correct:** tapping anywhere on or around the progress bar / counter reliably opens the exercise-jump menu.

**Diagnose first:** confirm whether the issue is purely the visual height of the bar, the actual touch-target bounds, or both, before changing layout.

## Acceptance criteria
- Done when a user can reliably open the exercise-jump menu by tapping the in-session progress bar or its counter, without needing to aim precisely.
- Done when the change does not break the existing jump-menu behavior or the rest of the active-workout layout.

## Scope and constraints
- In scope: the tap target / hit area for the in-session progress bar and its counter on the active workout screen.
- Out of scope: changing what the jump menu does, or the progress bar's meaning/value.

## Considerations for whoever builds it
- The fix is likely a larger touch target rather than a much taller bar; preserve the existing visual proportions where possible.
- Standard cross-cutting constraints apply (build via `./build.sh`; no commits/releases or UI tests unless asked).

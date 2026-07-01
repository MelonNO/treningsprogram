# Brief 10 — Auto-attribute any other-day workout to today

**Type:** Refinement / bug
**Cluster:** B — depends on item 07's day definition (build item 07 first).
**Outcome-only:** This brief describes the end result and user experience. It deliberately does not prescribe implementation.

## Context
A workout can only actually be performed *today*, but the plan is laid out per weekday. There are currently **two** ways a user ends up doing another day's workout:
1. The v1.11.0 **"Do this workout today"** button — the user must press it first; on completion it moves the session onto today and rebalances the week.
2. The plain **"Start Day Workout"** button shown on each day in the Program tab, which starts *any* day's workout directly. This path does **not** perform the move, so the completed session's plan-progress can land on the wrong day.

The user wants the move to "just happen" — no button, no prompt — whenever they finish a workout that belongs to a day other than today.

## What the user wants (end result)
Finishing **any** non-current-day workout (via either path above) should **automatically** attribute the completed session to **today** and **rebalance the rest of the week** — exactly what the "Do this workout today" button does now — but **silently, with no confirmation and no button to press**. The requirement to tap "Do this workout today" is removed; the direct "Start Day Workout" path gains the same auto-move behavior.

## Current vs correct behavior
- **Current (path 2, incorrect):** starting a future/other day's workout via "Start Day Workout" and completing it can mark the *source* day's plan as done and leave *today* looking unlogged — the session and the week don't agree.
- **Current (path 1):** works, but only because the user first pressed the "Do this workout today" button.
- **Correct:** regardless of which path started it, completing an other-day workout logs the session against **today**, vacates/discards the appropriate plan, and rebalances the week — automatically and silently.

## Diagnose first
The exact seam that currently mis-attributes path 2 (session date vs which day's planned exercises get marked logged vs week rebalance) should be diagnosed before implementing, so the fix converges both paths on the same correct move rather than patching symptoms. The existing "commit day move + rebalance on completion" logic used by path 1 is the reference behavior to generalize.

## Acceptance criteria (Done when …)
- Completing a workout that belongs to a day other than **today** logs the session against **today** with no user action.
- The week **rebalances** on that move, matching the current "Do this workout today" behavior.
- There is **no confirmation dialog and no "Do this workout today" button step** required — the move is automatic and silent.
- Both entry paths (the old button path and the direct "Start Day Workout" path) produce the same correct outcome; after completion the logged session and the week's progress agree (no "source day done / today still empty" mismatch).
- A workout that already belongs to today behaves unchanged.
- "Today" is determined using the **item 07 day-boundary rule** (e.g. a 01:00 completion attributes to the previous day when the cutoff is 04:00).

## Scope and constraints
- **In scope:** auto-attribution + week rebalance on completion of an other-day workout, across both entry paths; removing the button-press requirement.
- **Out of scope:** adding any confirmation (the user explicitly wants it silent — this is intentionally the opposite of brief 02's guard, which is only on the settings Generate action).
- Depends on **item 07**: use its definition of the current logical day.

## Decisions baked in
- **Silent and automatic** — no confirmation, no button.
- **Auto-rebalance** the week on the move.
- Both paths converge on the same behavior.

## Assumptions (user may override)
- **[A-10a]** The existing "Do this workout today" button may be removed entirely (its behavior becomes automatic). If the user wants to keep it as an explicit shortcut too, that's compatible — but it is no longer required.
- **[A-10b]** "Rebalance the week" means exactly what path 1 does today (regenerate the non-logged days around the now-logged today). No new rebalance semantics are introduced.

## Considerations for whoever builds it
- Tension with item 2 is intentional and fine per the user: item 2 guards the settings Generate/overwrite action; item 10 is a different path and stays silent. Ensure the silent move never routes through item 2's confirmation.
- Make sure the silent move can't discard a plan the user would miss without any trace — confirm the resulting History/session record clearly reflects what was done today.

## Standing constraints
- Build with `./build.sh` (not `./gradlew`). No commits/releases unless asked. No on-device/automated UI tests unless asked.

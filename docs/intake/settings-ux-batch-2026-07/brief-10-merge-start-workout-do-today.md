# Brief 10 — Merge "Do this workout today" into "Start Workout" (with append-to-logged-session)

**Type:** Feature / refactor — **behavior BROADENING, highest-risk item in the batch**
**Cluster:** Standalone (Program-tab surface). Not just a button removal — it adds a new capability.
**Outcome-only:** Describes the end result and user experience; does not prescribe implementation.

## Context
The Program tab's day section currently has **two** buttons:
- **"Start Workout"** — starts the selected day's workout.
- **"Do this workout today"** — a separate button, shown only for an eligible **other** day, that moves that day's workout into today and rebalances the week on completion. It is **only offered when today is not already logged** (current gating: the other day must be non-today and un-logged, **and** today must not already be logged).

## What the user wants (end result)
> "Remove the 'do this workout today' button — the 'start workout' button should do the same thing. Make sure it actually does."
>
> "Today's workout should be replaced; if the user already has logged activity on that day it should be added to the logged activity (so it looks like one long session)."

Remove the second button. Pressing **"Start Workout"** while viewing **another day's** plan moves that workout into **today** and rebalances the week. This must work **even when today already has logged activity**, in which case the moved-in workout is **appended to today's existing logged session** so it reads as one continuous session.

## Current vs correct behavior
- **Current:** two buttons; the move-to-today is offered only when today is not already logged; "Start Workout" on another day does not perform the move.
- **Correct:** one button ("Start Workout"); on a non-today day it moves the workout into today + rebalances; it works even when today is already logged (append), and replaces today's *planned* workout when today has no logged activity yet.

## Acceptance criteria (Done when …)
- The **"Do this workout today"** button no longer exists.
- Viewing **another day's** plan and pressing **"Start Workout"** starts that workout attributed to **today**, and on completion the week is **rebalanced** (the moved-from day handled as the current "do this workout today" move does).
- Pressing **"Start Workout"** on **today's own day** behaves as before.
- **Today has no logged activity yet:** today's **planned** workout is **replaced** by the moved-in workout (today's session becomes the moved workout).
- **Today already has logged activity:** the moved-in workout is **appended** to today's existing logged session, so History / the session reads as **one continuous session** — existing logged sets are **not** overwritten, duplicated, or split into a separate entry.
- The move is now **offered/possible even when today is already logged** (the previous gating that hid it in that case is removed).

## Scope and constraints
- **In scope:** the Program tab day-section buttons, and the move/append + rebalance on completion.
- **Out of scope:** rest days (no Start Workout button — unchanged).
- **"Today" must respect the configured day-boundary cutoff** (the app's logical-day definition), not raw midnight.

## Decisions baked in
- Single button ("Start Workout"); move + rebalance for a non-today day.
- Replace-today's-planned vs append-to-today's-logged rule exactly as stated above.

## Assumptions / deferred (user may override)
- **[A10-1]** For today's own day that already has logged activity, "Start Workout" continues today's session as it does now (it is not a "move").
- **[A10-2]** An **other** day that is itself **already logged** is not a candidate to redo into today (you can't re-do a completed day); "Start Workout" there reflects its logged state. *Confirm if the user wants otherwise.*
- **[A10-3]** Rest days remain without a Start Workout button.

## Considerations for whoever builds it
- **This BROADENS current behavior.** The existing move commits assume today is **not** logged; here you must relax that gating **and** add an append-into-existing-logged-session path. This merge is the riskiest part: it must not duplicate, drop, or overwrite existing logged sets, and must still rebalance the moved-from day correctly so the week stays coherent.
- **Diagnose first:** reconcile the two existing code paths — the direct "Start Day Workout on another day" attribution vs the explicit move-to-today (`moveFromDay`) path — so the single button produces the move semantics consistently.
- **Program-tab surface hazard:** touches `ProgramFragment.kt`, `fragment_program.xml` (remove the second button), plus the log/complete flow and the repository move/append commit. Items 4 (dialog edit), 8 (loading view), and 11 (rationale card) also touch the Program tab surface — see the INDEX cross-group hazard note.

## Standing constraints
- Build with `./build.sh` (not `./gradlew`). No commits/releases unless asked. No on-device/automated UI tests unless asked.

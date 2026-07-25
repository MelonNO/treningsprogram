# P2 — Do another day's workout today: move it into today, log it for today, then rebalance the week

**Type:** Feature
**Cluster:** Week-rebalance / Program-tab regeneration (with **P1**). Shares the rebalance mechanism; reuses **P4**'s corrected generation behavior.
**Outcome-only:** describes the desired end result, not the implementation.

## Context
Today, each planned day is fixed to its day; the user can only perform the workout planned for *today*. The user wants to be able to pick a **different day's** planned workout (from the **current week**) and do it **today** instead — for example, on Monday choosing to do Wednesday's planned workout. When that happens, the chosen workout moves into today and is logged for today, and the rest of the week rebalances to accommodate.

## What the user wants (end result)
- From the **current week**, the user can select **another day's** planned workout — in **either direction** (a later *or* an earlier day) — to perform **today**.
- On selection, that workout becomes **today's session to perform**; the user **performs and logs it normally** (start → record sets → complete); the completed session is **attributed to today**.
- **Timing (key):** the move and the week rebalance are **finalized only AFTER the user completes that workout**, not at selection time.
- **On completion:**
  - the chosen day's workout is now **today's logged session**;
  - **today's original planned workout is discarded** (the rebalance handles the week);
  - all **non-logged days** of the week — **including the vacated source day** — are **regenerated** to rebalance the week.
- This rebalance **always happens**, regardless of the **P1** auto-rebalance toggle.
- **Current week only.**

## Current vs correct behavior
- **Current:** the user can only do the workout planned for today; no way to pull another day's workout to today; no rebalance.
- **Correct:** the user can pull any current-week day's workout to today; on completion it is logged for today, today's original plan is discarded, and the non-logged days (incl. the vacated source day) regenerate to rebalance.

## Acceptance criteria (observable)
- **Done when** the user can select another current-week day's planned workout (earlier or later) to perform today.
- **Done when**, **until the workout is completed**, the week is **unchanged** — selection alone does not move, discard, or rebalance anything.
- **Done when**, on **completion**, the performed workout is **logged/attributed to today**.
- **Done when**, on completion, **today's original planned workout is discarded** and **all non-logged days (including the vacated source day) are regenerated** to rebalance the week.
- **Done when** this rebalance occurs **regardless of the P1 toggle** state.
- **Done when** days already **logged** earlier in the week are **not disturbed**.
- **Done when** the rebalance is confined to the **current week**.

## Scope and constraints
- **In scope:** selecting another day's workout to do today; performing/logging it as today's session; on completion, discarding today's original plan and regenerating the non-logged days (incl. the vacated day).
- The rebalance reuses the same generation pipeline (with verification) and **P4**'s real-weight behavior. The completed workout becomes a **logged day**, preserved by any later rebalance.
- **Out of scope:** prior weeks; changing automatic start-of-week generation.
- **Standard cross-cutting constraints:** build via `./build.sh`; no commits/releases unless asked; no on-device/UI tests unless asked.

## Decisions confirmed by the user (2026-06-28, via coordinator)
- "Logged for today" means interpretation **(a)**: the workout is **moved into today's slot and performed/logged normally** (session attributed to today) — **not** auto-marked done.
- Today's **original** planned workout is **discarded**; the rebalance handles the week.
- The **vacated** (pulled-from) day is **regenerated** by the rebalance, not left empty.
- **All directions** allowed (earlier or later day), **current week**.
- This rebalance **always** runs, regardless of the P1 toggle.
- Move + rebalance are **finalized only after the workout is completed**.

## Decisions deferred / assumptions (user may override)
- **[P2-A1] Pre-completion state (the documented default for the timing detail).** On selection, the chosen workout is **presented as today's session to perform**, but the move, the discard of today's original plan, and the week rebalance are **committed atomically only on completion**. If the user **abandons** or never completes the workout, the week is left **entirely unchanged** (no move, no discard, no rebalance; the source day keeps its workout). This is a sensible default for the user's "only after the workout is complete" instruction — **flagged for confirmation**.
- **[P2-A2] Entry point / UI** for "do this day's workout today" (e.g. a "Do this today" action on a day in the Program tab) is left to the orchestrator to place sensibly.
- **[P2-A3] Eligible source days** are assumed to be **non-logged** days of the current week (you cannot pull a day you've already logged). If the user wants past/logged days eligible, flag.
- **[P2-A4] If today already has a logged session**, the behavior is undefined here — assume this flow targets a not-yet-logged "today"; flag if the user expects otherwise.

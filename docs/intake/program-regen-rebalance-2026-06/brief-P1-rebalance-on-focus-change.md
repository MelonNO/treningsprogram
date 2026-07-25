# P1 — When a day's primary muscle focus changes, rebalance the rest of the week (toggle-gated)

**Type:** Feature
**Cluster:** Week-rebalance / Program-tab regeneration (with **P2**). Reuses the corrected single-day weight behavior from **P4**, and reuses the existing "regenerate around logged days" generation path.
**Outcome-only:** describes the desired end result, not the implementation.

## Context
On the Program tab, changing a single day today only affects that day — the **"Swap [day]'s workout"** dialog states *"Only this day changes — the rest of the week stays the same."* The user wants the option for the rest of the week to **rebalance** when a day's **primary muscle focus** changes, so muscle-group placement and recovery stay coherent (e.g. move Wednesday to Chest when Chest was on Thursday → the week adjusts). The app already has a "regenerate the non-logged days, preserve logged days" generation path that this can build on.

## What the user wants (end result)
- A user-visible **"auto rebalance" toggle**.
- **Toggle ON:** when a day's **primary muscle focus changes** — whether the user changed it **manually** (editing that day) or by **regenerating** that day with a new focus — the app automatically **regenerates all the other non-logged days** of the **current week** around the changed day, showing the **normal generation progress animation**.
- The **day the user just changed is locked** — kept exactly as set; only the *other* non-logged days regenerate around it.
- **Logged days are never touched.**
- **Toggle OFF:** nothing happens — behavior stays exactly as today (only that one day changes).
- **Trigger is a real change of the day's primary muscle focus** (e.g. legs → chest). Minor edits — changing sets/reps, or swapping one exercise **within the same focus** — do **not** trigger a rebalance.
- **Current week only.**

## Current vs correct behavior
- **Current:** changing a day changes only that day; the rest of the week never reacts.
- **Correct (toggle ON):** changing a day's primary muscle focus regenerates the other non-logged days around the locked, changed day; logged days are preserved; current week only.
- **Correct (toggle OFF):** unchanged from today.

## Acceptance criteria (observable)
- **Done when** there is a user-visible toggle that controls auto-rebalance.
- **Done when**, toggle ON, changing a day's **primary muscle focus** (manually or via regenerate) regenerates **all other non-logged days** of the current week around the **locked** changed day, with the normal progress animation.
- **Done when** minor edits that do **not** change the day's primary focus (sets/reps tweaks, same-focus exercise swaps) do **not** trigger a rebalance.
- **Done when**, toggle OFF, changing a day changes **only that day** (today's behavior is preserved).
- **Done when** the rebalance **never modifies logged days**.
- **Done when** rebalancing is confined to the **current week**.

## Scope and constraints
- **In scope:** the auto-rebalance toggle; detecting a primary-focus change on a day; regenerating the other non-logged days around the locked changed day; current week.
- The rebalance is a weekly-style generation of the non-logged, non-edited days — it should run through the same generation pipeline (which already includes the verification pass) and reuse **P4**'s corrected real-weight behavior.
- **Out of scope:** changing automatic start-of-week generation; touching prior weeks; the "do another day's workout today" flow (that is **P2**).
- **Standard cross-cutting constraints:** build via `./build.sh`; no commits/releases unless asked; no on-device/UI tests unless asked.

## Decisions confirmed by the user (2026-06-28, via coordinator)
- Rebalance is gated by an **auto-rebalance toggle** (OFF ⇒ nothing happens).
- Trigger is an **actual primary-focus change**, not every edit.
- The **changed day is locked**; only other **non-logged** days regenerate.
- Logged days untouched; **current week** only.

## Decisions deferred / assumptions (user may override)
- **[P1-A1] Toggle placement** (Program tab vs Settings) is left to the orchestrator to place sensibly — the user specified its behavior, not its location.
- **[P1-A2] "Logged day"** is taken to mean **any day with ≥1 logged exercise**, consistent with the app's existing preserve-logged-days behavior. If the user meant "a fully completed workout," flag and narrow.
- **[P1-A3] "Primary muscle focus" of a manually-edited day** = the day's dominant muscle group; only a change in that dominant group triggers a rebalance. The precise detection rule is an implementation detail, but the **outcome** (only a genuine focus change triggers) is fixed.
- The **P2** "move a day's workout to today" flow **always** rebalances regardless of this toggle (see brief-P2).

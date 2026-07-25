# Intake Index — Program Regen / Rebalance + Generation-Wait UX Batch

**Prepared for:** Project-lead orchestrator
**Source:** User-supplied batch of 5 items (mix of bugs and features) for the Android adaptive-workout app, plus one in-conversation extension to Item 1.
**Date:** 2026-06-28
**Status:** Understood and confirmed. NOT yet dispatched to the orchestrator (dispatch is a separate, later user instruction).

## Confirmation note
The understanding below was confirmed item-by-item with the user, but the sign-off (and all per-item answers) reached the intake agent **relayed via the coordinator**, not in the user's own words. Relayed approval reflects the user's intent as conveyed; if the orchestrator needs the gate re-affirmed, confirm with the user directly. Creating these documents is not the same as dispatching work.

All briefs are **outcome-only**: they describe the end result and user experience, never the implementation. The "how" belongs to the orchestrator and its workers.

## Items

| ID | Title | Type | Maps to user's item | Brief file | Status |
|----|-------|------|---------------------|------------|--------|
| P1 | Rebalance the week when a day's **primary muscle focus** changes (toggle-gated) | Feature | Item 1 | brief-P1-rebalance-on-focus-change.md | Ready |
| P2 | **Do another day's workout today** — move it into today, log for today, then rebalance | Feature | Item 1 extension | brief-P2-do-another-day-today.md | Ready |
| P3 | System **notification** when generation finishes while app is backgrounded | Feature | Item 2 | brief-P3-generation-complete-notification.md | Ready |
| P4 | **Single-day regen at full weekly parity** (real weights, variety, verification pass) | Bug + feature | Items 3 + 5 (merged) | brief-P4-single-day-regen-parity.md | Ready |
| P5 | Make the generation **wait less boring** (changing text, real status always visible) | Feature / UX | Item 4 | brief-P5-less-boring-generation-wait.md | Ready |

**Note on the merge:** the user's original **Item 3** ("no verification prompt for single-day regen like there is for the whole-week gen") and **Item 5** (single-day regen returns the same workout + all bodyweight) are the **same underlying change** — bring the single-day path to full weekly parity. They are documented as one item, **P4**.

## Clusters, integration seams, and parallelization

### Cluster A — Single-day regeneration parity (P4) — FOUNDATION, land first
P4 touches `AiRepository.generateSingleDayProgram` and brings it to parity with the weekly generator (history → real weights, retry loop, strict per-day time-budget gate, `validateProgram` verification). It establishes the **real-weight generation behavior that P1 and P2's rebalances reuse**, so it should land **before** them.

### Cluster B — Week rebalance & day-move (P1, P2) — ONE coordinated unit
Both live on the Program tab and share the **week-rebalance mechanism** (regenerate the non-logged days around locked/logged days, current week). They differ only in trigger:
- **P1** rebalances when a day's **primary muscle focus changes**, and only if the **auto-rebalance toggle** is ON.
- **P2** rebalances **always** (toggle-independent) after the user **completes** a workout pulled from another day into today.

Treat P1+P2 as a tightly-coordinated pair (one worker, or carefully serialized) — they edit the same Program-tab/regeneration surface and both call into the shared generation seam.

### Cluster C — Generation-wait UX (P3, P5) — can run in parallel with A/B
Both wrap the **generation lifecycle** and the shared progress callback: **P3** fires a completion notification (success/terminal-failure) when backgrounded; **P5** makes the during-wait text less boring while keeping the real status visible. Independent of the rebalance logic; coordinate the two with each other since both attach to generation progress/completion.

### Cross-group hazards
- **P1, P2, P4 all touch the generation seam** (`AiRepository` + the generation flow) — do **not** split them across uncoordinated parallel workers. Land **P4 first**; build P1+P2 on top.
- P2's **"only after completion"** timing (move/discard/rebalance commit atomically on workout completion; abandoning leaves the week unchanged — see [P2-A1]) is the subtlest part — keep it intact when wiring the rebalance.
- P3/P5 share the generation **progress/completion** plumbing — keep them consistent (same notion of "terminal outcome").

### Suggested order
1. **P4** — single-day regen parity (foundation: real weights + verification reused by the rebalances).
2. **P1 + P2** — rebalance-on-focus-change and do-another-day-today (build on P4; coordinate, shared surface).
3. **P3 + P5** — in parallel (independent of A/B logic): completion notification and less-boring wait.

## Confirmed decisions (user, 2026-06-28, via coordinator)
- **P1:** Gated by a user-visible **auto-rebalance toggle** (OFF ⇒ nothing happens). Trigger = an **actual change of the day's primary muscle focus** (manual or via regenerate), **not** every edit (sets/reps tweaks or same-focus exercise swaps do not trigger). The **changed day is locked**; only other **non-logged** days regenerate; **logged days untouched**; **current week** only; normal progress animation.
- **P2:** Pulling another day's workout to today moves it into today and the user **performs & logs it normally** (attributed to today — interpretation (a), not auto-marked done). Today's **original** plan is **discarded**; the **vacated** day is **regenerated**; **all directions** (earlier/later), **current week**. This rebalance **always** runs regardless of the P1 toggle. Move + rebalance are **finalized only after the workout is completed**.
- **P3:** **All** generation types; **only when backgrounded**; notify on **success and terminal failure** (after 3 fails); **tap → Program tab**; **wording is the builder's choice**.
- **P4:** Single-day regen = **full weekly parity** scoped to one day — history-driven **real weights** (no all-BW), respects the **user-selected muscle focus** (variety within it, focus fixed), runs the **retry loop + strict per-day time-budget gate + `validateProgram` verification**, and **fully replaces** the day **including any logged sets** on it.
- **P5:** **Combination** of friendly content + **real status** (real status always visible, combined or separate); on **all** generation-wait screens; **any** content/tone acceptable.

## Assumptions applied (user may override)
- **[P1-A1]** Auto-rebalance toggle **placement** (Program tab vs Settings) left to the orchestrator; user specified behavior, not location.
- **[P1-A2]** "Logged day" = any day with **≥1 logged exercise** (consistent with the app's existing preserve-logged-days behavior). If the user meant "a fully completed workout," flag and narrow.
- **[P1-A3]** "Primary muscle focus" of a manually-edited day = the day's **dominant muscle group**; only a change in that triggers a rebalance.
- **[P2-A1]** **(Most important open default)** On selection the chosen workout is presented as today's session to perform, but the move/discard/rebalance **commit only on completion**; **abandoning leaves the week unchanged**. Documented default for the user's "only after the workout is complete" instruction — flagged for confirmation.
- **[P2-A2]** Entry-point/UI for "do this today" left to the orchestrator.
- **[P2-A3]** Eligible source days = **non-logged** current-week days.
- **[P2-A4]** Behavior when "today" already has a logged session is undefined — flag if the user expects otherwise.
- **[P3-A1]** Notification copy is the builder's (suggested phrasings in the brief). **[P3-A2]** Permission denied ⇒ no notification, no crash. **[P3-A3]** Distinct success/failure copy/channel left to the builder.
- **[P5-A1/A2]** Rotating copy, cadence, and message count are the builder's, since any content is acceptable; user may supply copy.

## Cross-cutting constraints (apply to every brief)
- Build via `./build.sh` (not `./gradlew` directly).
- No commits, tags, or releases unless the user explicitly asks.
- No on-device or automated UI tests unless the user explicitly asks.
- **Diagnose-first** for the bug half of **P4**: confirm the cause (single-day prompt lacks history/weights; return-shape hardcodes `targetWeightKg:0`; current day excluded from context) before changing behavior.
- Keep the per-day **time-budget gate strict** wherever generation is touched (carried forward from G1/H3) — do not loosen it or smuggle in a salvage path.

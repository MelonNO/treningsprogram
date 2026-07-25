---
name: program-regen-rebalance-2026-06
description: Confirmed post-v1.10.6 5-item batch (P1-P5) at docs/intake/program-regen-rebalance-2026-06/ — week rebalance, do-another-day-today, gen-complete notification, single-day regen parity, less-boring wait
metadata:
  type: project
---

Confirmed 5-item batch, post-v1.10.6, briefs+INDEX at `docs/intake/program-regen-rebalance-2026-06/` (IDs P1–P5). Understood + user-confirmed (relayed via coordinator) 2026-06-28; NOT yet dispatched.

**Item mapping (user's original → brief):**
- P1 = Item 1 — rebalance week when a day's PRIMARY muscle focus changes; gated by an **auto-rebalance toggle** (OFF ⇒ nothing). Trigger = real primary-focus change (manual or regen), NOT minor edits. Changed day locked; only other NON-logged days regenerate; logged days untouched; current week.
- P2 = Item 1 extension — **do another day's workout today**: move chosen day's plan into today, user performs & logs normally (attributed to today, interpretation (a) not auto-done); today's original plan discarded; vacated day regenerated; all directions; current week. **ALWAYS rebalances regardless of P1 toggle.** KEY TIMING: move/discard/rebalance commit only AFTER workout completes; abandoning leaves week unchanged ([P2-A1], the one flagged default).
- P3 = Item 2 — gen-complete **notification** when backgrounded only; all gen types; fires on success AND terminal failure (3 fails); tap → Program tab; wording = builder's. (POST_NOTIFICATIONS + channels already exist via rest timer.)
- P4 = **Items 3 + 5 MERGED** — single-day regen (`AiRepository.generateSingleDayProgram`) to FULL weekly parity: history-driven real weights (fixes all-BW), respects user-selected focus (variety within it), runs retry loop + STRICT per-day time-budget gate + validateProgram verification, FULLY replaces day incl. logged sets. Item 3 ("no verification prompt on single-day like the week gen has") was NOT about auto-regen — it folds into parity. Diagnosed cause: single-day prompt has no history/weights + return-shape hardcodes `targetWeightKg:0` + current day excluded from context.
- P5 = Item 4 — less-boring wait: COMBINATION of friendly text + real status (real status ALWAYS visible, combined or separate line); all generation-wait screens; any content/tone.

**Why:** user's hands-on testing of the Program tab + generation flows surfaced these.
**How to apply:** Cluster A=P4 (foundation, land first — real weights reused by rebalances); Cluster B=P1+P2 (shared rebalance mechanism, coordinate); Cluster C=P3+P5 (gen-wait UX, parallel). P1/P2/P4 all touch the AiRepository generation seam — don't split across uncoordinated workers. Item-3-style "verification" = the validateProgram peer-review call.

[[reference_intake_doc_format]] [[project_generation_retry_hang_2026_06]]

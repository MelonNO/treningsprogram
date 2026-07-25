---
name: generation-timebudget-fix-2026-06
description: Confirmed post-v1.10.0 single-item intake batch (G1) at docs/intake/generation-timebudget-fix-2026-06/ — AI generates a good plan but the strict per-day time-budget gate discards it; fix is PROMPT-SIDE only, gate stays strict
metadata:
  type: project
---

Confirmed intake batch produced 2026-06-26 at `docs/intake/generation-timebudget-fix-2026-06/` (INDEX.md + brief-G1 + repro-failing-generation.md). Single bug. Source: user-reported after v1.10.0 shipped, with a strong user-supplied repro. Sign-off (bug understanding + fix-direction) **relayed via coordinator**, NOT user's direct words — noted as such in INDEX (relayed approval is not the intake gate). Creating docs ≠ dispatching orchestrator.

**The bug (G1):** Generating a plan (from the training-profile-change popup AND the AI menu — both share `AiRepository.generateAdaptedProgram`) produces a complete, rule-compliant plan, but the app discards it and saves nothing. User's lived complaint: "validation never happened and the plan did not get used… silently ends up with no new plan."

**Root cause (coordinator-relayed lead, to confirm on fixture):** the deterministic ±10-min PER-DAY time-budget gate rejects every attempt. In the captured 5-day/50-min repro, 4 of 5 days estimate UNDER the 40-min floor by the app's own `WorkoutTimeEstimator` (Wed ~33, Fri ~36, Sat ~31, Sun ~33; only Tue ~42 in-window). A deterministic miss short-circuits BEFORE the LLM review step (`validateProgram`) → "validation never happened"; after MAX attempts it throws → nothing saved. Candidate cause: the in-prompt self-size formula the model is told (`sets×(reps×3s+rest)+60s`) does NOT match the authoritative estimator (`sets×reps×3 + (sets-1)×rest + 60`, uses LAST/max number in the rep range) — so the model self-sizes to a window the deterministic check then rejects, here landing UNDER.

**HARD CONSTRAINT — settled by user (do not reopen):** Keep the time-budget gate STRICT. Do NOT widen/loosen the per-day window. Do NOT add a "save best attempt anyway"/salvage/never-discard fallback. Fix is PROMPT-SIDE only — steer the AI so days reliably land inside the strict window so the gate passes. User was offered loosen-gate and fallback alternatives and rejected both.

**Assumptions I applied (flagged for veto):** G1-A1 "reliably" judged vs the existing AI priority ordering (quality > fewer rejects > never fails; not a 0%-reject bar) — repro succeeds + rejection class materially reduced; G1-A2 fix lands once at the shared seam, benefits all entry points though only 2 named; G1-A3 "silent, no plan" is the symptom (gone when generation succeeds), NOT a request for failure-state messaging — the all-retries-fail user-error path was already shipped in B10 and is out of scope.

**How to apply:** if the user revisits AI-generation reliability or the time-budget gate, this batch is authoritative. Same hot seam as [[refinements-2026-06]] B08/B09/B10 (just shipped v1.10.0) — coordinate any future AI-seam work. See [[intake-doc-format]]. NOT yet dispatched to orchestrator.

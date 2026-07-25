---
name: project-generation-timebudget-fix
description: "Post-v1.10.0 follow-up bug: AI generation produced a valid plan but the strict per-day time-budget gate rejected every attempt → nothing saved. Prompt-side fix done + verified on branch generation-timebudget-fix-2026-06, shippable as v1.10.1; user device test still pending."
metadata: 
  node_type: memory
  type: project
  originSessionId: f6f91f3b-3ce3-443b-a5de-84068e426054
---

Follow-up bug after v1.10.0 shipped (2026-06-26). User: generating a plan (from the training-profile-change popup AND the AI menu — both run shared `AiRepository.generateAdaptedProgram`) produced a complete, rule-compliant plan, but "validation never happened and the plan did not get used."

## Root cause (confirmed)
The strict deterministic **±10-min per-day time-budget gate** runs BEFORE the LLM `validateProgram` review and short-circuits it when any day is out of window; after `MAX_GENERATION_ATTEMPTS=3` it throws → nothing saved. Two compounding causes: (1) the per-attempt rejection feedback said "Trim sets/exercises or rest" for BOTH under- and over-time days, so under-time days were driven SHORTER each retry and never converged; (2) the prompt's STATED estimate formula over-counted rest (`sets × rest` vs the estimator's `(sets−1) × rest`), so the model's self-estimate ran longer than the app's gate → systematic under-filling. In the user's fixture, 3 of 5 days estimated under the 40-min floor (Wed 33 / Fri 36 / Sat 31; Tue 42, Sun 58 in-window — Sun only because "Slow Tempo" trips the cardio keyword, see below).

## Fix (user decision: KEEP GATE STRICT, fix PROMPT-SIDE only; no salvage/fallback)
Pipeline followed properly this time: intake (`docs/intake/generation-timebudget-fix-2026-06/`, brief-G1 + repro fixture) → project-lead-orchestrator. Changes (AiRepository.kt only, +44/−11): direction-aware `dayDurationFeedback` helper (under→ADD, over→TRIM, in-window→null) with the reject CONDITION byte-for-byte preserved (40/60); strengthened attempt-1 guidance (under rejected as hard as over; aim for window centre); corrected the stated formula in all 3 prompt spots to match the estimator; added a time-budget self-check bullet. Gate logic, estimator, data model, MAX_GENERATION_ATTEMPTS, and the B10 all-retries-fail path all UNTOUCHED.

## Status (2026-06-26) — SHIPPED v1.10.1, LIVE + coordinator-verified
User gave direct ship word ("ship"). Shipped via build-release-shipper this time WITHOUT an auth/harness block (clean push to main). Commit `38046a5` on `main` (versionCode 39→40, versionName 1.10.1; AiRepository.kt + G1TimeBudgetFeedbackTest + docs/intake/generation-timebudget-fix-2026-06/; no `.kotlin/`/`flows/`), tag `v1.10.1`. Release PUBLISHED + coordinator-verified via fresh API GET: https://github.com/MelonNO/treningsprogram/releases/tag/v1.10.1 (draft=false, target_commitish=main, asset `treningsprogram-v1.10.1.apk` 102975061 bytes state=uploaded, md5 `cf61db5e5a142d63e79ea087432faca9`). Build had 493 tests green (G1 6/6 asserts the strict 40/60 boundary is preserved). Gate/estimator/data-model/MAX_GENERATION_ATTEMPTS untouched.
- **RESIDUAL (not blocking):** live generation still not directly proven — user declined Waydroid testing and didn't supply an API key, so the proof that real generations now land in-window is the USER generating on their own device post-update. If a live gen still silently fails, iterate to v1.10.2. It's a steering improvement, not a guarantee the gate never rejects.

## Incidental finding (NOT fixed — future cleanup candidate)
`MuscleClassifier` treats the substrings `"tempo"`/`"interval"` in an exercise NAME as cardio keywords, so a strength move like "...(Slow Tempo)" is misclassified as cardio and time-estimated as a ~30-min cardio block. Pre-existing estimator quirk, out of scope for this fix.

## Note
The orchestrator's report referenced a relayed "same auth as last session / coordinator has command of you" message that the coordinator NEVER sent (a confabulation). It correctly declined to expand scope or ship on it. No memory was written from it; no poisoning persisted. Per [[feedback_coordinator_background_agent_ops]].

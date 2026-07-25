---
name: generation-quality-overhaul-2026-07
description: Confirmed post-v1.12.0 6-item generation prompt+validator overhaul at docs/intake/generation-quality-overhaul-2026-07/ (P1-P5, V1)
metadata:
  type: project
---

Confirmed intake batch (2026-07-01), driven by two docs: `Change docs/prompt feedback` (§0–§8) + `Change docs/Validation feedback`. Briefs at **`docs/intake/generation-quality-overhaul-2026-07/`** (INDEX + 6 briefs). Scope = "everything in both documents", nothing deferred.

**Why:** user's ONE concrete complaint about AI generation is "exercises or weights are off." Root cause I traced in `AiRepository.kt`: three overlapping rules force a different exercise for every muscle every week (line ~1471 "rotate every single week" + the blacklist "single most important constraint" built from recent-logged + last plan + a mandatory random weekly variation theme), which churns away the anchor lifts AND — because parseProgram copies the model's `targetWeightKg` verbatim with NO reconciliation to logged history (history keyed by exact logged name) — the forced-new lifts get guessed weights.

**The 6 items (all edit the ~2043-line AiRepository.kt — the prompt builder, parse model, validateProgram, salvage path — so NOT parallelizable; one worker, order P2→P3→P1→P4→P5→V1):**
- **P1 (HEADLINE)** — exercise continuity + correct weights + variation hierarchy (§0/§3.3/§3.4/§3.7). Middle-ground variation the user specified: (1) most main lifts kept+progressed from logged history; (2) same-movement implement/grip/angle variation OK more freely, weight ESTIMATED FROM CLOSELY-RELATED LOGGED LIFT; (3) full main-lift swaps capped **≤2/week**; (4) accessories/order/weekday-placement/theme rotate freely. Replaces the blacklist-everything rule. Weekday may shift within recovery rules.
- **P2** — derive volume/rest/count/time per request across FULL 20–120 min (§3.1/§3.2/§3.5); delete fixed ~19-20 sets / ~46-47 min / 120s-ceiling / 3s-per-rep; fix per-rep to ~4-5s; keep prompt formula in lockstep w/ the authoritative deterministic estimator (already single-source).
- **P3** — all 4 goals fully+equally supported, wide §5 bands replace narrow rep table, de-hypertrophy-bake (§3.8/§3.9/§5/§4/§1), hinge caps stay hard, bands are SOFT.
- **P4** — injury empty=no-op, MILD=≤1 optional prehab/week no forced selection change, MOD/SEV unchanged (§3.6).
- **P5** — §6 auditable metadata (work/rest/setup secs, day estimate+withinWindow, weekly volume/pattern/blockState). RISK: output truncation/timeout (app's history) — fallback to minimal subset if it breaks gen.
- **V1** — new quality gate: "either completely reject OR modify" (NOT advisory), + user added "verify the modified plan" → after deterministic fix, RE-CHECK against same gates before saving; capped regen then deterministic fix, never loops. Build ON existing MAX-3-attempts + 360s deadline + REST-first auto-trim-then-re-review. Movement-balance checks MUST coverage-scale (§3.5) or they false-reject short weeks. Soft §5 bands never hard-reject. Watch the documented v1.10.7 false-reject history.

**V1 terminal behavior RESOLVED (user, 2026-07-01):** after capped regen + deterministic auto-fix + re-verify, if the plan STILL can't pass every gate → surface a clear "couldn't build a good plan" failure and **SAVE NOTHING** (NOT save-best-with-note). "Always usable" = a passing plan OR a clean honest failure, never an imperfect saved plan; caps still guarantee bounded-time termination.

**How to apply:** these are INTAKE docs only — creating them ≠ dispatching the orchestrator (separate later user instruction). Approval was coordinator-relayed, not user-direct. See [[intake-doc-format]], [[plain-language-questions]] (user found first Q-round too technical), [[project_generation_retry_hang_2026_06]] / [[project_generation_efficiency_2026_06]] for the tuning this overhaul unwinds.

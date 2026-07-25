---
name: project_long_session_fix_2026_07
description: "v1.17.0 SHIPPED — long (≥90 min) generation targets now reach in-window via multi-modal structure; the overnight-run '120-min produces nothing' failure is fixed. Follow-up calibration pass in progress."
metadata: 
  node_type: memory
  type: project
  originSessionId: db92b508-221f-4bb4-bead-47a455671941
---

**SHIPPED v1.17.0 (2026-07-01, commit `f170aed` on main, tag+release API-verified live, DB unchanged v17, versionCode 53).** Fixes the top open item from [[project_overnight_autonomous_chain_2026_07]] — a long session target (~100–130 min) generated NOTHING.

**Root cause (3-part collision, coordinator-diagnosed, orchestrator-confirmed):** in `AiRepository.kt` + `domain/WorkoutTimeEstimator.kt` — (1) the app's own soundness rules cap a *sound* single session ~55–70 min: HARD ≤8/≤7/≤5 exercises (Adv/Int/Beg, in BOTH the prompt SESSION-DESIGN line and `validateProgram` item #11), per-muscle ~8–10 hard-set cap, junk-volume rejection; so at a 120 target every day lands far under the 110 floor and the under-fill retry only buys ~5 min (can't add a 9th exercise). (2) `max_tokens=16384` truncated inflation attempts. (3) the salvage only captures `!anyUnderWindow` (OVER-ceiling) candidates (`AiRepository.kt` ~1109–1111), so an all-UNDER 120 plan → `finalizeOrSalvage(null)` → throws → nothing saved.

**The fix (all in `AiRepository.kt`, +104/−10, gated behind `isLongSession()` = target ≥ `LONG_SESSION_THRESHOLD_MIN`=90):** for long targets only, the prompt builds each day MULTI-MODAL — sound strength block first, THEN a DURATION-sized warm-up + conditioning/cardio finisher. **Only estimator-countable cardio modalities** (stationary bike, incline treadmill walk, easy jog/run intervals, jump rope) — NOT rowing/carries/sled/elliptical, which `MuscleClassifier` maps to Back/Core so they'd be timed by the strength formula (~4 min, not 40) and silently under-fill. Exercise-count cap now counts STRENGTH slots only (prompt + peer review). `dayDurationFeedback` long branch steers "extend the finisher" not "add a set" (reject condition byte-for-byte unchanged). `GENERATION_MAX_TOKENS=24576` on the gen call only (default 16384 for all other calls). Short/mid (≤80 min incl. the verified-lean 50-min) untouched.

**Verified:** 626 unit tests (+9 `L2LongSessionStructureTest`); live A/B (20/20 call budget, ledger in `docs/intake/long-session-fix-2026-07/`) confirmed 120-min saves in-window (days ~115–122), correct cardio classification, zero truncation, 50-min stays lean. **Product decision (user):** KEEP the strict gate — NO under-fill salvage; a plan that can't reach target must FAIL LOUD, never save a sub-target/padded plan.

**RESIDUAL (accepted as fail-loud):** ~100-min target lands ~1–2 min under the floor on the tested 2 attempts (model has a systematic self-estimate under-bias vs the exact formula; production runs 3 attempts, may close it). Band NOT widened (that gap is genuine under-target).

**FOLLOW-UP SHIPPED v1.17.1 (2026-07-01, commit `b270d4b` on main, tag+release API-verified live, DB unchanged v17, versionCode 54):** calibration pass — new `DURATION_AIM_BUFFER_MIN=12` + aim-high prompt wording (model under-counts its own sizing vs the exact formula, so aim ~target+12; an over-count is auto-trimmed/safe, an under-count is fatal). Prompt-only; gate/estimator/`trimOverflowToWindow` salvage/retry-ladder/weights/goals/injury all untouched. 632 tests (+6 `DurationCalibrationTest`); 9/20 live calls. RESULT: **100-min now SAVES** (lands over-only → existing trim salvage brings it in-window; was 88–89 under-floor = nothing saved), strength@50 attempt-1 in-window (was under-floor), 120 no regression, 50 stays lean. Residual: short-rest goals (endurance/weight-loss) @50 still may miss attempt-1 but self-heal over the retry ladder (real fix = goal-cardio steering, out of scope per four-goal-parity "don't touch"). Coordinator shipped directly again (relayed-consent deadlock recurred).

Ship note: relayed-consent deadlock recurred (orchestrator refuses coordinator-relayed ship consent — structurally unsatisfiable); user gave direct "ship it directly" → coordinator executed the release directly (precedent: v1.10.7, v1.12.0). See [[feedback_orchestrator_owns_changes]].

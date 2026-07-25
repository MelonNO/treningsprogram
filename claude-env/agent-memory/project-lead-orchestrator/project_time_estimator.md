---
name: time-estimator-shared-helper
description: domain/WorkoutTimeEstimator is the single source for the Program "~Xm" math AND the AI ±10-min duration gate
metadata:
  type: project
---

`domain/WorkoutTimeEstimator.kt` (object) is the ONE place the session time-estimate formula lives, as of 2026-06-23 (working-tree only — NOT committed/released). It exposes `estimateExerciseSeconds(ex)` and `estimateDayMinutes(exercises)`.

**Why:** Feature required AI-generated days to estimate within ±10 min of the user's session length, using the SAME formula the Program screen displays as "~Xm" — so the validator accepts exactly what the user sees. Extracting the formula avoided drift between display and enforcement.

**How to apply:**
- `ProgramFragment` no longer has its own `exerciseEstimateSeconds`/`parseCardioSeconds`/`ADMIN_TIME_PER_EXERCISE_SECONDS` — it delegates to the helper. Do NOT reintroduce a per-screen copy of the time math; change the helper instead.
- `AiRepository.generateAdaptedProgram` has a DETERMINISTIC ±10 gate right after `parseProgram`: groups by dayOfWeek, `estimateDayMinutes`, rejects out-of-range days into the existing 3-attempt `rejectionReasons` retry loop. Acceptance now = `durationReason.isEmpty() && validateProgram(...).accepted` (BOTH the Kotlin gate AND the LLM peer-review must pass). The Kotlin check is authoritative; the LLM validator's TIME-BUDGET item (now item 13) is belt-and-suspenders only.
- `validateProgram` signature is `(planJson, daysPerWeek, sessionDurationMinutes, goal, experience, injuries)`.
- Formula (preserve exactly): non-cardio `sets*(maxReps*3) + (sets-1)*rest + 60`; cardio `parseCardioSeconds(targetReps)+60` where "N min"→N*60, "X km"→X*5*60, else 1800; day minutes `(sumSeconds+30)/60`. Cardio detection = `MuscleClassifier.displayName(name)=="Cardio"`.
- Known inherited edge case (NOT a new bug): cardio `targetReps` with no min/km parses to 1800s (~31 min day) — can trip the ±10 gate if session target is far from ~31; behavior inherited from the original formula.
- **Gate short-circuits the LLM review (control flow to remember):** in `generateAdaptedProgram`, when any deterministic check fails (empty/rest-day/duration) `validateProgram` is SKIPPED — `validation = ValidationResult(false, deterministicReason)`. So a duration miss means "the LLM peer-review never ran." A plan failing the ±10 gate on every attempt throws after MAX_GENERATION_ATTEMPTS (3) and saves nothing.
- **MuscleClassifier name-keyword cardio trap — FIXED 2026-06-26 (branch `exercise-recognition-fix-2026-06`, R1; verified, NOT yet shipped).** Cardio detection USED to key on `"tempo"`/`"interval"`; a STRENGTH move named with those (e.g. "...Calf Raise (Bodyweight, Slow Tempo)") classified as Cardio → 30-min (1800s) fallback, inflating the day ~25+ min. The R1 fix REMOVED `"tempo"`/`"interval"` from the cardio keyword lists in BOTH `MuscleClassifier.fromName` and `finerMusclesFor` (genuine "Interval Run"/"Tempo Run" still resolve via `"run"`). Consequence: the G1 fixture's Sunday dropped 58→33 min, so `G1TimeBudgetFeedbackTest` was recomputed (Sunday now UNDER the 40-min floor; rejected days {3,5,6}→{3,5,6,7}; "4 of 5 days under", not 3). If a "tempo→cardio trap" comment appears elsewhere it is now STALE — the trap is gone. `isCardio` still = `MuscleClassifier.displayName(name)=="Cardio"`.
- **G1 fix (2026-06-26, prompt-side only, branch generation-timebudget-fix-2026-06, NOT shipped):** per-day reject feedback is now the pure package-level helper `dayDurationFeedback(day, est, target)` — direction-aware (under-floor → "ADD", over-ceiling → "TRIM"; null in-window). Reject CONDITION is byte-for-byte the old `est<target-10 || est>target+10` (only wording changed). The prompt's stated estimate formula was ALSO corrected to match the authoritative estimator: it had over-counted rest as `sets×(reps×3+rest)`; now `sets×reps×3 + (sets-1)×rest + 60` in all 3 prompt spots (generation TIME BUDGET, validateProgram item 13, single-day). Guarded by `G1TimeBudgetFeedbackTest`.

Related: [[warmup-and-muscle-consistency]] (MuscleClassifier is the shared classifier), [[log-screen-file-clusters]].

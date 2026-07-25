---
name: rest-ux-batch-2026-07
description: Confirmed post-v1.21.0 4-item rest-timer/UX batch at docs/intake/rest-ux-batch-2026-07/ (items 1,2,4,5; item 3 withdrawn) — manual per-category rest times + session-only adjustment memory + home globe removal + exercise-timer background bug
metadata:
  type: project
---

Confirmed 2026-07-02 (post-v1.21.0, DB v18), briefs at `docs/intake/rest-ux-batch-2026-07/`. User numbering 1–5 preserved; **item 3 (last-time reps display) WITHDRAWN by user** ("scrap this point it is wrong" — code showed reps were in fact rendered, `LastSessionFormat` "8 × 60 kg"; my grounded question surfaced that the report was wrong).

**Why:** user wants rest-timer control (AI vs own times) and small logging-screen fixes.

**How to apply:** decisions below are settled — don't re-ask.

- **Item 1:** remove `ill_particle_sphere` from Home hero, nothing replaces it. Implicitly confirmed (no objection).
- **Item 2:** +30/−30 rest adjustment → next-set rest = base ± net presses; sticks for remaining sets of the SAME exercise, session-only; resets next exercise/session; layers on top of item 4's manual times identically.
- **Item 4:** rest-mode toggle (AI default / manual opt-in), TWO categories "Heavy compounds" + "Accessories", defaults 3:00/1:30 m:ss, checkbox reveals fields, in BOTH wizard training step + Settings→Training; rest-sheet label reflects source ("Your time: …" — accepted suggestion). **KEY user-flagged addition: generation's session-duration math must count manual rest times when active** (WorkoutTimeEstimator/AiRepository budget). Assumptions A1–A3 (AI default, cardio→Accessories, auto classification) applied, user may veto.
- **Item 5:** per-exercise timer (`currentExerciseElapsedMs`) resets on backgrounding — cause: `WhileSubscribed(5000)` + `flatMapLatest` re-captures `System.currentTimeMillis()` start; user chose ROBUST fix (survives process death too, insofar as the workout resumes).

Clusters: A = 4→2 one worker (same rest resolver seam); item 5 same-file hazard with A in `LogWorkoutViewModel.kt` (serialize or fold in); item 1 trivial parallel.

Useful discovery: `PreferencesManager.restTimerSeconds` exists but is wired ONLY to backup/export — no UI, not used by the timer (fallback is a hardcoded 90 in LogWorkoutViewModel). Item 4 is the first user-facing rest setting.

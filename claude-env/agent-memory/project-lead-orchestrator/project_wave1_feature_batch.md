---
name: wave1-feature-batch
description: Wave 1 of the 2026-06 feature batch (C1/C4-view/E3/B3) — what was built, where it lives, how the AiRepository seam was left
metadata:
  type: project
---

Wave 1 of the feature-batch-2026-06 (see [[feature-batch-2026-06]] in the user auto-memory and `docs/intake/feature-batch-2026-06/SEQUENCE.md`). Resumed after a Pi build-host crash mid-Wave-1 (2026-06-24); the working tree already held the code.

**The four units and where they live (verified at code level, build green, all unit tests pass):**
- **C1** (est-1RM trend + PR timeline): `domain/OneRmTrend.kt` (pure) + `ui/history/RecapTrendsFragment.kt` (chart id `chart_trend_e1rm`, empty id `tv_trend_e1rm_empty`, PR list `layout_trend_pr_history`). Reached via Stats tab → "Recap" sub-tab → tap an exercise.
- **C4 view** (Home recovery panel): `domain/MuscleRecovery.kt` (pure, 3 bands: RECOVERING <48h, READY 48h–7d, OVERDUE >7d, + UNTRAINED) + `HomeViewModel.muscleRecovery` + `HomeFragment.renderRecovery` (card `card_recovery`, list `layout_home_recovery`). DAO: `WorkoutSetDao.observeLastTrainedPerMuscleGroup()` (warm-ups excluded, completed only). Groups = Chest/Back/Legs/Shoulders/Arms/Core (Cardio deliberately excluded — not a recovery target). C4's AI-NUDGE half is NOT in Wave 1.
- **E3** (library browser): `ui/library/` (Fragment/Filter/Adapter/ViewModel/DetailFragment) over `ExerciseCatalog.entries` (full bundled catalog). `ExerciseLibraryFilter` is pure/JVM-tested. Entry point: Settings → "Exercise Library" row (`row_exercise_library`).
- **B3** (stall detection): `domain/StallDetector.kt` (pure; STALL_WINDOW=3, IMPROVEMENT_EPSILON_KG=0.5, e1RM-based, double-progression-aware). UI alert: `HistoryViewModel.stalledLifts` + `HistoryProgressFragment.renderStalled` (card `card_stalled` titled "Plateau detected"). Prompt feed: `AiRepository.buildSessionHistory` appends a "STALLED LIFTS" block.

**Single 1RM formula across the whole app:** `domain/Epley.kt` (`object Epley.estimate(weightKg, reps) = weight*(1+reps/30)`, returns 0.0 for non-positive). OneRmTrend, StallDetector, RecapTrendsFragment, HistoryProgressFragment all call it — the old inline `weight*(1+reps/30)` copies were refactored onto it. **Why:** C1 and B3 must never show two different 1RM numbers (SEQUENCE.md Wave-1 requirement). **How to apply:** any future 1RM math must go through Epley, never re-inline.

**AiRepository seam state (Wave 1 end):** B3's change is PROMPT-ONLY and additive — `buildSessionHistory` returns `"$sessionDetails\n$trends$stallBlock"`; stallBlock is "" when nothing stalls. Response side (`extractJson`/`parseProgram`) untouched. Coherent and ready for Wave 2's B2 response-field edit. B3 is the ONLY Wave-1 unit that touched AiRepository.

**Build/test facts for this resume:** clean (no stale daemon after crash). `./build.sh assembleDebug` green; `./build.sh compileReleaseSources` green; `./build.sh test` → 165 tests, 0 fail/err/skip, incl. new B3StallDetectorTest(12)/C1OneRmPrTest(8)/C4RecoveryStateTest(10)/E3LibraryFilterTest(18). Fresh debug APK md5 `65d98b5cd56d6fcd7c80aae3d83f9e18` (changes per build — recompute, don't reuse).

**Wave 1 = confirmed PASS (2026-06-24).** On-device via ui-test-worker (Waydroid, MD5-verified install) all 4 units PASS: C1 (trend chart + PR rows date+wt×reps; warm-up 200kg correctly excluded from e1RM), C4 (Chest 1d→Recovering, Legs 4d→Ready, never-trained→Untrained — muscleGroup string-match works on real data), E3 (873 exercises, filters 873→127→20, graceful "No instructions available."), B3 ("Plateau detected" names Bench + suggestion; progressing Squat NOT flagged). Signaled coordinator: shippable.

**Known pre-existing bug surfaced during Wave-1 testing (NOT a Wave-1 defect, untouched by this wave):** the legacy Stats→Progress "Personal Records" widget (`WorkoutSetDao.getTopPersonalRecords` / `observePRsWithDate`) INCLUDES warm-up sets — shows a 200kg warm-up as a Bench PR, whereas C1's new PR timeline correctly excludes warm-ups. Violates the working-set=isWarmup=0 invariant ([[warmup-and-muscle-consistency]]). Route as a separate fix if the user wants it; do not fold into Wave 1.

**Tree-coherence note:** ui-test-worker left 7 untracked Maestro flow files under `flows/` (test artifacts, not app changes). Nothing staged/committed. Coherent.

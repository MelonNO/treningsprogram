---
name: feature-batch-2026-07-03
description: 10-item batch (N1/N3/N4/N5/N7/B1/B5/B7/B10/B11) shipped as v1.25.0 — DB v19 (lift_goals+exercise_notes), backup v6; key seams and traps for future work
metadata:
  type: project
---

Shipped v1.25.0 (vc 65) on 2026-07-03: single-pass self-implementation of the 10-item research batch, one commit per item on `feature-batch-2026-07-03`, fast-forwarded to main. 914 tests × 2 variants green (was 840).

**Why:** user-picked selection from RESEARCH.md; briefs at `docs/intake/feature-research-2026-07-03/`. Ship pre-authorized (no consent round-trip).

**How to apply — seams future work will hit:**
- **Goals (N5):** `LiftGoal` status is a one-way ACTIVE→ACHIEVED flip in `GoalRepository.detectReached` (called from LogWorkoutViewModel.completeWorkout AFTER processWorkoutCompletion; result.copy(reachedGoals=…)). No XP by design (A-G1) — adding goal XP later drags in StatsRecomputer merge parity. AI does NOT see goals (A-G3 deferred).
- **Chart guides:** `StrengthChartView.Guide(value,label,extendRange,isGoal)` is the ONE mechanism for N3 milestones + N5 target lines; extendRange=true widens the y-range (goal line), false clips to data (milestones).
- **Exercise notes (N7):** name-keyed `exercise_notes` (COLLATE NOCASE reads; save deletes-then-inserts to avoid case-dup rows). Write path ONLY via WorkoutRepository.saveExerciseNote (backup hook). Log-screen add is LONG-PRESS on the exercise name (judgment call — no-clutter AC); library detail has the visible Add/Edit button.
- **Warm-up ramp (B1):** WarmupRamp lives in ui/log next to PlateMath (not domain — it builds on PlateMath). Heavy-compound applicability = RestTimePolicy.isHeavyCompound (shared with manual rest). Ladder logs via ONE sequential coroutine (logWarmupRamp) — parallel logSet calls race on setNumber.
- **Effort→prompt (N4):** EffortTrend.promptBlock returns "" for label-free history so old-data prompts are structurally unchanged; wired inside AiRepository.buildSessionHistory (Room-coupled — the live-gen JVM harness CANNOT exercise it; that's why 0 live calls were spent, per the brief's unit-tests-only scope).
- **Wrapped (B7):** MonthlyWrapped is pure over (sessions, allSets, achievements, weighIns); month = logical date; current month excluded from availableMonths. Home ready-card gate = prefs.wrappedSeenMonthKey vs readyMonthKey. Surface is a full-screen DialogFragment (no nav-graph change).
- **Backup v6:** goals merge = identity(exercise,target,isE1rm,createdAt) + achieved-state-wins; notes merge = name + latest-updatedAtMs-wins. TWO version-pin tests exist (BackupV4PrefsTest, E2BackupProgramsTest) — every bump must touch both.
- **Widget (B10):** reads UserStats.currentStreak (never recomputes); LogWorkoutFragment.startCompletionFlow nudges requestRefresh.

**Traps confirmed this round:** Epley.estimate(w,1) = w×(1+1/30), NOT w — don't hand-compute test expectations, derive via Epley. H5DispatcherTest still flakes in full runs, passes isolated. `✕`-style escapes are valid in Android XML string attrs (aapt2 resolves them).

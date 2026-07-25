---
name: wave4-feature-batch
description: Wave 4 (E1 manual program editing: edit/delete/add/reorder planned_exercises on Program tab) — design, seams, dispatched 2026-06-24; FINAL wave
metadata:
  type: project
---

Wave 4 = E1, the FINAL wave of feature-batch-2026-06 (see [[wave3-feature-batch]], `brief-E1-manual-program-editing.md`). ONE worker. User ASLEEP, full autonomous authorization. E1 builds DIRECTLY on E2's settled program/storage model (Room=13, NO further schema bump expected).

**Brief acceptance ("Done when…"):** on Program screen the user can (1) EDIT a planned exercise's sets/reps/targetWeight/notes, (2) DELETE a planned exercise from a day, (3) ADD a new exercise to a day, (4) REORDER exercises within a day; edits PERSIST + reflect everywhere the plan shows (Program tab, Home today-view, guided logging); regen REPLACING manual edits is ACCEPTED (assumption K = all four ops; keep simple, confirmed).

**Settled seam map (verified in main tree at dispatch, 2026-06-24):**
- `PlannedExercise` entity unchanged needed: id, weekStart, dayOfWeek, orderInDay, exerciseName, sets, targetReps, targetWeightKg, notes, isLogged/actual*, rationale, programId. Room=13.
- Repo (WorkoutRepository.kt) primitives E1 reuses — NO new schema, NO new repo plumbing needed beyond what exists:
  - `updatePlannedExercise(ex)` (336) → @Update for in-place EDIT.
  - `saveDayPlan(weekStart, dayOfWeek, exercises)` (306) → program-scoped delete-day + re-insert; PRESERVES week rationale; assigns programId. This is the natural primitive for DELETE/ADD/REORDER: pass the full new ordered day list, it re-keys orderInDay by list index. Requests backup.
  - `getPlannedForWeek/getPlannedForDay` (290/283) → program-scoped via flatMapLatest(observeActive).
- Program tab is the editing surface: `ProgramViewModel` (already has logExercise/swapExercise/regenerateDay patterns), `ProgramFragment` (selectedDay UI, `inflateDayOverviewCard` renders each ex, `showRegenerateDayDialog` is the dialog pattern to mirror). Day's exercises = `selectedDayExercises` StateFlow.
- Home today-view: `HomeViewModel.getPlannedForDay(thisMonday(), currentDayOfWeek())` (136) → reflects edits automatically (same program-scoped flow). NO Home code change needed for persistence to show.
- ADD picker source = `ExerciseCatalog.entries` (data/ExerciseCatalog.kt; also drives E3 library). `orderInDay` is assigned by list index (AiRepository:954 pattern).

**Design CHOSEN (minimal, on existing seams):** EDIT = updatePlannedExercise(copy(sets/targetReps/targetWeightKg/notes)). DELETE/ADD/REORDER = mutate the day's ordered list in VM then `saveDayPlan(thisMonday(), day, newOrderedList)` (re-keys orderInDay, preserves rationale, program-scoped). New PlannedExercise for ADD: weekStart=thisMonday(), dayOfWeek=day, sensible defaults (sets/reps/weight/notes from dialog). NO new DAO, NO migration.

**Wave 4 gate (all 4):** debug+release build via ./build.sh (md5); ./build.sh test counts from result XMLs + name new E1 tests + all prior green; on-device ui-test-worker for edit/delete/add/reorder + persistence (Program→Home today-view + survive nav/restart-until-regen); seams coherent (Room still 13, no API touched, tree coherent). NO commits/tags/releases/staging.

**WORKTREE GOTCHA (carry from Wave 3):** if worker branches from a stale commit (db495ec v1.7.0 = pre-Waves), it must sync Waves1-3 working-tree files in first; lead then INTEGRATES the E1 delta into MAIN tree (the real baseline) and re-verifies build+test THERE. Confirm worker's base commit.

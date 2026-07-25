---
name: restday-and-regen-seam
description: B08 rest-day mode + B09 preserve-logged regenerate — design rationale and the data-safety guarantee on the AiRepository generation seam
metadata:
  type: project
---

Refinements batch (post-v1.9.1, branch `refinements-2026-06`) added two features on the hot
generation seam. Non-obvious design facts worth remembering:

**B08 rest-day mode** is keyed entirely off ONE pref, `PreferencesManager.restDaysCsv` (no extra mode
flag): NON-BLANK ⇒ rest-day mode (user picked rest weekdays, training = complement, days/week
DERIVED); BLANK ⇒ count mode (pre-B08). This makes migration safe automatically — existing users have
a blank CSV ⇒ keep their count behaviour until they opt in (the ratified migration choice). Pure logic
lives in `domain/TrainingDaySelection` (`effective()`, `scheduleViolation()` — both unit-tested). The
generator enforces rest days via a deterministic, retryable check inside the loop (not just the prompt).

**B09 preserve-logged regenerate.** The "logged day" signal is `PlannedExercise.isLogged` (set in
`LogWorkoutViewModel.completeWorkout()` for planned exercises whose name matches a logged working set).
A single logged exercise locks the whole day. Pure logic in `domain/RegeneratePlanner`.

**THE DATA-SAFETY GUARANTEE (load-bearing):** logged SETS + workout HISTORY live in
`workout_sessions` / `workout_sets`. The plan lives in `planned_exercises`. `savePlan` /
`saveDayPlan` / `savePlanPreservingLoggedDays` ONLY ever touch `planned_exercises` — they never
reference the session/set tables. So NO regenerate path can delete logged history; it is structural,
not just careful coding. This is why B09's hard constraint is satisfiable.

**Generation approach (full-week-echo):** `generateAdaptedProgram` gained `restDays: Set<Int>` and
`lockedExercises: List<PlannedExercise>`. For preserve-regen the model is told the logged days
(reproduce verbatim) and asked for the full week; we then DISCARD the model's echo of logged days and
persist only the non-logged days (`savePlanPreservingLoggedDays` keeps logged days' real rows). So
even if the model mangles a locked day, real logged rows are untouched. The deterministic duration +
rest-day checks SKIP locked days (exemptDays) so a logged-then-marked-rest day can't cause a stall.

**Routing:** Program-tab "Regenerate (keep logged days)" → `ProgramViewModel.regeneratePreservingLoggedDays`
(preserve). Settings → AI & Program → "Generate Now" → `SettingsViewModel.doGenerate` is now the
FULL-FRESH path (replaces all days, deload-aware). The deload/mesocycle computation was factored into
`WorkoutRepository.buildRegenMesocycle(monday)` shared by both VMs. All 4 generate call sites
(MainActivity auto, SetupWizardVM, SettingsVM, ProgramVM preserve) thread restDays via
`TrainingDaySelection.effective(prefs.restDaysCsv, prefs.daysPerWeek)`.

Related: [[generator-call-sites]], [[time-estimator-shared-helper]] (same AiRepository seam).

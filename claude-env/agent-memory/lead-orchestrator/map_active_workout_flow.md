---
name: map-active-workout-flow
description: File map for the active-workout / log / persistence / swap / PR / explanation flow
metadata:
  type: reference
---

Active "Log a workout" flow (the surface most batch fixes touch):

- `ui/log/LogWorkoutFragment.kt` — the screen. Owns set-entry UI, nav buttons (Back/Skip/Next in bottom bar of `res/layout/fragment_log_workout.xml`), swap dialog (`showSwapDialog`/`updateExerciseDisplay`), exercise image + chips, calls `ExerciseInfoBottomSheet` on name tap. `saveCurrentValues()` pushes entered weight/reps into VM in-memory maps.
- `ui/log/LogWorkoutViewModel.kt` — session/plan/index state. `_savedWeights`/`_savedReps` are IN-MEMORY maps (lost on process death → root of weight-revert bug). `loadGuidedPlan()` sets `_currentIndex` to first un-logged exercise (root of exercise-1-vs-later resume asymmetry). `swapCurrentExercise()` only copies `exerciseName` (leaves old sets/reps/weight/dbId/notes → swap-not-fully-updating bug). `skipExercise()` just calls `nextExercise()`.
- `ui/log/ExerciseInfoBottomSheet.kt` — the explanation window. Built programmatically; only receives `name` + `dbId`. Per-exercise AI note lives in `PlannedExercise.notes` (NOT passed in yet → that's the "add AI note" enhancement).
- `data/repository/WorkoutRepository.kt` + `data/db/dao/WorkoutSetDao.kt` + `WorkoutSessionDao` — persistence. Sets saved immediately via `addSet`. Active session = the one row with isCompleted=0.
- `data/repository/GamificationRepository.detectPersonalRecords()` — PR detection. Uses `getPreviousMaxWeight(name, sessionId) ?: 0f` then `currentMax > prevMax` → first-ever performance (prevMax null→0) wrongly counts as PR. Only weight PRs are gamified; recap `isPrThisSession` in WorkoutRepository.buildSessionRecap uses `getMaxWeightBefore`. ExercisePrWithDate (WorkoutSetDao) feeds Stats/Progress PR display.
- `ui/home/HomeFragment.kt`/`HomeViewModel.kt` — "Resume Workout" button navigates to log with the active session id (the resume-routing fix — don't regress).
- `data/CalisthenicsProgressionMap.kt` — easier/harder swap variant lists (families, easiest→hardest).
- `data/db/entity/PlannedExercise.kt` — per-exercise plan row: sets/targetReps/targetWeightKg/notes(AI cue)/recommendedRestSeconds/exerciseDbId/orderInDay. Swaps are in-memory only (DB plan not modified).

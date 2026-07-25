---
name: project-log-screen-clusters
description: File-overlap clusters for the active-workout "Log" screen — which files multiple change-items collide on (for safe parallel dispatch)
metadata:
  type: project
---

The active-workout "Log" screen concentrates many features into a few files, so most Log-screen changes COLLIDE and must be serialized or co-assigned, not run as blind parallel agents.

Hot files (edited by many items):
- `ui/log/LogWorkoutFragment.kt` — set entry, navigation (Back/Next), swap dialog, quick-access menu wiring, explanation window launch, draft save on pause. Touched by Skip-removal, explanation-AI-note, persistence wiring, quick-access.
- `ui/log/LogWorkoutViewModel.kt` — session load/resume, draft persistence, swap, jump, add-exercise, plus pure testable helpers `resumeIndexFor` / `insertAfter`. Touched by persistence, quick-access, swap.
- `ui/log/ExerciseInfoBottomSheet.kt` — the explanation window (built programmatically, no view-binding). `newInstance(name, dbId, aiNote?)`. Two call sites: LogWorkoutFragment (workout) and ProgramFragment (program tab — shows notes separately on its own card, so passes no aiNote).

Disjoint surfaces (safe to parallelize):
- PR-baseline rule lives in `data/repository/GamificationRepository.kt` via testable `isWeightPr(currentMax, previousMax?)` — only file for that item.
- `loggedAtMs` column on `WorkoutSet` (DB v10, MIGRATION_9_10) underpins resume-index ordering AND the separate Recap "pacing" feature — a shared dependency between persistence work and recap work.

**Why:** Confirmed during Batch-2 orchestration by reading every modified file. **How to apply:** When planning Log-screen work, assume Fragment+ViewModel overlap; give those to one agent or sequence them. Pure logic is extracted into companion funcs specifically so it can be JVM-tested off-device (see [[reference-test-harness]]).

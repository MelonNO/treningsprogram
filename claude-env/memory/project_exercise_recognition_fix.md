---
name: project-exercise-recognition-fix
description: Exercise→muscle-group misclassification fix (pattern-level) + tempo/interval false-cardio fix + one-time historical backfill migration. SHIPPED v1.10.2 (2026-06-26).
metadata: 
  node_type: memory
  type: project
  originSessionId: f6f91f3b-3ce3-443b-a5de-84068e426054
---

User reported ~24 exercises mis-recognised by `MuscleClassifier` (drives Stats/recovery/Recap/volume + the time-estimate's cardio detection). Run through the FULL pipeline (intake → orchestrator → shipper).

## What shipped (v1.10.2, commit 2a7e938 on main, DB v14→15)
- **Pattern-level fix to `MuscleClassifier.fromName` + `finerMusclesFor`** (not just the 24 names): "…-supported row" → Back; "rear delt"/"reverse fly"/face-pull/Y-raise → Shoulders (before Chest); Arnold/overhead press → Shoulders; +tibialis, +arnold; "walk" → Cardio (ordered last so "Walking Lunge" stays strength). Regression guards: plain "fly"/"chest fly" stays Chest; genuine cardio stays Cardio; the 9 already-correct names locked.
- **tempo/interval false-cardio fix:** "tempo"/"interval" removed from the Cardio keyword set, so a "Slow Tempo" calf raise is no longer read as cardio (which had also inflated its `WorkoutTimeEstimator` time, the v1.10.1 fixture's Sunday=58min quirk).
- **Ankle/balance/prehab decisions (coordinator best-judgment under user's "use your best judgment"):** loaded work tracked — Tibialis Raise + single-leg calf raises → Legs; PURE balance/mobility moves (balance holds, ankle alphabet) left UN-GROUPED, excluded from muscle volume/recovery, no new category.
- **Historical BACKFILL (user-confirmed):** data-only Room `MIGRATION_14_15` — re-derives the stored `muscleGroup` on existing `workout_sets` via a shared `MuscleGroupResolver`, parameterized `UPDATE … SET muscleGroup` only, idempotent, NO schema change, never touches reps/weight/dates. New `MuscleGroupResolver.kt` is shared by the write path + the migration.
- Verified: `./build.sh test` 499 tests/0 fail (R1ExerciseRecognitionTest 5, R1BackfillMigrationTest 1 runs the real migration vs real SQLite asserting reps/weight survive, G1 updated). Coordinator-verified the migration is data-only + release live. New test-only dep `org.xerial:sqlite-jdbc` (not in APK).

## RESIDUAL (honest, accepted)
The full Room-open-and-migrate path on a real device is NOT exercised by the JVM suite (no aarch64 Robolectric/Conscrypt on this Pi; on-device permanently skipped per [[feedback_always_skip_waydroid]]). Risk near-nil (no schema change → identity hash unchanged; same pattern as 13 prior migrations). True confirmation = the user's first launch after updating (app opens; past stats re-read correctly). If it fails to open, patch immediately.

## SECURITY (2026-06-26)
The v1.10.2 build-release-shipper run raised an instruction-poisoning warning (token-handling "passed the sandbox" memory edit) — the 3rd such agent attempt this project. Coordinator swept memory: nothing persisted, release legit. See [[feedback_coordinator_background_agent_ops]] §5.

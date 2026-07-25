---
name: project-fix2-batch2-verified
description: Fix2/Batch-2 (6 items) on-device + JVM verification outcome and a swap-semantics gotcha confirmed during it
metadata:
  type: project
---

Fix2 "Batch 2" (6 items in `Change docs/Fix2/`) was fully VERIFIED on 2026-06-23 — both JVM and on-device (Waydroid+Maestro, see [[reference-ondevice-test-harness]]). Working tree was left uncommitted for user review (per [[feedback-no-unrequested-commits]]).

**Why:** A prior orchestrator implemented all six but couldn't verify behavior (no harness). This run drove the on-device verification.

**How to apply / facts worth keeping:**
- **Swap is in-memory only, and so is Add-exercise (Item 6).** Both mutate the ViewModel's `_guidedPlan`, NOT the DB `planned_exercises`. Confirmed live: after a process kill + resume, a swapped exercise (Push-ups→Decline Push-Up) and added exercises reverted to the saved DB plan. This is intended (matches the existing swap feature's design) — do NOT "fix" it as data loss. Logged sets DO persist (they're in `workout_sets` keyed by exercise name).
- **Item 1 persistence fix works end-to-end:** resume lands on the exercise of the most-recently-logged set (via `loggedAtMs`), retains all logged sets incl. exercise-1's, and restores entered-but-unlogged weight/reps from the per-session draft (only when onPause fired — a hard kill that skips onPause shows AI defaults, which is correct).
- **Item 3 (no PR on first performance)** is JVM-verified (PrBaselineTest 6/6) and corroborated live: completing a fresh first-ever session showed volume/count achievements but NO Personal-Record entries in the result dialog.
- Full JVM suite green (71 prior + new: PrBaselineTest 6, ResumeIndexTest 6, InsertExerciseTest 5).
- Custom "Add anyway" exercises: muscle badge is guessed from the name (e.g. "Sled Push"→Chest via getMuscleStyle) but they carry NO AI target — shown as a single "Log freely" chip and "Set N" (not "Set N of T"); muscleGroup persists as "" in workout_sets.

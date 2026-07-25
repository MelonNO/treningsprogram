---
name: exercise-recognition-fix-2026-06
description: Post-v1.10.1 single-item bug batch fixing MuscleClassifier name-based misclassification of 24 exercises + tempo/interval false-cardio; docs at docs/intake/exercise-recognition-fix-2026-06/
metadata:
  type: project
---

Post-v1.10.1 intake: "fix the exercise recognition" for 24 named exercises mis-attributed by `data/MuscleClassifier.kt` (name-substring keyword classifier). Briefs at `docs/intake/exercise-recognition-fix-2026-06/` (INDEX + brief-R1 + fixture-expected-classification.md, a 24-row table that doubles as a test fixture).

**Why:** `MuscleClassifier.fromName` matches substrings in fixed precedence; this drives muscle attribution across Stats / recovery / Recap / per-muscle volume AND the time estimate (`WorkoutTimeEstimator.isCardio` → `displayName` → `fromName`). Failure modes confirmed: (a) chest-supported row → Chest (word "chest"), rear-delt/reverse fly → Chest (word "fly"); (b) Arnold Press/Tibialis/Incline Walk fall through to blank; (c) tempo/interval in a strength name → Cardio (inflates time estimate too). Note `finerMusclesFor` (recovery view) and the display badge are SEPARATE rule sets — all must agree for cross-surface consistency.

**Settled (coordinator best-judgment under explicit user delegation — NOT direct user words; recorded as such in INDEX):**
- Pattern-level fix (not literal-string-only); include tempo/interval false-cardio fix. 24 names = concrete examples + regression guards.
- Pure balance/proprioception/mobility moves (balance holds, ankle alphabet) → stay UN-GROUPED ("Training"), excluded from volume/recovery, display gracefully. NO new muscle category (avoid ripple into colors/recovery/stats/muscle-picker/AI prompt).
- Loaded lower-leg work counts: Tibialis Raise → Legs; single-leg calf raises stay Legs(Calves).
- Rear-delt ROW ("…Rear Delt Row") → Shoulders (target-muscle naming); plain "…-supported row" → Back.
- Regression guards baked into acceptance: plain/chest/incline "fly" stays Chest (only rear-delt/reverse → Shoulders); presses stay Chest under the row fix; genuine cardio stays Cardio; 9 already-correct names locked.

**How to apply:** if a future request touches exercise classification, reuse this fixture as the guard set and remember the cross-surface (fromName + finerMusclesFor + displayName) consistency requirement and the time-estimate coupling.

**FLAGGED deferred decision (not in Q-A–Q-D):** muscle group is stored at SET-WRITE time, so the fix is forward-only unless a backfill re-derives already-logged sets. Surfaced in INDEX + brief; orchestrator/user to decide.

# Intake Index — Exercise Recognition Fix (2026-06)

**Prepared for:** Project-lead orchestrator
**Source:** User request "fix the exercise recognition" for 24 named exercises mis-attributed by the name-based muscle-group classifier, following a finding logged during the v1.10.1 fix.
**Repo state at intake:** clean working tree on `main` at v1.10.1.
**Status:** READY to dispatch (single-item batch).

## Confirmation note (provenance — read first)

The per-question decisions below were made by the **coordinator under the user's explicit delegation** ("use your best judgment" on Q-A–Q-D). They are **NOT the user's own verbatim words.** Treat them as settled working decisions, but if the orchestrator surfaces a contradiction with observed behaviour, the user has not personally adjudicated these and a round-trip may be warranted.

Creating these docs is not itself a dispatch instruction — dispatch proceeds per the standing auto-dispatch arrangement relayed by the coordinator.

## Items

| ID | Title | Type | Brief file | Status |
|----|-------|------|------------|--------|
| R1 | Exercise-name → muscle-group recognition (pattern-level fix + tempo/interval false-cardio) | Bug | `brief-R1-exercise-recognition.md` | READY |

Companion fixture (expected-classification table + pattern regression guards): `fixture-expected-classification.md`.

## Merge / cluster + parallelization

- **Single unit, single worker.** This is one cohesive change to one mechanism — the name-based classifier. The per-exercise corrections, the "…-supported row" / "rear-delt fly" pattern corrections, and the tempo/interval false-cardio correction all live in the same classification path and would collide if split across workers. **Do not split.**
- No cross-item seams; nothing else in this batch.

## Confirmed decisions (coordinator best-judgment, under user delegation)

1. **Mapping (Q-A):** the 24 names resolve to the groups in the fixture table. Incline Walk → Cardio. The 9 names already correct today are **locked as regression guards**, not changed.
2. **Ankle/balance/prehab cluster (Q-B):**
   - Pure balance / proprioception / mobility moves — **Hand-Supported Single-Leg Balance Hold (Wall Touch)**, **Standing Ankle Balance Hold (Ankle Prehab)**, **Standing Single-Leg Ankle Balance Hold (Hand on Wall)**, **Ankle Alphabet / Foot Circles (Seated)** — stay **un-grouped** (recognised as non-muscle "Training"/mobility work, **excluded** from muscle volume and recovery, displayed gracefully). **No new muscle category is created.**
   - **Tibialis Raise** → **Legs** (loaded lower-leg strengthening — counts).
   - The two single-leg **calf raises** stay **Legs (Calves)** — real calf loading, already correct.
3. **Rear-delt row (Q-C):** **Chest-Supported Rear Delt Row (Wide Elbows)** → **Shoulders** (target-muscle naming, consistent with the other rear-delt moves). The plain **Chest-Supported Dumbbell Row** → **Back**.
4. **Scope (Q-D):** **pattern-level fix**, not literal-string-only. Acceptance is written against the underlying name patterns with the 24 names as concrete examples, and **includes the tempo/interval false-cardio fix** (a strength move named "Tempo Squat" / "Interval Lunge" must classify by its movement, not Cardio — which also removes the time-estimate inflation).

## Assumptions applied (user may override)

- **A1 —** The 9 currently-correct names are to be preserved exactly (regression guards), not re-discussed.
- **A2 —** "Un-grouped" for the four balance/mobility moves means the existing neutral "Training" treatment (blank stored group, excluded from `WHERE muscleGroup != ''` stats), not a new visible label.

## Deferred decision — FLAGGED for the orchestrator/user (new, not covered by Q-A–Q-D)

- **Historical backfill.** Muscle group is resolved and stored at **set-write time** (denormalised onto each logged set). A classifier fix therefore corrects **newly logged** sets only; sets already logged under a wrong group keep their stored value unless deliberately re-derived. **Decision needed:** correct going-forward only, or also backfill existing mis-grouped sets so past Stats/Recap/volume retroactively read correctly? This was not part of the coordinator's Q-A–Q-D decisions. Captured in the brief under "Decisions deferred"; recommend the orchestrator confirm before assuming one or the other.

## Cross-cutting constraints (every brief)

- Build with `./build.sh` (not `./gradlew`).
- No commits or releases unless explicitly asked.
- No on-device / automated UI tests unless explicitly asked.

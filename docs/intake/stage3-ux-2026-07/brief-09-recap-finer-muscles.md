# Brief 09 — Recap: "Muscles hit this session" gets specific (triceps, not arms)

**Type:** Feature
**Cluster:** H1 (Recap overhaul: 3 → 9 → 14 → 10) — one worker, after item 3.

> Outcome-only brief. No confirmation round was possible (user asleep); assumptions are labelled.

## Context
Recap's "Muscles hit this session" card shows working-set counts per **coarse** muscle group ("Arms", "Legs" — `SessionRecap.muscleVolume` from the stored `muscleGroup`). The app already has a finer taxonomy: the Home recovery panel resolves exercises to fine labels (triceps, biceps, quads, hamstrings, front delts…) via the classifier's finer-muscles mapping (`MuscleClassifier.finerMusclesFor`, used by `HomeViewModel.buildWeightedRecoveryItems`).

## What the user wants (end result)
The Recap card lists the **specific muscles** hit — e.g. "Triceps 6 sets · Chest 9 sets · Front delts 3 sets" — instead of coarse groups. The user's explicit carve-out: the **Stats tab's muscle balance stays high-level** — no change there.

## Acceptance criteria
- Done when a session of bench + triceps pushdowns shows e.g. Chest / Triceps / Front delts rows, not "Chest / Arms".
- Done when the counts still reflect working sets (warm-ups excluded) and the bars still scale correctly.
- Done when the fine labels agree with the recovery panel's labels for the same exercises (one taxonomy, no contradictions between screens).
- Done when Stats-tab muscle balance is untouched.
- Label derivation unit-tested off-device.

## Scope and constraints
- **In scope:** the Recap muscles card's granularity.
- **Out of scope:** Stats muscle balance (explicit user instruction); the stored `muscleGroup` data; recovery panel.

## Assumptions (user may veto)
- **A-09a:** an exercise contributing to multiple fine muscles counts toward its primary emphasis (same weighting philosophy as the recovery panel), with sensible whole-set rounding — the card stays readable, not fractional.

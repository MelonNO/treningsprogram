# Brief B1 — Warm-up ramp suggestions for heavy compounds

**Type:** Feature (training optimality)
**Cluster:** Logging-screen pair with N7 (same files — ONE worker, B1 first)

> Outcome-only brief: describes the end result and user experience. The "how" belongs to the orchestrator and its workers.

## Context
Warm-up sets exist as a first-class concept (warm-up chip on the logging screen; `WorkoutSet.isWarmup`; excluded from PRs/strength/volume), and the app knows exactly what is loadable on the user's equipment (`PlateMath` + the active GymPreset — 50 mm home bar at 7 kg, specific plate pairs, loadable dumbbells). But the user must invent the warm-up ladder themselves every session. Parked last round only because the logging screen was contended; it no longer is.

## What the user wants (end result)
1. For a heavy compound with a real working weight, the logging screen offers a concrete warm-up ladder — e.g. ~40% / ~60% / ~80% of today's working weight — with each step **rounded to a weight actually loadable** on the active gym preset.
2. Accepting the ladder takes one tap: the suggested sets are logged as warm-up sets (existing chip semantics), ready to check off/adjust like any set.
3. The suggestion is an offer, not a nag: easy to ignore or dismiss, never blocking, and it never auto-logs anything.
4. Exercises where a ramp makes no sense (isolation, bodyweight, cardio) get no suggestion.
5. Warm-up sets created this way behave exactly like manually logged warm-ups everywhere downstream (no PR/volume/strength/recovery contamination).

## Acceptance criteria
- Done when a heavy compound with a working weight shows a ladder whose steps are loadable on the active preset (spot-verified against `PlateMath` in unit tests, including the 50 mm home-bar profile).
- Done when one tap logs the ladder as warm-up sets and they appear with the warm-up chip, editable/deletable as normal.
- Done when isolation/bodyweight/cardio exercises show no suggestion, and an exercise with no known working weight shows none.
- Done when ignoring the suggestion leaves zero trace in the logged session.
- Done when switching gym preset changes the rounding accordingly.
- Ladder math (percent steps, rounding, applicability) unit-tested.

## Scope and constraints
- **In scope:** suggestion + one-tap logging on the logging screen.
- **Out of scope:** warm-ups in the generated plan/prompt; changing warm-up semantics anywhere downstream; per-exercise ramp customization UI (v1 is one sensible policy).
- No DB schema change, no live API.
- Standing: build via `./build.sh`; no commits/releases unless asked; no on-device/automated UI tests.

## Assumptions (user may override — ramp philosophy was an open question)
- **A-W1:** default ladder ≈ 40% × 5, 60% × 3, 80% × 2 of today's working weight, skipping steps that round below the empty bar; very light working weights produce a shorter ladder or none.
- **A-W2:** "heavy compound" applicability reuses the same exercise classification the rest-time categories already use (Heavy compounds vs Accessories), so the two features agree on what "heavy" means.
- **A-W3:** the working weight used is today's planned/prefilled weight for the first working set (adjusting it re-offers an updated ladder until a warm-up is logged).

## Considerations for whoever builds it
- Same worker and surface as N7; land B1 first (bigger UI footprint), N7's quiet line after.
- The logging screen already carries many per-exercise elements (last-time line, Beat chip, spec chips, rest UI, plate readout) — the suggestion must collapse/disappear once warm-ups are logged.

# S1 — Sweep: Home (today) & Workout logging flow

**Type:** Bug sweep (diagnose-first)
**Phase:** 1 (bug sweep & fixes)
**Cluster:** SW-A (logging path) — see INDEX.
**Outcome-only:** Defines *where to look* and *the bar to hold*, not specific fixes. Workers diagnose and fix; the orchestrator owns the "how".

---

## Context

The core daily loop: `ui/home` (today's plan, Start Workout) and `ui/log` (LogWorkout — set entry, rest timer, Complete Workout, workout draft restore). `WorkoutSet` is denormalized (`exerciseName` string). A `workoutDraftJson` preference persists an in-progress workout. This is the highest-traffic, highest-data-loss-risk path in the app.

> **Cross-reference (concrete user-tested items):** Home is also changed by **U3 (Home reorder)** and **U1 (recovery panel rework)**. This sweep should verify the **final, reordered Home** with the **reworked recovery panel** — not the old layout/behavior. Coordinate so S1 doesn't sweep behavior that U1/U3 are replacing.

## The hunt (where to look)

- **Home/today:** today's exercises render correctly for all states (no plan yet, rest day, plan exists, mid-mesocycle, deload week); Start Workout entry point; the C4 recovery panel on Home (shipped v1.8.0) under empty/insufficient data.
- **Logging flow:** entering/editing sets (weight, reps, warm-up flag, notes), rest timer behavior, adding/removing sets and exercises mid-session, Complete Workout, and **draft restore** after backgrounding/process death.
- **Data integrity:** logged sets must never silently disappear on back-navigation, rotation, backgrounding, or process death; warm-up flag must round-trip; completion must persist exactly what was entered.

## Acceptance bar (ALL tiers apply)

This sweep holds the **full severity bar**: crashes & data-loss, functional-correctness errors, visual/layout glitches, and minor polish/inconsistencies. Nothing is too small to log.

## Adversarial / edge states to exercise (on-device)

Rotation mid-entry; background→restore mid-workout; process death + draft restore; rapid taps on Start/Complete/Add-set; no-network; no-API-key; empty/first-run (no plan, no history); double-tap Complete; very large set counts.

## Acceptance criteria ("Done when …")

- **Done when** the Home/today and logging flows have been driven through the adversarial states above on-device **and** code-reviewed, with every finding recorded against the all-tiers bar.
- **Done when** no logged-set data is lost across back-nav, rotation, backgrounding, or process death, and draft restore reproduces the in-progress workout faithfully.
- **Done when** each confirmed defect is **diagnosed to root cause before any fix** (no symptom-masking), then fixed and re-verified on-device.
- **Done when** Home/today renders a sensible state for no-plan, rest-day, deload-week, and first-run.

## Scope & constraints

- **Diagnose first** — this brief does not pre-list defects; it defines the surface and the bar.
- **In scope:** the Home/today screen, the logging flow, draft persistence/restore, the C4 Home recovery panel's robustness.
- **Cross-cutting:** build via `./build.sh`; no git commits/releases unless the user asks; on-device UI verification IS required for this batch (see SEQUENCE.md).

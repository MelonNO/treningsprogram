# S2 — Sweep: Program tab (named programs, mesocycles, manual editing, deload)

**Type:** Bug sweep (diagnose-first)
**Phase:** 1 (bug sweep & fixes)
**Cluster:** SW-B (program & generation) — see INDEX.
**Outcome-only:** Defines *where to look* and *the bar to hold*. Workers diagnose and fix.

---

## Context

`ui/program` plus the v1.8.0 program features: **named programs + periodized mesocycles** (E2), **manual program editing** (E1), and **stall-triggered deload** (E2/B3). Backed by `Program` and `PlannedExercise` entities, `DayPlanEditor`, `DeloadPolicy`, `WorkoutRepository.savePlan/saveDayPlan/updatePlannedExercise`. This is fresh, complex surface shipped overnight with no dedicated adversarial sweep, and the heaviest logic in the batch.

## The hunt (where to look)

- **Named programs:** create, rename, select/switch, delete; behavior with zero programs, one program, many; which program is "active" and how that interacts with generation.
- **Mesocycles:** block progression across weeks; week boundaries; what the UI shows mid-block vs at block end; correctness of the periodization state.
- **Manual editing (E1):** edit sets/reps/weight/notes, delete, add, reorder — and the documented behavior that **manual edits last only until the next regeneration** (verify the user isn't surprised by silent overwrite; that's a UX candidate for Phase 2).
- **Deload:** stall/fatigue-triggered deload firing correctly (couples to B3 stall detection); deload week presentation; what happens if the deload trigger and a manual edit or regeneration collide.

## Acceptance bar (ALL tiers apply)

Full severity bar: crashes & data-loss, functional correctness (wrong plan state, lost edits), visual/layout, minor polish.

## Adversarial / edge states to exercise (on-device)

Switching programs mid-week; editing then regenerating; rotation/backgrounding mid-edit; reorder + delete in quick succession; deload week + manual edit; empty (no program/no plan); first-run; no-network/no-API-key during a generation that would touch the program.

## Acceptance criteria ("Done when …")

- **Done when** named-program, mesocycle, manual-editing, and deload behaviors have been exercised on-device through the adversarial states **and** code-reviewed, with every finding recorded against the all-tiers bar.
- **Done when** no program data or manual edit is lost unexpectedly (and any *intended* overwrite-on-regeneration is confirmed correct and flagged for the Phase-2 UX review if it surprises the user).
- **Done when** each confirmed defect is **diagnosed to root cause before any fix**, then fixed and re-verified on-device.
- **Done when** the Program tab renders sensible empty/first-run/deload states.

## Scope & constraints

- **Diagnose first.**
- **Coordination:** shares the program-storage seam with **S3** (AI generation) and the **P-1 timeout/retry** item; the deload couples to **S6/B3 stall logic**. Treat as one cluster with S3 (SW-B) to avoid blind concurrent edits to `WorkoutRepository`/`AiRepository`.
- **Cross-cutting:** build via `./build.sh`; no commits/releases unless asked; on-device verification required (SEQUENCE.md).

# E1 — Manual program editing

**Type:** New feature
**Cluster:** P2 (program-control) — same worker as E2, **sequenced after E2.**
**Outcome-only:** Describes the end result and user experience, not the implementation. The "how" is the orchestrator's.

---

## Context

Today the program is AI-owned: the user can generate, regenerate a single day, swap an exercise, and log against the plan (`ProgramViewModel.swapExercise/logExercise`, `WorkoutRepository.updatePlannedExercise/saveDayPlan`), but cannot freely edit the plan by hand. The repository already exposes the primitives needed (`updatePlannedExercise`, `saveDayPlan`, `deleteForDay`, `insertAll`; order is the `orderInDay` field). This feature gives the user **direct manual control** over their planned exercises — kept deliberately simple.

## What the user wants (end result)

The user can hand-edit their planned program: change an exercise's sets/reps/weight/notes, delete a planned exercise, add a new one, and reorder exercises within a day — without having to regenerate the whole week.

## Important behavioral decision (confirmed)

- **Manual edits are simple and temporary.** They last **until the next regeneration**, and it is **accepted that a regeneration overwrites them** (regeneration replaces the week's plan). The user explicitly chose to keep E1 simple rather than make manual edits survive regeneration. This must be **clearly true to the user** (no surprise data loss expectation) — the user knows a regen replaces hand edits.

## Acceptance criteria ("Done when …")

- **Done when** the user can, on the Program screen, **edit** a planned exercise's sets, reps, target weight, and notes.
- **Done when** the user can **delete** a planned exercise from a day.
- **Done when** the user can **add** a new exercise to a day.
- **Done when** the user can **reorder** exercises within a day.
- **Done when** these edits persist and are reflected wherever the plan is shown (Program tab, today's plan on Home, guided logging).
- **Done when** it is understood/acceptable that the **next regeneration replaces** the manually edited plan (no expectation that hand edits survive a regen).

## Scope and constraints

- **In scope:** edit (sets/reps/weight/notes), delete, add, reorder — within a day, on the Program screen.
- **Out of scope (unless the user later asks):** preserving manual edits across regeneration; editing across multiple weeks at once; a full drag-and-drop program builder beyond simple reorder.
- **Local-only:** no API, no schema change (operates over existing `planned_exercises` storage).
- **Cross-cutting constraints:** build via `./build.sh`; no git commits/releases unless the user asks; **on-device UI verification IS required for this batch** per the per-wave gate in `SEQUENCE.md`.

## Decisions baked in

- **Edits last only until the next regeneration; regen overwriting them is acceptable** (user-confirmed).
- **Keep it simple** (user-confirmed).

## Assumptions (user may override)

- **ASSUMPTION (scope, question K):** the simple version includes **all four** operations — edit, delete, add, **and reorder**. *(User may drop reorder if it is not wanted.)*

## Considerations for whoever builds it (surfaced, not decided)

- **Ordering dependency (critical):** per the INDEX, E1 must be built **after E2**. E2 introduces a new "program" entity and reworks how plans are keyed/selected; building E1 against today's single-`weekStart` model first would force a rework once E2 lands. Same worker (P2), sequenced E2 → E1.
- Both E1 and E2 touch `planned_exercises` storage and the Program tab — manage as one unit, not parallel workers.

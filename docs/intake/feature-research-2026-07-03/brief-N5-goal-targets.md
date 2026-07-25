# Brief N5 — Goal targets for the big lifts

**Type:** Feature (motivation, long-arc)
**Cluster:** Schema pair with N7 (shared DB-migration/backup seam); chart part lands AFTER N3 on Progress; Home nudge lands after N1

> Outcome-only brief: describes the end result and user experience. The "how" belongs to the orchestrator and its workers.

## Context
The app measures everything and aims at nothing: streaks and weekly challenges reward showing up, but there is no way to declare "100 kg bench by October" and watch progress toward it. The strength chart (e1RM trend), the R6 celebration surface, and Home's card stack all exist to hang this on. This is the batch's only genuinely new mechanic.

## What the user wants (end result)
1. The user can set a goal for a strength exercise: a target weight (or e1RM), with an optional target date. A small number of active goals at a time is the intended experience (this is focus, not a todo list).
2. The existing strength/e1RM chart for that exercise shows the goal as a target line, with a visible progress figure (e.g. "87% of the way to 100 kg").
3. Home gives a quiet nudge when a goal gets close (e.g. within one normal progression step), so the attempt is planned, not accidental.
4. Reaching the goal triggers a real celebration moment (reusing the workout-complete celebration language) and the goal is marked achieved — kept visible as history, not silently deleted.
5. Goals survive backup/restore like all other user data.
6. Editing and abandoning a goal is possible and unceremonious.

## Acceptance criteria
- Done when a goal can be created, edited, achieved, and abandoned, and each state is reflected on the chart and wherever goals are listed.
- Done when the target line + progress % render on the exercise's existing chart without disturbing the chart for exercises with no goal.
- Done when goal-reach is detected from a logged working set / resulting e1RM (per A-G2) and celebrated exactly once.
- Done when backup → restore round-trips goals (backup version bumped per the established step-registration pattern in `BackupModels`), and old backups without goals restore cleanly.
- Done when a fresh install with no goals shows no goal UI debris anywhere.
- Goal progress/reach logic unit-tested, including the no-weigh-in-needed path (goals are absolute weights, not BW-relative).

## Scope and constraints
- **In scope:** goal entity + storage + migration + backup, chart target line, progress display, Home nudge, reach celebration, goal management UI (entry point per A-G4).
- **Out of scope:** goal XP (A-G1); AI awareness of goals (A-G3 — deferred decision); BW-relative goals (N3's territory, chart-only); rep-count goals.
- **DB schema change** (new table) + **backup v5 → v6**. Coordinate with N7's schema change — single migration/backup seam (see INDEX).
- Standing: build via `./build.sh`; no commits/releases unless asked; no on-device/automated UI tests.

## Assumptions (user may override)
- **A-G1:** goal reach grants a celebration but **no XP** in v1 — a new XP source would drag in the StatsRecomputer merge-parity hazard for a first iteration.
- **A-G2:** "reached" = a logged working set meets/exceeds the target weight (or the session e1RM does, if the goal was set as e1RM) — same strictness style as PR detection; warm-ups never count.
- **A-G3 (DEFERRED DECISION, user asked):** the AI does **not** see active goals in v1. If the user says yes, that becomes a small follow-up prompt item under the same frugal-API rules as N4.
- **A-G4:** goals are created/managed from the exercise's Progress/detail context and listed on Profile; no new bottom-nav surface.
- **A-G5:** target date is optional flavor (shown as "by October") — no failure state or penalty when it passes; the goal simply stays active.

## Considerations for whoever builds it
- Touches three shared surfaces of this batch: Progress chart (after N3), Home (after N1), and the DB/backup seam (with N7). Sequencing per INDEX matters more for this item than any other.
- Celebration reuse should coordinate with the R6 `WorkoutResult`-driven flow rather than inventing a parallel celebration path.

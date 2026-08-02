# Brief 05 — Show final-set effort in the "Last time" line

Type: **Feature**
Cluster: independent (Group D)

> Outcome-only brief. Describes the end result and user experience — the "how" belongs to the orchestrator and its workers.

## Context

During a workout, each exercise shows a "Last time" line summarizing the previous session's working sets (`ui/log/LastSessionFormat.kt`, e.g. "Last time · 3 sets · 8 × 60 kg"). Per-set effort ratings already exist (`WorkoutSet.rpeLabel`, logged since the v1.25.0 effort feature) but are not surfaced here.

## What the user wants (end result)

The "Last time" line also shows the effort rating the user gave on the **final set** of that previous session. Confirmed: only the final set's effort, even when the sets differ from one another.

## Acceptance criteria

- Done when the line includes the previous session's final-set effort whenever that set has one (e.g. "3 sets · 8 × 60 kg · last set: Hard" — exact wording is delegated).
- Done when both the uniform-collapse form ("N sets · reps × weight") and the per-set form carry the effort.
- Done when a previous session whose final set has no effort recorded shows the line exactly as today — no empty suffix, no placeholder.
- Done when existing `LastSessionFormat` behavior is otherwise unchanged (covered by its unit tests).

## Scope and constraints

- In scope: the "Last time" line on the logging screen only.
- Out of scope: showing effort anywhere else; changing how effort is captured or stored; backfilling old sessions (A2: pre-effort sessions simply show no suffix).
- Standing: build via `./build.sh`; no commits/releases unless asked; no on-device tests unless asked.

## Decisions made under delegation (veto-able)

- D2: exact wording and placement of the effort suffix.

## Considerations for whoever builds it

- `LastSessionFormat` is a pure, unit-tested object — keep it pure so the new form is testable off-device.
- Item 04 adds a flag option inside the exercise-info sheet; different file, but both live in the log-screen area — coordinate merges if the same worker window touches `LogWorkoutFragment`.

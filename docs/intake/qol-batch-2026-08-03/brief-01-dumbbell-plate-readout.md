# Brief 01 — Dumbbell recognition in the per-side plate readout (general fix)

Type: **Bug**
Cluster: independent (Group C)

> Outcome-only brief. Describes the end result and user experience — the "how" belongs to the orchestrator and its workers.

## Context

The logging screen's keypad shows a live "per side" plate readout (`ui/log/PlateMath.kt`) telling the user which plates to load. For dumbbell lifts (when the gym profile has loadable dumbbell handles) the entered weight is one dumbbell's total, decomposed against the handle weight.

Whether a lift counts as a dumbbell exercise is decided purely from its name: it must contain "dumbbell" or a "DB" token (`PlateMath.isDumbbellExercise`). It is not a barbell lift either unless it matches a barbell hint.

## Current (incorrect) behavior

An exercise that is a dumbbell lift by nature but whose plan name lacks "dumbbell"/"DB" — e.g. "Zottman Curl" (the user's reported case), "Hammer Curl", "Lateral Raise", "Concentration Curl" — falls through both checks. The user gets **no per-side plate breakdown at all**; they only see the total weight they typed.

## Correct behavior

All exercises that are dumbbell lifts by nature receive the dumbbell per-side readout, exactly as explicitly-named dumbbell lifts do today, regardless of how the plan happens to name them. Confirmed general fix — not a Zottman-only patch.

## Diagnose first

The name-based gap is confirmed, but the worker should establish the *right general mechanism* (e.g. what authoritative signal says "this movement is dumbbell-by-nature") rather than hard-coding one more name. Beware false positives: lifts that legitimately have barbell/cable/machine variants must not be forced into dumbbell math.

## Acceptance criteria

- Done when logging "Zottman Curl" (no "dumbbell" in the name) shows the per-side dumbbell readout, given a gym profile with loadable dumbbell handles.
- Done when other dumbbell-by-nature lifts without "dumbbell"/"DB" in their names also get the readout.
- Done when explicitly-named dumbbell lifts, barbell lifts, and machine/cable/bodyweight lifts behave exactly as before (no regressions in existing classification tests).
- Done when a lift that is genuinely ambiguous or non-dumbbell is not misclassified into dumbbell math.

## Scope and constraints

- In scope: the classification feeding the per-side readout, plus unit tests covering the newly-recognized names.
- Out of scope: changes to the readout's math or formatting; gym-profile/preset semantics.
- Standing: build via `./build.sh`; no commits/releases unless asked; no on-device tests unless asked.

## Considerations for whoever builds it

- The app already has richer exercise knowledge (`ExerciseCatalog`, `ExerciseDbResolver` — which knows "zottman curl" maps to a dumbbell exercise DB entry) that may serve as the general signal.
- For dumbbell lifts the entered weight is ONE dumbbell's total — keep that convention.

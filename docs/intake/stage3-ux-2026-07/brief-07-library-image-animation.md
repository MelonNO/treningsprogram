# Brief 07 — Exercise library: animate both exercise images like in a workout

**Type:** Feature
**Cluster:** Standalone (library) — own small worker.

> Outcome-only brief. No confirmation round was possible (user asleep); assumptions are labelled.

## Context
The bundled exercise database ships **two images per exercise** (start/end position). During a workout both are used: the exercise-info sheet (and the log screen's image) alternate the two frames on a timer, reading as a simple animation. The library's exercise **detail screen** (`ExerciseDetailFragment`) loads only frame 0 as a static image, wasting the second frame.

## What the user wants (end result)
In the exercise library, an exercise's images play the same **two-frame alternation** as during a workout, so the movement reads as an animation instead of a frozen pose.

## Acceptance criteria
- Done when the library detail screen alternates the exercise's two frames at the same cadence/feel as the workout surfaces.
- Done when an exercise with only one image (or none) degrades exactly as today (static image / hidden).
- Done when leaving the screen stops the animation cleanly (no leaked timers, no crash on fast exit).
- Done when scrolling the library list stays smooth (see A-07a).

## Scope and constraints
- **In scope:** the library detail screen's image area.
- **Out of scope:** new image assets; the workout surfaces (already correct).

## Assumptions (user may veto)
- **A-07a:** the animation applies to the **detail screen** ("in the exercise library … like how it is shown during a workout"); the library *list* thumbnails stay static — animating dozens of list cells would hurt scroll performance for no informational gain.

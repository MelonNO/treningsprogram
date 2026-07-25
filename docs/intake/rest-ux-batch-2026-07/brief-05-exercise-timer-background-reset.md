# Brief 05 — Per-exercise timer resets when the app is minimized

**Type:** Bug
**Cluster:** Standalone (logging screen) — coordinate with Brief 02's worker only because both touch `LogWorkoutViewModel.kt` (different regions).

> Outcome-only brief: describes the end result and user experience. The "how" belongs to the orchestrator and its workers.

## Context
The logging screen shows a small "time on this exercise" readout (`tv_exercise_time` in `fragment_log_workout.xml`, fed by `LogWorkoutViewModel.currentExerciseElapsedMs`). The overall workout timer on the same screen behaves correctly across minimizing.

## Current (incorrect) behavior
Every time the user minimizes the app (or otherwise leaves it briefly) and comes back mid-workout, the per-exercise timer restarts from zero, losing the real elapsed time on the current exercise.

## Diagnostic context (verified in code — worker should confirm, not re-guess)
The elapsed value is derived from a "now minus start" loop whose start moment is re-captured whenever the flow restarts: it is shared with `SharingStarted.WhileSubscribed(5000)` and rebuilt via `flatMapLatest` on the current-exercise index, so backgrounding longer than ~5 s cancels the upstream flow and resubscription captures a fresh `System.currentTimeMillis()` as the start. The session-level `elapsedTimeMs` does not reset because it derives from a stored start timestamp (`_sessionStartMs`). Confirm this is the whole story before fixing.

## Correct behavior (end result)
The per-exercise timer tracks **real wall-clock time since the exercise became the current one**, and the user chose the **robust** version:

- Minimizing the app and returning — for any duration — shows the true elapsed time (it kept "counting" while away).
- Surviving Android killing the app process in the background mid-workout: when the user reopens and lands back in the same in-progress workout on the same exercise, the timer still shows the true elapsed time since that exercise became current.
- Switching to the next/previous exercise still resets the timer, exactly as designed today.

## Acceptance criteria
- Done when: start an exercise, minimize for >1 minute, return → the timer shows ≥1 minute more, not a restart from 0:00.
- Done when: with the workout in progress, the process is killed in the background (e.g. developer "kill app" / system reclaim), the app is reopened and the same in-progress workout+exercise is resumed → the timer reflects total real time since that exercise became current.
- Done when moving between exercises still resets the timer per exercise (no regression).
- Done when the overall workout timer's existing correct behavior is untouched.
- Unit-test coverage for the timing logic where it is unit-testable off-device; on-device behavior is verified by the user (no Waydroid/automated UI tests).

## Scope and constraints
- **In scope:** the per-exercise elapsed readout's correctness across backgrounding and process recreation.
- **Out of scope:** any visual change to the readout; the rest timer (separate machinery, Briefs 02/04); the overall workout timer.
- If the app today does **not** resume an in-progress workout at all after process death, restoring the whole workout is NOT in scope — the requirement is that the exercise timer is correct **whenever the workout itself resumes**. Flag to the user if this boundary turns out to matter in practice.

## Decisions baked in
- Robust variant chosen by the user (Q5: "Make it robust") — survives process death, not just minimize/return.

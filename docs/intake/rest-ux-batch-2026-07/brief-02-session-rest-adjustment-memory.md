# Brief 02 — Remember the user's mid-workout rest adjustment for that exercise, this session only

**Type:** Feature
**Cluster:** A (rest-timer machinery) — build **after** Brief 04, layered on top of it, same worker.

> Outcome-only brief: describes the end result and user experience. The "how" belongs to the orchestrator and its workers.

## Context
During a workout, completing a set opens the rest-timer bottom sheet (`ui/log/RestTimerBottomSheet.kt`, driven by `RestTimerManager`/`RestTimerService`). The sheet has **+30 s / −30 s** buttons that adjust only the *currently running* countdown. The next set's timer goes back to the base rest time for the exercise — today the AI's per-exercise suggestion (`PlannedExercise.recommendedRestSeconds`, resolved by `LogWorkoutViewModel.getRestSecondsForCurrentExercise()`, fallback 90 s); after Brief 04 the base may instead be the user's per-category manual time.

## What the user wants (end result)
When the user adjusts the rest time with +30/−30 and then does another set, the timer for that next set starts from **the time the user set**, not the base time — and this stickiness has exactly this shape:

- **Adjusted duration** = base rest time for the exercise **plus/minus the net of all +30/−30 presses**. Example (user-confirmed): AI says 1:30, user presses +30 once → the next set's rest starts at **2:00**. (The buttons still visibly adjust the remaining countdown in the moment, as today.)
- **Sticks for all remaining sets of that same exercise in this workout session** (user chose this option explicitly).
- **Resets to the base time** when the user moves to the next exercise, and for the next session — even for the same exercise. Nothing is persisted beyond the current workout session.
- Works identically whether the base time comes from the AI suggestion or from the user's manual per-category times (Brief 04) — the session adjustment layers on top of whichever base is active (user-confirmed).

## Acceptance criteria
- Done when: AI/base rest is 90 s, user presses +30 during set 1's rest → set 2 and set 3 of the same exercise start their rest at 120 s.
- Done when: multiple presses accumulate (e.g. +30, +30 → base + 60; +30 then −30 → back to base).
- Done when: moving on to the next exercise, its rest timer uses that exercise's own base time (no carry-over).
- Done when: after completing (or abandoning) the workout, a later session with the same exercise starts from the base time again — the adjustment is never saved.
- Done when the same behavior holds with manual category times active (Brief 04): e.g. manual "Heavy compounds" = 3:00, user presses −30 → remaining sets of that exercise start at 2:30; next heavy-compound exercise is back to 3:00.
- Done when an aggressive negative net adjustment cannot produce a nonsensical timer (a sensible floor applies, as the current sheet already enforces for the live countdown).

## Scope and constraints
- **In scope:** the rest timer's starting duration per set within one workout session; only the existing +30/−30 controls are the input.
- **Out of scope:** persisting user rest preferences across sessions (that is Brief 04's job, via explicit settings); changing the AI's suggestions; any new UI beyond what the sheet already has.

## Decisions baked in
- Semantics = base ± net adjustments (Q2a: yes).
- Stickiness scope = remaining sets of the same exercise, this session only (Q2b: option a).
- Applies on top of manual category times exactly the same way (Q4d: yes).

## Considerations for whoever builds it
- "Same exercise" during one session should follow the logging screen's notion of the current exercise (including its session-only exercise-swap behavior) — surfaced, not decided; use whatever reads most naturally in the existing flow.
- The rest sheet's source label (see Brief 04's accepted suggestion) should still show the *base* source honestly; how an active session adjustment is reflected in that label is the builder's call as long as the displayed start time is the adjusted one.

# Brief 16 — Weight calculator keypad: tap outside to dismiss

**Type:** Feature (interaction polish)
**Cluster:** Standalone — logging screen; build against the post-release-1 (rest-ux) state of these files.

> Outcome-only brief. No confirmation round was possible (user asleep); assumptions are labelled.

## Context
Tapping the weight field during a workout opens the calculator-style keypad (`layoutWeightKeypad`, an inline panel toggled by visibility in `LogWorkoutFragment`; state in `WeightCalculator`). Today it closes only via its **Done** key, focusing another input field, or switching exercise. Tapping elsewhere on the screen leaves it open, covering content.

## What the user wants (end result)
While the keypad is open, **tapping anywhere outside the keypad panel closes it** — same result as Done: any pending expression ("60 + 5") resolves into the weight field first.

## Acceptance criteria
- Done when a tap outside the open keypad dismisses it and the weight field holds the resolved value.
- Done when taps **on** the keypad (all keys, the expression row, plate hint) never dismiss it.
- Done when the existing dismissal paths (Done, reps/name focus, exercise switch) keep working unchanged.
- Done when scrolling the screen behaves sanely with the pad open (a scroll gesture shouldn't feel broken — see A-16a).
- Done when the ±2.5 quick-step buttons still reseed the open pad as today (if the outside-tap rule closes the pad on those buttons instead, resolve per A-16a and keep behavior consistent).

## Scope and constraints
- **In scope:** the keypad's dismissal behavior.
- **Out of scope:** the keypad's keys/math/plate hint; the reps field's system keyboard.

## Assumptions (user may veto)
- **A-16a:** the outside tap **also performs its normal action** (it isn't swallowed) — tapping another button both closes the pad and activates that button, matching how the inline panel already behaves for field-focus changes. Exception allowed where pass-through would be clearly accident-prone; builder keeps it consistent and predictable.

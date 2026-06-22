# Issue 09 — Workout time estimates are too optimistic (no administrative time)

**One-liner:** Estimated workout durations only count work + rest and ignore the real overhead between exercises.

## App context
The Program/day and exercise cards show estimated durations (e.g. "~48m" for a day, "~19m" per exercise). This is the estimate shown before/while training, not the logged actual time.

## Current (incorrect) behavior
- The estimate appears to sum only exercise/set time plus rest time. It ignores "administrative" overhead between exercises — fetching the right dumbbells, adjusting equipment, walking between stations, setup. The result is consistently too low.

## Correct behavior (target)
- The estimate includes a realistic allowance for between-exercise (and per-exercise setup) overhead, producing a total that is meaningfully closer to real elapsed time.

## Acceptance criteria
- [ ] A multi-exercise day's estimate is noticeably higher than the pure (work + rest) sum.
- [ ] The per-exercise and per-day estimates remain internally consistent (day ≈ sum of exercises + overhead).
- [ ] Estimates still display rounded (no false precision).

## Constraints & scope
- This concerns the **displayed estimate only**, not the actual logged session duration.
- The overhead model (e.g. a per-exercise transition allowance, possibly larger for equipment-heavy moves) is a tunable — pick a sensible default and **flag the value/model as a decision** rather than hard-coding silently.

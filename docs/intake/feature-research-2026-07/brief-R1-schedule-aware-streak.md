# Brief R1 — Schedule-aware streak (rest days keep the flame alive)

**Type:** Feature (gamification mechanics rework) — user-approved direction (Q1 "good")
**Cluster:** G-mechanics (with R4) — same worker, R1 first.

> Outcome-only brief: describes the end result and user experience. The "how" belongs to the orchestrator and its workers.

## Context
The streak (`UserStats.currentStreak`/`bestStreak`, computed in `GamificationRepository.processWorkoutCompletion`, mirrored by `data/backup/StatsRecomputer`) today increments only when workouts fall on *consecutive logical days*. A planned rest day silently resets the chain to 1, so for a 3–4 day/week trainee the streak is almost always 1–2 and the sixteen `streak_*` / seven `best_*` achievements (up to 365 days) are effectively unreachable. Meanwhile the app already knows the schedule: rest days are configured (`restDaysCsv`/days-per-week) and every past day is auto-logged as a REST or MISSED `WorkoutSession` (`WorkoutSession.kind`, v1.12.0). The streak is displayed on Home ("🔥 N"), Profile, and the workout-complete dialog.

## What the user wants (end result)
The streak measures **sticking to the plan**, not training every calendar day:

- Completing a workout on a planned training day continues the streak (+1 as today).
- A **planned rest day does not break the streak** — including the auto-logged REST days and configured rest days. It doesn't increment it either; the streak counts training days adhered to.
- A **missed planned training day breaks the streak** (the auto-logged MISSED day is the signal).
- The streak the user sees on Home/Profile is correct *now*, not only after the next workout: if yesterday was missed, the displayed streak already reflects the break.
- Unplanned extra workouts (rest-day training, freestyle) still count toward the streak — training more than planned never punishes.
- Best streak, the streak/best achievements, the workout-complete dialog's streak line, and the backup-merge recompute all follow the new semantics consistently.

## Acceptance criteria
- Done when: train Mon, rest Tue (planned), train Wed → streak shows 2 after Wednesday's workout (today it would show 1).
- Done when: train Mon, MISS a planned Tue, train Wed → streak shows 1 after Wednesday (chain broken by the miss).
- Done when: with a live streak and yesterday auto-logged MISSED, opening the app today shows the broken (reset) streak *before* any new workout.
- Done when: a workout logged on a planned rest day still extends the streak and never breaks it.
- Done when best-streak tracking and all `streak_*`/`best_*` achievement unlocks use the new streak values.
- Done when a backup **merge**'s stats recompute produces the same streak the live app would have (StatsRecomputer parity with the new rule).
- Done when two workouts on the same logical day still count the day once.
- Unit-tested off-device (pure day-walk logic); user verifies feel on-device.

## Scope and constraints
- **In scope:** streak semantics, its display freshness, achievements driven by it, recompute parity.
- **Out of scope:** XP amounts, level formula, any other stat; retroactively rewriting history (see A-R1).
- Must respect the logical day boundary (`DayBoundary`, user-configurable cutoff hour) exactly as the rest/missed backfill does.

## Decisions baked in
- Streak = plan adherence; rest days neutral; missed planned day breaks it (user approved this exact framing).

## Assumptions (user may override)
- **A-R1 (forward-only):** current streak values are migrated as-is and grow correctly from ship day; we do NOT replay all history to retroactively recompute streaks (which could mass-unlock streak achievements overnight). The backup-merge recompute uses the new rule going forward.
- **A-R2:** days before the rest/missed auto-logging feature existed (no session rows at all) are treated as neutral, not as breaks.

## Considerations for whoever builds it
- The "displayed streak is fresh without a workout" requirement means the break must be applied when the MISSED day is known (the app already backfills on foreground) — surfaced, not prescribed.
- Streak-guard notification (Brief R2) depends on this brief's definition of "streak at risk tonight".

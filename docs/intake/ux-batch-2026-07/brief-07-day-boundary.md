# Brief 07 — Configurable day boundary (default 04:00)

**Type:** Feature (wide-reaching)
**Cluster:** B — foundational; **do before item 10**, which relies on this definition of "today."
**Outcome-only:** This brief describes the end result and user experience. It deliberately does not prescribe implementation.

## Context
Right now the app treats midnight as the boundary between days everywhere it decides "which day is it." A user who trains at 01:00 hasn't slept yet and thinks of it as the previous day, but the app files it under the new calendar day. The app derives "today"/"which day" in **many** places (roughly 80 date derivations): the date a workout is logged under, History grouping, streaks, the v1.12.0 auto rest/missed-day detection, "today's plan" on Home, the program's week-start, and PR/trend dates.

## What the user wants (end result)
Shift the logical day boundary so anything **before the cutoff counts as the previous calendar day** — a "day" runs from the cutoff to just before the next day's cutoff (default 04:00 → 03:59). The cutoff is **adjustable in Settings**, defaulting to **04:00**. The rule applies **consistently everywhere** the app decides which day something belongs to.

Concretely: a workout performed at 01:00 is logged, shown in History, counted toward streaks, and treated by rest/missed detection and "today's plan" all as the **previous** day.

## Acceptance criteria (Done when …)
- A **Settings** control lets the user set the day-boundary cutoff, defaulting to **04:00**.
- With the default cutoff, a workout logged at any time from 00:00 up to (but not including) 04:00 is attributed to the **previous** calendar day, and this attribution is consistent across: the logged workout's date, History grouping, streak counting, auto rest/missed-day detection, "today's plan"/"today's session" on Home, the program week-start, and PR/trend dates.
- Changing the cutoff in Settings changes the boundary everywhere consistently (no surface left on the old midnight rule).
- **Auto rest/missed reconciliation:** opening the app at, say, 02:00 (before the cutoff) treats it as **still the previous day** — it does **not** yet close out / mark that day as rest or missed. The day is only closed out once the app is opened at/after the cutoff. (Confirmed correct by the user.)
- Existing already-logged data is reinterpreted under the new boundary from its stored timestamp — no data is rewritten or lost.

## Scope and constraints
- **In scope:** every place the app derives "which day is it," listed above.
- The rule is **global and consistent** — the user explicitly rejected applying it to only some surfaces (e.g. logging but not streaks), because that produces contradictions ("History says yesterday, streak says today").
- **Reconcile with the shipped auto rest/missed-day feature (v1.12.0):** its "today"/"yesterday"/epoch-day window math and its onStart trigger must adopt the same cutoff, or rest/missed days will be mis-dated.
- **No DB migration / backfill expected:** the boundary is a *derivation* over each session's existing timestamp, so historical sessions are simply reinterpreted. (If the builder finds a stored per-session "day" that would need rewriting, surface it — but the intent is derivation-only.)

## Decisions baked in
- Boundary is **adjustable in Settings, default 04:00**.
- Applies **everywhere** the app decides the current/owning day.
- Pre-cutoff app opens do **not** yet close out the previous day for rest/missed logging.

## Assumptions (user may override)
- **[A-3]** The cutoff is a whole-hour setting (a sensible range such as 0–6). Sub-hour granularity was not requested.
- **[A-7a]** "Week start" shifts only in the sense that the *day* a week-boundary calculation lands on now respects the cutoff; the app keeps its existing choice of which weekday a program week starts on. Confirm if the user meant something more.

## Considerations for whoever builds it
- This is the largest, most cross-cutting item in the batch. A single shared notion of "current logical day / today" that every surface consults (rather than ~80 independent `now()`/midnight calculations) would make the behavior consistent and testable — but the *how* is the orchestrator's call; the required *outcome* is global consistency.
- Watch DST and timezone edges — the existing rest/missed logic already works in local epoch-days for DST-safety; keep that property.
- Test the boundary explicitly at 00:00, 03:59, and 04:00 for logging, History grouping, streaks, and rest/missed classification.
- Interacts with **item 10**: "current day" for auto-attributing a finished workout must use *this* cutoff. Build item 7 first (or the same worker owns both).

## Standing constraints
- Build with `./build.sh` (not `./gradlew`). No commits/releases unless asked. No on-device/automated UI tests unless asked.

# B07 — Hide empty data fields instead of guidance copy

**Type:** Improvement
**Cluster:** Progress / Recap / History family (with B03)
**Outcome-only:** describes the desired end result, not the implementation.

## Context
Across the data screens (Recap, Progress, History, Stats, and the per-exercise Trends screen), empty charts and sections currently show "log more to see X" guidance copy — for example "Log a couple of weeks to see your volume trend.", "Train across at least two weeks to see your frequency.", "Select an exercise above to see its PR history.", "No PR history yet…", and "Not enough data yet" on charts. The user finds these placeholder messages cluttering and wants empty fields simply hidden until there is data.

## What the user wants (end result)
App-wide, when a data field / chart / section has no data yet, it should be **hidden** rather than showing a "log more to see this" message. The per-field/per-chart guidance copy is removed.

**Accepted improvement (confirmed):** to avoid a brand-new user landing on a completely blank screen that looks broken, keep **one** short top-level line on an otherwise-completely-empty screen (e.g. "Complete a workout to see your stats"). This single fallback line is the only guidance that remains; all the per-field/per-chart clutter is gone.

## Acceptance criteria
- Done when individual empty charts/sections across Recap, Progress, History, Stats, and Trends no longer show "log more to see X" copy — they are hidden until they have data.
- Done when a charts/section that **does** have data still renders normally.
- Done when a screen that has **no** data at all still shows exactly one short top-level line explaining what to do (so it never appears blank/broken), rather than a stack of per-field placeholders.
- Done when, as data accumulates, fields appear individually as each becomes populated.

## Scope and constraints
- In scope: the empty-state behavior of data fields/charts/sections on Recap, Progress, History, Stats, and the per-exercise Trends screen.
- Out of scope: changing how the data itself is computed or what charts exist when populated.

## Assumptions (user may override)
- **[A1]** "Empty data fields" covers the per-chart/per-section empty states on the screens listed above (the surfaces that today carry "log more to see X" copy). If the user intended a narrower set (e.g. only Recap), narrow accordingly.

## Considerations for whoever builds it
- Distinguish a per-field empty state (hide it) from a whole-screen empty state (keep the single top-level line). The single line should read naturally for a first-time user.
- Standard cross-cutting constraints apply (build via `./build.sh`; no commits/releases or UI tests unless asked).

# Intake Index — History week-view swipe navigation (2026-07-25)

**Prepared for:** Project-lead orchestrator
**Source:** User request, single item, follow-on to the v1.26.0 History week-browser
(QoL item 04).
**Status:** CONFIRMED — understanding confirmed by the user (answers relayed verbatim via
coordinator, 2026-07-25). Note: these documents record confirmed understanding; they do
not themselves authorize dispatch — that is a separate user instruction.

## Items

| ID | Title | Type | Brief file | Status |
|----|-------|------|------------|--------|
| 01 | Swipe between weeks in the History week view | Feature (UI gesture) | `brief-01-history-week-swipe.md` | Confirmed |

## Merge / cluster / parallelization guidance

Single item — one worker / one unit. Touches the History Log surface
(`HistoryLogFragment.kt`, `HistoryViewModel.kt`, `domain/HistoryBrowser.kt`, History Log
layout) and possibly reuses `ui/widget/HorizontalSwipeLayout.kt` (shared with the Program
tab — do not alter Program-tab behavior). No other in-flight work is known on these files;
if the orchestrator runs this alongside anything else touching the History tab, serialize
those.

## Confirmed understanding (summary)

With a week open in the History tab's week-browser, swiping right shows the previous
(older) week and swiping left the next (newer) week — same direction language as the
Program tab. Selection lands on the same weekday. Forward stops at the current week,
backward at the earliest browsable week, with a subtle end-of-range nudge. Month
boundaries are seamless. The gesture exists only in the open week view.

## Confirmed decisions (user's own answers)

1. Direction mapping matches the Program tab (right = older, left = newer).
2. Bounds: current week forward, first logged week backward; end-of-range nudge OK.
3. Same weekday stays selected after a swipe.

## Decisions made under delegation (veto-able)

- **Filter interaction (user said "your choice"):** swiping respects an active search /
  date-range filter — it walks only the weeks the browser shows under that filter, and the
  filtered list's edges are the swipe bounds.
- **Future-weekday fallback:** if the carried-over weekday would be a future day (only in
  the current week), the week's normal default day is selected instead.

## Assumptions applied (not objected to — veto-able)

- Gesture only inside the open week view; month list unchanged.
- Month-boundary crossing is seamless.
- Visuals/animation/nudge form are the builder's call, matching the Program tab's feel.

## Cross-cutting constraints

- Build via `./build.sh`, not `./gradlew`.
- No commits or releases unless the user explicitly asks.
- No on-device / Waydroid / Maestro testing — verify via build + unit tests only.

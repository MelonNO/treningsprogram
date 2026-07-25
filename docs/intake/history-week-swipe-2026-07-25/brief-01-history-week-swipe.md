# Brief 01 — Swipe between weeks in the History week view

**Type:** New feature (UI gesture)
**Status:** Confirmed by user (relayed via coordinator, 2026-07-25)

> Outcome-only: this brief describes the end result and user experience. The "how" is the
> builder's to decide.

## Context

The History tab's Log sub-tab was rebuilt in v1.26.0 (QoL item 04) into a monthly
week-browser: month list → tappable week rows → an open **week view** with a 7-day chip
strip and the selected day's performed exercises. Relevant existing code:
`ui/history/HistoryLogFragment.kt`, `ui/history/HistoryViewModel.kt`,
`domain/HistoryBrowser.kt`.

Prior art: the Program tab already supports horizontal week-swiping via
`ui/widget/HorizontalSwipeLayout.kt` (v1.21.0) — swipe right (finger moves right) goes
**back** in time, swipe left goes forward. The user expects the same gesture language here.

## What the user wants (end result)

When a week is open in the History week view, the user can swipe horizontally anywhere on
that view to jump directly to the adjacent week — no need to back out to the month list:

- **Swipe right → previous (older) week. Swipe left → next (newer) week.** Identical
  direction mapping and feel to the Program tab's week swipe.
- Crossing a month boundary is **seamless** — swiping from the first browsable week of a
  month lands on the last browsable week of the month before it, and vice versa. The user
  never notices month grouping while swiping.
- The month list itself is unchanged; the gesture exists **only inside the open week view**.

### Day selection after a swipe (confirmed + fallback decided)

- The destination week highlights the **same weekday** the user was just viewing
  (Wednesday → Wednesday), even if that day has no workout — the week view already renders
  empty/rest/missed days, and positional predictability was the user's explicit choice.
- **Fallback (decision under delegation):** if the same weekday in the destination week is
  in the **future** (possible only in the current week), fall back to that week's normal
  default-day pick (`HistoryBrowser.defaultDay`), the same day the app would select when
  tapping that week open.

### Boundaries (confirmed)

- **Forward bound:** the most recent browsable week (the current week). No swiping into
  future weeks.
- **Backward bound:** the first (earliest) browsable week.
- At either end, the swipe does not navigate; a **subtle end-of-range nudge** is shown
  (small bounce or brief hint — exact form is the builder's choice, matching the app's
  established feel).

### Search / date-range filter (decision under delegation)

When a search query or date-range filter is active, the browser only contains matching
weeks. **Swiping walks exactly the weeks visible under the active filter**, in
chronological order — skipping nothing that is shown, showing nothing that is hidden. The
first/last visible weeks under the filter become the swipe boundaries (with the same
end-of-range nudge). Rationale: the swipe should never land the user on a week the browser
itself would not list; filter and gesture stay one consistent world.

## Acceptance criteria

Done when:

1. With a week open in History, a right swipe shows the previous browsable week and a left
   swipe shows the next — same gesture direction and recognition feel as the Program tab's
   week swipe.
2. After a swipe, the same weekday as before is selected in the new week; if that weekday
   is in the future (current week), the week's default day is selected instead.
3. Swiping forward stops at the current week and swiping back stops at the earliest
   browsable week; at each end the user gets a subtle nudge and stays on the same week.
4. Swiping across a month boundary works with no extra step or visible seam.
5. With a search or date-range filter active, swiping moves only through the weeks that
   filter shows, and the swipe bounds are that filtered list's first/last weeks.
6. The gesture does not fire on the month list, and does not break existing week-view
   interactions: tapping day chips, tapping an exercise to open its sheet, vertical
   scrolling, and system-back (which still closes the week view back to the month list).
7. Existing unit tests still pass; new behavior that is testable off-device (e.g.
   previous/next-week resolution, same-weekday/fallback selection, boundary handling under
   a filter) is unit-tested.

## Scope and constraints

**In scope:** the swipe gesture in the History week view, day-selection carry-over,
boundary behavior + nudge, filter-aware week ordering.

**Out of scope:** any change to the month list, the day view content, the exercise sheet,
search/filter semantics themselves, or the Program tab's existing swipe.

**Hard constraints (standing):** build via `./build.sh` (not `./gradlew`); no commits or
releases unless the user asks; no on-device/automated UI tests unless the user asks —
verify via build + unit tests.

## Decisions baked in (user-confirmed)

- Direction mapping matches the Program tab (right = older, left = newer).
- Forward bound = current week; backward bound = earliest logged/browsable week.
- End-of-range nudge is wanted (form delegated to builder).
- Same-weekday selection carries across swipes.

## Decisions made under delegation (veto-able)

- **Filter interaction:** swipe respects the active search/date filter (walks only visible
  weeks; filtered list edges are the swipe bounds).
- **Future-weekday fallback:** same weekday, unless it lies in the future → week's default
  day.

## Considerations for whoever builds it (surfaced, not decided)

- `HorizontalSwipeLayout` intercepts horizontal moves across its whole area; the History
  week view lives inside a `NestedScrollView` with tappable chips and rows — verify the
  gesture coexists with vertical scrolling and taps exactly as it does on the Program tab.
- The Program tab's swipe is the consistency reference for gesture threshold, animation,
  and any transition feel; reusing the same component is the natural route but remains the
  builder's call.
- Week identity in the browser is the Monday `weekStart` epoch-day key
  (`HistoryBrowser.mondayOf`); "same weekday" is a fixed offset from it.

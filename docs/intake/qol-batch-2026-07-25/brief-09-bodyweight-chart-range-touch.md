# Brief 09 — Body-weight chart follows the date range + touch-to-read on body-weight, strength, and reps charts

Type: Feature (plus one existing inconsistency fixed)
Cluster: standalone (Stats tab → Progress sub-tab)

> Outcome-only: this brief describes the end result and user experience. The implementation approach belongs to the orchestrator/worker.

## Context

Stats tab → Progress sub-tab. A date-range picker there already filters the strength and reps charts — but the **body-weight chart ignores it** and always shows all-time. No chart in the app has any touch-to-read interaction today (the only chart touch anywhere is the heatmap's tap-to-navigate).

## What the user wants (end result)

1. **Range:** the body-weight chart follows the same selected date range as the other Progress charts; with no range chosen it shows all-time (as the others do).
2. **Touch-to-read on the body-weight chart:** pressing (and dragging) on the graph shows a **vertical line** at the touch position, snapped to the **nearest actual weigh-in**, displaying that weigh-in's **date and exact weight**.
3. **Same touch-to-read on the strength chart and the reps chart** (accepted improvement) — same gesture, showing the values of their plotted points.

## Acceptance criteria

- Done when picking a date range updates the body-weight chart to that range, and clearing it returns to all-time — consistent with the strength/reps charts.
- Done when touching the body-weight chart shows the vertical marker + nearest weigh-in's date and weight, following the finger while dragging, and disappearing appropriately on release (exact dismiss behavior is the builder's call, it must just feel natural).
- Done when the strength and reps charts support the same touch-to-read gesture with their own point values.
- Done when the page still scrolls normally — the gesture must not make vertical scrolling of the Progress screen fight the charts.
- Done when charts without enough data behave gracefully (no crash, no phantom markers).

## Scope and constraints

- Raw weigh-in values are what touch reports on the body-weight chart (user confirmed: nearest actual weigh-in, not the smoothed trend value).
- The existing chart visuals (trend line, callouts, glow) are not redesigned by this item — interaction and range-filtering are added.
- Standing constraints: build via `./build.sh`; no commits/releases unless asked; no on-device UI tests — verify via unit tests.

## Decisions the user deferred / open points flagged

- The relative-strength card also uses weigh-ins unfiltered today; whether it should also follow the range was NOT asked or decided — leave its behavior unchanged and flag it, rather than silently changing it.

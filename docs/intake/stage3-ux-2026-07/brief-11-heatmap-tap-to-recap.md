# Brief 11 — Weekly volume heatmap: tap a square to open its session's Recap

**Type:** Feature
**Cluster:** H3 (Stats tab: 11 + 15) — one worker; needs the Recap open-with-session mechanism (already exists).

> Outcome-only brief. No confirmation round was possible (user asleep); assumptions are labelled.

## Context
The Stats sub-tab's weekly volume heatmap (`VolumeHeatmapView`, data from `VolumeHeatmap.Grid`) is a grid of **muscle-group rows × week columns**; each cell is the working-set count for that muscle in that week. It is currently pure display — no touch handling. The app already has a "open Recap at a specific session, with a muscle highlighted" mechanism (`HistoryFragment.openRecap` / `RecapTargetViewModel` + highlight, used by the Home recovery panel).

## What the user wants (end result)
Tapping a heatmap square takes the user to the **Recap tab with the relevant session selected**. Since a cell aggregates a whole week for one muscle, the app picks the session that best represents the cell (see A-11a) and highlights that muscle in the recap, using the existing highlight behavior.

## Acceptance criteria
- Done when tapping a non-empty cell switches to the Recap sub-tab with a session from that cell's week that actually trained that muscle selected, and that muscle highlighted/scrolled-to.
- Done when tapping an empty cell does nothing (no crash, no navigation).
- Done when the tap targets correspond accurately to the drawn cells (including on small screens).
- Cell→session resolution unit-tested off-device.

## Scope and constraints
- **In scope:** tap handling on the heatmap + the cell→session mapping.
- **Out of scope:** the heatmap's data/rendering; Recap internals (H1's).

## Assumptions (user may veto)
- **A-11a:** a cell maps to the **most recent session in that week containing working sets for that muscle** ("that session" is ambiguous for a weekly aggregate; most-recent is the least surprising single choice). If the user wants a chooser for multi-session cells, that's a follow-up.

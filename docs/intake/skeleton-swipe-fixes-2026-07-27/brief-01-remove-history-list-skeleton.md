# Brief 01 — Remove the History list skeleton loader

**Type:** Bug / removal
**Cluster:** B (skeleton removals) — but see cross-cluster seam with Brief 03 in INDEX
**Outcome-only:** this brief describes the end result and user experience, never how to build it.

## Context

The History tab's Sessions (Log) sub-tab shows a grey placeholder shimmer ("skeleton
loader") while content loads. The user reports it appears even when the real content is
already visible underneath it — the skeleton sits on top of information that is already
there. The user has judged the skeleton unnecessary on this surface altogether.

## Current (incorrect) behavior

- Opening / returning to the History Sessions list can show the grey shimmer placeholder
  even though the session content is already rendered beneath it.

## Correct behavior (confirmed)

- **The skeleton loader is removed entirely from the History Sessions list.** Content
  simply appears when ready. No shimmer/placeholder on this surface under any
  circumstance — not even on a slow first load.

## Acceptance criteria

- Done when opening the History tab's Sessions list never shows a grey placeholder
  shimmer, in any state (first launch, returning to the tab, switching weeks, filtered
  views, slow load).
- Done when the list content appears directly, with no visual placeholder phase.
- Done when no regression is introduced in what the list shows (sessions, week browser,
  filters all render as before — only the skeleton is gone).

## Scope and constraints

- **In scope:** only the History Sessions/Log list surface.
- **Out of scope:** skeletons on other History sub-tabs (Stats, Recap) — the user did
  not ask to touch those. Progress is covered separately by Brief 02.
- Standing constraints: build via `./build.sh` (not `./gradlew`); no commits/releases
  unless the user asks; no on-device/automated UI tests unless the user asks.

## Decisions baked in

- User chose **full removal** over "keep only for genuinely slow first loads" (answered
  "remove" to that exact choice).

## Considerations for whoever builds it

- This surface is also touched by Brief 03 (week-swipe zoning) — same fragment/layout
  area. See INDEX for sequencing guidance.

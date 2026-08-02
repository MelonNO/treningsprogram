# Brief 02 — Remove the Recap skeleton loader

Type: **Bug**
Cluster: Group B with item 03 (same file — land 02 first)

> Outcome-only brief. Describes the end result and user experience — the "how" belongs to the orchestrator and its workers.

## Context

v1.28.0 removed the stuck skeleton loaders (grey placeholder shimmer) from the History list and Program screens; the user dislikes skeletons and chose removal over repair. The Recap screen (`ui/history/HistoryRecapFragment.kt`) still has one: `Skeleton.showDelayed(binding.skeletonRecap)` on load, hidden when content arrives.

## Current (incorrect) behavior

The Recap screen still shows the same kind of skeleton loader that was removed elsewhere — the "earlier skeleton loader issue" persists on this one screen.

## Correct behavior

The Recap screen has **no skeleton loader at all** — same treatment History and Program received in v1.28.0. Content simply appears when ready.

## Acceptance criteria

- Done when opening a Recap never shows a skeleton/placeholder shimmer, on fast or slow loads.
- Done when the Recap's real content still loads and renders exactly as before.
- Done when no orphaned skeleton views/logic remain wired to this screen.

## Scope and constraints

- In scope: the Recap screen's skeleton only.
- Out of scope: any other screen; the shared `Skeleton` helper may stay if still used elsewhere (remove it only if this was its last user and removal is trivial).
- Standing: build via `./build.sh`; no commits/releases unless asked; no on-device tests unless asked.

## Considerations for whoever builds it

- Item 03 edits the same fragment (calorie chip tap) — same worker, this item first.
- Mirror the approach used for the v1.28.0 removals (commit `deabeb8`) for consistency.

# B03 — Progress-tab exercise picker sorted by most sessions

**Type:** Improvement
**Cluster:** Progress / Recap / History family (with B07)
**Outcome-only:** describes the desired end result, not the implementation.

## Context
On the Progress tab, the user selects an exercise (to view its PR / trend history) from an exercise-name picker. The list is currently ordered alphabetically. The user finds their most-trained exercises hard to reach near the top.

## What the user wants (end result)
The exercise picker should list exercises by **how many sessions each exercise appears in, descending** (most-trained first). Ties (same session count) fall back to alphabetical order. As the user types to filter, the matching results keep this most-sessions-first order rather than re-alphabetizing.

## Acceptance criteria
- Done when the picker, on open, shows exercises ordered by session count (highest first), with alphabetical tie-breaking.
- Done when typing to filter the list preserves the most-sessions-first ordering among the matches (does not revert to alphabetical).
- Done when an exercise the user has trained most appears at or near the top.

## Scope and constraints
- In scope: the ordering of the exercise picker on the Progress tab.
- Out of scope: changing what selecting an exercise does (it still opens the same PR/trend destination), or the picker's filtering behavior beyond ordering.

## Considerations for whoever builds it
- "Number of sessions" means distinct sessions the exercise appears in, not total set count — confirm this matches the existing data the screen already uses.
- Standard cross-cutting constraints apply (build via `./build.sh`; no commits/releases or UI tests unless asked).

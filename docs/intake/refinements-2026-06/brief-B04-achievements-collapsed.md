# B04 — Achievements list defaults to collapsed

**Type:** Improvement
**Cluster:** Profile / Home widgets (with B06)
**Outcome-only:** describes the desired end result, not the implementation.

## Context
On the Profile screen, the achievements section has a collapsible list with a header that toggles expand/collapse. It currently opens **expanded** by default, which the user finds takes up too much room.

## What the user wants (end result)
The achievements list should start **collapsed** every time the Profile screen is opened. The header still toggles it open/closed as today.

## Acceptance criteria
- Done when opening the Profile screen shows the achievements list collapsed by default.
- Done when the header still expands and collapses the list on tap.
- Done when the achievements header still shows its summary (e.g. unlocked/total count) while collapsed.

## Scope and constraints
- In scope: the default expand/collapse state of the achievements list on Profile.
- Out of scope: remembering the user's last expand/collapse state (the user wants it to **always** start collapsed, not restore the previous state); the achievement contents themselves.

## Decisions baked in
- Always start collapsed; do not persist or restore the user's previous expand/collapse choice.

## Considerations for whoever builds it
- Standard cross-cutting constraints apply (build via `./build.sh`; no commits/releases or UI tests unless asked).

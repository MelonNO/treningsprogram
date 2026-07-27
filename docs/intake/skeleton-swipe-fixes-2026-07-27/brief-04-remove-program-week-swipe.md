# Brief 04 — Remove week-swiping from the Program tab (current week only)

**Type:** Change request (feature removal)
**Cluster:** A (swipe gestures)
**Outcome-only:** this brief describes the end result and user experience, never how to build it.

## Context

Since v1.21.0 the Program tab lets the user swipe horizontally to view past weeks
(read-only), with a "viewing past week / tap to return" banner. Swiping is the only way
to reach past weeks there. Since v1.26.0 the History tab has a month → week → day
browser that covers viewing past weeks.

## What the user wants (end result — confirmed)

- **The Program tab shows the current week only.** Week-swiping is removed.
- Anything that existed solely to support browsing back in the Program tab goes away
  with it (e.g. the past-week read-only state and its "tap to return" banner can no
  longer be reached and should not linger as dead UI).
- Past weeks are viewed **exclusively** through the History tab's week browser.
- No replacement affordance: the user explicitly declined arrows/buttons for past weeks
  in Program ("program is only for viewing the current week and all other weeks must be
  checked in the history tab").

## Acceptance criteria

- Done when horizontal swiping on the Program tab does nothing (no week change, no
  visual shift), and there is no other way to view a non-current week there.
- Done when the Program tab always displays the current week, with all current-week
  functionality (generate, regenerate, edit, start workout, rationale, etc.) unchanged.
- Done when no past-week remnants remain visible or reachable in the Program tab
  (banner, read-only mode indicators tied to past-week viewing).
- Done when the History tab's week browser still provides access to past weeks
  (unchanged by this item).

## Scope and constraints

- **In scope:** the Program tab's week navigation only.
- **Out of scope:** the History tab (Briefs 01/03); the shared swipe widget itself where
  History still needs it; any current-week Program behavior.
- Standing constraints: build via `./build.sh` (not `./gradlew`); no commits/releases
  unless the user asks; no on-device/automated UI tests unless the user asks.

## Decisions baked in

- User confirmed Program = current week only, past weeks live in History. No arrows or
  alternative past-week navigation in Program.

## Considerations for whoever builds it

- The swipe widget is shared with the History week view (Brief 03) — removal here must
  not break History's use of it.
- Whether internal past-week plumbing is deleted or merely unreachable is the builder's
  call; the user-facing requirement is only that no past-week state can be reached or
  seen from the Program tab.

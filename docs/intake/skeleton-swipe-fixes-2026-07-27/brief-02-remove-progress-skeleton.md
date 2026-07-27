# Brief 02 — Remove the Progress tab skeleton loader

**Type:** Bug / removal
**Cluster:** B (skeleton removals)
**Outcome-only:** this brief describes the end result and user experience, never how to build it.

## Context

The History tab's Progress sub-tab shows a body-weight chart by default and lets the
user select an exercise to see strength progress. It has a skeleton shimmer intended as
a loading placeholder.

## Current (incorrect) behavior

- The skeleton shimmer is shown **while the body-weight chart is already visible**, and
  it **does not go away until an exercise is selected**. It sits on screen indefinitely
  over/alongside real content, serving no loading purpose.

## Correct behavior (confirmed)

- **The skeleton loader is removed entirely from the Progress sub-tab.** No shimmer or
  placeholder there in any state. Content (body-weight chart, exercise charts) simply
  appears when ready. Removing it also eliminates the stuck-shimmer behavior by
  definition.

## Acceptance criteria

- Done when the Progress sub-tab never shows a grey placeholder shimmer — not on entry,
  not while the body-weight chart is displayed, not while switching exercises, not on
  slow loads.
- Done when the body-weight chart and exercise-progress views render exactly as before,
  minus the skeleton.
- Done when nothing on the Progress screen waits for an exercise selection in order to
  clear a visual placeholder.

## Scope and constraints

- **In scope:** only the History → Progress sub-tab's skeleton.
- **Out of scope:** skeletons on Stats and Recap sub-tabs (not requested); the Progress
  screen's actual charts and empty-state copy (unchanged).
- Standing constraints: build via `./build.sh` (not `./gradlew`); no commits/releases
  unless the user asks; no on-device/automated UI tests unless the user asks.

## Decisions baked in

- User chose **full removal** over "keep it but only while genuinely loading" (answered
  "remove" to that exact choice).

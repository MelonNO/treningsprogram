# Brief B10 — Home-screen widget: streak + challenge progress

**Type:** Feature (motivation, ambient)
**Cluster:** Standalone worker

> Outcome-only brief: describes the end result and user experience. The "how" belongs to the orchestrator and its workers.

## Context
`TodayWorkoutWidgetProvider` shows today's planned workout on the phone's home screen — verified still plan-only. The app now has a schedule-aware streak and weekly challenges with a Perfect Week bonus; neither reaches the widget. The widget is the one surface that nudges without a notification.

## What the user wants (end result)
1. The widget additionally shows the current streak (count + flame, matching in-app streak presentation) and compact weekly-challenge progress (e.g. per-challenge progress or an x/3 summary).
2. Today's-plan content remains the widget's primary information; the additions are compact accents in the Auros language.
3. The widget stays current: it reflects streak/challenge changes after workouts are completed, days roll over, or a restore/recompute happens — within normal widget-update latitude.
4. Rest days keep their existing rest-day presentation, with the streak still visible (a rest day maintains a plan-adherence streak — that's the point of R1).

## Acceptance criteria
- Done when the widget shows streak and challenge progress that match the in-app values at refresh time.
- Done when completing a workout or the day rolling over updates the widget without requiring an app open (subject to standard widget update constraints).
- Done when a user with no streak/no active challenges gets a clean layout, not zeros and empty bars.
- Done when the existing today-plan content and widget interactions (tap-through) are unchanged.
- Data-selection logic (what the widget shows for given stats/challenge states) unit-tested.

## Scope and constraints
- **In scope:** the existing widget's content upgrade.
- **Out of scope:** new widget types/sizes as separate products; notification behavior; any mechanics change.
- No DB schema change, no live API.
- Standing: build via `./build.sh`; no commits/releases unless asked; no on-device/automated UI tests. (Widget rendering is exactly the kind of surface the user checks on-device.)

## Assumptions (user may override)
- **A-D1:** one upgraded widget (the existing one), designed to degrade gracefully at small sizes rather than shipping multiple widget variants.

## Considerations for whoever builds it
- Streak display must read the same source of truth as the app (post-R1 plan-adherence streak), not recompute its own.

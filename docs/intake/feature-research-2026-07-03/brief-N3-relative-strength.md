# Brief N3 — Relative strength: lifts per kg of body weight

**Type:** Feature (training insight)
**Cluster:** Progress sub-tab (with N5's target line — N3 lands BEFORE N5 on that surface)

> Outcome-only brief: describes the end result and user experience. The "how" belongs to the orchestrator and its workers.

## Context
The app now tracks body weight first-class (weigh-ins in `body_measurements`, trend on Home, BW chart in Stats → Progress) and has e1RM machinery (`Epley`, `OneRmTrend`) driving strength charts — but the two never meet. For a lifter tracking both, the real question is "am I getting stronger, or just heavier?" Muscle *balance* stays high-level in Stats (user rule); this is strength-vs-body-weight, which that rule does not touch.

## What the user wants (end result)
1. A relative-strength view in Stats → Progress: for a selected big lift, the trend of estimated 1RM divided by body weight at that time (e.g. squat at 1.12× BW, trending up).
2. Body weight for each point is the nearest weigh-in to that session's date; periods with no weigh-ins simply have no relative-strength points (no guessing).
3. Optional milestone markers on the chart for classic ratios (e.g. 0.5×/0.75×/1.0×/1.5× BW) so crossing one is visible.
4. The view respects the existing Progress-tab conventions: same date-range filter, same Auros chart language, Monday-week alignment.

## Acceptance criteria
- Done when selecting a strength exercise with both lift history and weigh-in data shows a relative-strength trend, and the values match hand-computed e1RM ÷ nearest-weigh-in for spot-checked points (unit-tested).
- Done when an exercise or period without weigh-in data degrades gracefully (clear empty/partial state, no fabricated points).
- Done when warm-up sets are excluded (existing strength-history rule) and first-ever lifts are treated as baseline data like any other point (no PR semantics here at all).
- Done when the existing absolute strength chart and BW chart are unchanged.

## Scope and constraints
- **In scope:** the chart view + milestone markers, Stats → Progress only.
- **Out of scope:** milestones as achievements/XP (explicitly not in v1 — see A-R1); any change to PR definitions; body-weight entry UX.
- No DB schema change, no live API.
- Standing: build via `./build.sh`; no commits/releases unless asked; no on-device/automated UI tests.

## Assumptions (user may override)
- **A-R1:** milestones are **chart-only** — no achievements, no XP. (The user did not pick the PR-expansion item B3; wiring milestone achievements would reopen that XP-rebalance question and the StatsRecomputer parity hazard.)
- **A-R2:** offered for strength lifts the app already charts e1RM for; bodyweight-only exercises are excluded from this view.

## Considerations for whoever builds it
- N5 adds a goal target line to the same Progress surface — sequence N3 before N5 or give both to one worker (see INDEX).

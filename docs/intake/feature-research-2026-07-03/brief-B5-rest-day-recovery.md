# Brief B5 — Rest-day active recovery suggestions

**Type:** Feature (recovery / gentle guidance)
**Cluster:** Home surface (after N1; sequence with B7/N5 cards per INDEX)

> Outcome-only brief: describes the end result and user experience. The "how" belongs to the orchestrator and its workers.

## Context
Rest days are first-class (schedule-aware streak, auto-logged REST days) and the recovery model knows per-muscle recovery state scaled by logged effort (`MuscleRecovery`). But a rest day in the app is a blank: no guidance, nothing to do. The idea: on rest days, offer a small optional active-recovery suggestion informed by what is actually sore. Parked last round on tone/content questions; the user has now picked it.

## What the user wants (end result)
1. On a rest day, Home shows a small optional card suggesting light active recovery — e.g. a walk, or a couple of mobility moves — biased toward the muscle groups the recovery model currently marks as recovering.
2. Tone is invitation, not obligation: dismissible for the day with one tap, never counted, never streak-relevant, never nagging. A rest day with the card ignored is a perfectly completed rest day.
3. On training days the card never appears.
4. No notifications for this in v1 (the notification center is untouched).

## Acceptance criteria
- Done when a rest day (scheduled rest, or a no-plan day) shows the suggestion card and a training day never does.
- Done when the suggestion reflects current recovery state (e.g. sore legs → upper-body mobility or a walk, not lunges) — selection logic unit-tested against recovery inputs.
- Done when dismissing hides it for that day and it returns on the next rest day.
- Done when nothing about the card affects streaks, XP, challenges, stats, or logged history.
- Done when a brand-new user with no history gets a sensible generic suggestion (walk) rather than nothing or nonsense.

## Scope and constraints
- **In scope:** the Home card + a small built-in suggestion catalog + recovery-aware selection.
- **Out of scope:** notifications; logging/tracking recovery activities as sessions; AI-generated suggestions (no live API); video/media content.
- No DB schema change, no live API.
- Standing: build via `./build.sh`; no commits/releases unless asked; no on-device/automated UI tests.

## Assumptions (user may override — content/tone were the open questions)
- **A-A1:** content is a small **built-in static catalog** (order of 10–20 short suggestions: walk variants + simple equipment-free mobility moves), rotated so it doesn't repeat daily — no AI call, no external content.
- **A-A2:** suggestions are text-only (name + one-line description), matching the app's existing quiet-utility register.
- **A-A3:** "dismiss" is per-day; there is also a permanent off-switch in App Settings for users who never want the card.

## Considerations for whoever builds it
- Home hosts several additions in this batch — land after N1, coordinate with B7's card and N5's nudge (see INDEX) so the card stack has one deliberate order.

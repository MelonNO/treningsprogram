# Brief 03 — Estimated calories burned

Type: Feature
Cluster: standalone; light hazard with Cluster A (the workout-complete summary surface item 10 also touches) and with item 04 (both live in `ui/history/`, different files)

> Outcome-only: this brief describes the end result and user experience. The implementation approach belongs to the orchestrator/worker.

## Context

Nothing calorie-related exists in the app. Per-session data available for an estimate: stored duration (minutes), date, per-set reps/weight/warm-up flag/RPE/timestamps, muscle group per set (including "Cardio"), and sparse manual body-weight entries. No heart rate, no height/age/sex.

## What the user wants (end result)

An **estimated calories burned** figure for performed training, visible in three places:

1. the **workout-complete summary** (the moment a workout is finished),
2. the **session Recap** (Stats tab → Recap),
3. the **Stats sub-tab** as a **weekly total** alongside the existing weekly numbers.

## Acceptance criteria

- Done when finishing a workout shows its estimated calories on the completion summary.
- Done when any session's Recap shows that session's estimated calories.
- Done when the Stats sub-tab shows a weekly calories total.
- Done when a user with no logged body weight still gets figures, computed with a **75 kg** assumed weight.
- Done when past sessions show estimates too, not just sessions logged after the update (Assumption A1, veto-able).
- Done when the figure is visibly an estimate (approximate presentation), not an exact-looking claim.

## Scope and constraints

- Rough estimate explicitly accepted by the user — based on workout length, type of work, and body weight; there is no heart-rate data and none should be required.
- No new user-profile inputs (height/age/sex) are required by this item.

## Decisions baked in

- Placement: completion summary + Recap + Stats weekly (user picked these three; not on Home).
- 75 kg fallback when no body-weight data exists (user's own number).

## Assumptions (user may override)

- A1: estimates appear for all historical sessions (they are derivable from stored data).
- A2: the body weight used for a session is the most recent weigh-in at or before that session's date; 75 kg only when no weigh-in exists at all.

## Considerations for whoever builds it (surfaced, not decided)

- Distinguishing cardio vs strength intensity in the estimate is the builder's call; the data allows it (muscle group per set, pacing from set timestamps, session duration).
- Whether values are derived on the fly or persisted is the builder's call — but if anything is persisted, backup/restore and any recompute paths must stay consistent (project convention).
- Warm-up sets: they cost energy too; whether/how they factor in is the builder's call — just be consistent across the three surfaces.
- Rest-day/missed auto-logged entries must show no calorie figure.
- Standing constraints: build via `./build.sh`; no commits/releases unless asked; no on-device UI tests — verify via unit tests.

# Brief N1 — Pre-workout "numbers to beat" on Home

**Type:** Feature (training optimality + motivation)
**Cluster:** Home surface (with B5/B7/N5's Home cards — see INDEX; N1 goes FIRST on Home)

> Outcome-only brief: describes the end result and user experience. The "how" belongs to the orchestrator and its workers.

## Context
Home's today card lists today's planned exercises, but the concrete target — the number that would be progress — only appears once the user is inside the logging screen, as the R7 "Beat" chip (`BeatTarget`, mirroring `GamificationRepository.isWeightPr`). The motivating fact exists; it just arrives late. `RecentPrs` and the Beat-chip lookup already know each exercise's historical best working weight.

## What the user wants (end result)
1. On Home's today card, each planned exercise with history shows a compact target line/chip: the weight to beat (the same historical-best working weight the in-workout Beat chip shows), e.g. "Beat: 60 kg".
2. Exercises with no history show nothing extra — a first session sets baselines, never targets (ratified rule).
3. The number shown on Home and the number the Beat chip shows in-workout for the same exercise are always the same.
4. The addition is quiet and compact — Home's today card must not become cluttered or taller than the screen for a typical 5–6 exercise day.

## Acceptance criteria
- Done when an exercise with logged history shows its beat target on Home and a fresh exercise shows none.
- Done when the Home target equals the in-workout Beat chip's initial target for the same exercise, including after a PR was set in a previous session (both reflect the new best).
- Done when warm-up sets never influence the shown target.
- Done when rest days / days with no plan show no targets and no empty placeholder.
- Target derivation is unit-tested for agreement with `BeatTarget.chipTarget`.

## Scope and constraints
- **In scope:** Home today-card presentation of existing target data.
- **Out of scope:** changing the PR/target definition; rep or e1RM targets (not picked); any logging-screen change.
- Standing: build via `./build.sh`; no commits/releases unless asked; no on-device/automated UI tests.

## Assumptions (user may override)
- **A-N1a:** the target reads as a small muted "Beat: X kg" element per exercise (Auros-quiet, not a vivid chip — the vivid moment stays in-workout).

## Considerations for whoever builds it
- Home lookups must stay async/jank-free (the logging screen already does the same per-exercise history fetch pattern).
- Several other picked items add cards to Home (B5, B7, N5's nudge) — coordinate ordering per INDEX to avoid merge churn.

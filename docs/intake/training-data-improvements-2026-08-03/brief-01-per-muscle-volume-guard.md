# Brief 01 — Per-muscle weekly floor check on generated plans

Type: **Feature (generator quality)**
Cluster: A (with 03 — same generation surface). **Wait for `gen-science-fixes-2026-08-03` to land first.**

> Outcome-only brief. Describes the end result, not the implementation.

## Context

Program generation (`AiRepository`) builds a weekly plan from goal, days/week, and history. The accept path already checks duration and (post gen-science fixes) weights and recovery, but **nothing checks that each major muscle group gets adequate weekly frequency or minimum volume** for the user's goal.

## Evidence (derived from the user's real logs, 3-week sample)

On a 5-day/week **Hypertrophy** profile, generated weeks allocated:

- **Legs: exactly one planned day per week, every week.** Realized leg frequency was 0–1 days/week; one missed day produced a week with zero leg sets. Quads specifically got almost nothing across three weeks (one light single-leg movement).
- **Shoulders (direct work): ~3–6 planned sets/week, realized 3 sets/week every week** — far below productive hypertrophy volume, while chest/back/arms got 8–16.
- The only overhead-press slot was repeatedly planned on a day the user never trains (see brief 03), so overhead pressing was performed **zero times** in the sample.

A hypertrophy plan a coach would sign off on at 5 days/week trains each major muscle ~2×/week with roughly 10+ hard sets for non-prioritized muscles; the generator produced 1×/week legs and 3-set shoulders without any warning.

## What the user gets (end result)

Generated weekly plans on a Hypertrophy (and Muscle-gain-adjacent) goal at ≥4 days/week satisfy per-muscle floors — each major muscle group appears on **at least 2 days** and receives **at least a minimum number of direct hard sets** — or the plan is repaired/regenerated before acceptance, the same way duration violations are handled today. A muscle group must never depend on a single planned day when the floor is active.

## Acceptance criteria

- Done when a generated Hypertrophy week at ≥4 days/week never allocates a major muscle group (Chest, Back, Legs, Shoulders, Arms) to fewer than 2 training days, and never below the per-muscle set floor, without the accept path catching it.
- Done when the floors are defined in one adjustable place (thresholds are assumption A1 — defaults: ≥2 days, ≥~6 direct hard sets — and may be tuned without hunting through prompt text).
- Done when the check degrades sensibly for low-frequency profiles (e.g. 2–3 days/week cannot give every muscle 2 dedicated days; the floor logic must not make those profiles ungeneratable).
- Done when the check uses the app's muscle classification and is therefore consistent with what Stats shows (classifier fixes from the in-flight branch are a prerequisite).
- Done when existing generation tests still pass and new offline tests cover: a plan violating the floor is not accepted as-is; a compliant plan passes unchanged.

## Scope and constraints

- In scope: generation prompt guidance + deterministic accept-path check (belt and braces, mirroring how duration is handled).
- Out of scope: changing realized-week stats; rebalancing already-saved plans (that is brief 02); cardio/duration logic (in-flight elsewhere).
- Offline validation only — no live API calls without an explicit user grant.

## Honest limit (do not overclaim)

This check verifies **plan allocation** — that each muscle is scheduled with adequate frequency and set count. It cannot judge whether the allocated sets deliver an adequate stimulus in practice: with effort capture staying at the coarse Easy/Moderate/Hard scale (finer RIR capture was proposed as item 06 and **cut by the user**), a lift that stalls under a floor-compliant plan cannot be attributed to user effort vs generator progression. The brief's promise is structural coverage, nothing more.

## Flagged assumption (veto-able)

- **A1:** the specific floor values. The outcome (no 1-day/3-set muscle groups on a 5-day hypertrophy plan) is the requirement; exact thresholds are a product tuning knob.

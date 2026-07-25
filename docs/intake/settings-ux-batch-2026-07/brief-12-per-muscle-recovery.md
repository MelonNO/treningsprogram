# Brief 12 — Per-muscle recovery times, scaled by logged effort

**Type:** Feature
**Cluster:** Standalone (recovery domain + Home card). Independent of every other item.
**Outcome-only:** Describes the end result and user experience; does not prescribe implementation.

## Context
The recovery model currently uses a **single flat ~48-hour "recovering" window for every muscle**, scaled only by a coarse contribution weight (primary 1.0 / major-synergist 0.6 / minor-synergist 0.3). This drives the **"Muscle Recovery" card on the Home tab** (states like RECOVERING / READY / OVERDUE and a "recovery fraction"). Per-set effort is already captured when logging (the RPE/RIR-style label the user sets on a set).

## What the user wants (end result)
> "Rest times seem to be fixed at 48 hours; they should be specified to reflect the muscle and how hard the muscle was worked."

Replace the flat 48h with:
- a **per-muscle-group base recovery time** (bigger/harder-to-recover muscles rest longer than smaller ones), and
- **scaling by the session's logged effort** — the RIR/RPE the user set — so harder-worked sessions lengthen recovery and easier ones shorten it.

The computation must be **deterministic and on-device — no AI/API call** (the user explicitly chose this over an AI-driven approach).

## Acceptance criteria (Done when …)
- Different muscle groups have **different base recovery durations** (not one shared 48h for all).
- A muscle's recovery time is **lengthened or shortened by the logged effort** of the session(s) that worked it (higher effort → longer recovery; lower effort → shorter).
- The recovery calculation is **deterministic and on-device** — no network or AI call is made.
- The Home "Muscle Recovery" card reflects these per-muscle, effort-scaled recovery states and timings.
- Scaling is driven by the **logged effort** the user set — **not** by set count / volume / load.

## Scope and constraints
- **In scope:** the recovery/readiness model and its Home card.
- **Out of scope:** the between-set rest **timer** (a separate, seconds-scale feature — not what "48 hours" refers to); how effort is logged (unchanged).

## Decisions baked in
- Per-muscle base-recovery table + effort scaling; deterministic/on-device (confirmed).
- Effort = the user-set RIR/RPE, not volume/load.

## Flagged build-time decisions (recommendation given; final call is the builder's)
- **[12a — effort fallback]** When a set has **no logged effort**, the scaling needs a defined default. **Recommended:** treat a blank/absent effort as **medium** effort (neither lengthen nor shorten). The user did **not** pin this — the final call is the builder's.
- **[12b — table granularity]** The recovery card already runs on a **finer-grained** muscle taxonomy; the base-recovery values may be defined at that fine grain or at the coarse 7-group level. The user's intent ("bigger / harder-worked muscles rest longer") holds either way — grain is a builder detail.

## Assumptions (user may override)
- **[A12-1]** "Effort the user set" = the existing per-set RPE/RIR label; no new logging field is required.
- **[A12-2]** The existing primary/synergist contribution weighting may be kept, adjusted, or replaced as long as the outcome (per-muscle base + effort scaling) holds — flag whichever is chosen.

## Considerations for whoever builds it
- Pure domain change to the recovery model plus its Home consumption; independent of all other items in this batch — safe to run in parallel.
- Keep it **unit-testable** (the current model already is); base the per-muscle table on a sensible, documented rationale (the existing model already cites its reasoning in comments).

## Standing constraints
- Build with `./build.sh` (not `./gradlew`). No commits/releases unless asked. No on-device/automated UI tests unless asked.

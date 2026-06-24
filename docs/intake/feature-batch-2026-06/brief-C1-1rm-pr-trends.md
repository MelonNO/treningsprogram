# C1 — Per-exercise PR history & estimated-1RM trends

**Type:** New feature
**Cluster:** P3 (parallel analytics) — independent worker.
**Outcome-only:** Describes the end result and user experience, not the implementation. The "how" is the orchestrator's.

---

## Context

The Recap & Trends screen (`RecapTrendsFragment` / `RecapTrendsViewModel`) already plots a per-exercise strength history from `getStrengthHistory` (which returns top weight and best reps per session) and already computes a list of PRs internally. What it does not show is an **estimated-1RM trend** (a single curve that captures both load and rep progress) or a clear **PR timeline**. This is presentation and a formula over data already on-device — no new data source, no API, no schema change.

## What the user wants (end result)

For a chosen exercise, the user can see how their strength has trended over time as an **estimated one-rep-max curve**, and can see a **history of their personal records** for that exercise (each PR with its date and the weight×reps achieved).

## Acceptance criteria ("Done when …")

- **Done when** for a selected exercise, the user can view an **estimated-1RM trend over time** derived from their logged working sets.
- **Done when** the user can see a **PR history/timeline** for that exercise — each personal record with its date and the performance (weight × reps) that set it.
- **Done when** warm-up sets are excluded from both the trend and the PR detection, consistent with the app's existing stats convention.
- **Done when** an exercise with insufficient history shows a sensible empty state rather than a broken chart.

## Scope and constraints

- **In scope:** an estimated-1RM trend and a PR history for a chosen exercise, on the existing Recap & Trends surface.
- **Out of scope (unless the user later asks):** predicting future 1RM; cross-exercise "overall strength" scores; editing past PRs.
- **Local-only:** no API call, no DB schema change.
- **Cross-cutting constraints:** build via `./build.sh`; no git commits/releases unless the user asks; **on-device UI verification IS required for this batch** per the per-wave gate in `SEQUENCE.md`.

## Assumptions (user may override)

- **ASSUMPTION (formula, question G):** estimated 1RM uses the **Epley equation** (a standard, widely-used 1RM estimate). *(User may prefer Brzycki or another.)*
- **ASSUMPTION (PR definition, question H):** "PR" is tracked by **estimated 1RM** (so a rep PR at the same weight, or a weight PR, both register), with warm-ups excluded. *(User may prefer "heaviest weight ever" or "best at each rep count.")*

## Considerations for whoever builds it (surfaced, not decided)

- The estimated-1RM formula must be **consistent with B3** (which also relies on estimated 1RM) so the app never shows two different 1RM numbers for the same lift. Per the INDEX, B3 lives in a different worker (P1) — this is a shared-convention coordination point, not a file collision.
- This is the safest parallel item: it lives on `RecapTrendsFragment`, which no other item in the batch touches.

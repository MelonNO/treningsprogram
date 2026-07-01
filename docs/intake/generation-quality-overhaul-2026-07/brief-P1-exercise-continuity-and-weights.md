# P1 — Exercise continuity, sensible variation & correct weights  (HEADLINE / top priority)

**Type:** Bug (wrong lived behavior) + refinement
**Cluster:** Generation prompt — all six briefs edit the same generator/validator file; see INDEX. P1 is the user's #1 complaint and the highest-value outcome.
**Covers doc sections:** prompt-feedback §0 (open question), §3.3 (anchor load to history), §3.4 (progression-block state), §3.7 (systematic variation, not full rotation).

> **Outcome-only.** This describes the end result the user wants and how to tell it's achieved. It does NOT prescribe how to build it. "Diagnose first" notes mark where the cause must be confirmed in code before changing anything.

## Context
The user's single concrete complaint about AI program generation is: **"exercises or weights are off."** Investigation of the live generator points to one root cause with two visible symptoms:

- The app currently forces a *different* exercise for nearly every muscle every week — pushed by three overlapping rules: a general "rotate exercises every single week" instruction, an exercise **blacklist** (built from the user's recently-logged lifts + last week's plan) that is labelled *"the single most important constraint in this prompt,"* and a mandatory random weekly **variation theme** (e.g. "dumbbells only," "machines only," "compound only"). Together these churn away the very main lifts the user is trying to progress → **"exercises are off."**
- The plan's target weights are taken straight from the model's output; nothing reconciles them against what the user actually logged. Because the forced-new exercise usually has **no logged history under its name**, the model simply guesses a weight → **"weights are off."** History is matched by exact exercise-name string, so even a slightly renamed lift can lose its history.

The two symptoms are the same problem: forced churn removes the trackable anchor, and the un-anchored replacement gets a guessed weight.

## What the user wants (end result)
A **middle-ground** program that is neither frozen-identical nor fully churned:

1. **Main/anchor lifts persist and progress.** For each major muscle, the primary compound recurs week to week and its target weight is progressively overloaded from the user's **logged history** (up after successful sessions, hold/deload on plateau/regression).
2. **Variation is still clearly present — no two weeks are identical copies.** The user's intended model is a clear hierarchy by how disruptive each change is to progression tracking:
   1. **Most main lifts: kept and progressed** week to week (anchors; weight tracked from logged history). This is the default for the majority of main lifts.
   2. **Implement/grip/angle variation ON a main lift** (barbell ↔ dumbbell, grip change, angle/stance change) — allowed more freely; stays recognizably the same movement pattern; weight is **estimated from the closely-related logged lift**, not fabricated.
   3. **Full main-lift swaps** (replacing a main lift with a genuinely different exercise) — allowed but **RARE: a hard cap of ≤2 fully-swapped main lifts per week.** The remaining main lifts stay and progress.
   4. **Accessories / isolation / order / weekday placement / weekly theme** — rotate freely for freshness (respecting the recovery rules).
   This ≤2-full-swaps-per-week cap is the concrete **replacement for the current "blacklist everything / must choose different exercises every week" rule.**
3. **Weights are always anchored, never fabricated:**
   - Kept lift with history → progress from its logged weight.
   - **Varied anchor (same movement, different implement/grip/angle) → estimate the weight from the closely-related logged lift** (map the variation back to the anchor's logged performance) — this is §3.3's "or an estimate from a closely related logged lift" clause. Do NOT guess from scratch.
   - Genuinely new lift with no related history → prescribe by RPE/RIR with a conservative start; never an unexplained absolute kg.

## Current (incorrect) vs correct behavior
- **Current:** Anchor lifts are force-swapped for unrelated exercises weekly; weights on the new/forced lifts are guessed with no tie to logged performance; the in-app mesocycle-block feature already says "build on last week" but the unconditional blacklist overrides it.
- **Correct:** Anchors persist and progress from logged data; variation lives in accessories + same-movement anchor tweaks (periodic) + day placement; every prescribed weight traces to logged history, a related logged lift, or an honest RPE/RIR conservative start.

## Diagnose first (cause partly known, confirm before changing)
- Confirm the churn drivers and that the blacklist fires **even when the user is mid-block / progressing** (it should not force the anchor to change then).
- Confirm the weight path: weights are model-emitted with no history reconciliation, and history/trends are keyed by the exact logged name.
- Confirm what it takes to **map a same-movement variation back to its logged anchor** (implement/grip/angle → shared movement/muscle). The app does not do this today; this is the plumbing gap the doc's open question (§0) hints at.

## Acceptance criteria — Done when…
- Across consecutive weeks (same program & goal), **at most two main lifts are fully swapped** to a genuinely different exercise; every other main lift is **kept** (as the same movement, optionally with an implement/grip/angle variation) and **progressed**, not replaced.
- The recurring anchor's **target weight tracks the user's logged progression** (rises after success; holds or deloads on plateau/regression).
- **No two consecutive weeks are identical copies** — at minimum accessories / grip / angle / order / theme (and optionally weekday placement) differ.
- When a main lift is **varied by implement/grip/angle, its weight is derived from the closely-related logged lift** (a sensible mapped value), not an unrelated guess.
- Anchor-movement variation occurs on a **deliberate cadence**, not so often it breaks week-to-week tracking.
- A lift with **no logged/related history** is prescribed by RPE/RIR with a conservative start — never an unexplained absolute kg.
- **Workout days may land on different weekdays across weeks** while never violating recovery rules (no same primary muscle on consecutive days; ~48 h between heavy leg days; sensible even spacing).

## Scope and constraints
- In scope: the generator's rotation / variation / blacklist / weekly-structure logic, and how logged history is matched and used to set/estimate weights (including the same-movement→anchor mapping).
- Day-shifting must respect whatever schedule mode is active: in **count mode** the app picks days freely (may shift week to week); in **fixed rest-day mode** (user pinned rest days) day placement must stay within the allowed weekdays.
- Recovery rules (§1 priority order places recovery/fatigue ABOVE focus muscles and variation; §4 fatigue limits) always win over variation.
- Must NOT produce a "freeze the whole plan" solution, and must NOT return to churning anchors out for unrelated lifts.

## Decisions baked in (user-confirmed)
- Middle-ground variation as a four-level hierarchy: (1) most main lifts kept + progressed; (2) same-movement implement/grip/angle variation allowed more freely; (3) **full main-lift swaps capped at ≤2 per week**; (4) accessories/order/day-placement/theme rotate freely. Weekday placement gated on recovery.
- The ≤2-full-swaps-per-week cap replaces the current "blacklist everything / different exercise every week" rule.
- Weights: logged → progress; varied anchor → estimate from related logged lift; truly new → RPE/RIR conservative.

## Assumptions applied (user may override)
- **[ASSUMPTION]** "Closely related logged lift" is mapped by movement pattern / primary muscle (e.g. barbell bench, dumbbell bench, incline press share a press anchor). The exact mapping mechanism is left to the builder (outcome-only).
- **[ASSUMPTION]** Anchor-variation cadence is tied to block boundaries (reusing the existing mesocycle-block signal) unless the user prefers a different interval.

## Considerations for whoever builds it (surfaced, not decided)
- The blacklist is currently the prompt's "single most important constraint"; P1 fundamentally changes its role — it must stop forcing anchors to change while still varying accessories and new-block weeks.
- The existing **mesocycle-block** feature already tells the model to build on last week but is overridden by the blacklist; resolving that conflict is central here and overlaps §3.4 "continue-block vs new-block."
- Estimating a varied anchor's weight from a related lift needs a name/movement mapping the app lacks today (history keyed by exact string) — the biggest new piece of plumbing in this brief.

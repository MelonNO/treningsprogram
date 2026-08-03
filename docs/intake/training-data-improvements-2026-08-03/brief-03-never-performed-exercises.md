# Brief 03 — Never-performed planned exercises are re-planned forever

Type: **Feature**
Cluster: A (with 01 — generation surface). **Wait for `gen-science-fixes-2026-08-03` to land first.**

> Outcome-only brief. Scope reduced by the user on 2026-08-03: this brief covers **exercise-level** detection only. The weekday-adherence half of the original brief was cut and must not be built.

## Context

Plans are generated week by week. Nothing today compares a *planned exercise* against whether it ever actually gets performed, so an exercise the user never does can be re-prescribed indefinitely — and whatever training purpose it served silently never happens.

## Evidence (derived from the user's real logs, ~6-week sample)

- A shoulder-press movement was planned **week after week and performed zero times** across the entire sample — it was the plan's only overhead-press slot, so an entire movement pattern existed on paper only.
- The pattern is visible for other slots too: specific planned exercises recur across multiple weeks with zero logged performances, while planned siblings in the same weeks get logged consistently — so this distinguishes "user skips this exercise" from "user skipped that whole session".

## What the user gets (end result)

The app notices when a planned exercise has recurred across several consecutive planned weeks with **zero** logged performances, and:

1. **Surfaces it** — a single, dismissible suggestion naming the exercise (e.g. swap it for an alternative or drop it), never a silent plan rewrite.
2. **Informs generation** — the generator stops blindly re-prescribing an exercise the user demonstrably never does; if the exercise was the sole carrier of a muscle or movement pattern, the replacement preserves that coverage rather than deleting it (interacts with brief 01's floors).

## Acceptance criteria

- Done when an exercise planned in N consecutive generated weeks (threshold adjustable, default ~3) with zero logged performances triggers one dismissible suggestion — and dismissal is remembered (no repeat for the same exercise until circumstances change).
- Done when exercises that go unperformed because their **entire session** was missed do not count toward the detection (skipping a whole day is not an opinion about one exercise).
- Done when generation, given the signal, replaces or re-slots the never-performed exercise while preserving its muscle/movement coverage — never simply deleting the stimulus.
- Done when users who perform their planned exercises never see any of this.
- Done when the behavior is covered by offline unit tests (synthetic plan/log histories → detection and non-detection cases).

## Scope and constraints

- In scope: exercise-level detection from existing plan + session data, the suggestion surface, feeding the signal into generation inputs.
- Out of scope: **weekday/rest-day adherence detection (cut by the user 2026-08-03 — do not build)**; changing REST/MISSED auto-marking; any silent modification of plans.
- Offline validation only — no live API calls without an explicit grant.

## Decisions delegated to the builder (standing "you choose" on method/UI shape — veto-able)

- Detection threshold default and what resets it (e.g. one logged performance).
- Where the suggestion appears (Program tab vs Home card vs notification) and its wording.

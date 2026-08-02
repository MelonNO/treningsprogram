# Brief 03 — Tappable calorie estimate → actual-numbers explanation

Type: **Feature**
Cluster: Group B with item 02 (same file — after 02)

> Outcome-only brief. Describes the end result and user experience — the "how" belongs to the orchestrator and its workers.

## Context

The Recap screen (`ui/history/HistoryRecapFragment.kt`) shows an estimated-calories stat chip (QoL item 03 of v1.26.0), computed by `domain/CalorieEstimator`. Today the chip is display-only; the user has no way to see where the number comes from.

## What the user wants (end result)

Tapping the calorie-estimate chip in a Recap opens an explanation of how **that session's** figure was calculated — the actual inputs used (e.g. session duration, work done — whatever the estimator really consumes) and how they combine, step by step, into the final number shown on the chip. Confirmed: actual per-session numbers plugged in, **not** a generic description of the method.

## Acceptance criteria

- Done when tapping the calorie chip on any Recap opens an explanation view.
- Done when the explanation shows the real input values from that session and the arithmetic that produces exactly the number on the chip (the walkthrough's result matches the chip).
- Done when the explanation is understandable to a non-technical reader (plain language, no formula soup without labels).
- Done when dismissing it returns cleanly to the Recap.

## Scope and constraints

- In scope: making the chip tappable + the explanation surface.
- Out of scope: changing how calories are calculated; calorie displays anywhere other than the Recap.
- Standing: build via `./build.sh`; no commits/releases unless asked; no on-device tests unless asked.

## Decisions made under delegation (veto-able)

- D1: presentation (dialog vs bottom sheet) and layout are the builder's choice, per the user's standing method delegation.

## Considerations for whoever builds it

- Derive the displayed breakdown from the same code path that computes the chip's value so the two can never disagree.
- Item 02 (skeleton removal) edits this fragment first — same worker.

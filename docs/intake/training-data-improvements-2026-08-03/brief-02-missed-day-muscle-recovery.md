# Brief 02 — Missed-day muscle recovery within the week

Type: **Feature** (gap in the existing auto-rebalance mechanism)
Cluster: separate worker from Cluster A; app-side plan logic. **Wait for `gen-science-fixes-2026-08-03` to land** (shared area) and prefer landing after brief 01 (uses its notion of muscle coverage).

> Outcome-only brief.

## Context

The app already auto-rebalances plans in some situations (v1.11.0 era: single-day regen parity, move/rebalance committed on workout-complete) and auto-logs past empty days as REST/MISSED (v1.12.0). What it does **not** do: notice that a missed day held the week's only coverage of a muscle group and try to recover that coverage in the remaining days.

## Evidence (derived from the user's real logs, 3-week sample)

- The plan's single leg day fell on a day the user missed; result: **an entire week with zero leg sets**, invisible to the user unless they study Stats. The muscle simply vanished from the week.
- Because generated plans concentrate some muscles on one day (see brief 01), a single MISSED marker can zero a muscle group — this actually happened, it is not hypothetical.

## What the user gets (end result)

When a planned day becomes MISSED (or is clearly going to be skipped) and its muscle group(s) do not appear on any remaining day of the week, the app **offers** to recover the coverage — e.g. append the missed muscles' key exercises to a remaining day or propose a swapped day — using the existing rebalance/do-another-day machinery. The user accepts or dismisses; dismissal leaves the week as-is.

## Acceptance criteria

- Done when a missed day whose muscles are still covered later in the week produces **no** prompt (no nagging).
- Done when a missed day that zeroes a muscle group for the week produces a clear, dismissible offer to recover it, and accepting it results in a valid updated plan for the remaining days.
- Done when recovery never overwrites or duplicates days the user already logged (same preservation rule as regen-preserves-logged).
- Done when declining the offer is remembered for that week (no repeat prompts for the same miss).
- Done when the behavior is covered by offline unit tests (miss → detection → offered plan shape).

## Scope and constraints

- In scope: detection + offer + applying the accepted recovery within the current week.
- Out of scope: changing how days are marked MISSED; cross-week make-up scheduling; generator allocation (brief 01 handles prevention, this handles cure).
- Must respect the existing auto-rebalance toggle default and Settings placement.

## Flagged assumption (veto-able)

- **A2:** recovery is **offered, never silently applied**. If the user prefers silent auto-recovery (consistent with auto-rebalance-on-complete), that is a one-line product decision to flip — the detection and plan-repair outcome is identical.

## Decisions delegated to the builder (standing "you choose" on method/UI shape — veto-able)

- Surface of the offer (notification vs in-app card vs both) and its wording.
- Whether recovery appends to an existing remaining day or proposes converting a REST day, when both are possible.

# Brief 10 — Finishing a moved workout behaves like a normal finish (celebration + hop to Home)

Type: Bug (behavior gap — the current skip is deliberate code, but wrong per the user)
Cluster: A (touches the same log-workout completion flow as 01/07, plus the Program tab's post-completion handling)

> Outcome-only: this brief describes the end result and user experience. The implementation approach belongs to the orchestrator/worker.

## Context

Normal workout completion: the user lands on the Program tab, the day chip celebration animation plays, and the app then automatically switches to the Home tab. When the completed workout was **another day's workout done today** (a "moved" workout), the app instead takes an early path: it stays on the Program tab, kicks off the automatic week rebalance, and **neither the celebration nor the switch to Home happens**. The user reported the missing tab switch; the rebalance behavior itself they want kept exactly as is.

## Current vs correct behavior

- Current: moved-workout finish → no celebration animation, stuck on Program tab; rebalance runs silently.
- Correct: moved-workout finish → **identical experience to a normal finish** — celebration on the Program tab, then the automatic hop to Home — while the rebalance still happens **exactly as today** (same trigger, same conditions, same silence).

## Acceptance criteria

- Done when completing a workout done in place of another day plays the same completion celebration and ends with the app on the Home tab, matching a normal finish.
- Done when the automatic week rebalance still fires in exactly the same situations as before, silently, with unchanged results (user: "the rebalancing should stay the same").
- Done when normal (non-moved) workout finishes are completely unchanged.
- Done when the finish flow is glitch-free if the rebalance changes the visible week around the animation (no crash, no blank/broken week card; end state correct — Assumption A7).

## Scope and constraints

- The rebalance itself (when it runs, what it does, its silence) is OUT of scope — do not alter it.
- Standing constraints: build via `./build.sh`; no commits/releases unless asked; no on-device UI tests — verify via unit tests.

## Assumptions (user may override)

- A7: it is acceptable for the visible week to update during/after the celebration as the rebalance lands, as long as nothing looks broken and the final state is correct.

## Considerations for whoever builds it (surfaced, not decided)

- After a move, the chip states change (source day cleared, today done) — the celebration should reflect today's chip sensibly.
- Same-file hazard with items 01/07 (log-workout screen) — Cluster A sequencing applies.

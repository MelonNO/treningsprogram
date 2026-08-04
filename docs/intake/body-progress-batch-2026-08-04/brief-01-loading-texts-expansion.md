# Brief 01 — Loading-text library: 150 messages, random rotation

Type: Improvement
Cluster: Independent (may run in parallel with Cluster A)

> Outcome-only: this brief describes the end result and user experience. The "how" belongs to the orchestrator and its workers.

## Context

While an AI program generates, the app rotates short wait messages ("tips") as companion text under the real per-attempt status line. The current library is `ui/common/GenerationTips.kt` — **12 messages**, cycled in fixed list order via an incrementing index. Four surfaces use it: Program tab full generation, Program tab single-day regeneration, Settings AI test, and the first-time setup wizard.

## What the user wants (end result)

- The message library grows to **150 messages**.
- Tone: **"a bit of it all"** — a mix of training tips, app facts, encouragement, and humor/fun. The goal is that waiting is entertaining.
- Messages appear in a **random order, so the sequence is different every time** — no more fixed 1→2→3 cycle.
- All four existing surfaces keep showing the rotating text exactly as today (same cadence, same placement, real status line untouched); only the content pool and ordering change.

## Acceptance criteria

- Done when the library contains 150 distinct messages spanning tips, app facts, encouragement, and humor.
- Done when two consecutive generation waits show the messages in different orders (random sequencing observable).
- Done when all four wait surfaces draw from the same 150-message pool.
- Done when the per-attempt status line (e.g. attempt progress) is unaffected — the rotating text remains purely companion copy.

## Scope and constraints

- **In scope:** message content authoring; rotation-order randomization.
- **Out of scope:** changing rotation timing/cadence, wait-screen layout, or the status-line mechanics.
- Messages must remain truthful about what the app actually does (no invented features in "app facts").

## Decisions baked in

- 150 messages (user-specified count).
- Mixed tone ("do a bit of it all").
- Random sequence every time.

## Assumptions (user may override)

- **A3:** randomization = shuffle so no message repeats until the whole pool has been shown once in that wait; a fresh shuffle each wait. (Delegated decision — any equally "different every time" scheme that avoids obvious immediate repeats is acceptable.)

## Considerations for whoever builds it

- `GenerationTips` is deliberately a pure, unit-testable object; randomization should stay testable (seedable or injectable order).
- The four call sites each keep their own incrementing index today — random ordering must work coherently at each site.

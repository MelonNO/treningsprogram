# Brief N4 — Logged effort trends feed program generation

**Type:** Feature (training optimality; prompt-side)
**Cluster:** Standalone worker — sole `AiRepository` item in this batch

> Outcome-only brief: describes the end result and user experience. The "how" belongs to the orchestrator and its workers.

## Context
Per-set effort is logged (`WorkoutSet.rpeLabel`) and already drives per-muscle recovery, and the generation prompt now names stalled lifts (stall block + deload context in `AiRepository`). But the AI still cannot see how hard the work *felt*: the prompt's history lines are `reps×weight` only, and the exercise-trends block carries no effort signal. This is the confirmed remaining half of backlog item B2.

## What the user wants (end result)
1. When generating a program, the AI receives a compact per-lift effort signal derived from recently logged set efforts (e.g. "Bench Press: recent working sets mostly hard, trending harder"), alongside the existing trends/stall context.
2. The intended effect on the product: generated weeks moderate progression on lifts the user logs as grinding, and push lifts consistently logged easy — instead of treating all trends as effort-neutral.
3. Sets without an effort label simply contribute nothing (much history predates effort logging); the signal appears only where there is real data.
4. Prompt size stays disciplined: a few lines, not a per-set dump.

## Acceptance criteria
- Done when the built generation prompt contains the effort-trend signal for lifts that have recent effort-labelled working sets, and omits it (cleanly, no empty header) when none do — unit-tested on the prompt-builder output, off-API.
- Done when warm-up sets never contribute to the signal.
- Done when prompt assembly for users with zero effort-labelled history is byte-identical in structure to today's (no regression for old data).
- Existing prompt-related unit tests still green; no change to the response format, validation gate, or retry ladder.

## Scope and constraints
- **In scope:** prompt-side signal derivation + inclusion, unit-verifiable.
- **Out of scope:** any change to the deterministic time gate, validator, retry ladder, or response schema; any new hard rule forcing the AI's hand (effort is context, like body weight was in R3).
- **Frugal live-API (hard):** correctness of *inclusion* is proven by unit tests only. Whether generation quality actually improves is judged by the user on-device over normal use — do NOT run live A/B sweeps.
- Standing: build via `./build.sh`; no commits/releases unless asked; no on-device/automated UI tests.

## Assumptions (user may override)
- **A-E1:** effort enters as *context* (soft guidance), not as new HARD reject rules in the validator.
- **A-E2:** signal window ≈ the same recent-history horizon the trends block already uses (no new lookback setting).

## Considerations for whoever builds it
- `rpeLabel` is a string label — derivation must tolerate the label vocabulary actually written by the logging UI (ground in code, don't assume numeric RPE).
- This is the only item in the batch touching `AiRepository` — no intra-batch contention, but the file is large and heavily commented; keep the diff surgical.

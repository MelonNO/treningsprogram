# Brief 02 — Re-evaluate workout generation: findings-and-proposals report (evaluate-first, report ONLY)

Type: **Investigation / evaluate-first** — produces a report, not code changes
Cluster: independent — no file overlap with brief 01

> Outcome-only brief. Describes the end result — the "how" belongs to the orchestrator and its workers.

## Context

The AI weekly-program generation lives in `data/repository/AiRepository.kt` (~2,400 lines): prompt building, SSE streaming, retry/deadline handling, JSON extraction/repair, duration gating, REST-first auto-trim, peer-review validator, single-day regeneration. It has accreted through many targeted fix rounds (v1.10.x time-budget/retry/efficiency fixes, v1.13.0 prompt+validator overhaul, v1.17.x long-session fixes, and more — see `docs/intake/generation-*` and `docs/intake/long-session-fix-2026-07/`). The user has **no specific current complaint** — this is a general health-check: after so much patching, take a fresh end-to-end look and find what is worth improving.

## What the user wants (end result)

A **written report** — findings and concrete proposed improvements — that the user reviews and approves **before anything is built**. Nothing is implemented under this item; approved proposals become future intake/build items.

The evaluation should look across the whole generation experience, for example: plan quality levers (exercise selection, weights, variation, structure), reliability (failure/retry/timeout behavior), speed, prompt and validator design, internal consistency of the accumulated fixes (rules that now fight each other), and maintainability of the pipeline. Each finding should come with a proposed improvement, its expected benefit, and its risk/effort — so the user can pick.

**The 120-minute generation ceiling is in scope** (user chose to include it). It is a known open item awaiting a product decision; the report should present it with options and a recommendation, framed for the user to decide.

## Acceptance criteria

- Done when a report exists covering the generation pipeline end-to-end, with concrete findings and per-finding proposal + benefit + risk/effort.
- Done when the report addresses the 120-minute ceiling with options framed as a product decision for the user.
- Done when the evaluation made **zero live Anthropic API calls** and changed **zero code** (report only).
- Done when the report is presented to the user and no proposal is implemented under this item.

## Scope and constraints (HARD)

- **Zero live API calls.** The evaluation is code-, prompt-, and history-review only. A past sweep drained the user's API credits AND monthly spend limit. If the evaluator concludes a live run is genuinely needed to decide something, that goes back to the user as a **separate explicit request with a cost estimate** — never assumed, never bundled.
- **Adaptive-thinking stays off the table** — it was tried and explicitly rejected; do not re-propose it.
- Report only: no code edits, no "quick fixes along the way," no commits.
- Standing: no on-device tests unless asked.

## Decisions the user has made (settled — do not re-ask)

- Report-first, user approves before any build work (explicitly chosen over fix-as-you-go).
- 120-minute ceiling: include in the evaluation.
- Zero live API calls to begin with.

## Considerations for whoever evaluates

- The `docs/intake/generation-*` folders and shipped-history memory record *why* current trade-offs exist (e.g. the strict duration gate, no-retry-on-timeout, JSON-first prompt). Proposals should engage with those reasons, not rediscover or silently undo them.
- Watch for rule interactions: many rounds of steering text, gates, and trims now coexist in one prompt/validator path — contradictions or dead rules are prime findings.
- `StatsRecomputer` merge-parity and other cross-system invariants constrain some kinds of change; flag any proposal that touches them.

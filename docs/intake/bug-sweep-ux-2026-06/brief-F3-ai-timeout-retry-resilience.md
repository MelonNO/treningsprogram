# F3 — Fix: AI network timeout / retry resilience (no more silent hangs)

**Type:** Bug fix / resilience (cause known)
**Phase:** 1 (bug sweep & fixes)
**Cluster:** SW-B (program & generation) — see INDEX.
**Outcome-only:** Describes the desired end result, not the implementation.

> **DECISION (recorded):** the user accepted **P-1** — make AI timeout/retry resilience a first-class fix item.

---

## Context

Every AI flow (program generation, weekly adaptation, B1 coach summary, B2 rationale, E2 stall-triggered deload) rides the same OkHttp client with a pre-existing **120s network timeout**. This timeout already bit the v1.8.0 E2 **live deload** path on the Waydroid harness — the call did not complete and the user-facing result was effectively a **silent hang** rather than a clear outcome. Long model responses on a slow connection are realistic, so this affects multiple features at once.

## Current (incorrect) behavior

A slow or long-running AI request can hit the timeout and leave the user with a silent/indefinite "nothing happened" state — no clear success, no clear failure, no retry — across any of the AI flows.

## Correct behavior

When an AI request is slow or fails, the user gets a **clear, recoverable experience**: the app waits a reasonable amount of time, retries transient failures, and on ultimate failure shows an explicit "AI is taking a while / it didn't go through — try again" state rather than hanging silently. Successful slow calls still complete.

## Acceptance criteria ("Done when …")

- **Done when** an AI request that would previously hang silently instead ends in a clear, user-visible state (in-progress indication, then success, or an explicit failure with a retry path).
- **Done when** transient failures are retried at least once before surfacing failure, and a legitimately slow-but-successful response is allowed enough time to complete.
- **Done when** the improved behavior applies consistently across all AI flows (generation, adaptation, coach summary, rationale, deload), since they share the network layer.
- **Done when** the E2 live-deload path that previously failed on-device now completes or fails gracefully with a retry, verified on the harness.

## Scope & constraints

- **In scope:** timeout duration, retry-on-transient-failure, and the user-facing in-progress/failure/retry experience for AI calls.
- **Out of scope:** changing the prompt, the model, or response parsing (those are S3); offline queueing of AI requests.
- **Coordination:** touches the **shared network layer / `AiRepository` seam** — the hottest seam in the batch. **Sequence this as its own unit and land it before (or coordinated with) the S3 sweep**, which relies on the timeout behavior being defined. Do not let F3 and S3 edit `AiRepository` blind concurrently.
- **Cross-cutting:** build via `./build.sh`; no commits/releases unless asked; on-device verification required (SEQUENCE.md).

# B10 — AI prose-instead-of-JSON: prevent stall + recover

**Type:** Bug / hardening
**Cluster:** AI generation seam (with B08, B09) — sequence FIRST (foundation)
**Outcome-only:** describes the desired end result, not the implementation.

## Context
When generating a program, the AI is supposed to return a JSON plan. The user has hit cases where the model instead emits a long stretch of planning/reasoning prose ("I'll work through this systematically before generating the JSON…"), runs very long, and the response gets cut off **before** any usable JSON appears. When that happens the app cannot read a plan and **generation stops** — it neither produces a plan nor surfaces a clear failure; it stalls. The user supplied two captured examples, both of which are walls of planning prose that end mid-reasoning without ever emitting JSON.

This is the same failure class as the prior intermittent full-regeneration parse failure (the S3 parse-hardening work) and lives on the `AiRepository` generation seam.

## What the user wants (end result)

### Recover (when a bad response arrives)
- A response with **no parseable JSON** must be treated **exactly like a rejected/not-accepted plan** — it counts as a failed attempt and the app retries, up to the normal attempt limit (the same retry path used today for quality/duration failures).
- A response that is clearly **truncated / cut off** (ran out of room before completing) must **also** be treated as a rejected/retryable attempt.
- If **all** retries come back unusable, the user sees a **clear error** ("couldn't generate a valid plan — please try again") — never a silent stall or hang.

### Prevent (reduce how often it happens) — without sacrificing quality
- The goal is that the app can **reliably read a plan from the response**. It is **acceptable** for the model to reason/think first and *then* produce the JSON, **as long as the app can extract that JSON**. Do **not** strip the model's reasoning if doing so costs plan quality — the reasoning helps satisfy the hard constraints (time budget, injury gating, blacklist, safety caps).
- Prevention should aim at making the JSON reliably present and extractable (e.g. enough room for the response to actually reach the JSON, robust extraction, retry on failure) rather than forbidding the model from thinking.

### Priority order (explicit, confirmed)
1. **High-quality workout plan** (most important).
2. **Fewer rejects/retries.**
3. Ideally never fails.

**Quality outranks reject-rate.** Steer prevention toward keeping the model's reasoning and guaranteeing extractable JSON — not toward removing reasoning to cut retries.

## Current vs correct behavior
- **Current:** a prose-only / no-JSON / truncated response causes generation to abort (a thrown "no JSON found" error that escapes the normal per-attempt rejection path), so it does not retry and the user is left with a stall.
- **Correct:** such a response is treated as a rejected attempt and retried; if retries are exhausted, a clear user-facing error appears. Reasoning-then-JSON responses that *are* extractable succeed normally.

**Diagnose first:** confirm exactly how a no-JSON / truncated response currently escapes the retry path (vs how a quality/duration rejection stays inside it), so the fix routes it through the same rejection/retry handling.

## Acceptance criteria
- Done when a response containing no parseable JSON is treated as a rejected attempt and retried, the same as a quality rejection.
- Done when a clearly truncated/cut-off response is likewise treated as retryable.
- Done when exhausting all retries shows a clear, user-visible failure message rather than a silent stall.
- Done when a response that reasons first and then includes valid JSON is accepted (the JSON is extracted and used) — reasoning is not required to be absent.
- Done when, across real generations, the app no longer stalls on a prose/non-JSON/truncated response; it either produces a valid plan or surfaces a clear retry/error state.
- Done when plan quality is not degraded in service of reducing retries (quality is the top priority).

## Scope and constraints
- In scope: how the generation flow handles non-JSON / truncated AI responses (retry + clear failure), and prevention that keeps reasoning while ensuring extractable JSON.
- Out of scope: removing or suppressing the model's reasoning at the cost of plan quality.

## Decisions baked in
- No-JSON and truncated responses are both retryable (treated as rejected attempts).
- Clear user-visible error after retries are exhausted.
- Reasoning-then-JSON is acceptable as long as the JSON is extractable.
- Priority: quality > fewer rejects > never fails.

## Assumptions (user may override)
- **[A3]** "React as if it was a not-accepted response" maps to the existing per-attempt rejection/retry path (currently used for quality/duration failures), reusing its attempt limit and clear-failure surfacing.

## Considerations for whoever builds it
- Land this **before or together with** B08/B09 so the new generation paths they add inherit this robust handling rather than reintroducing the stall.
- This builds on the prior S3 parse-hardening and the F3 timeout/retry resilience work — extend that seam rather than working around it.
- Standard cross-cutting constraints apply (build via `./build.sh`; no commits/releases or UI tests unless asked).

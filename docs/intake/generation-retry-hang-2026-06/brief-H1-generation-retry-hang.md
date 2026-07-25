# H1 — "Generate AI Program" must never hang on "Attempt N of 3"; it must always reach a saved plan or a clear error

**Type:** Bug (generation reliability — indefinite/apparent-infinite stall)
**Cluster:** AI generation seam (`AiRepository.generateAdaptedProgram` + the generation prompt) — same hot seam as v1.10.0 (B08/B09/B10) and v1.10.1 (G1). Same worker as **H3** and **H2** (same file).
**Pairs with H3:** the user wants **both** halves — "never hang AND get a plan." H1 is the *never-hang / always-terminate-visibly* half; **H3** is the *generation actually succeeds (days land in-window, the gate passes)* half. Build them together.
**Priority:** HIGH — this blocks plan generation entirely for the affected user (100% reproducible).
**Outcome-only:** describes the desired end result, not the implementation. Root cause is **unconfirmed** — diagnose first. The exact fix belongs to the orchestrator.

## Context
On a real device (build **1.10.2**, the current shipped build), tapping **"Generate AI Program"** on the **Program tab** starts the normal AI generation flow (which routes through the shared `AiRepository.generateAdaptedProgram` 3-attempt loop). The first generation call succeeds and returns a complete, well-formed 5-day plan, but the app then **gets stuck on the progress indicator showing "Generating / Attempt 2 of 3" and never finishes** — for the affected user, indefinitely (observed stuck for roughly 8+ minutes; the user's last generate attempt was logged at 02:33:52 and the Prompt-Log screenshot was taken at ~02:42).

This reproduces **100% of the time** for this user.

**Primary evidence (preserved in `evidence/` beside this brief):**
- `evidence/prompt_log_screenshot.png` — the in-app **Prompt Log** (Profile tab) showing exactly **one** entry: `GENERATE ATTEMPT 1  2026-06-27 02:33:52`. There is **no** `GENERATE ATTEMPT 2`/`3` entry and **no** quality-review ("verify") entry.
- `evidence/attempt1_prompt.txt` — the generation prompt that produced that attempt (copied from the in-app log; the user's paste was truncated at the very end, but the body is intact).
- `evidence/attempt1_response.json` — the model's response to attempt 1: a **complete, well-formed 5-day plan** (rationale + 5 day objects with exercises).

**What the user reported (verbatim answers to intake questions):**
1. Never saw the on-screen text "Reviewing plan for quality…".
2. "no it says generating 2 of 3 forever even though it only generated the first one and nothing else" — i.e. **no error message at all**; the UI is stuck on the attempt-2 progress text.
3. The Prompt Log shows **only** the single `GENERATE ATTEMPT 1` row.
4. Entry point: the **"Generate AI Program"** action on the Program tab.
5. Build **1.10.2**.
6. Fails **every time**.

## Current vs correct behavior
- **Current (incorrect):** The first generate call returns a plan; the app advances the progress indicator to "Attempt 2 of 3" / "Generating 2 of 3"; then nothing further happens — **no second generation attempt is ever recorded, the quality-review step never runs, no error or timeout is shown, and no plan is saved.** The user is left on a frozen progress indicator with no resolution.
- **Correct:** Generating a plan must **always reach a terminal, user-visible outcome within a short, bounded time** — either:
  - a **saved, usable plan** (with the quality-review step running for any plan that passes the deterministic pre-checks), or
  - a **clear, actionable error message** the user can see and act on (e.g. a network/timeout/rate-limit problem, or "rejected after all attempts").

  It must **never** leave the user on a "Generating / Attempt N of 3" indicator that does not resolve.

## Diagnose first (leads grounded in the code — NOT conclusions; confirm before acting)
**The trigger sequence (confirmed):**
- Attempt 1 returns a complete, rule-shaped plan, but **every one of its 5 days estimates UNDER the strict 40-min floor** by the app's own `WorkoutTimeEstimator` — measured from `evidence/attempt1_response.json`: day 2 ≈ 38 min, day 3 ≈ 34, day 5 ≈ 34, day 6 ≈ 39, day 7 ≈ 37 (target 50, accepted window 40–60).
- So the **deterministic per-day time-budget gate rejects attempt 1**. Because a deterministic check failed, the LLM quality-review ("verify") step is **correctly skipped** — which is exactly why the user never saw "Reviewing plan for quality…" (answer #1) and why there is no verify entry in the log.
- The loop then advances to **attempt 2** (the progress indicator updates to "Attempt 2 of 3"). **This under-time rejection is the same time-budget seam as v1.10.1/G1; the prompt steering still does not reliably land days in-window.** It is the *trigger*, not the new defect.

**The new defect (root cause unconfirmed — this is the work):**
- The loop never gets past attempt 2: attempt 2's prompt is **never written to the Prompt Log** (that write happens immediately after the attempt-2 generate call returns), so the stall is **at or just before the attempt-2 generate call**, after the UI was already updated to "Attempt 2 of 3".

**Important nuance — do NOT assume "there is no timeout":**
- The network client is **not** missing timeouts. It is configured with connect 30s / read 180s / write 30s and a hard **`callTimeout` of 240s**, and each generate call is wrapped in a **2× retry** (`withAiRetry`). So a fully-stalled attempt-2 *network* call would eventually error out after up to ~2×240s ≈ **8 minutes** — which sits right at the observed ~8-minute window, and the resulting error (a transient/IO failure) would normally surface as a "network error / timed out" message.
- Therefore the orchestrator must determine **which of two shapes** this actually is:
  - **(a) a genuinely unbounded hang** in non-network code on the retry path (a coroutine/flow that suspends without resuming, an unbounded loop, a deadlock, etc.), or
  - **(b) a very long but eventually-bounded network stall** on the attempt-2 call that merely *feels* infinite — in which case the additional question is **why the eventual error is not reaching the user** (note: the user was on the Prompt Log / Profile screen, not the Program tab, when observing the stall, so any error surfaced on the Program tab may have gone unseen) and **why attempt 2 stalls deterministically when attempt 1 succeeded**.
- Either way the user-facing outcome is unacceptable and must be fixed.

**Candidate areas to investigate (don't assume any one is the cause):**
- Whether the attempt-2 generate call stalls at the network layer, and if so why it does so deterministically on attempt 2 but not attempt 1; whether immediate, no-backoff retry right after attempt 1 contributes.
- Whether the eventual failure is actually surfaced to the user on whatever screen they are on, or silently swallowed / shown only transiently.
- Whether any coroutine / `StateFlow` / suspension point on the retry path can suspend and never resume.
- Whether the progress indicator can be left showing "Attempt N of 3" after the underlying work has actually ended.

## Acceptance criteria (observable)
- **Done when** "Generate AI Program" (and the shared generate flow it routes through) **always reaches a terminal outcome within a short, bounded time** — it never sits on "Generating / Attempt N of 3" without resolving.
- **Done when** a successful generation **saves a usable plan**, and the **quality-review step runs** for any plan that passes the deterministic pre-checks.
- **Done when** a generation that cannot succeed ends with a **clear, actionable error the user can see** (network/timeout/rate-limit/"rejected after all attempts"), surfaced **promptly** — not after minutes of a frozen counter, and on the screen the user is actually looking at.
- **Done when** the user's 100%-reproducible case **no longer hangs**: it either yields a saved plan or shows a clear error quickly.

## Scope and constraints
- **In scope:** making the generation/retry flow reach a **bounded terminal state** and **surface its outcome to the user**, for the "Generate AI Program" entry point and (because the cause lives in the shared `generateAdaptedProgram` flow) every generation entry point that shares that seam.
- **Out of scope / hard constraint (carried forward from v1.10.1/G1 — do not reopen):** keep the per-day time-budget gate **strict**. Do **not** add a "save the best attempt anyway" / salvage / never-discard fallback, and do not widen/loosen the window. Fixing the hang must **not** smuggle in a salvage path that saves an out-of-window plan.
- **Standard cross-cutting constraints:** build via `./build.sh` (not `./gradlew`); no commits/releases unless asked; no on-device/automated UI tests unless asked.

## Decision resolved (user confirmed 2026-06-27)
- The user wants **both** halves of the goal: never hang **and** actually get a usable plan. The "generation actually succeeds" half is now an explicit, in-scope item — see **H3** (`brief-H3-generation-underfill-timebudget.md`). H1 remains the *never-hang / always-terminate-visibly* half; its required outcome — never hang, always end with a saved plan or a clear, visible error — must hold **regardless** of whether H3 fully succeeds at landing days in-window.

## Assumptions (user may override)
- **[H1-A1] Entry-point scope.** The fix is expected to land at the shared `generateAdaptedProgram` flow and thereby benefit every generation entry point, even though the user only exercised "Generate AI Program". Acceptance is judged on that entry point plus the shared regenerate/setup paths.
- **[H1-A2] The under-time rejection is the trigger, not the bug to fix here.** It is treated as a known-class issue (the v1.10.1/G1 seam). The defect this brief targets is the **non-terminating / apparently-infinite stall** that follows it.
- **[H1-A3] Acceptance is "bounded terminal outcome + visible result," not a guarantee that generation always succeeds.** A run that genuinely cannot produce an in-window plan may still end in a clear "rejected after all attempts" error — what it may never do is hang.

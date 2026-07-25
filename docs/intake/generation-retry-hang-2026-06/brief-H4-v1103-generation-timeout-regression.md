# H4 — v1.10.3 REGRESSION: large AI requests (full program + weekly summary) time out at 180s and fail terminally with nothing logged

**Type:** Bug (regression introduced by the v1.10.3 ship of H1/H3) — diagnose-first
**Cluster:** AI generation seam (`AiRepository` — the generation/retry path, the per-call timeout config, and the H3 prompt size). Same worker/seam as H1/H3/H2.
**Priority:** URGENT — the user is **fully blocked** from generating any program (100% reproducible on v1.10.3, every entry point).
**Outcome-only:** describes the desired end result, not the implementation. Root cause is a strong hypothesis but **must be confirmed against the live API** before fixing.

## Context
After updating to **v1.10.3** (the build carrying the H1/H3/H2 fixes), generating a program is **worse** than the original bug:
- Tapping **"Generate AI Program"** (Program tab), the **AI menu**, and the **training-profile popup** all stick on **"Generating"**, then after a while end with the error **"The AI request timed out. Please check your network connection and try again."** — even though the user **has a working network connection**.
- The in-app **Prompt Log is genuinely empty** — **no entry at all** is written (not even "GENERATE ATTEMPT 1"), confirmed not a "Clear" artifact.
- It started on the **first generate after updating to 1.10.3** and happens **every time**.

**Localizing evidence the user gave (verbatim answers):** "only weekly is broken, daily regen works." So:
- **Single-day "Regenerate" WORKS.**
- **Weekly coach summary is BROKEN** (same as full-generate).
- **Full program generation is BROKEN.**

This is a **regression from the H1/H3 changes in this very batch** — it must be fixed before the batch can be considered done.

## Current vs correct behavior
- **Current (incorrect):** Full program generation and the weekly coach summary **always fail with a timeout** ("The AI request timed out…") under a normal, working network, logging nothing; the user cannot generate a program at all. Single-day regenerate still works.
- **Correct:** Full program generation **and** the weekly coach summary must **complete and save/produce a result under a normal network** — a large-but-normal response must **not** spuriously time out — and the attempt must be **logged**; or, if a generation genuinely cannot complete, it must fail with a **clear, correct, prompt** error. And the fix must **not reintroduce the multi-minute "Generating N of 3" hang** that H1 was added to kill.

## Diagnose first — leading hypothesis (grounded in code; CONFIRM against the LIVE API before fixing)
The unifying factor across the broken paths (full-generate + weekly summary) vs. the working one (single-day) is **request/response size and latency**, not connectivity (single-day proves the network/API-key/endpoint are fine).

Grounded code facts that fit the symptom exactly:
- The terminal message **"The AI request timed out…"** is the `SocketTimeoutException` branch of `friendlyAiErrorMessage` — i.e. the **OkHttp read timeout (180s)** is being exceeded. (It is *not* the H1 360s overall-deadline message, and not the 240s callTimeout — the **180s read timeout fires first**.)
- **H1 made a generate-call timeout terminal:** full-generate now retries with `isTransientGenerationError`, which is `isTransientAiError(t) && t !is SocketTimeoutException` — so a SocketTimeout on the generate call is **not retried**, it fails immediately. (Weekly summary still uses the *default* retry, so it retries once on timeout — ~2×180s — then fails the same way. Single-day also uses the default retry but its request is small and returns under 180s, so it succeeds.)
- **The Prompt Log writes only after a response is received** (the `promptLog.add("generate_attempt_…")` / `promptLog.add("weekly_summary")` calls run *after* the API call returns). A call that times out **never logs anything** — which is exactly why the log is now empty, whereas in v1.10.2 the first call returned within 180s and therefore logged "ATTEMPT 1".

**Why it regressed in v1.10.3 (hypothesis to confirm):** in v1.10.2 the first full-generate call returned within 180s (it logged ATTEMPT 1). In v1.10.3 the first call now exceeds 180s. The plausible mechanism is **H3's time-budget steering asking the model for fuller, larger plans** (more exercises/sets, longer notes, "ADD work to any under-time day", aim for 50 min) → **larger/slower responses** that cross the 180s read timeout — and **H1's no-retry-on-timeout** turns what used to be a retried (sometimes-recovering) timeout into a hard terminal failure. Possibly compounded by model/endpoint latency. **This must be verified with real measurement, not assumed.**

**The orchestrator MUST reproduce against the live API:**
- A **live Anthropic API key** has been provided by the user at **`/home/migul/treningsprogram/claude k`** (gitignored + untracked). **Do not print it, echo it, or copy it anywhere committable.**
- Run the **actual full-generate prompt** (the canonical `buildPrompt` output for the repro profile; a captured sample is in `evidence/attempt1_prompt.txt`) through the **real endpoint** and **measure response latency and size** to confirm whether/why 180s is exceeded. Do the same for the weekly-summary prompt.
- Verify the chosen fix **end-to-end against the live API** (a real generation completes, saves, and logs within a reasonable time) **before any re-ship**.

**Open questions for the orchestrator to resolve (do not pre-decide):**
- (a) Was the **weekly coach summary already failing before v1.10.3**, or is it newly broken? Its retry policy and prompt were **not** changed by H1/H3, so its breakage may be **pre-existing/latent** or **environmental** (model latency), not caused by this batch — determine which, as it changes the fix's scope.
- (b) What is the right way to make large generations succeed **without** reintroducing the hang — e.g. a larger read/call timeout for the large-generate path, a **bounded** retry on timeout, response streaming, and/or reducing requested output size. (Surfaced as options, **not** prescribed; whichever is chosen must keep the never-hang guarantee from H1 and the strict gate from H3.)
- (c) Confirm the "Prompt Log writes only on response" behavior and decide whether an attempt should be made observable **even when it times out** (so a future timeout isn't invisible in the debug log).

## Acceptance criteria (observable)
- **Done when** **full program generation** AND the **weekly coach summary** reliably **complete and save/produce a result under a normal, working network** — a large-but-normal response **does not spuriously time out** — and the attempt is **logged** in the Prompt Log.
- **Done when**, if a generation genuinely cannot complete, it ends with a **clear, correct, prompt** error (and the error is **accurate** — not "check your network" when the network is fine and the real cause is response size/latency).
- **Done when** the fix is **verified end-to-end against the live API** (a real full generation for the repro profile completes, saves, and logs) before any re-ship.
- **Done when** the fix does **NOT reintroduce** the multi-minute "Generating N of 3" hang (H1's guarantee holds) and does **NOT** weaken or bypass the strict time-budget gate (H3's constraint holds).
- **Done when** single-day regenerate continues to work (no collateral regression).

## Scope and constraints
- **In scope:** making large AI generations (full program + weekly summary) survive normal response latency/size and reach a saved/produced result or a correct prompt error, across the shared generation seam.
- **Out of scope / hard constraints carried forward:** do **not** reintroduce the unbounded/multi-minute hang (H1); keep the per-day **time-budget gate strict** with **no salvage/fallback/relaxation** (H3); H3 stays **prompt-side** for the under-fill steering.
- **Security:** the live key at `/home/migul/treningsprogram/claude k` is for local reproduction/verification only — never commit, print, or relocate it.
- **Standard cross-cutting constraints:** build via `./build.sh` (not `./gradlew`); no commits/releases unless asked; on-device/automated UI tests only if the user asks (live-API reproduction here is a deliberate, user-enabled exception for verification).

## Decisions baked in (user-confirmed regression facts, 2026-06-27)
- Version 1.10.3; Prompt Log genuinely empty (no Clear); it ends after a while with "The AI request timed out…"; the user has a working network; single-day regen works, weekly summary + full-generate are broken; started on the first generate after the update, every time.

## Assumptions (user/orchestrator may override)
- **[H4-A1]** The 180s read timeout being exceeded by large responses is the leading cause — to be **confirmed by live measurement**, not assumed; the fix should target the *measured* cause.
- **[H4-A2]** The error message shown to the user should be **accurate** to the real cause; "check your network connection" is misleading when the network is fine and the cause is response latency/size. (Improving the message is desirable but secondary to actually letting large generations succeed.)
- **[H4-A3]** This brief supersedes the assumption in H1/H3 that the shipped fix was complete; H1's never-hang and H3's under-fill outcomes still stand and must not be regressed by the H4 fix.

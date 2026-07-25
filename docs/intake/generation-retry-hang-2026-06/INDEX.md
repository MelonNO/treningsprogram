# Intake — Generation retry hang + chronic under-fill (post-v1.10.2)

**Prepared for:** Project-lead orchestrator
**Source:** User bug report ("when trying to generate a workout plan it is not able to get the verification prompt and it just ends up not doing anything"), relayed and clarified via the coordinator. Primary evidence supplied by the user (Prompt-Log screenshot + the attempt-1 prompt and response).
**Status:** FINAL — problem statement and scope **confirmed by the user**. Ready for the orchestrator. **UPDATED 2026-06-27 with H4, a v1.10.3 regression follow-up** (H1/H2/H3 shipped as v1.10.3 and introduced a new timeout failure — see H4).

## Confirmation note
The user **directly confirmed** the restated problem statement ("Yes, that's right") and the scope ("Both — never hang AND get a plan"), relayed verbatim through the coordinator. This is a direct sign-off on the understanding and scope. The **H4 regression facts** are likewise **user-direct-confirmed** (verbatim answers describing the v1.10.3 behavior). **Creating/finalizing these documents does not itself dispatch the orchestrator** — that is a separate coordinator/user action.

## v1.10.3 regression (H4) — read this first
H1/H2/H3 were shipped as **v1.10.3**, which introduced a **new, worse failure**: full program generation **and** the weekly coach summary now **time out at the 180s read timeout** ("The AI request timed out…") under a working network, **logging nothing**, so the user is **fully blocked** (single-day regenerate still works). This is captured as **H4** and must be fixed before this batch is done. H4 supersedes the prior "FINAL/ready" status as the current top priority.

## Confirmed problem statement
On build 1.10.2, tapping **"Generate AI Program"** generates the first plan, then the app **freezes on "Generating / Attempt 2 of 3"** and never finishes — no second attempt is logged, the quality-review ("verification") step never runs, **no error appears, and no plan is saved**. It happens **every time**. The user wants **both**: generation must **never hang like this** (always end fast with a saved plan or a clear, visible error) **and** it must **actually succeed** (produce and save a usable plan), not merely fail cleanly.

## Items
| ID | Title | Type | Priority | Brief | Status |
|----|-------|------|----------|-------|--------|
| H4 | **v1.10.3 REGRESSION:** large AI requests (full program + weekly summary) time out at 180s and fail terminally with nothing logged | Bug (diagnose-first) | URGENT | `brief-H4-v1103-generation-timeout-regression.md` | Final — top priority |
| H1 | "Generate AI Program" must never hang on "Attempt N of 3"; always end fast with a saved plan or a clear, visible error | Bug (diagnose-first) | HIGH | `brief-H1-generation-retry-hang.md` | Shipped in v1.10.3 (regressed → see H4) |
| H3 | Generation must reliably produce a plan that PASSES the strict time-budget gate (days land in-window) — prompt-side only | Bug (diagnose-first) | HIGH | `brief-H3-generation-underfill-timebudget.md` | Shipped in v1.10.3 (likely contributor to H4) |
| H2 | Generation prompt's exercise blacklist is corrupted by comma-splitting names | Bug (diagnose-first) | Medium | `brief-H2-blacklist-comma-corruption.md` | Shipped in v1.10.3 |

H1 and H3 are the **two halves of one user goal** ("never hang AND get a plan"); H2 is an independent quality fix. **All three shipped as v1.10.3 and together introduced H4** — H1's no-retry-on-timeout + H3's larger responses + the unchanged 180s read timeout combine to make large generations fail terminally. **H4 is now the active work.**

## Combined acceptance (the whole batch, incl. H4)
"**Full program generation AND the weekly coach summary** reliably **complete and save/produce a result under a normal, working network** (a large-but-normal response must **not** spuriously time out), **logging the attempt**, with the days passing the strict time-budget gate and the verify step running on the passing plan; if a generation genuinely cannot complete, it ends **quickly** with a **clear, correct, visible error** (accurate to the real cause, even when the user is on a different screen than the Program tab); it **NEVER hangs** on 'Generating N of 3'; single-day regenerate keeps working; and the time-budget gate is **never weakened or bypassed**." **The H4 fix must be verified end-to-end against the live API before any re-ship** (see H4 brief; key at `/home/migul/treningsprogram/claude k` — never commit/print it).

## Evidence (durable copies in `evidence/`)
- `evidence/prompt_log_screenshot.png` — Prompt Log showing the single `GENERATE ATTEMPT 1 2026-06-27 02:33:52` row (no attempt 2/3, no verify entry).
- `evidence/attempt1_prompt.txt` — the attempt-1 generation prompt (also shows the corrupted blacklist of H2; the paste is truncated at the very end).
- `evidence/attempt1_response.json` — the attempt-1 model response: a complete, well-formed 5-day plan (the canonical fixture for H3).

## Merge / cluster + parallelization guidance
- **One worker for all three.** H1, H3, and H2 all live in `AiRepository` (H1 in `generateAdaptedProgram` + the retry path; H3 in the generation prompt's time-budget steering; H2 in `buildPrompt`'s blacklist assembly). They overlap the same file/seam, so a single worker should own them to avoid merge conflicts. Do **not** parallelize across separate workers on this file.
- **Order within the worker (updated for H4):**
  1. **H4 FIRST (URGENT)** — large generations are timing out at 180s and the user is fully blocked. Reproduce against the LIVE API (key at `/home/migul/treningsprogram/claude k`), measure latency/size, and fix so large full-program + weekly-summary requests succeed without timing out — **without** reintroducing the hang (H1) or weakening the gate (H3). H4 interacts directly with H1 (no-retry-on-timeout) and H3 (larger responses), so it must be solved together with re-verifying those.
  2. **H3 / H1 re-verify** — ensure the H4 fix still satisfies H3 (days land in-window, gate strict) and H1 (never hang; bounded terminal outcome). These shipped in v1.10.3 but must be re-checked under the H4 fix.
  3. **H2** — already shipped in v1.10.3; verify it still holds.
- **Same hot seam as prior ships:** v1.10.0 (B08/B09/B10) and v1.10.1 (G1, `docs/intake/generation-timebudget-fix-2026-06/`) changed this exact code. Re-verify the current `AiRepository` before acting — prior briefs' notes may have drifted. **Note: v1.10.1 already added heavy time-budget steering and the model is STILL under-filling — so diagnose before re-patching (H3).**

## Confirmed facts (from the user / primary evidence)
- 100% reproducible. Build **1.10.2**. Entry point: **"Generate AI Program"** (Program tab), which routes through the shared `AiRepository.generateAdaptedProgram` 3-attempt loop.
- Observable failure: stuck on "Generating / Attempt 2 of 3"; **only** `GENERATE ATTEMPT 1` in the Prompt Log; **no** verify entry; **no** error/toast; **nothing saved**. User never saw "Reviewing plan for quality…".
- Observed stuck ≈ 8+ minutes (attempt 1 logged 02:33:52; screenshot ~02:42).

## Grounded findings (context for diagnosis — not conclusions)
- **Under-fill confirmed (H3):** all 5 days of the attempt-1 plan estimate **UNDER 40 min** (38/34/34/39/37 by the app's own `WorkoutTimeEstimator`; window 40–60). So the strict per-day **time-budget gate rejects attempt 1**, which **correctly skips the quality-review step** (hence no verify, no "Reviewing plan for quality…") and advances to attempt 2. Same seam as v1.10.1/G1; the prior prompt fix still does not reliably land days in-window.
- **Possible structural tension (H3, flagged for the user via the orchestrator):** within the prompt's ~18–20 working-set-per-session cap and ≤120 s hypertrophy rest, a 6-exercise day naturally estimates ~37–42 min — so reaching 50 min may be in genuine tension with the volume caps. If diagnosis confirms this, a pure prompt-side fix may be structurally insufficient and the orchestrator must **report back to the user** rather than relax the gate.
- **The hang (H1):** the loop never gets past attempt 2 — attempt 2's prompt is never written to the log (that write happens right after the attempt-2 call returns), so the stall is at/just-before the attempt-2 generate call.
- **Do NOT assume "no timeout" (H1):** the OkHttp client has connect 30s / read 180s / write 30s / **callTimeout 240s**, and each call has a **2× retry**, so a fully-stalled attempt-2 network call would eventually error after up to ~2×240s ≈ 8 min (right at the observed window). So "infinite" may actually be "very long but eventually bounded" — the orchestrator must determine whether this is a genuinely unbounded hang in non-network code, or a long network stall whose eventual error simply isn't reaching the user (the user was on the Prompt Log screen, not the Program tab, when observing it). Both are unacceptable; both are in scope.
- `validateProgram` (the "verify" step) is **fail-open** (wrapped in `runCatching { … }.getOrElse { accepted = true }`) and only writes its log entry on a successful API call — consistent with "verify never ran" rather than "verify ran and failed silently."

## Grounded findings — H4 (v1.10.3 regression)
- **Symptom (user-direct):** on v1.10.3, full-generate + AI-menu + popup all stick on "Generating", then end with **"The AI request timed out. Please check your network connection and try again."** under a working network; Prompt Log is **empty** (no Clear); single-day regenerate **works**, weekly coach summary is **broken**; every time since the update.
- **The 180s read timeout is the operative limit:** the message is the `SocketTimeoutException` branch of `friendlyAiErrorMessage` — i.e. OkHttp's **read timeout (180s)**, not the H1 360s overall deadline, not the 240s callTimeout.
- **H1 made a generate timeout terminal:** full-generate retries with `isTransientGenerationError = isTransientAiError(t) && t !is SocketTimeoutException`, so a SocketTimeout on the generate call is **not retried** → terminal. (Weekly summary uses the *default* retry → retries once on timeout, ~2×180s, then fails the same way. Single-day uses the default retry too but its request is small → returns under 180s → succeeds.)
- **Why the log is empty:** the Prompt Log entry is written **after** the API response returns; a timed-out call returns nothing → logs nothing. (In v1.10.2 the first call returned under 180s and therefore logged "ATTEMPT 1".)
- **Unifying factor:** full-generate + weekly = **large request/response**; single-day = small. So the cause is large-response latency vs the 180s read timeout — **not** the full-generate-only helpers `buildPreviousPlanContext`/`buildBlacklistNames` (weekly doesn't use those), and **not** connectivity (single-day proves the live API works).
- **Leading regression mechanism (confirm via LIVE API):** H3's steering toward fuller/larger plans → larger/slower responses crossing 180s, made terminal by H1's no-retry-on-timeout. **Orchestrator must measure real latency/size against the live API** (`/home/migul/treningsprogram/claude k`, never commit/print) and verify the fix end-to-end before re-ship.
- **Open Qs for orchestrator:** (a) was weekly summary already failing pre-1.10.3 (its path wasn't changed by this batch — could be latent/environmental)? (b) right fix that lets large generations succeed without reintroducing the hang (timeout raise / bounded retry / streaming / smaller output)? (c) should a timed-out attempt still be made visible in the Prompt Log?

## Decision resolved (user confirmed 2026-06-27)
- **Scope = both halves.** The under-fill that forces the doomed retry (H3) **is in scope** alongside the never-hang guarantee (H1). The user wants generation to actually succeed, not just fail cleanly.

## Open escalation (may need the user later — flagged, not decided)
- **If** the orchestrator's diagnosis (H3) finds the per-session volume caps and the 50-min session target are **structurally unsatisfiable together** within the strict gate, that finding must be **escalated to the user** before any change that would touch the gate, the caps, or add a salvage path — all of which are forbidden by the hard constraint. The intake does not decide this; it flags it.

## Assumptions applied (user may veto)
- **[H1-A1]** Fix lands at the shared `generateAdaptedProgram` flow → benefits all generation entry points.
- **[H1-A2]** The under-time rejection is the *trigger* of the hang; H1 targets the non-terminating stall, H3 targets the under-fill itself.
- **[H1-A3]** H1 acceptance = bounded terminal outcome + a result the user can see (even on another screen); not, by itself, a guarantee that generation always succeeds — that is H3's job.
- **[H3-A1]** "Reliably produces a usable plan" = repro succeeds + under-time rejection class materially reduced, judged against the project's quality > fewer-rejects > never-fails ordering; not a 0%-reject guarantee.
- **[H3-A2]** Prompt-side-only is the hard constraint; a proven structural caps-vs-target contradiction is an escalation to the user, not a license to relax the gate.
- **[H2-A1]** H2 is bundled with H1/H3 because it shares the prompt-construction code, but is independent and could ship separately.

## Cross-cutting constraints
- Keep the per-day **time-budget gate STRICT** — no salvage / "save best attempt anyway" / window-widening / bypass (carried forward from v1.10.1/G1). Both the hang fix (H1) and the under-fill fix (H3) must respect this; H3 is **prompt-side only**.
- Build via `./build.sh` (not `./gradlew`). No commits/releases unless asked. No on-device/automated UI tests unless asked.

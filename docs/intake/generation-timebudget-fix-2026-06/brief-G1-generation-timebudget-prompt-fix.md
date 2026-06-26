# G1 — Generation reliably yields a usable plan; strict time-budget gate kept, fixed on the prompt side

**Type:** Bug (generation reliability)
**Cluster:** AI generation seam (`AiRepository` + the generation prompt) — standalone item, but shares the same hot seam as the just-shipped B08/B09/B10
**Outcome-only:** describes the desired end result, not the implementation. The diagnosis and the exact prompt changes belong to the orchestrator.

## Context
When the user generates a workout plan, the AI returns a plan, but the app discards it and saves nothing — the user is left with **no new plan and no clear reason why**. The user reproduced this with a real failing case: the model returned a **complete, well-formed, fully rule-compliant** plan and the app still threw it away.

The bug manifests at the two entry points the user named:
- the **popup that appears when changing the training profile** (Settings → Training), and
- the **AI menu** (Settings → AI).

Both run the same shared generation flow (`AiRepository.generateAdaptedProgram`), as do the other generation entry points (first-run setup wizard, the Program-tab regenerate actions, single-day regenerate). Because the cause lives in the shared generation seam, a prompt-side fix benefits all of them.

The supplied failing case: a **5-day** program (rest days **Mon + Thu**; trains Tue/Wed/Fri/Sat/Sun), **50-min** session target, **hypertrophy**, **intermediate**, with **injury-rehab + equipment + blacklist** constraints. This case exercises the newly-shipped rest-day scheduling (v1.10.0). The user's **exact captured prompt + exact model response** are preserved verbatim alongside this brief in **`repro-failing-generation.md`** — that is the canonical regression fixture for this work.

## What the user wants (end result)
- Generation **reliably yields a usable plan the user can actually use** — the common, otherwise-compliant case (like the supplied repro) ends with a plan **saved and in use**, not silently discarded.
- The **strict per-day time-budget gate is kept exactly as strict**: the accepted per-day duration window is **not widened or loosened**, and **no "save the best attempt anyway" / salvage / fallback** path is added. A plan that genuinely misses the window is still rejected.
- The fix is achieved on the **prompt / generation-guidance side**: the AI is steered so that its training days **reliably land inside the strict accepted time window**, so the strict gate **passes** instead of rejecting good plans.

## Current vs correct behavior
- **Current:** the model produces a complete, rule-compliant plan, but the app's strict deterministic per-day time-budget check rejects every attempt, so generation ends with **nothing saved** and the user gets **no plan and no clear outcome** ("it just silently ends up with no new plan"). The AI-side validation step is never reached because the deterministic check rejects first.
- **Correct:** for the same kind of request, the model's days **land inside the strict window**, the gate passes, and a usable plan is **saved and shown** — the strict gate is unchanged; the model simply produces plans that satisfy it.

**Diagnose first (starting lead, confirm on the fixture):** the coordinator supplied a diagnostic lead with the repro — running the app's own estimator over the captured response, **4 of the 5 training days estimate UNDER the 40-min floor** (Wed ~33, Fri ~36, Sat ~31, Sun ~33; only Tue ~42 is in-window). So every attempt misses the **deterministic per-day time-budget gate**, which short-circuits before the LLM review step ("validation never happened") and, after the attempt limit, throws and saves nothing ("plan not used"). This is a **starting point, not a conclusion** — confirm on the fixture in `repro-failing-generation.md` which gate rejects and why. The natural thing to verify: whether the in-prompt guidance the model is given to self-size each day actually steers it to land inside the **authoritative deterministic** window the app enforces — here the days come in **under** the floor, so the model's notion of "within the time budget" and the app's notion are not agreeing.

## Acceptance criteria
- **Done when** the user-supplied failing case (5-day, rest Mon+Thu, 50-min, hypertrophy, intermediate, injury-rehab + equipment + blacklist) results in a **plan that is saved and usable**, instead of being discarded.
- **Done when** the strict per-day time-budget gate is **unchanged**: the accepted duration window is the same width as before, and there is **no salvage/fallback** path that saves a plan which misses the window.
- **Done when** the fix lives on the **prompt / generation-guidance side** — the improvement is in what the model is told/asked to produce, not in relaxing or bypassing the check.
- **Done when** the systemic tendency to reject otherwise-compliant plans on the time budget is **materially reduced** across realistic requests (varied day counts and session-length targets), so generation reliably ends with a usable plan rather than an empty result.
- **Done when** plan quality (injury gating, blacklist, per-muscle/per-session volume caps, role-based rep ranges, effort/progression rules, and every other existing rule) is **not degraded** in order to hit the time window.

## Scope and constraints
- **In scope:** improving the generation prompt / guidance so the AI's training days reliably satisfy the existing strict per-day time-budget gate, across all generation entry points that share the seam (the two named entry points plus setup and the regenerate paths benefit automatically).
- **Out of scope / explicitly rejected by the user:**
  - Widening or loosening the deterministic per-day duration window.
  - Adding any "save the best attempt anyway" / never-discard-a-valid-plan / salvage fallback.
  - Changing the strictness or semantics of the gate itself.
- **Hard constraint (settled — do not reopen unless a genuine contradiction is found):** keep the time-budget check strict; fix the prompt. The user was offered the loosen-gate and never-discard-fallback alternatives and **rejected both**.

## Decisions baked in (confirmed by the user via the coordinator)
- The gate stays strict (window not widened, no fallback).
- The fix is prompt-side: make the AI reliably hit the strict window.
- The user got a good plan but nothing was saved — eliminating that "good plan thrown away" outcome is the point.

## Assumptions (user may override)
- **[G1-A1] Success threshold.** "Reliably yields a usable plan" is read against the project's already-confirmed AI-generation priority ordering (from the B10 work): **(1) high-quality plan, (2) fewer rejects, (3) ideally never fails** — quality outranks reject-rate, and a strict deterministic gate + an LLM means a 0%-failure guarantee is not the bar. Acceptance is therefore: the supplied repro now succeeds, and the *class* of "otherwise-compliant plan rejected on the time budget" is materially reduced — not "the gate never rejects again."
- **[G1-A2] Entry-point scope.** Because the cause is in the shared generation prompt/flow, the fix is expected to land once at that shared seam and thereby benefit every generation entry point, even though the user only named the training-profile popup and the AI menu. Acceptance is judged on the named two plus the shared regenerate/setup paths.
- **[G1-A3] "Silent, no plan" is the symptom, not a second request.** The user's "it just silently ends up with no new plan" is treated as the symptom that disappears once generation reliably succeeds — **not** a separate request to change failure-state messaging. The genuine all-retries-fail user-error behavior was already addressed in the shipped B10 work and is **out of scope** here (do not re-touch it, and do not turn it into a salvage path).

## Considerations for whoever builds it (surfaced, not decided)
- **The verbatim fixture is included.** `repro-failing-generation.md` in this batch holds the captured prompt and the full model response, plus the coordinator's diagnostic lead. It is the canonical regression check — the fix should make this exact case end with a saved, usable plan. (Note: the prompt's middle sections were condensed in the relay for length; the exact prompt is reconstructable from `AiRepository.buildPrompt` with the stated inputs.)
- This item edits the **same hot generation seam** (`AiRepository` + the generation prompt) that B08/B09/B10 just changed in v1.10.0. Treat it with the same care; re-verify the current code before acting (the prior batch's notes may have drifted).
- Verifying the fix on-device/end-to-end (does a real generation now save a usable plan for the repro profile?) is appropriate, but only run automated/on-device UI tests if the user asks.
- Standard cross-cutting constraints apply: build via `./build.sh` (not `./gradlew`); no commits/releases unless asked; no on-device/automated UI tests unless asked.

# H3 — Generation must reliably produce a plan that PASSES the strict time-budget gate (days land in-window) — prompt-side only

**Type:** Bug (generation reliability — chronic under-fill of session time)
**Cluster:** AI generation seam (`AiRepository.generateAdaptedProgram` + the generation prompt). Same worker as **H1** (the never-hang half) and **H2** (same file).
**Priority:** HIGH — without this, "Generate AI Program" never yields a usable plan for the affected user (every attempt is rejected on the time budget). This is the "**get a plan**" half of the user's confirmed goal.
**Outcome-only:** describes the desired end result, not the implementation. Root cause of the persistent under-fill is **unconfirmed** — diagnose first.

## Context
The same 100%-reproducible failure documented in H1 has a second half. Every attempt's plan is **rejected because its training days estimate UNDER the strict 40-min floor**. Measured from the captured attempt-1 response (`evidence/attempt1_response.json`) with the app's own `WorkoutTimeEstimator`: the five days estimate **38 / 34 / 34 / 39 / 37 min** against a target of **50** (accepted window **40–60**, ±10). So even setting the hang aside, generation cannot succeed: the model keeps producing under-filled days and the strict gate keeps rejecting them.

The user's profile for the repro (from the captured prompt): **5-day**, **50-min** sessions, **hypertrophy**, **intermediate**, with **posterior-chain emphasis**, **injury/ankle-rehab**, **equipment** and **blacklist** constraints.

**This is the same class of issue as v1.10.1/G1** (`docs/intake/generation-timebudget-fix-2026-06/`), which **already added heavy prompt-side time-budget steering** (an exact self-size formula, an explicit "a day UNDER 40 is rejected just as hard as one OVER 60", an under-fill self-check, and direction-aware retry feedback). **Despite all that, the model is still under-filling (34–39 min).** So a naïve "add more steering" re-patch is not obviously sufficient — the orchestrator must first diagnose **why the existing steering is not working** before changing it.

## What the user wants (end result)
- Generating a plan **reliably produces and SAVES a usable plan** whose training days **land inside the strict accepted window (≥40 min, aimed at ~50)**, so the strict gate **passes** and the quality-review ("verify") step then runs on the passing plan.
- Achieved **prompt-side only**: steer the model so its days reliably land in-window. The strict gate is **unchanged**.

## Current vs correct behavior
- **Current:** the model returns complete, otherwise-rule-compliant plans whose days come in **under 40 min**, so the strict per-day time-budget gate rejects **every** attempt and nothing is ever saved.
- **Correct:** for the same kind of request, the model's days land inside the strict window (~50 min, ≥40), the gate passes, the plan is saved and shown, and the verify step runs on it.

## Diagnose first — WHY is the existing steering insufficient? (investigate; do NOT prescribe)
v1.10.1 already steers hard and the model still under-fills, so the cause is non-obvious. Investigate, with the captured prompt + response as the working fixture (`evidence/attempt1_prompt.txt`, `evidence/attempt1_response.json`):
- **Is the model mis-applying / ignoring the self-size formula?** It is told the exact formula and to ADD work to any day under 40 — yet days land at 34–39. Determine whether it is computing wrong, rounding wrong, or simply not self-correcting.
- **Is the per-session volume cap structurally colliding with the 50-min target?** *(Strong candidate — flagged below.)* The prompt caps sessions at ~18–20 total working sets and (for hypertrophy) rest ≤120 s. Within those limits, days of ~6 exercises naturally estimate in the high-30s to low-40s minutes — i.e. reaching 50 min may require more sets/rest than the caps allow. If so, "ADD work until you hit 50" and "never exceed the set cap" are partially **contradictory**, and the model resolves the conflict by staying under-time.
- **Does the enforced formula under-count vs. reality**, or does the model's internal estimate diverge from the authoritative `WorkoutTimeEstimator` (e.g. how rest-between-sets, warm-ups, or the last-vs-first rep in a range is counted)?
- **Is the under-fill self-check being skipped** because the model emits JSON-first (it is told to lead with the JSON and keep reasoning brief), so it never actually runs the "if under 40, add work" pass?

**Flagged risk / potential contradiction (surface to the user, do not decide):** a quick arithmetic check supports the "caps vs target" tension — within ~18–20 working sets and ≤120 s rest, a hypertrophy day tends to land ~37–42 min, so **50 min may not be reliably reachable without breaching a cap**. If diagnosis confirms this, a **pure prompt-side fix may be structurally insufficient**, because you cannot steer the model to do something the caps forbid. The user's hard constraint is *gate stays strict, prompt-side only, no relaxation* — so if the orchestrator finds the caps and the time target are genuinely unsatisfiable together, that finding must be **reported back to the user** (it may force a product decision that the current constraint set forbids resolving silently). The orchestrator must **not** quietly relax the gate, the caps, or add a salvage path to escape this tension.

## Acceptance criteria (observable)
- **Done when** the user's repro profile (5-day, 50-min, hypertrophy, intermediate, posterior-chain, ankle-rehab + equipment + blacklist) reliably ends with a **plan SAVED** whose days all estimate **inside the strict window (≥40, ~50)**, with the **verify step running** on the passing plan.
- **Done when** the *class* of "otherwise-compliant plan rejected for being under-time" is **materially reduced** across realistic requests (varied days/week and session-length targets) — generation reliably ends with a usable plan, not an empty result.
- **Done when** the strict per-day time-budget gate is **unchanged** (window not widened/loosened; no salvage/fallback), and plan quality (injury gating, blacklist, volume caps, role-based rep ranges, effort/progression rules, every other existing rule) is **not degraded** to hit the time window.
- **Done when** the diagnosis of *why the prior steering was insufficient* is established **before** re-patching (so the fix targets the real cause), and — if a structural caps-vs-target contradiction is found — it is **escalated to the user** rather than resolved by weakening the gate.

## Scope and constraints
- **In scope:** prompt-side generation guidance so the model's days reliably satisfy the existing strict per-day time-budget gate, across all generation entry points sharing the seam.
- **Out of scope / HARD constraint (settled by the user — do not reopen):** keep the time-budget gate **strict**. **No** widening/loosening the window, **no** "save best attempt anyway"/salvage/never-discard fallback, **no** bypassing the gate. Fix is **prompt-side only**.
- **Standard cross-cutting constraints:** build via `./build.sh` (not `./gradlew`); no commits/releases unless asked; no on-device/automated UI tests unless asked.

## Assumptions (user may override)
- **[H3-A1] Success threshold.** "Reliably produces a usable plan" is read against the project's confirmed AI-generation priority ordering (quality > fewer rejects > ideally never fails): the repro now succeeds and the under-time rejection class is materially reduced — **not** "the gate never rejects again."
- **[H3-A2] Prompt-side only is the user's hard constraint**, but if diagnosis proves the volume caps and the 50-min target are structurally unsatisfiable together, that is an **escalation to the user**, not a license to relax the gate.
- **[H3-A3] Shared-seam scope.** The fix lands once at the shared generation prompt/flow and benefits every generation entry point, though only "Generate AI Program" was exercised.

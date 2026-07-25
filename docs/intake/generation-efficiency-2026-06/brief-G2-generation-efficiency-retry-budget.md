# G2 — Full program generation must reliably SAVE a gate-passing plan, and do it FASTER (Option A: strict gate, no salvage, deadline unchanged)

**Type:** Bug (diagnose-first) — generation efficiency / retry-budget exhaustion. Post-v1.10.5.
**Cluster:** AI generation seam (`AiRepository` — the generate/retry loop, the base generation prompt, and the per-day time-budget gate). Same hot seam as the prior generation ships (v1.10.1 / v1.10.3 / v1.10.4 / v1.10.5).
**Priority:** HIGH — the user is currently **blocked** from generating any program on the latest build (100% reproducible on v1.10.5).
**Outcome-only:** describes the desired end result, not the implementation. The root cause is strongly evidenced below but the **code diagnosis and fix are the orchestrator's** — do not pre-decide the implementation.

## Context
On the current shipped build **v1.10.5**, tapping **"Generate AI Program"** (Program tab) runs **three** full-generation attempts back-to-back, never produces a saved plan, and after roughly **six minutes** ends with the on-screen error **"Generation took too long and was stopped. Please check your connection and try again."** (this is the **overall-generation-deadline** message — the 360-second wall-clock cap on the whole generate flow — **not** the older SocketTimeout "AI request timed out" wording). **Nothing is saved.** **Single-day "Regenerate" still works** (it is one small, fast request).

This is the same recurring "generation fails, nothing saved" area we have iterated on across v1.10.1 → v1.10.5, but the evidence shows a **different, compound root cause** from the prior fixes (it is not the under-fill of v1.10.1/G1, not the Attempt-2 hang of v1.10.3/H1, and not the 180s read-timeout of v1.10.4/H4 — those shipped). See `docs/intake/generation-retry-hang-2026-06/` and `docs/intake/generation-timebudget-fix-2026-06/` for that prior lineage.

## Current (incorrect) vs correct behavior
- **Current (incorrect):** Full program generation runs three attempts, takes ~6+ minutes, and ends on the 360-second overall deadline with **nothing saved** and the quality-review ("validation") step never reached. Even on runs where a valid plan is briefly produced mid-flight, it is discarded and the user gets nothing. The whole experience is **far too slow** even when it could eventually succeed.
- **Correct:** Full program generation reliably **produces and SAVES a fully gate-passing, in-window plan**, and does so **noticeably faster** — minimal wasted attempts/tokens/latency — comfortably inside the existing time budget, with the quality-review step reachable again once a plan clears the gate. Single-day Regenerate keeps working.

## Diagnose first — what the user's evidence shows (confirm in code; the fix is the orchestrator's)
The user's Prompt Log export from one failed run (v1.10.5) shows the failure is a **compound time/budget exhaustion**, not a single defect:

- **Three attempts were logged** (v1.10.5's log-on-failure works):
  - `GENERATE ATTEMPT 1` @ 18:25:18
  - `GENERATE ATTEMPT 2` @ 18:26:26 (~68 s after #1)
  - `GENERATE ATTEMPT 3` @ 18:30:53 (~4.5 min after #2)
  - No `validate`/peer-review entry — it was **never reached**.
- **Attempt 1 wasted its whole turn rambling.** The captured response opens "I'll plan silently, then output the JSON." and then emits **pages of visible chain-of-thought** — per-day split decisions, blacklist cross-checks, exhaustive set-by-set time-budget arithmetic — and **runs out of output room before emitting any JSON** (cut off mid-sentence at "Actually the injury rules say I MUST i"). So attempt 1 produced **no plan** and burned ~68 s + a large token budget.
- **Attempt 2 actually SUCCEEDED at producing a complete, valid JSON plan.** The retry prompt injected a "PREVIOUS PLAN REJECTED — FIX THIS FIRST" note telling the model to **lead with the JSON and keep reasoning brief** — and it worked: a full rationale + 5-day plan, JSON-first. **But that good plan was then rejected by the strict per-day time-budget gate** because **one day estimated ~71 min**, over the 40–60 window. Per the standing "no salvage" rule, the whole otherwise-usable week was **discarded**, and the rejection ("…that day is OVER the window… TRIM this day…") was fed into attempt 3.
- **Attempt 3 ran out of wall-clock time.** It started at 18:30:53, but the **360-second overall generation deadline** (running since ~18:25) expired during it — its logged response is literally `<request failed: Timed out waiting for 360000 ms>` → terminal "Generation took too long and was stopped."

**Leading hypothesis (evidence-grounded; user-endorsed as the prime candidate — orchestrator to confirm and own the fix):** the effective **"lead with the JSON, keep reasoning brief"** instruction is currently injected **only on retry**, so **attempt 1 always wastes time/tokens rambling before any plan exists**. That wasted first attempt is the main reason three slow attempts cannot fit inside the 360-second budget. Moving that instruction (and any equivalent "don't externalise your reasoning") into the **first/base prompt** is the prime efficiency target. The orchestrator should also look for **any other latency wins** its diagnosis surfaces (e.g. reducing wasted tokens/turns), since the user experiences the whole ~6-minute flow as too slow.

**Note on the over-time day:** attempt 2's single ~71-min day is an over-shoot (the opposite of the v1.10.1 under-fill). The user has chosen **not** to auto-trim or salvage such plans (see Decisions below); the intended path is that a fully in-window plan is produced cleanly and in time. If diagnosis finds the model *reliably* produces an out-of-window day such that a clean plan cannot be reached even after the efficiency fix, that is an **escalation back to the user** (see Open escalation) — not a license to trim, salvage, or relax the gate.

## Acceptance criteria (observable)
- **Done when** "Generate AI Program" (full 5-day generation for the repro profile) reliably **produces and SAVES a fully gate-passing, in-window plan**, with the quality-review/validation step reachable once a plan clears the gate.
- **Done when** the flow is **noticeably faster** than today's ~6-minute three-attempt run — the wasted first-attempt rambling is eliminated and wasted attempts/tokens/latency are minimised — and a successful generation completes comfortably **within the existing 360-second deadline** (the deadline is **not** loosened).
- **Done when** the strict per-day time-budget gate is **unchanged** — no salvaging, trimming, or saving of out-of-window plans; every saved plan genuinely passed the gate.
- **Done when** single-day "Regenerate" continues to work (no collateral regression).
- **Done when** the fix is **verified end-to-end against the live API** (a real full generation for the repro profile completes, saves, logs, and reaches validation) before any re-ship. A live key was provided by the user for the prior round at `/home/migul/treningsprogram/claude k` (gitignored/untracked — never print, commit, or relocate it); confirm it is still available.

## Scope and constraints
- **In scope:** making full program generation reliably reach a **saved, gate-passing, in-window plan faster**, by eliminating wasted attempts/tokens/latency (prime candidate: stop attempt 1 from rambling instead of emitting JSON), across the shared generation seam.
- **Out of scope / hard constraints (Option A — user-confirmed):**
  - Keep the per-day **time-budget gate STRICT** — no salvage, no auto-trim, no "save the best attempt anyway", no window-widening, no bypass.
  - **Do NOT loosen the 360-second overall generation deadline** (or otherwise weaken the never-hang guarantee from v1.10.3/H1). The fix is to make generation *fit* the budget, not to extend it.
  - **Do NOT reintroduce** the prior failures: the "Attempt N of 3" hang (H1), the 180s read-timeout regression (H4), or under-fill (G1/H3).
- **Standard cross-cutting constraints:** build via `./build.sh` (not `./gradlew`); no commits/releases unless asked; on-device/automated UI tests only if the user asks (live-API reproduction for verification is the deliberate, user-enabled exception established in the prior round).

## Decisions baked in (user-confirmed, 2026-06-27)
- **Option A chosen.** Fix is **efficiency-only**: make a valid, fully in-window plan be produced and SAVED within the existing budget by removing wasted work — **without** loosening the gate, trimming/salvaging out-of-window plans, or extending the deadline.
- **Speed is a first-class outcome.** The user explicitly wants generation to be **faster** ("make it more efficient, it is taking a LONG time"), not merely "eventually succeeds within 360 s." Minimising latency/wasted attempts is part of "done," not a nice-to-have.
- **Accepted tradeoff:** if the model still occasionally produces an out-of-window day and a clean plan cannot be reached, the run may still end with nothing saved — the user accepts this rather than auto-trimming or salvaging (they explicitly rejected option B = auto-trim and option C = longer deadline).

## Open escalation (flagged, not decided)
- If the orchestrator's diagnosis finds that, even after the efficiency fix, the model **reliably** produces at least one out-of-window day so a clean gate-passing plan **cannot** be reached within the deadline — i.e. the strict gate + the model's behaviour are structurally unsatisfiable for this profile — that finding must be **escalated to the user** before any change that would touch the gate, add trimming/salvage, or extend the deadline (all forbidden under Option A). The intake does not decide this; it flags it.

## Evidence
- **Primary (this round):** user's Prompt Log screenshot + full text export of the failed v1.10.5 run, showing the three attempts, the attempt-2 valid-but-gate-rejected plan, the attempt-3 `Timed out waiting for 360000 ms`, the over-time-day rejection feedback, and the on-screen "Generation took too long and was stopped." error. (Held by the user/coordinator; the orchestrator can request the raw files if needed.)
- **Representative repro-profile fixture (prior round, still valid):** `docs/intake/generation-retry-hang-2026-06/evidence/attempt1_prompt.txt` (the canonical generate prompt for this same profile) and `.../attempt1_response.json` (a sample rambling/under-filled response). Same profile as this round.
- **Repro profile (extracted from the prompt):** Goal = Hypertrophy, Experience = Intermediate, Days/week = 5, **Session target = 50 min** (NOT a >55-min structural-wall case), Priority = Arms, Equipment = pull-up bar / bench / barbell / dumbbells / ab roller (no rack, low ceiling), Injury = weak ankle / wants rehab work.

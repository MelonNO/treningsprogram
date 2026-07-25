# Intake — Generation efficiency: full generation must save a gate-passing plan, faster (post-v1.10.5)

**Prepared for:** Project-lead orchestrator
**Source:** User bug report ("now it just loads forever when trying to generate" → on re-test, "now it actually generated three responses but no validation prompts. it times out" → "make it more efficient it is taking a LONG time"), relayed and clarified via the coordinator. Primary evidence: the user's Prompt Log screenshot + full text export of a failed v1.10.5 run.
**Status:** FINAL — problem statement, scope, and the Option-A decision **confirmed by the user**. Ready for the orchestrator.

## Confirmation note
The user **directly confirmed** the corrected three-attempt understanding **and** chose **Option A** (keep the gate strict, no salvage, don't loosen the deadline; fix is efficiency-only), and added that **speed itself is a required outcome** ("make it more efficient, it is taking a LONG time"). Relayed verbatim through the coordinator. **Creating/finalizing these documents does not itself dispatch the orchestrator** — that is a separate coordinator/user action.

## Confirmed problem statement
On **v1.10.5**, "Generate AI Program" runs **three** full-generation attempts, takes ~6+ minutes, and ends on the **360-second overall deadline** ("Generation took too long and was stopped.") with **nothing saved** and validation never reached. The cause is a **compound time/budget exhaustion**: attempt 1 wastes its turn emitting prose/chain-of-thought instead of JSON (the "lead with JSON, keep reasoning brief" steering is injected only on **retry**); attempt 2 produced a complete valid plan that the **strict per-day time-gate** rejected for a single ~71-min day; and three slow attempts overran the 360-second deadline. Single-day "Regenerate" still works. The user wants generation to reliably **SAVE a gate-passing, in-window plan** **and be noticeably FASTER**, under Option A (strict gate, no salvage, deadline unchanged).

## Items
| ID | Title | Type | Priority | Brief |
|----|-------|------|----------|-------|
| G2 | Full generation must reliably SAVE a gate-passing, in-window plan, and do it FASTER — eliminate wasted first-attempt rambling and other latency waste (Option A) | Bug (diagnose-first) | HIGH | `brief-G2-generation-efficiency-retry-budget.md` |

Single item. No merge/cluster needed within this batch; it is **one worker** on the `AiRepository` generation seam.

## Merge / cluster + parallelization guidance
- **One worker, one file seam.** All of G2 lives in `AiRepository` (the base generation prompt, the generate/retry loop, the per-day time-budget gate). Do **not** split across workers.
- **Same hot seam as prior ships** — v1.10.1 (G1, `docs/intake/generation-timebudget-fix-2026-06/`) and v1.10.3/v1.10.4/v1.10.5 (H1–H4 + ANR fix, `docs/intake/generation-retry-hang-2026-06/`) all changed this exact code. **Re-verify the current `AiRepository` before acting** — earlier briefs' line-level notes may have drifted.
- **This batch is distinct from those.** It is **not** under-fill (G1), **not** the Attempt-2 hang (H1), **not** the 180s read-timeout (H4) — those shipped. This is the **retry-budget / first-attempt-waste** interaction that overruns the 360s deadline.

## Decision resolved (user-confirmed 2026-06-27) — Option A
- **Gate stays STRICT; no salvage.** No trimming/auto-fixing of out-of-window plans, no "save best attempt", no window-widening, no bypass.
- **Deadline NOT loosened.** The 360-second overall generation deadline stays; the fix makes generation *fit* the budget.
- **Efficiency-only fix.** Prime candidate (user-endorsed): move the "lead with JSON / keep reasoning brief" instruction into the **first/base** prompt so attempt 1 doesn't waste ~68 s rambling; plus any other latency wins diagnosis surfaces.
- **Speed is part of "done."** A successful generation must be **noticeably faster** than today's ~6-minute three-attempt run, not merely "succeeds within 360 s."
- **Accepted tradeoff:** the user accepts that if the model still produces an out-of-window day and a clean plan can't be reached, the run may still save nothing — they explicitly rejected **B** (auto-trim/salvage) and **C** (longer deadline).

## Open escalation (flagged, not decided)
- If diagnosis finds the strict gate and the model's behaviour are **structurally unsatisfiable** for this profile even after the efficiency fix (a clean in-window plan can't be reached in time), **escalate to the user** before any gate/salvage/deadline change. Intake flags this; it does not decide it.

## Verification requirement
- The fix must be **verified end-to-end against the live API** (a real full generation for the repro profile completes, **saves**, logs, and reaches validation, faster than today) before any re-ship. Live key from the prior round at `/home/migul/treningsprogram/claude k` (gitignored/untracked — **never print, commit, or relocate it**); confirm it is still present.

## Confirmed facts (from the user / primary evidence)
- 100% reproducible on **v1.10.5**. Entry point: **"Generate AI Program"** (Program tab).
- Prompt Log of the failed run: `GENERATE ATTEMPT 1` @18:25:18, `ATTEMPT 2` @18:26:26, `ATTEMPT 3` @18:30:53; **no validate entry**; on-screen error **"Generation took too long and was stopped. Please check your connection and try again."** (the 360s overall-deadline message).
- Attempt 1 = prose ramble, no JSON, cut off. Attempt 2 = **complete valid JSON plan**, rejected by the strict gate for one ~71-min day. Attempt 3 = `Timed out waiting for 360000 ms`.
- Single-day **Regenerate works**; full-generate has **not succeeded** since updating.
- Repro profile: Hypertrophy / Intermediate / 5 days / **50-min** target / Arms priority / limited equipment / weak-ankle rehab — **not** a >55-min structural-wall case.

## Cross-cutting constraints
- Keep the per-day **time-budget gate STRICT** — no salvage/trim/widening/bypass. Do **not** loosen the 360-second deadline. Do **not** reintroduce the hang (H1), the read-timeout regression (H4), or under-fill (G1/H3).
- Build via `./build.sh` (not `./gradlew`). No commits/releases unless asked. No on-device/automated UI tests unless asked (live-API verification here is the deliberate, user-enabled exception).

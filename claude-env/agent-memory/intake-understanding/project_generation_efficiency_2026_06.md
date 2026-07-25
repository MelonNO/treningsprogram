---
name: generation-efficiency-2026-06
description: post-v1.10.5 generation bug at docs/intake/generation-efficiency-2026-06/ — full-gen runs 3 slow attempts, overruns 360s deadline, saves nothing; Option A (efficiency-only, strict gate, no salvage, deadline unchanged); speed is first-class
metadata:
  type: project
---

Intake produced 2026-06-27 at `docs/intake/generation-efficiency-2026-06/` (INDEX.md + brief-G2). Single item **G2**, HIGH, diagnose-first. **Status: FINAL — user directly confirmed the corrected 3-attempt understanding AND chose Option A.** Finalizing docs ≠ dispatch (separate coordinator/user action).

**The bug (v1.10.5, "Generate AI Program"):** runs THREE full-gen attempts, ~6+ min, ends on the **360s overall-generation deadline** ("Generation took too long and was stopped." — NOT the SocketTimeout wording), nothing saved, validation never reached. Single-day Regenerate works. 100% repro.

**Root cause = COMPOUND time/budget exhaustion (evidence: user Prompt Log export, 3 attempts logged):**
- Attempt 1 (18:25:18): model rambles prose/chain-of-thought ("I'll plan silently, then output the JSON") + per-day set-by-set time math, runs out of room, NO JSON (cut off mid-sentence). Burns ~68s. Cause: the "lead with JSON / keep reasoning brief" steering is injected ONLY on RETRY, so attempt 1 always wastes its turn.
- Attempt 2 (18:26:26): retry feedback worked → produced a COMPLETE VALID JSON plan, but the strict per-day time-gate REJECTED it for ONE ~71-min day (over 40–60 window; note this is OVER-shoot, opposite of v1.10.1 under-fill). Per no-salvage rule the whole week was discarded.
- Attempt 3 (18:30:53): `<request failed: Timed out waiting for 360000 ms>` — 360s wall-clock deadline (running since ~18:25) expired.

**User decision (CONFIRMED 2026-06-27) = Option A:** keep gate STRICT, NO salvage/auto-trim, do NOT loosen the 360s deadline. Fix is EFFICIENCY-ONLY: stop attempt 1 rambling (move "lead with JSON, keep reasoning brief" into the BASE/first prompt) + any other latency wins. User explicitly rejected B (auto-trim/salvage out-of-window plans) and C (longer deadline), and accepts the tradeoff that it may still save nothing if the model keeps producing an out-of-window day. **SPEED is a FIRST-CLASS outcome** ("make it more efficient it is taking a LONG time") — must be noticeably faster than the ~6-min run, not merely "succeeds within 360s".

**Repro profile (unchanged):** Hypertrophy / Intermediate / 5 days / **50-min** target / Arms priority / limited equipment (no rack, low ceiling) / weak-ankle rehab. NOT a >55-min structural-wall case.

**Distinct from prior ships** (do NOT conflate): not under-fill (v1.10.1/G1), not the Attempt-2 hang (v1.10.3/H1), not the 180s read-timeout (v1.10.4/H4) — all shipped. Same `AiRepository` hot seam; ONE worker. Verify fix end-to-end vs LIVE API (key at `/home/migul/treningsprogram/claude k`, never print/commit) before re-ship.

**Open escalation flagged:** if strict gate + model behaviour are structurally unsatisfiable even after the efficiency fix, escalate to user — no gate/salvage/deadline change without user say-so.

**How to apply:** if the user revisits AI-generation reliability/speed/the time-budget gate/"generation times out", this batch is authoritative for the post-v1.10.5 state. Lineage: [[generation-retry-hang-2026-06]] (v1.10.3/H1–H4) and [[generation-timebudget-fix-2026-06]] (v1.10.1/G1). See [[intake-doc-format]].

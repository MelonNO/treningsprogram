---
name: project_overnight_autonomous_chain_2026_07
description: Overnight autonomous multi-stage directive (2026-07-01) — UX ship → live gen-test+fix+ship → simplification+ship; 20 TOTAL API-call cap
metadata: 
  node_type: memory
  type: project
  originSessionId: 48107a7a-f1aa-425b-a4ed-8ba7d8ec4dbd
---

**Set 2026-07-01, user went to bed and authorized an autonomous chain.** Execute stages IN ORDER, each gated on the prior being build-verified + shipped. Coordinator drives; orchestrator owns code/build/ship ([[feedback_orchestrator_owns_changes]]). Ship each stage as its own version WITHOUT further user confirmation (user explicitly said "implement fixes and ship" / "ship this as another version").

**STAGE 1 (in flight):** project-lead-orchestrator building UX batch (`docs/intake/ux-batch-2026-07/`, 8 items) → verify tag/release → ship (likely v1.14.0).

**STAGE 2 (after Stage 1 verified+shipped):** orchestrator LIVE-tests the v1.13.0 GENERATION fixes ([[project_generation_quality_overhaul_2026_07]]) with REAL Anthropic API calls. Key file: `/home/migul/treningsprogram/claude k` (gitignored, 109 bytes).
- **HARD CAP: 20 API calls TOTAL across ALL agents combined (coordinator + orchestrator + any workers) — NOT 20 each.** If coordinator uses 5, orchestrator has 15. Coordinator reserves 0 → all 20 to Stage 2. Communicate this ceiling explicitly to every agent; require exact usage count in reports. Precedent: a prior live sweep drained the key AND the monthly spend limit ([[feedback_frugal_api_testing]]) — be lean, decision-driven, stop-early, no re-verifying proven items.
- Tests must verify: (1) plans PASS the validations, (2) they actually FULFILL the v1.13.0 fixes + intention (keep+progress anchors, ≤2 swaps/wk, weights from history, 20–120min sizing, 4-goal parity, injury no-op), (3) they are genuinely OPTIMAL routines. No Waydroid; live test likely via a JVM harness invoking the real prompt-build + API + validators.
- Then implement fixes for CLEAR failures and ship (likely v1.15.0). SAFETY: only fix clear/validation failures; do NOT ship speculative "more optimal" tweaks that risk regression while user sleeps — note subjective-only concerns for the user instead. If generation is fundamentally broken (needs redesign), STOP and report, don't ship a hasty fix.

**STAGE 3 (after Stage 2):** orchestrator SIMPLIFIES — remove all dead/unused files + code, de-complicate, make the codebase + filesystem as simple/understandable as possible. **Constraint: NOTHING meaningful may change for app features/functions.** Conservative: only remove genuinely dead/unreachable code + unused files; if unsure whether used, KEEP it. Verify via full unit-test suite (must stay green) + clean build. Ship as its own version (likely v1.16.0).

**STAGE 4 (after Stage 3; ADDED by user mid-run 2026-07-01):** improve EXERCISE-DB MATCHING so the ~130 currently-unrecognized exercises in `docs/intake/overnight-run-2026-07/unrecognized-exercises.txt` resolve correctly (muscle group + library match). Diagnose-first (MuscleClassifier vs DB matcher vs both); builds on [[project_exercise_recognition_fix]]. Heavy patterns: verbose parenthetical names, rear-delt/chest-supported/flye variants, and a big ankle/tibialis/calf REHAB+PREHAB cluster (likely needs a rehab category). Unit-test each listed name resolves; bump DB version if a re-derive-muscleGroup backfill migration is used. One orchestrator pass, no intake. Ship as its own version (~v1.17.0).

**Standing:** surgical commits only (leave pre-existing working-tree dirt: `.gitignore`, deleted `Change docs/Archive/*`, `flows/*.yaml`, AND the `docs/intake/overnight-run-2026-07/` folder — uncommitted); build+unit-tests only otherwise; releases per [[reference_release_process]]. Independently verify every "shipped" (tag+main+live release asset) before advancing.

**NEVER BLOCK (user broadened auth 2026-07-01):** if a question arises or a stage gets stuck, NOTE it in the report and CONTINUE — prioritize shipping what's possible without the user; flag questionable/needs-attention items. Full authority as long as it follows user intent. Final comprehensive report required when done.

**CRASH-RESUMABLE LIVING REPORT: `/home/migul/treningsprogram/docs/intake/overnight-run-2026-07/RUN-REPORT.md`** — updated after every stage; holds the running 20-call API ledger, per-stage status, open questions, findings, and (at end) the final summary. On a fresh session after a crash: read that file and resume at the first stage not marked ✅ SHIPPED+VERIFIED.

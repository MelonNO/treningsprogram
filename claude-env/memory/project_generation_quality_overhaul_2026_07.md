---
name: project_generation_quality_overhaul_2026_07
description: "SHIPPED v1.13.0 — workout-generation prompt+validator overhaul (6 briefs) from the two \"Change docs\" feedback files"
metadata: 
  node_type: memory
  type: project
  originSessionId: 48107a7a-f1aa-425b-a4ed-8ba7d8ec4dbd
---

**SHIPPED v1.13.0** (2026-07-01, commit `21b5682` on main, tag verified live, APK `treningsprogram-v1.13.0.apk` md5 `911077334a25a576cbe7ced7fc9f8c91`, DB UNCHANGED v16, versionCode 49). Full pipeline: intake ([[feedback_intake_agent_verbatim_relay]]) → 6 outcome-only briefs at `docs/intake/generation-quality-overhaul-2026-07/` → project-lead-orchestrator built AND shipped ([[feedback_orchestrator_owns_changes]]). **568/568 unit tests pass.** No relayed-ship deadlock this round — user said "build and ship" directly and orchestrator owned the release.

Source: two files in `Change docs/` — "prompt feedback" (9 fixes §3.1–3.9 + guardrails/evidence) and "Validation feedback" (HARD-REJECTION quality-gate spec).

**What shipped (all in `AiRepository.kt` + `WorkoutTimeEstimator.kt`):**
- **P2 duration-driven sizing** — per-rep work 3→4s (`WORK_SECONDS_PER_REP`, prompt formula kept in lockstep); DELETED the broken fixed ~46-min/~19–20-set/120s constants + `hypertrophyRestDirective` hammer; TIME BUDGET now derives across full **20–120 min** range.
- **P3 four goals + wide bands** — narrow role→rep table replaced with wide §5 goal bands (soft guidelines); 120s hypertrophy rest ceiling removed (strength ≤300s, hyp compounds ≤240s); parse rest clamp 180→600s.
- **P1 (HEADLINE) continuity+weights** — the user's #1 real complaint "exercises/weights are off". Root cause: 3 overlapping rules force-churned main lifts every week → guessed weights on forced-new lifts. Fix: blacklist demoted from "single most important constraint" to "RECENTLY USED — vary don't churn"; keep+progress anchor lifts, **≤2 full main-lift swaps/week**; same-movement implement/grip/angle variation OK with weight estimated from closely-related logged lift; weights anchored to logged history, never fabricated.
- **P4 injury no-op** — empty injury = zero change; MILD = ≤1 optional prehab/week, no forced selection change absent a named aggravator (generator + validator aligned).
- **P5 auditable metadata** — full §6 per-exercise/day/week metadata added to output contract + parse model; deterministic estimator stays authoritative. Implemented in FULL (64K model output ceiling ≫ caps, so no truncation risk).
- **V1 quality gate** — terminal ladder (regenerate cap-3 → deterministic fix → **re-verify** → save-or-fail-clearly) ALREADY existed; V1 work was DE-false-rejecting `validateProgram`: never hard-reject on §5 ranges (soft-band guard), coverage-scaled movement balance, P4-aligned injury.

**Confirmed user decisions baked in:** full range 20–120 min both ends; all 4 goals first-class; variation hierarchy (most lifts progressed · grip/implement tweaks weighted from related lift · ≤2 full swaps/wk · accessories/days/theme free); §5 = SOFT guidelines validator must never hard-reject on; V1 terminal = save-a-passing-plan OR clean failure (nothing saved), never loop, never save imperfect (user REVERSED the intake agent's "save best-available with a note" assumption → fail clearly).

**Did NOT** reintroduce adaptive thinking or any fixed set/minute constant (both barred by regression history [[project_generation_efficiency_fix]]); streaming/retry/360s deadline untouched.

**RESIDUAL (live-generation only — user checks on-device per [[feedback_always_skip_waydroid]] + [[feedback_frugal_api_testing]]):** per-rep 3→4s raises every existing user's Program "~Xm" estimate ~15% and shifts the ±10 gate (intended, user-visible on first open).

**LIVE-VERIFIED 2026-07-01 (17-call sweep, part of [[project_overnight_autonomous_chain_2026_07]]):** real-API test harness (JVM/Robolectric driving the REAL buildPrompt/parse/validate/gates) confirmed: **HEADLINE continuity+weights WORKS** (anchors kept & progressed, weights anchored to logged history, plateaued lifts held, ≤2 swaps, blockState:continue) · four-goal parity PASS · injury no-op PASS (empty=zero change; real injury changes selection + adds rehab) · 20-min & hypertrophy@50 sizing PASS · V1 peer-review does NOT false-reject. **CLEAR FAILURE — 120-min targets produce NOTHING:** model caps sessions ~55–60 min, adds only ~5 min across 3 attempts, "add more" retry can TRUNCATE → nothing saved (reproduced on no-history controls, genuine). Handed back UNFIXED (product decision + live iteration needed; blind prompt patch risks regressing the working headline fix). Low-volume goals @~50min under-fill on attempt 1 but self-heal over the retry ladder. Mechanism: model declares `dayEstimateMinutes` as aspirational target not a sum (OUTPUT section forbids per-day arithmetic, a v1.10.6 anti-truncation measure); gate correctly ignores it.

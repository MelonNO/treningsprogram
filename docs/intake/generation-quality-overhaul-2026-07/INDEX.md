# Intake hand-off — Workout-generation quality overhaul (prompt + validator)

**Prepared for:** Project-lead orchestrator
**Source:** `Change docs/prompt feedback` (generator prompt critique, §0–§8) + `Change docs/Validation feedback` (quality-gate hard-rejection spec). Lineage: `Change docs/Archive/routine-generator-rules.md` (+ `(1)`), treated as history only.
**Prepared by:** intake-understanding agent, 2026-07-01
**Status:** Understanding CONFIRMED by the user (relayed via coordinator across multiple rounds). Ready for orchestration.

## Confirmation note (read this)
- The user's sign-off and all answers were **relayed to me by the coordinator**, not delivered as the user's own direct words to me. Per standing practice the coordinator relays faithfully; but only the user's own words are the true gate — if any point below surprises the user, stop and re-confirm.
- **Creating these documents is NOT a dispatch to the orchestrator.** Dispatching is a separate, later, explicit user instruction.
- All work is **outcome-only**: these briefs say *what* the result must be, never *how* to build it. "Diagnose first" notes mark where the mechanism must be confirmed in code before changing anything.

## Scope
The user confirmed: **"everything in both documents"** — nothing deferred. This includes the big architectural items (§3.4 block state, §6 metadata, the new validator gate).

**The single headline outcome (top priority): fix "exercises or weights are off"** — caused by the app force-churning the user's main lifts every week and then guessing weights on the forced-new lifts (see P1). Everything else supports or surrounds this.

## Items
| ID | Title | Type | Brief | Doc sections |
|----|-------|------|-------|--------------|
| **P1** | Exercise continuity, sensible variation & correct weights **(HEADLINE)** | Bug + refinement | `brief-P1-exercise-continuity-and-weights.md` | §0, §3.3, §3.4, §3.7 |
| **P2** | Duration-driven sizing across the full 20–120 min range | Bug + refactor | `brief-P2-duration-driven-sizing.md` | §3.1, §3.2, §3.5 |
| **P3** | All four goals fully & equally supported, wide bands | Feature/quality | `brief-P3-four-goals-and-wide-bands.md` | §3.8, §3.9, §5, §4, §1 |
| **P4** | Injury: empty = no-op; severity-scaled | Refinement/bug | `brief-P4-injury-empty-noop.md` | §3.6 |
| **P5** | Auditable per-exercise/day/week metadata | Feature | `brief-P5-auditable-metadata.md` | §6, supports §3.2 |
| **V1** | Quality gate: reject-or-fix, re-verify, never loop | Feature/reliability | `brief-V1-quality-gate-reject-or-fix.md` | entire "Validation feedback" doc + user's 2 added requirements |

## Merge / cluster + parallelization guidance
**All six briefs edit essentially ONE file** — the generator/validator (`AiRepository.kt`, ~2043 lines: the prompt builder, the plan parse model, the validator, and the salvage/finalize path). This is the dominant integration seam.

**Parallelization verdict: do NOT fan out into parallel worktrees.** Independent workers editing the same giant prompt string / same file would collide badly. Recommend **one worker (or the orchestrator itself) making the changes in a coordinated internal order**, or at most sequential hand-offs on a shared branch.

**Suggested build order (dependency-driven):**
1. **P2** — duration-driven sizing (the substrate everything else sits on).
2. **P3** — goal parity + wide bands (defines the rep/rest/volume vocabulary P1 & V1 use).
3. **P1** — the HEADLINE (continuity + weights + variation hierarchy); sits on the P2/P3 substrate.
4. **P4** — injury no-op (localized).
5. **P5** — metadata (output-contract change; do once content/schema is stable; feeds V1).
6. **V1** — quality gate LAST (validates against the final gate shape; consumes P5's declared numbers).

> Note the tension: **P1 is the user's #1 priority outcome** even though it is 3rd in build order. If the orchestrator wants an early win, P1 can lead — but P2/P3 give it the substrate to be correct.

**Cross-item consistency hazards (must be kept in lockstep):**
- **Block state (continue/new)** appears in P1 (§3.4) and P5 (§6 `blockState`) — one source of truth. The existing in-app **mesocycle-block** feature is the natural signal; the current unconditional blacklist overrides it (the core conflict P1 resolves).
- **Injury** logic spans P4 (generator) and V1 (validator) — change together so an empty-injury plan is never rejected for "missing rehab."
- **Soft bands** (P3/§5) constrain V1 — the validator must never hard-reject on the numeric guideline ranges.
- **Time formula** (P2/§3.2) must stay matched between the prompt, the deterministic estimator, and the Program-screen duration display.

## Confirmed decisions (user)
1. Scope = **everything in both documents**; nothing deferred.
2. **Full 20–120 min** session range must be supported and correct at both ends (short = no junk padding; long = actually reaches target).
3. **All four goals** (Strength, Hypertrophy, Endurance, Weight loss) fully & equally supported; none a relabeled hypertrophy plan.
4. **Variation hierarchy (middle ground):** (1) most main lifts kept + progressed with weights tracked from logged history; (2) same-movement implement/grip/angle variation allowed more freely, weight **estimated from the closely-related logged lift**; (3) **full main-lift swaps capped at ≤2 per week**; (4) accessories/order/**weekday placement**/theme rotate freely (respecting rest rules). NOT a frozen plan; NOT full churn. This replaces the "blacklist everything / different exercise every week" rule.
5. **Weekday placement** may shift week to week as long as recovery rules hold (no same primary muscle consecutive days; ~48 h between heavy leg days; sensible spacing; and, in fixed rest-day mode, within the pinned schedule).
6. **Quality gate:** regenerate first (capped), then deterministically auto-fix, then **re-verify the fixed plan against the same gates before saving**. Terminates in bounded time in exactly one of two outcomes — a **saved plan that passed every gate**, or, if even the fixed plan can't pass, a **clear "couldn't build a good plan" failure with NOTHING saved.** The app must **never** save a best-available/imperfect plan, never hangs, never loops. ("Always usable" = a passing plan OR a clean, honest failure — not saving an imperfect plan.)

## Assumptions applied (each labelled in its brief — user may veto)
- P1: "closely related logged lift" mapped by movement pattern / primary muscle; anchor-variation cadence tied to block boundaries.
- P2: ±10-min accepted tolerance retained; only the sizing logic becomes duration-derived.
- P3: §5 ranges adopted as the wide bands; "heavy hinge" = near-max (light 2nd hinge allowed).
- P4: MILD = ≤1 optional prehab per week (not per session).
- P5: if full §6 metadata causes truncation/timeout, fall back to a minimal time+block-state subset and flag it.
- V1: *(the earlier open decision is RESOLVED — see Confirmed decision #6: if even the auto-fixed plan can't pass, fail clearly and save nothing; do NOT save a best-available plan.)*

## Cross-cutting constraints (apply to every brief)
- **Priority order (§1):** safety → user hard boundaries → equipment → goal → experience → **recovery/fatigue → focus muscles →** movement balance → variation. Recovery ranks ABOVE focus muscles and variation.
- **Preserve the §2 "sustain" behaviors:** hard training-day/duration/goal/experience/equipment constraints, injury inputs, focus muscles as a slight priority, excluded-exercise forbidding (incl. renamed near-duplicates), effort/progression on every exercise, the physically-incoherent-cue guard, the tiered hinge caps.
- **§5 numeric ranges are GUIDELINES, not hard rules** everywhere; the hinge rep caps are the only hard numeric overrides.
- **§7 evidence basis** justifies removing the 120 s rest ceiling and widening the rep bands — do not silently re-narrow them.
- **Regression watch:** unwinding the v1.10.x under-fill tuning at ~50 min; changing the per-rep estimator shifts the ±10 gate + Program-screen display; the blacklist is presently "the single most important constraint" and P1 changes that.
- **Build/verify:** build via `./build.sh` (not `./gradlew`); **no commits or releases unless the user asks**; **no on-device / automated UI tests unless the user asks** — verify via `./build.sh` + unit tests only.

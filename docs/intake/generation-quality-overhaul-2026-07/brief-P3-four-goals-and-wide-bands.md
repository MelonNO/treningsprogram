# P3 — All four goals fully & equally supported, with wide goal-based bands

**Type:** Feature / quality improvement (goal parity) + refinement
**Cluster:** Generation prompt (same file as all briefs). Defines the rep/rest/volume vocabulary P1 and V1 rely on — see INDEX for order.
**Covers doc sections:** §3.8 (replace the narrow rep table with wide goal-based bands), §3.9 (goal-general, not hypertrophy-baked), §5 (numeric guardrails as the wide bands — treated as GUIDELINES), §4 (fatigue limits incl. "heavy hinge" definition), §1 (priority order — recovery above focus).

> **Outcome-only.** Describes the target behavior, not the implementation.

## Context
The user must be able to pick **Strength, Hypertrophy, Endurance, or Weight loss** and get a genuinely good, distinct plan for each — none may be a second-class citizen or a hypertrophy plan relabeled. Today the prompt carries hypertrophy-baked defaults (e.g. a 120 s rest ceiling, "train each muscle twice/week") that leak into other goals, and it uses a **narrow deterministic role→rep table** (e.g. 6–10 / 8–12 / 10–15) that adds rigidity without benefit. The evidence basis (§7) supports wider loading spectra and longer rests for strength.

## What the user wants (end result)
- **Each of the four goals produces a visibly distinct, appropriate plan:**
  - **Strength** — leads with heavy, low-rep compounds; longer rests; lower total volume; intensity over volume.
  - **Hypertrophy** — moderate loads and rep ranges; volume spread across the week.
  - **Endurance** — conditioning-led; higher-rep / shorter-rest resistance work; dedicated cardio sessions.
  - **Weight loss** — density/circuits + cardio for calorie expenditure, plus enough resistance work to preserve muscle; not a renamed hypertrophy plan.
- **Wide goal-based rep/rest/volume bands replace the narrow table** (use the §5 ranges), keeping a **role spread within each session** (primary compound lower-rep → isolation higher-rep) — never one identical range applied to a whole session. The **hinge rep caps stay hard** (barbell hinge ≤8, loaded DB hinge ≤12; bodyweight/light hip-extension exempt).
- **No hypertrophy-only assumption leaks into the other three goals** — e.g. the 120 s rest ceiling must not constrain strength (which wants longer rests); rest scales to the goal (longer for heavy strength, shorter for endurance/density work). Never fill time with junk volume or duplicate movement patterns to hit a number.
- The numeric ranges are **guidelines, not hard rules** — adjustable when goal, duration, injury, equipment, or exercise type justifies it. (This directly constrains the validator — see V1: it must not hard-reject on these ranges.)

## Acceptance criteria — Done when…
- Generating with each of the four goals yields a plan whose structure is clearly and correctly distinct (rep ranges, rest, volume, cardio presence) and appropriate to that goal.
- Rep ranges come from the **wide goal bands** with a within-session role spread; **no session applies one identical rep range to every exercise**; the old narrow table is gone.
- Barbell/loaded-DB **hinge caps are still enforced** as hard overrides.
- No hypertrophy-specific rule (e.g. the 120 s ceiling, forced 2×/week frequency) is applied to Strength/Endurance/Weight-loss where it doesn't belong; strength sessions can use their longer rests.
- A **light second hinge** in a week (e.g. an RDL after a deadlift day — 2 loaded hinge patterns/week) is **not** blocked; only a second *heavy/near-max* barbell hinge is limited (§4 "heavy" definition).
- Where goal and focus muscles conflict with recovery, **recovery wins** (§1: recovery/fatigue ranks above focus muscles and variation).

## Scope and constraints
- Numeric bands from §5 are the source for the wide ranges; treat them as soft guidelines everywhere.
- Cardio-on-its-own-day remains a hard setting when enabled, but gate its *relevance* on goal (matters for weight-loss/endurance; irrelevant for a pure hypertrophy block).
- Keep the existing "sustain" behaviors from §2 (goal-specific programming, experience scaling, equipment feasibility, effort targets, physically-incoherent-cue guard, hinge caps).

## Decisions baked in (user-confirmed)
- All four goals fully and equally supported; §3.9 de-hypertrophy-baking is in scope and important.

## Assumptions applied (user may override)
- **[ASSUMPTION]** The §5 ranges are adopted verbatim as the wide bands. The user can adjust any specific range.
- **[ASSUMPTION]** "Heavy hinge" = near-max, per §4, so a light second hinge is allowed.

## Considerations for whoever builds it
- The wide bands interact with P2 (duration sizing) and P1 (rep ranges must NOT be used as a variation lever — rep range is fixed by role + goal so progression stays trackable, per §3.7).
- The §1 priority order is cross-cutting — see INDEX; it should govern conflict resolution in every part of the prompt.

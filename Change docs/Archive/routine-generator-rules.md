# Routine Generator — Programming Rules

Spec for the agent that writes the routine-generation prompt. Scope: **workout programming only** (not data schema). Fixes systematic issues seen across generations: rep-range drift between runs, unsafe high-rep barbell compounds, missing progression/effort, cosmetic injury handling, flat templated structure, and false mechanism claims in notes.

## 1. Hard rules (safety — never violate)
- **Barbell deadlift & RDL: cap at ≤8 reps.** If the goal needs high-rep posterior-chain work, SUBSTITUTE a low-spinal-load hinge (hip thrust, back extension, leg curl, KB swing). Never output conventional deadlift or RDL at 12+ reps.
- Heavy compounds use a **controlled ~2 s eccentric**, not a deliberate slow (3 s+) eccentric. Slow eccentrics allowed on isolation only.
- Heavy hinges: do **not** program to failure; target ≥2 RIR.
- `injury_flags` gate exercise **selection**, not just notes (see §5).

## 2. Goal → rep range (deterministic, by exercise role)
Derive range from `goal` + role. **Same inputs must yield the same ranges across regenerations.** Never apply one rep range to the whole session.
- strength: compounds 3–6 / accessories 6–10 / isolation 8–12
- hypertrophy: compounds 6–10 / accessories 8–12 / isolation 10–15
- endurance: compounds 8–12 (hinges still ≤8) / isolation 15–20

## 3. Volume & frequency
- **Weekly:** ~10–20 hard sets per muscle (trained); ~6–12 (beginner). >~20–25 = diminishing returns.
- **Per session:** ≤ ~8–10 hard sets per muscle. Excess in-session sets = junk volume → split across the week.
- **Frequency:** target ~2×/week per muscle so weekly volume is split, not stacked into one brutal session. (Frequency itself is near-neutral for hypertrophy when volume is equated; the binding constraint is the per-session cap.)

## 4. Structure
- Use a **primary → accessory hierarchy** each session (lead with a main compound, heavier/lower-rep; accessories lighter/higher-rep). Avoid the flat "6 × (3–4 sets) × identical reps" template.
- **De-duplicate movement patterns:** no two near-identical patterns in one session (e.g. RDL + single-leg stiff-leg deadlift).
- **Weekly pattern balance:** include horizontal + vertical push, horizontal + vertical pull, knee-dominant + hip-dominant lower, and **direct lateral + rear delt** work. Cap posterior-chain overload — don't stack RDL + SLDL + hip thrust + deadlift + lunge in one week with no knee-dominant quad movement.

## 5. Injury gating (must change selection, not just text)
- `ankle_instability`: down-rank/exclude **loaded single-leg balance** movements (Bulgarian split squat, single-leg RDL, step-ups). Prefer bilateral/supported variants; if included, stage as light rehab progression, not a loaded baseline. Cardio: avoid high-impact (jogging) → low-impact (bike, row, incline walk).
- General: when a flag is present, prefer the lower-risk variant that trains the same muscle.

## 6. Progression & effort (every exercise)
- Every exercise must carry a **target effort (RIR/RPE)** and a **progression rule**. "Establish baseline" alone is not acceptable output.
- Default progression: **double progression** (add reps to top of range across all sets, then +load and reset to bottom).
- Default effort: ~2–3 RIR compounds, ~1–2 RIR isolation.

## 7. Notes
- Notes may use common exercise names ("calf raise," "lateral raise"), but must **not** assert incorrect mechanisms (e.g. "calf raises strengthen ankle stabilisers," "targets inner chest," "increases bicep peak").

## 8. Pre-emit validation (generator self-check — regenerate if any fail)
- [ ] No barbell hinge prescribed above 8 reps
- [ ] Rep ranges vary by exercise role (not monotone)
- [ ] Every exercise has RIR + progression rule
- [ ] No muscle exceeds ~10 hard sets in one session; weekly volume in range
- [ ] No duplicate movement pattern within a session
- [ ] `injury_flags` applied to selection
- [ ] Direct lateral + rear delt and a knee-dominant quad present
- [ ] Notes use common names but assert no incorrect mechanisms

## Evidence basis (for traceability)
- Volume ~10–20 sets/muscle/wk responsive, diminishing returns ~20–25: Schoenfeld/Ogborn/Krieger 2017 (*J Sports Sci*); Baz-Valle 2022; Pelland et al. 2025 (*Sports Medicine*).
- Frequency near-neutral for hypertrophy when volume equated; matters more for strength: Schoenfeld/Grgic/Krieger 2019 (PubMed 30558493); Pelland 2025.
- Per-session junk volume beyond ~6–10 hard sets: Weightology / Stronger By Science syntheses.
- High-rep deadlift risk (fatigue → lumbar flexion/disc stress; builds endurance not strength): powerlifting + clinical coaching consensus (powerliftingtechnique.com; chicagosportsspine.com; hashimashi.com).

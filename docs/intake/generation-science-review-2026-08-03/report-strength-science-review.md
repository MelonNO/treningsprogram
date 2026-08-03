# Generation Science Review — do the generated plans hold up as strength training? (2026-08-03)

Report ONLY — nothing was implemented. Every proposal awaits approval. Complements (does not repeat) `docs/intake/flagged-info-gen-eval-2026-08-03/report-02-generation-evaluation.md`, which covered pipeline mechanics; this pass evaluates the **training content** of real generated plans.

## 1. Method & budget

- **14 of 20 authorized live API calls used** (6 unspent). Every call is ledgered in `api-call-ledger.txt` (line appended before each send). No retries were needed; all 14 returned clean.
- Plans were generated through the app's REAL code path: the actual `AiRepository.buildPrompt` output (all 25 parameters, neutral variation theme pinned for comparability), one plain POST per cell with the app's own model (`claude-sonnet-4-6`) and token budget (24576), parsed by the real `parseProgram`, timed by the real `WorkoutTimeEstimator`, muscle-tagged by the real `MuscleClassifier`.
- **Single attempt per cell** (no retry ladder, no `validateProgram`) to maximize matrix coverage inside the budget. So findings about gate failures describe *attempt-1* behavior; in production the 3-attempt ladder + feedback usually recovers — at latency/cost.
- Every analyzed cell was verified genuinely live (real API envelope with `id`/`usage`/model, ledger cross-check, mtimes inside the run window; zero dry-run stubs among analyzed files). Raw prompts, responses, and deterministic per-plan analyses are under `raw/`.
- The throwaway harness test was deleted after the run, as planned.

## 2. Profile matrix (all cells live-verified)

| Cell | Goal | Exp | Days | Min | Variant | Days in ±10-min window (attempt 1) |
|------|------|-----|------|-----|---------|------------------------------------|
| 01 | Strength | Int | 4 | 60 | — (also week-1 of progression pair) | 2/4 |
| 02 | Strength | Beg | 3 | 45 | — | 1/3 |
| 03 | Strength | Adv | 5 | 75 | — | 0/5 |
| 04 | Hypertrophy | Int | 5 | 60 | — | 1/5 (the 1 "IN" is a false positive — see S5) |
| 05 | Hypertrophy | Beg | 3 | 60 | — | 0/3 |
| 06 | Hypertrophy | Adv | 6 | 90 | long-session path | 0/6 |
| 07 | Endurance | Int | 4 | 60 | — | 4/4 |
| 08 | Weight Loss | Beg | 3 | 45 | — | 3/3 |
| 09 | Weight Loss | Int | 5 | 60 | — | 0/5 |
| 10 | Strength | Int | 4 | 60 | minimal home gym | 1/4 |
| 11 | Hypertrophy | Int | 4 | 60 | shoulder injury (Moderate) | 1/4 |
| 12 | Strength | Int | 4 | 60 | week 2 (history = week 1 performed) | 1/4 |
| 13 | Strength | Int | 4 | 60 | week 3 (history = weeks 1–2) | 2/4 |
| 14 | Hypertrophy | Int | 3 | 60 | cross-week probe: heavy chest yesterday (Sunday) | 0/3 |

## 3. What holds up well (a competent coach would sign off on these)

- **Goal differentiation is real, not relabeled.** Strength cells: 1–6-rep primaries, 240–300 s rests, RPE 7 baselines, low isolation. Hypertrophy: 5–10-rep compounds, 10–20-rep isolation, 60–210 s rests, double progression everywhere. Endurance: 12–20 reps, 45–90 s rests, cardio in every session, conditioning-led. Weight loss: 10–20 reps, 60–90 s rests, ≥2 cardio sessions (HIIT + steady-state mix), explicit muscle-retention work. All four goals are visibly distinct — the prompt's band system works.
- **Exercise ordering is consistently correct**: heaviest compound first, isolation last, in all 57 days.
- **Effort & progression prescriptions are complete and sane**: every exercise carries RPE/RIR + a concrete rule; hinges consistently carry "≥2 RIR always"; the ≤8-rep barbell-hinge cap held in all 14 plans, including endurance/weight-loss where everything else runs high-rep.
- **Injury handling (cell 11) is exemplary**: "shoulder pain when pressing overhead" (Moderate) → overhead pressing fully substituted (landmine press, neutral-grip incline), flat bench correctly kept (the stated aggravator is overhead only), exactly 2 prehab slots (face-pull cue + side-lying external rotation at 2 kg), slow eccentrics confined to isolation. This is what a good physio-literate coach does.
- **Equipment constraints (cell 10) were perfectly respected**: dumbbell/bench/band-only plan with zero forbidden implements and sensible band substitutions for laterals/face-pulls.
- **Anchor continuity + history-anchored overload work when history has real loads** (cell 14): squat 87.5→90 kg labeled "progressing", plateaued lifts held at top-of-range reps before adding load (exactly the double-progression rule), related-lift estimates disclosed ("estimated from pronated pulldown at 60 kg"). Across the week-1→2→3 chain (cells 01→12→13), every main anchor persisted (bench, squat, deadlift, row, pull-up, OHP) with ≤2 accessory-level swaps per week and honest rationales. The P1 anti-churn design demonstrably works.
- **Beginner plans are appropriately conservative**: 3–5 fundamental movements, full-body A/B/C, 2–3 sets, no advanced techniques; advanced plans correctly add volume, a drop set, tempo work.
- **Weekly volumes are mostly in productive ranges** (strength ~8–15 hard sets/muscle; hypertrophy ~10–20; beginner lower) — no junk-volume stacking, no 30-set muscle days.

## 4. Systematic problems (recurring across plans — ranked)

### S1. Cross-week recovery is structurally blind (the user's reported case)
**The user's real observed case: Dumbbell Bench Press on Sunday → a new week generated with Barbell Bench Press on Monday — ~0 recovery across the week boundary.** Code review confirms this is structural, not bad luck in the model:
- The prompt **never states today's date or the date the generated week starts.** History sessions carry ISO dates and the previous-plan block carries weekday labels, but with no "today" anchor the model *cannot compute* that the last history session was yesterday.
- Every recovery rule ("never train the same primary muscle group on consecutive days", "~48 h between heavy leg sessions") is scoped to the week being built. **No rule spans the boundary.**
- The previous plan is framed purely as a *variety* input ("vary these in the new plan"), never as a recovery input.

Live probe (cell 14, heavy chest synthesized on yesterday's Sunday): chest happened to land on Wednesday — acceptable spacing, **but by luck/pattern-mirroring, not by design**; the rationale shows zero awareness of the boundary. Nothing prevents the user's observed failure, and with weekday placement instructed to "shift week to week", it will recur stochastically. The user's own report is the empirical confirmation.

### S2. Attempt-1 duration under-fill is near-universal for strength-only days
Only **16 of 57 generated days (28%) landed in the ±10-min window on attempt 1**, and only 2 of 14 plans passed whole (cells 07 & 08 — precisely the two whose every day carries a duration-timed cardio entry, which gives the model an easy sizing lever). Misses are almost all UNDER, typically by 1–15 min despite the "aim target+12, err HIGH" calibration. Worst case: cell 06 (90-min hypertrophy) — **the LONG-SESSION multi-modal instruction was ignored outright**: six sound ~55-min PPL days, zero finishers, every day 25–40 min under. Cell 09's dedicated interval day estimates 31 min against a 50–70 window (the "6×400m" fallback = 30 min — see S5), unfixable by the model as written. In production the ladder + feedback recovers most of these, but this measured 28% attempt-1 pass rate is the engine of the latency/cost/deadline problems in the prior report (its findings 2.1, 3.4, Axis 7 now have fresh quantified support).

### S3. The "never fabricate weights" rule is obeyed only stochastically
With identical no-history prompts: cells 01, 02, 07–11 correctly prescribed 0 kg + "start conservative ~60% est. 1RM" notes; cells **03, 04, 05, 06 emitted invented absolute loads** — including **130 kg deadlifts and 110 kg squats for an unknown "Advanced" user** (03) and 120 kg deadlifts in 06. A wrong guess here is a genuine injury vector for a new user who trusts the number. Cell 13 shows the failure under degenerate history too: given all-0 kg logs it *announced* "I've set conservative but realistic starting loads based on typical intermediate baselines" — disclosed, but still fabricating against the rule. **Nothing deterministic checks weights** — this rule lives entirely in the prompt.

### S4. Same-muscle loading on consecutive days inside the week (pull day → deadlift day)
In every 5–6-day plan the deadlift day directly follows the dedicated pull day: cell 03 (Fri: 5×3-5 weighted pull-ups + rows → Sat: 5×2-4 deadlift + *more weighted pull-ups*), cell 04 (Fri pull → Sat deadlift), cell 06 (Fri pull → Sat deadlift 4×4-6). Lats, spinal erectors, and grip get back-to-back heavy loading — a violation of the plan's own "never the same primary muscle on consecutive days" rule (the app itself classifies deadlift as Back) and something most coaches would space. Recurring, not a one-off.

### S5. MuscleClassifier gaps corrupt the volume story AND the duration gate (live-demonstrated)
- **12 of 14 plans contain ≥1 strength exercise classified as "" (no muscle)**: Incline Barbell/Dumbbell Press (6 plans), Landmine Press, Cable Pull-Through (2), Straight-Arm Cable Pullover, Band Pull-Apart. These vanish from per-muscle volume stats and recovery tracking — e.g. true chest volume in cell 01 is 8 sets, but the app sees 4.
- **"Cable Crunch" classifies as Cardio** (the substring "run" inside "c-run-ch"), so the estimator counts it as a 30-min duration entry. Demonstrated live twice with opposite effects: cell 01 day 7 was pushed to est 76 (falsely OVER the window → would be rejected/trimmed for the wrong reason); cell 04 day 6 was lifted to est 69 (falsely IN — a genuinely under-time day passing the gate). The prior report's finding 2.7 (rep-style cardio → 1800 s fallback) is no longer latent; it fires in practice.

### S6. Minor recurring quality slips
- Duplicate movement patterns in one session despite the de-dup rule: cell 06 day 4 has *two* Dumbbell Lateral Raise slots; cell 07 day 7 stacks deadlift + DB RDL + back extension (three hinges).
- Hypertrophy frequency at 3 days: cell 14 kept a 1×/muscle body-part split (continuity with the user's prior structure won over the ~2×/week frequency guideline; cell 05 chose full-body ×3 correctly). Defensible trade-off, but a coach would usually push 3-day hypertrophy toward full-body/UL.
- Note incoherence: cell 14's face pull says "progressing — up from 27.5 kg" while prescribing 27.5 kg.
- Push volume trends low in the tightest cells (cell 08: 3 chest sets/week) — acceptable given 45-min ×3 constraints, worth watching.

### S7 (code-level, from this review's reading — not exercised live)
`trimOverflowToWindow`'s first lever cuts `recommendedRestSeconds` stepwise toward a **60 s floor regardless of goal**. A salvaged over-target *Strength* day can end up prescribing 60 s rests between heavy compound sets — directly against the 3–5-min band the same prompt enforces, and poor strength programming. (The comment justifying it — "validateProgram never penalises low rest" — is about passing review, not about training quality.)

## 5. Cross-week recovery axis — detail (requested dimension)

Named user case: **DB bench press Sunday → barbell bench press Monday** in the next generated week.
- Root cause is structural (S1): no date anchor, no cross-boundary recovery rule, previous plan framed as variety-only. Established by code/prompt review at zero API cost.
- One live probe (cell 14) with heavy chest synthesized on yesterday's Sunday did not reproduce Monday-chest (chest landed Wednesday) — consistent with a stochastic failure the user will hit intermittently, and the probe's rationale confirms the model reasons only about *variety* vs last week, never about *recovery* from it. One sample cannot establish frequency; the structural gap plus the user's observed case are the evidence that matters.
- The same blindness applies to all muscle groups and to the "~48 h heavy legs" rule (a Sunday squat day followed by a Monday squat/deadlift day is equally unprotected).

## 6. Progression pair (weeks 1→2→3) findings

- Anchor persistence: perfect across both transitions; accessory rotation stayed within the ≤2-swap rule; rationales honestly describe what changed.
- Rep-scheme progression: week 2 correctly tightened ranges into fixed strength doubles/triples with "+load when all sets clean at ≥2 RIR" rules.
- Load progression could not be numerically tested through this chain: week 1 (correctly) prescribed 0 kg for the new user, so synthesized "performed as prescribed" history carried 0 kg forward — cell 13 then exposed S3 (fabrication under degenerate history). **Cell 14 (real loads in history) is the valid load-progression probe, and it passed cleanly** (+2.5 kg on progressing lifts, hold-reps on plateaued lifts, disclosed estimates for variations).

## 7. Proposals (for approval — none implemented)

| # | Proposal | Benefit | Risk / effort |
|---|----------|---------|---------------|
| P1 | **Cross-week recovery context** (fixes S1): inject into `buildPrompt` (a) "Today is \<weekday, date\>; the plan you are writing covers Mon \<date\>–Sun \<date\>"; (b) a compact "last 3 sessions: \<date, weekday, primary muscles\>" line derived from history the app already fetches; (c) extend the consecutive-day and 48-h rules explicitly across the boundary ("day 1 of this plan is the day after the most recent session above"). | Directly addresses the user-hit failure; also fixes boundary-blind heavy-legs spacing. | S–M effort. Prompt change → one confirming live run advisable (6 calls remain unspent from this grant, if you extend it; otherwise your call). No schema impact. |
| P2 | **Deterministic weight-sanity gate** (fixes S3): post-parse, if the user has no logged history (or no history for that lift family), coerce fabricated absolute loads to 0 kg (keep the RPE note) or reject the attempt with targeted feedback. | Removes the "130 kg deadlift for an unknown user" injury vector; makes the anti-fabrication rule enforceable instead of aspirational. | S–M. Needs a careful definition of "has history" (the resolver's family matching already exists). Unit-testable offline; no live run required. |
| P3 | **Classifier + estimator fixes** (fixes S5): add patterns for incline/landmine press, pull-through, pullover, pull-apart; stop "crunch" matching Cardio (move the Core "crunch" rule ahead of Cardio or word-boundary the "run" keyword); apply the prior report's 2.7 fix (rep-style reps on a Cardio-classified name → strength formula, not 1800 s). | Restores volume/recovery stats integrity (12/14 plans affected) and stops the gate passing/failing days for false reasons (demonstrated live in both directions). | M. Classifier changes ripple into stats → needs a v1.10.2-style data-only backfill; estimator change shifts some "~Xm" labels. Offline-testable. |
| P4 | **Attempt-1 under-fill decision input** (S2): this run measured 28% attempt-1 day pass-rate, ~100% failure of the 90-min multi-modal instruction on attempt 1. Feeds the prior report's open decisions: its 2.1 (salvage survives deadline), 3.4 (trim early), Axis 7 Option A/C. If one lever is chosen: for LONG sessions, app-authored finisher sizing (Option C) is what this data most supports — the model demonstrably won't do it unprompted on attempt 1. | Fewer wasted attempts, faster generations, fewer total failures at long targets. | Decision belongs with the prior report's Axis 7; M–L depending on option. |
| P5 | **Adjacency guard for pull/hinge days** (fixes S4): one prompt line ("a heavy deadlift day counts as back/posterior-chain for the consecutive-day rule — do not place it the day after a pulling day"), optionally backed by a deterministic classifier-based adjacency warning in the accept path (warn, don't reject). | Removes the recurring Fri-pull→Sat-deadlift pattern. | S. Prompt-only if desired; live confirmation optional. |
| P6 | **Goal-aware salvage rest floor** (fixes S7): in `trimOverflowToWindow`, floor rest at the goal's band minimum (e.g. strength ~180 s, hypertrophy compounds ~90 s) instead of a flat 60 s, trimming sets/exercises earlier for strength. | Salvaged strength days keep strength-appropriate rests. | S–M. Reduces trim headroom → slightly more unsalvageable strength days; needs the existing trim unit tests extended. |

Priority if only a few get done: **P1** (user-hit, structural), **P3** (integrity of stats + gate, live-proven), **P2** (safety), then P4–P6.

## 8. Limitations (honest)

- Single generation per cell; attempt-1 only (no ladder/validator) — production outcomes after retries are better than the raw pass-rates above, at cost.
- One live probe for the cross-week axis; frequency of the user's failure mode is not estimated — only its structural possibility (certain) and one non-reproduction.
- Histories for cells 12/13/14 were synthesized (realistic format, "performed as prescribed"); real user logs are messier.
- The neutral variation theme was pinned for comparability; theme–goal interactions (e.g. a random "HIGH REP" theme on a Strength week) were not live-tested — the prompt's guard text exists but its compliance is unverified (would cost reserve calls).
- Weekly set tallies use the app's own coarse MuscleClassifier groups; finer quad/ham/delt-head splits were assessed by reading exercise names, not computed.

## 9. Verdict

The plans are, on training content, **substantially better than typical app-generated programming**: goal-appropriate loading schemes, correct ordering, complete effort/progression prescriptions, genuinely good injury and equipment handling, and working anchor-based progressive overload. The failures that matter are **systemic, not taste**: the generator cannot see the week boundary it schedules across (S1 — the user's own case), its attempt-1 output almost never fits the duration window without a cardio lever (S2), its weight-anchoring discipline is unenforced (S3), and the app's own muscle classifier quietly mis-scores both the stats and the gate (S5). All four have concrete, mostly offline-testable fixes proposed above.

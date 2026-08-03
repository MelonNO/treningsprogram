# Intake Index — Training-data-driven app improvements 2026-08-03

Prepared for: **Project-lead orchestrator**
Source: analysis of the user's real backup (v8 export, 2026-08-03), focused on the last three logged weeks (mid-July → early August 2026), decoded via the release R8 mapping. The user asked intake to find improvements from this data and pre-authorized these documents without a per-item confirmation round — assumptions are labelled and veto-able.
Status: **PRE-AUTHORIZED, then PRUNED BY THE USER 2026-08-03** (verbatim: "scrap parts of 3 only check for exercises also scrap 4, 5, 6, 7"). Surviving scope: **01, 02, and a reduced 03**. Creating these documents does not itself dispatch work.

Baseline: v1.29.0 on `main`, with branch `gen-science-fixes-2026-08-03` in flight (see hazards).

Privacy: briefs contain **aggregates and derived findings only** — no raw session rows, no per-session logs. The backup file itself is outside the repo and must never be committed.

## Where these come from (grounded findings, summarized)

Three-week realized picture (hard sets/week, training days/week per muscle, from the user's own logs):

| Muscle | Wk1 | Wk2 | Wk3 | Days/wk |
|--------|-----|-----|-----|---------|
| Chest | 8 | 16 | 13 | 1–3 |
| Back | 11 | 7 | 15 | 2 |
| Arms | 12 | 9 | 14 | 2–3 |
| Legs | 12 | **0** | 11 | **0–1** |
| Shoulders | **3** | **3** | **3** | 1 |

Other grounded facts used by the surviving briefs: profile is 5 days/week, Hypertrophy, Intermediate, priority "Arms"; plans allocate legs to exactly one day per week, so one missed day zeroed a whole muscle group for a week; specific planned exercises (including the plan's only overhead-press slot) recurred for weeks with zero logged performances while their planned siblings were logged consistently.

## Items

| ID | Title | Type | Brief | Status |
|----|-------|------|-------|--------|
| 01 | Per-muscle weekly floor check on generated plans (frequency + minimum volume) | Feature (generator quality) | `brief-01-per-muscle-volume-guard.md` | Pre-authorized |
| 02 | Missed-day muscle recovery within the week | Feature | `brief-02-missed-day-muscle-recovery.md` | Pre-authorized |
| 03 | Never-performed planned exercises are re-planned forever | Feature | `brief-03-never-performed-exercises.md` | Pre-authorized — **reduced scope** (exercise-level only) |

## Cut by the user 2026-08-03 — do not re-propose or re-brief

The following were fully briefed from the same data and then **scrapped by the user**. Recorded here so a future analysis does not "rediscover" them; none of them is undiscovered work.

- **Weekday-adherence half of 03** (never-trained weekday detection, rest-day suggestion, generator deprioritization of low-adherence days) — cut; only the exercise-level half survives. The former assumption A3 is moot.
- **04 Bodyweight-exercise progression dead-end detection** — cut.
- **05 Duplicate planned day** — cut. **Note: this documented a real defect, not a feature idea** — the stored plan for the week starting 2026-07-13 contains two identical planned days plus one merged 8-exercise day, most plausibly a stale artifact of a plan-mutation operation (move/append/regen). The user has chosen not to pursue it: it is **known and deliberately unfixed**. If duplicate planned days surface again, treat it as this known issue, not a new discovery.
- **06 Finer proximity-to-failure capture (RIR-style effort scale)** — cut. Effort logging stays at Easy/Moderate/Hard. This was the diagnostic enabler for deeper stall analysis; its absence limits what item 01 can honestly claim (see brief 01's "Honest limit" note: the floor check verifies plan *allocation*, it cannot attribute a stalled lift to user effort vs generator progression).
- **07 Bodyweight-trend vs goal insight** — cut.

## Overlap with shipped / in-flight / parked work (do not build twice)

- **Shipped or in progress on `gen-science-fixes-2026-08-03`:** cross-week recovery context (science-review P1), weight-fabrication gate (P2), classifier/estimator fixes (P3), cardio removal from generated plans, duration-estimator correction, backup field-name portability. **None of the items here re-covers those.**
- **Parked (previous orchestrator's outstanding findings — do not duplicate):** science-review P4 (attempt-1 under-fill / Axis 7), P5 (pull→hinge adjacency guard), P6 (goal-aware salvage rest floor).
- Item 01 is *adjacent* to the science review's S6 "push volume trends low — worth watching" note, but that was a synthetic 3-day-plan observation with no brief; item 01 is live-user evidence on 5-day plans and is the first actual work item for it.
- Item 02 extends the existing auto-rebalance mechanism (v1.11.0 era) — it is a gap in that feature, not a new parallel one.

## Merge / cluster and parallelization guidance

- **Cluster A = items 01 + 03 (one worker).** Both change what the generation prompt/pipeline knows or checks (`AiRepository` prompt build + accept path). Building them separately guarantees prompt-merge collisions.
- **Item 02 separate** — plan/rebalance logic on the app side (repository/plan layer), not the prompt.
- **HAZARD / sequencing:** Cluster A and item 02 touch the same area as the in-flight `gen-science-fixes-2026-08-03` branch (now multiple commits ahead). **Do not start any of this batch until that branch lands**; rebase on its result.
- Suggested order: Cluster A first, then 02 (02 benefits from 01's floor definitions when preserving coverage).

## Flagged assumptions (user may veto — no confirmation round was held)

- **A1 (01):** "floor" defaults assumed: each major muscle ≥2 planned days/week and ≥~6 direct hard sets/week on Hypertrophy at ≥4 days/week — thresholds are a product decision; the brief requires them to be adjustable in one place.
- **A2 (02):** recovery is **offered, not silently applied** (a prompt/notification, consistent with existing rebalance UX). Silent auto-moving is a product decision the user has not made.

## Cross-cutting constraints

- Build via `./build.sh` (never `./gradlew` directly).
- No commits or releases unless the user asks; branch `gen-science-fixes-2026-08-03` is owned by another orchestrator — coordinate, don't touch.
- No on-device / automated UI tests unless the user asks; verify via build + unit tests.
- Any change consuming `MuscleClassifier` output must keep StatsRecomputer merge-parity (standing constraint).
- **No live Anthropic API calls for verification without an explicit grant** (standing frugality rule); prompt changes in Cluster A should be validated offline (unit tests on prompt text / accept path) unless the user grants calls.

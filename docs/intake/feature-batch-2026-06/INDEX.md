# Intake Index — Feature Batch (2026-06)

**Prepared for:** Project-lead orchestrator
**Source:** Open-ended feature-exploration session with the user. Eight ideas were explored in depth; the user selected this batch and made the product decisions recorded below.
**Status:** **All eight items confirmed and briefed** (B1, B2, B3, C1, C4, E1, E2, E3). Outcome-only — the "how" belongs to the orchestrator and its workers.
**Enforceable build order:** see **`SEQUENCE.md`** in this folder — the numbered-wave implementation sequence the coordinator enforces (dispatching one wave at a time). The merge/cluster + parallelization guidance below is the *analysis*; `SEQUENCE.md` is the *ordering contract* derived from it.

---

## Confirmation note (read first)

The user confirmed **directly, in their own words** ("Confirmed — it's me, the user") that the decisions baked into these briefs are theirs and that these documents should be produced. Some intermediate answers were relayed through a coordinator; the load-bearing go-ahead was direct. Per operating rules, only the user's direct word is the gate — that gate is satisfied for all eight items, including E2 (the user supplied E2's two outstanding decisions, L1 and M2, after the first seven were briefed).

These documents are intake artifacts only. **Nothing here has been handed to the orchestrator or to implementation** — that is a separate, later instruction the user will give.

---

## Items

| ID | Title | Type | Brief | Status |
|----|-------|------|-------|--------|
| B1 | AI weekly coach summary | New feature | `brief-B1-ai-coach-summary.md` | Confirmed |
| B2 | "Why did the program change?" rationale | New feature | `brief-B2-program-change-rationale.md` | Confirmed |
| B3 | Plateau / stall detection | New feature | `brief-B3-plateau-detection.md` | Confirmed |
| C1 | Per-exercise PR history & estimated-1RM trends | New feature | `brief-C1-1rm-pr-trends.md` | Confirmed |
| C4 | Muscle-group recovery / freshness view | New feature | `brief-C4-recovery-view.md` | Confirmed |
| E1 | Manual program editing | New feature | `brief-E1-manual-program-editing.md` | Confirmed |
| E2 | Multiple programs + periodized mesocycles | New feature | `brief-E2-programs-and-mesocycles.md` | Confirmed |
| E3 | Exercise library browser with instructions | New feature | `brief-E3-exercise-library-browser.md` | Confirmed |

**E2's two outstanding scope questions are now answered by the user:**
- **L → L1 — AI-driven weekly progression:** within a mesocycle block, progression is the app's existing weekly AI adaptation based on logged performance (not a fixed deterministic ramp); the per-week API call is accepted.
- **M → M2 — stall/fatigue-triggered deload:** the deload is not a fixed final week; it kicks in when plateaus/accumulated fatigue are detected. This **deliberately couples E2's deload trigger to B3 (plateau/stall detection)** — the user is fine with the coupling. See the dependency edge in the parallelization plan below.

---

## Merge / cluster + parallelization guidance

This section answers: **what can be built simultaneously, what must be one unit, and what must be sequenced.** It is grounded in the actual files each item touches.

### Integration seams (shared surfaces multiple items contend for)

- **Generation prompt & flow** — `data/repository/AiRepository.kt` (`buildPrompt`, `generateAdaptedProgram`, `buildSessionHistory`, `parseProgram`). Touched by **B2** (new `rationale` field in the model response), **B3** (if stalls feed into the prompt), and **E2** (program/mesocycle-aware generation). **Hottest seam in the batch.**
- **Program storage** — `planned_exercises` table, `PlannedExerciseDao`, `WorkoutRepository.savePlan/saveDayPlan/updatePlannedExercise`. Touched by **E1** and **E2**. Both also touch the **Program tab** (`ProgramFragment`/`ProgramViewModel`).
- **Stats / Recap screens** — `HistoryStatsFragment`, `RecapTrendsFragment`/`RecapTrendsViewModel`. Touched by **C1**, and possibly **B1/B3** if their output is placed there (placement is left to the orchestrator — see B1/B3 briefs).
- **AI history-context layer (read-only)** — `getStrengthHistory`, `getWeeklyVolume`, `buildSessionHistory`. **Read** by B1, B3, C1. Concurrent *reads* are not a collision; only concurrent *writes* to the same file are.
- **DB schema version** — any new table (B1 if summaries persist; E2's program entity) bumps the Room version and edits `AppDatabase` + the migration list. **Two workers adding tables in parallel will collide on the version number/migration list** — serialize schema bumps.

### Parallel groups

**P1 — AI coaching cluster (B1 + B2 + B3): ONE worker, internally ordered.**
All three read the same history-context layer; B2 and B3 both write to `AiRepository`/the prompt. Splitting them across workers means concurrent edits to `buildPrompt`/`generateAdaptedProgram` and overlapping response parsing — a guaranteed merge conflict. Keep them in **one worker/cluster**. Internal order: do the **prompt/response-shape change once** (B2's `rationale` field, then B3's stall signal into the prompt), with **B1** alongside (separate user-facing readout, same context plumbing). Internal dependency: if B3 feeds stalls into the prompt, B3 detection must exist before that prompt edit — same worker, so this is just ordering.

**P2 — Program-control cluster (E2 → E1): ONE worker, strictly sequenced.**
Both touch `planned_exercises` storage and the Program tab. **E2 introduces a new "program" entity and reworks how plans are keyed/selected — that is the foundation E1's manual editing sits on.** Building E1 first against today's single-`weekStart` model, then re-keying underneath it with E2, would force a rework of E1. Order: **E2 first, then E1.** This is the heaviest item in the batch and likely the **critical path.**

**New cross-cluster dependency from decision M2: B3 → E2 (deload).** E2's deload is **triggered by stall/fatigue detection (M2)**, which reuses **B3's** logic. So **B3 must exist before — or be delivered together with — E2's deload-trigger piece.** B3 is in P1 and E2 is in P2, creating a **P1 → P2 ordering edge specifically on E2's deload logic.** This does **not** block all of E2: E2's program-entity/storage and named-program work can proceed in parallel with B3; only the deload-trigger piece waits for B3. (Decision L1 also keeps E2 inside the generation flow, so the `AiRepository` seam below still applies to E2 alongside B2/B3.)

**P3 — Local analytics, parallel-safe (C1, C4, E3): up to three independent workers.**
Read-mostly, on distinct screens, no/low file overlap:
- **C1** → new content on `RecapTrendsFragment` (reads `getStrengthHistory`).
- **C4 (view)** → a new freshness panel **on Home (locked placement)**, reads muscle-group/last-trained data. **C4 is split:** the *view* is parallel-safe here; the *AI-nudge* piece (feeding recovery state into the prompt, assumption J) touches `AiRepository` and is **NOT parallel-safe** — it is sequenced onto the `AiRepository` track, not run in this group. (If the user overrides J to inform-only, the nudge piece disappears.)
- **E3** → a new list+detail screen over the already-bundled `ExerciseCatalog` (no API, no schema change).
These can run **fully in parallel** with each other and with P1/P2. Notes: (i) **C4's view is locked to Home**, so it does not touch the Program tab; only if the user overrides to the Program tab does it touch `ProgramFragment` and need to reschedule after the P2 work; (ii) **C4's AI-nudge piece** rides the serialized `AiRepository` seam, not this parallel group; (iii) **C1 on RecapTrends** collides with nothing else, since no other item touches that screen.

### Cross-group hazards & dependencies to manage

- **B3 → E2 deload (ORDERING DEPENDENCY, from M2):** E2's stall/fatigue-triggered deload reuses B3's detection. **B3 must land before, or with, E2's deload-trigger piece.** P1 → P2 edge on that piece only; the rest of E2 is unblocked.
- **`AiRepository` (P1 ↔ P2):** B2/B3 edit the prompt/response; E2 (per L1) stays in the adaptive generation flow and makes it program/mesocycle-aware. If P1 and P2 run concurrently they will both edit `AiRepository`. **Sequence the AI-touching parts or have the two workers explicitly coordinate on that file.** Loudest seam in the batch.
- **DB schema version (B1 / B2 ↔ E2):** if B1 (persisted summaries), B2 (rationale persisted with the plan), and/or E2 (program entity) add tables, their Room migrations must be authored one version bump at a time, not concurrently.
- **`AiRepository` (C4-nudge):** C4's AI-nudge piece also edits the prompt — it joins the serialized `AiRepository` track, never run concurrently with B2/B3/E2's edits.
- **Program tab (P2 ↔ C4):** not a concern given C4's view is locked to Home; only reappears if the user overrides C4 to the Program tab.

### Suggested overall order

1. **Start in parallel now:** P3 (C1, C4, E3) as independent workers, and P1 (B1/B2/B3) as one worker. **Prioritize B3 within P1**, because E2's deload depends on it.
2. **E2** — begin its **program-entity / storage / named-program** work early (longest pole, E1's foundation); it can run in parallel with B3. **Hold E2's deload-trigger piece until B3 lands.**
3. **E1** — after E2's storage/program model is settled.
4. **Manage the `AiRepository` seam** between P1 and E2: whichever AI-touching change goes second rebases on the first. Never let both edit `AiRepository` blind.

### Summary table

| Group | Items | Mode | Rationale |
|---|---|---|---|
| P1 | B1, B2, B3 | One worker, internally ordered (do B3 early) | Share AI history-context + prompt/response; concurrent edits to `AiRepository` would collide. B3 is also a prerequisite for E2's deload. |
| P2 | E2 → E1 | One worker, strictly sequenced; E2's deload waits on B3 | Both touch program storage + Program tab; E2's program entity is E1's foundation. E2 deload depends on B3 (M2). |
| P3 | C1, **C4 view**, E3 | Up to 3 parallel workers | Read-mostly, distinct screens, no/low overlap. C4's AI-nudge piece is NOT here (rides the `AiRepository` seam). |
| Seams/edges | **B3→E2 deload (ordering)**, `AiRepository` (P1↔P2, + C4-nudge), DB schema (B1/B2↔E2), Program tab (only if C4 overridden to Program) | Sequence/coordinate | Flagged so they are not edited blind |

---

## Confirmed decisions baked into these briefs

- **API calls:** extra Claude API calls are acceptable; use the model where it adds value (B1/B2/B3, and C4 if useful).
- **B1 cadence:** **automatic, weekly.**
- **B2:** the model emits a **`rationale` field in the generation response it already produces** — the "why" is the model's own stated reasoning, surfaced to the user.
- **B3 & C4 thresholds:** grounded in **cited exercise science**, not arbitrary numbers (see each brief).
- **E1:** kept **simple**; manual edits **last only until the next regeneration**, which may overwrite them (accepted).
- **E2:** **both** named saved programs **and** periodized mesocycle blocks. **L1** — within a block, progression is the existing weekly AI adaptation (per-week call accepted), not a fixed ramp. **M2** — deload is **stall/fatigue-triggered** (reuses B3), not a fixed final week; the B3 coupling is accepted.
- **C4:** **fixed recovery-window coloring** (recovering / ready / overdue), with windows from the literature.
- **Garmin (formerly D4):** **dropped entirely** — no brief, out of this batch.

## Assumptions applied (user may override any of these)

These were minor open questions the user delegated to standard defaults. Each is labelled **ASSUMPTION** in its brief so it can be vetoed:

- **C (B1):** weekly summaries are **persisted** as a scrollable history (small new store), not ephemeral.
- **D (B2):** the rationale **persists with the saved plan** (visible on the Program tab until the next regeneration), not a one-time message.
- **F (B3):** a detected stall **both** shows on screen **and** is fed into the next generation prompt.
- **G (C1):** **Epley** formula for estimated 1RM.
- **H (C1):** PRs tracked by **estimated 1RM** (captures rep PRs), warm-ups excluded per existing convention.
- **J (C4):** the recovery view **informs the user and also nudges the AI** on what to train. (The nudge half is the `AiRepository`-seam piece; inform-only override removes it.)
- **C4 placement (LOCKED, not just an assumption):** the recovery view sits on **Home**, not the Program tab — locked to keep it parallel-safe in Wave 1. User may override to the Program tab (which reschedules it after Wave 3).
- **K (E1):** manual editing covers **edit (sets/reps/weight/notes), delete, add, and reorder**.
- **N (E2):** within a named program, the AI's weekly auto-adaptation **still runs** (consistent with L1) unless the user explicitly freezes the program.
- **O (E3):** **browse-only** reference first; an "add/swap into plan" action is noted as an optional nice-to-have.
- **P (E3):** exposes the **full bundled catalog** (already on-device with instructions/images), not just the ~26 defaults.

## Cross-cutting constraints (apply to every brief)

- Build via `./build.sh` (not `./gradlew` directly).
- No git commits/releases unless the user explicitly asks.
- **On-device UI testing IS required for this batch as part of the per-wave verification gate.** The general "no on-device/automated UI tests unless explicitly asked" rule is **explicitly satisfied** here: `SEQUENCE.md` requires `ui-test-worker` on-device evidence for every UI-affecting feature before a wave passes. See `SEQUENCE.md` for the full evidence-backed, per-wave gate (Wave Verification Report + confirmed PASS). This carve-out is scoped to *verifying these features*; it does not authorize commits/releases.
- A concurrent in-flight task is implementing **automatic cloud backup/restore** and is modifying `PreferencesManager`. Any new persisted data (B1 summaries, B2 rationale, future E2 program entity) should be implemented mindful of that churn, and — where it is user data — should be considered for inclusion in the backup set.

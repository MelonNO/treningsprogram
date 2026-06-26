# Intake Index — Bug Sweep + UX Improvements (2026-06)

**Prepared for:** Project-lead orchestrator
**Source:** Intake session with the user. The user requested a large, app-wide bug-search-and-fix pass plus UX improvements, post-v1.8.0. A follow-up round added **concrete user-tested items** (U1–U3) from real-device testing, folded into this same batch.
**Status:** **Confirmed and briefed.** Outcome-only — the "how" belongs to the orchestrator and its workers.
**Enforceable build order:** see **`SEQUENCE.md`** in this folder.

---

## Confirmation note (read first)

The user gave a **direct go-ahead** in their own words ("seems good go ahead") after reviewing a confirmation summary of the locked scope, the two-phase structure, the PR-truth resolution, and the folded-in proposals. Per operating rules, only the user's direct word is the gate — that gate is satisfied for this batch.

A **second direct go-ahead** ("Go with the understanders reccomendation combine them") authorized the concrete user-tested items (U1–U3) and their folding into this batch. That gate is also satisfied.

These documents are intake artifacts only. **Nothing here has been handed to the orchestrator or to implementation** — that is a separate, later instruction the user will give.

---

## Confirmed scope (locked)

- **Breadth:** WHOLE APP — every screen, every flow.
- **Method:** BOTH adversarial on-device testing (rotation, backgrounding/restore, empty/first-run, rapid taps, no-network, no-API-key) AND code/logic review.
- **Severity bar:** ALL tiers — crashes & data-loss, functional correctness, visual/layout, AND minor polish/inconsistencies. Nothing is out of bounds for severity.
- **Size:** EXHAUSTIVE / as-thorough-as-possible. Large batch.
- **Stance:** mostly **"diagnose first"** — the bug briefs define *where to look* and *the bar*, not a pre-list of fixes.
- **UX:** the understander proposes a prioritized UX set, the user approves/cuts; weighted to the user's named worst-feeling flows — **Recap, Progress, History.**
- **Don't-touch zones:** none.

## Decisions & reversals recorded

- **Two-phase structure (Q8):** **bugs-first (Phase 1) → UX-second (Phase 2)**, one INDEX. The orchestrator may parallelize *within* each phase.
- **PR-truth (Q11 / P-3):** **retire/replace the legacy warm-up-counting PR widget** with the correct C1 estimated-1RM logic — one source of PR truth. (Brief F2.)
- **Achievements >200 (F1):** **FIX NOW.** This **reverses** the user's prior "don't apply" (2026-06-23).
- **AI timeout/retry (P-1 / F3):** **FIX** — first-class item.
- **Empty/first-run audit (P-2):** **IN-SCOPE**, naturally covered by the exhaustive on-device method.

### Concrete user-tested items (U1–U3) — added follow-up round

From real-device testing the user gave 8 concrete items; one ("remove AI coach summary") was **cancelled**, the rest were confirmed and folded in here. Decisions baked in:

- **U1 — Recovery view rework (items #1–#4):** (#1) recovery counts **secondary muscles with per-exercise weighting** — different exercises affect different muscles to different degrees; (#3) **finer muscle taxonomy** (e.g. upper/lower back, front/side/rear delts, biceps/triceps out of "Arms", quads/hamstrings/glutes/calves out of "Legs"); (#2) recovery view shows **only currently-recovering** muscles (hide rested/ready/untrained) with **how much recovery remains**; (#4) tapping a recovering muscle opens the **last session that trained it** (not a new/generated workout).
- **U2 — XP log (item #6):** tapping the XP bar opens an **XP history**; **records going forward only** (no historical backfill).
- **U3 — Home reorder (item #8):** fixed top→bottom order **XP bar → Weekly Challenge → Today's Plan → Body weight → Muscle recovery → Recent workout**; **reorder only** (body-weight widget already exists).
- **#7 (richer Recap visuals):** **merged into UX1** (understander's best judgment on the graph set) — not a separate brief.
- **#5 (remove AI coach summary / B1):** **CANCELLED** by the user — B1 is kept; **S3 retains its B1 coverage unchanged.**

---

## Items

### Phase 1 — Bug sweep & fixes

| ID | Title | Type | Brief | Cluster |
|----|-------|------|-------|---------|
| S1 | Home (today) & workout logging flow | Sweep (diagnose-first) | `brief-S1-home-and-workout-logging.md` | SW-A |
| S2 | Program tab (named programs, mesocycles, manual editing, deload) | Sweep | `brief-S2-program-tab.md` | SW-B |
| S3 | AI generation, adaptation & coaching outputs | Sweep | `brief-S3-ai-generation-and-adaptation.md` | SW-B |
| S4 | Stats / History / Recap / Progress (+ 1RM/plateau readouts) | Sweep | `brief-S4-stats-history-recap-progress.md` | SW-C |
| S5 | Exercise library, recovery view, profile & summary | Sweep | `brief-S5-library-recovery-profile.md` | SW-C |
| S6 | Gamification & achievements | Sweep | `brief-S6-gamification-achievements.md` | SW-C |
| S7 | Settings, onboarding/setup & backup/restore | Sweep | `brief-S7-settings-backup-restore.md` | SW-D |
| S8 | Navigation & app-lifecycle (cross-cutting) | Sweep | `brief-S8-navigation-and-lifecycle.md` | SW-D |
| F1 | Achievements count >200 (orphan rows) | Fix (cause known) | `brief-F1-achievements-orphan-rows.md` | SW-C |
| F2 | One source of PR truth (retire legacy PR widget) | Fix + consolidation | `brief-F2-pr-truth-consolidation.md` | SW-C |
| F3 | AI network timeout / retry resilience | Fix / resilience | `brief-F3-ai-timeout-retry-resilience.md` | SW-B |
| U1 | Recovery view rework (secondary muscles, finer taxonomy, recovering-only, tap→last session) | Feature change (user-tested #1–#4) | `brief-U1-recovery-rework.md` | SW-C |

### Phase 2 — UX improvements

| ID | Title | Type | Brief |
|----|-------|------|-------|
| UX1 | UX improvements (weighted to Recap/Progress/History; absorbs user-tested #7 richer Recap visuals) | UX (propose → approve/cut) | `brief-UX1-stats-area-ux-improvements.md` |
| U2 | XP log / history (tap XP bar; forward-recording) | New feature (user-tested #6) | `brief-U2-xp-log.md` |
| U3 | Home screen reorder | UX change (user-tested #8) | `brief-U3-home-screen-reorder.md` |

---

## Merge / cluster + parallelization guidance

Grounded in the surfaces each item touches. The sweeps (S*) are scoped by *screen/subsystem* so they parallelize cleanly; the fix briefs (F*) attach to whichever cluster owns their files.

### Integration seams (shared surfaces multiple items contend for)

- **`AiRepository` / network layer** — the **hottest seam.** Touched by **S3** (generation/parsing/adaptation review) and **F3** (timeout/retry). Coordinate; never edit blind concurrently.
- **`WorkoutRepository` / `planned_exercises` / Program tab** — touched by **S2** and indirectly by **S3** (generation writes plans). Keep S2+S3 coordinated.
- **Stats/History surface** — touched by **S4** and **F2** (legacy widget retirement). Coordinate on the same screens.
- **Gamification/achievements** — **S6** (unlock correctness + duplicate defs), **F1** (count/orphan rows), and **U2** (new XP-event log). Coordinate so F1's pruning doesn't disturb unlock state S6 verifies, and so U2's XP-event capture doesn't disturb existing XP/level math.
- **Recovery surface (Home panel + `MuscleRecovery` model)** — **U1** reworks it (weighted secondary muscles, finer taxonomy, recovering-only display, tap→last session). This **supersedes the recovery portions of S5 and the Home recovery panel in S1** — those sweeps verify the *reworked* behavior. **U3** reorders the Home panel's position. Coordinate so S1/S5 don't sweep the old behavior.
- **Home screen** — **S1** (sweep), **U3** (reorder), **U1** (recovery panel content). Land consistently so S1 verifies the final Home.
- **Lifecycle/navigation** — **S8** is the cross-cutting net; per-screen sweeps also exercise lifecycle on their own surfaces. De-dup at integration.
- **`PreferencesManager` / `ExportRepository`** — **S7**, plus an in-flight cloud-backup task. Coordinate.

### Clusters (each cluster = one worker / one coordinated unit to avoid blind concurrent edits)

- **SW-A — Logging path:** S1. Mostly self-contained (Home/today + log flow + draft).
- **SW-B — Program & generation:** **S2, S3, F3.** Share `AiRepository`/`WorkoutRepository`. **Internal order: F3 (timeout/retry) first or coordinated, then S3, alongside S2.** The deload couples to B3 stall logic exercised in S6 — coordinate the stall convention.
- **SW-C — Analytics & display:** **S4, S5, S6, F1, F2, U1.** Distinct screens but several attached items live here. **Internal notes:** F2 coordinates with S4 (same Stats screens); F1 coordinates with S6 (achievements); **U1 (recovery rework) supersedes S5's recovery portion** and feeds the Home panel that S1/U3 touch. U2 (XP log) attaches to S6's gamification surface but is Phase 2.
- **SW-D — Settings & lifecycle:** **S7, S8.** S7 is settings/backup; S8 is the cross-cutting lifecycle net.

### Parallel-safety

- **SW-A, SW-C, SW-D** are largely parallel-safe with each other (distinct file surfaces), allowing several workers at once.
- **SW-B is the critical-path cluster** (hottest seam, heaviest logic). Sequence its internals; don't run it blind alongside anything else that edits `AiRepository`.
- **S8 (lifecycle)** overlaps every surface by nature — best run as a cross-cutting pass whose findings are de-duplicated against the per-screen sweeps at integration.

---

## Two-phase sequence (summary; full contract in SEQUENCE.md)

1. **Phase 1 — Bug sweep & fixes + recovery rework.** All S* sweeps + F1/F2/F3 + **U1**, by cluster, parallelizing the parallel-safe clusters and sequencing SW-B internally. **U1 (recovery rework) lands in Phase 1** because it is substantial model/feature work that the recovery-touching sweeps (S5, S1) must verify in its *reworked* form — building it first avoids sweeping behavior that is about to change. Diagnose-first for the sweeps/fixes; on-device verification per the gate. Phase 1 must reach a stable, verified state before Phase 2.
2. **Phase 2 — UX improvements (UX1) + concrete UX additions (U2, U3).** The understander presents a prioritized UX list (UX1, which **absorbs user-tested #7 richer Recap visuals**) grounded in Phase-1 findings; **the user approves/cuts each UX1 item**. **U2 (XP log)** and **U3 (Home reorder)** are concrete, already-confirmed user items — they are not subject to the UX1 approve/cut gate (the user already specified them) and are built as specified.

Rationale for bugs-first: polishing flows that are about to change under bug-fixes wastes effort, and UX changes verify more cleanly once correctness is settled. U1 is the exception placed in Phase 1 because it is feature-level, not polish.

---

## Deferred decisions (flagged for whoever builds it)

- **Backup-set expansion (S7):** whether to add newly-shipped v1.8.0 user data (named programs, manual edits, B1 summaries) and prior gaps (custom Exercise library, GymPresets, excluded prefs) to the export format is **not decided** — surface as a finding; adding it (schema bump, entangled with in-flight cloud backup) is out of scope unless the user asks.
- **Manual-edit overwrite-on-regeneration (S2):** the documented "edits last only until next regeneration" behavior — if it surprises the user, it's a Phase-2 UX candidate, not a Phase-1 defect.
- **UX1 item list:** the specific UX items and any design choices are deferred to the user's approve/cut review informed by Phase-1 findings.
- **U1 muscle taxonomy & per-exercise weights:** the user approved the *direction* (finer subdivisions) and the *principle* (weighted secondary muscles, varying by exercise). The **exact final subdivision list and the exact per-exercise primary/secondary weighting values** are delegated to the builder to land defensibly (ideally grounded like the existing cited recovery bands).
- **U2 XP-bar tap location:** which XP-bar surface(s) open the log is delegated to the builder's best judgment (user said "wherever the XP bar appears").
- **UX1 Recap graph set (#7):** which specific graphs to add is the understander's/orchestrator's best judgment, coordinated with UX1 so Recap visuals aren't duplicated.

---

## Cross-cutting constraints (apply to every brief)

- Build via `./build.sh` (not `./gradlew` directly).
- No git commits/releases unless the user explicitly asks.
- **On-device UI testing IS required for this batch** as part of the per-wave/per-cluster verification gate (see SEQUENCE.md). The general "no on-device/automated UI tests unless explicitly asked" rule is **explicitly satisfied** here by the user's "everything"/"as thorough as possible" answers — adversarial on-device testing is in-scope by the user's instruction. This carve-out is scoped to *this sweep*; it does not authorize commits/releases.
- An in-flight task is implementing **automatic cloud backup/restore** and modifies `PreferencesManager`/`ExportRepository`. Coordinate any settings/backup work (S7) with it.
- **Diagnose before fixing** — no symptom-masking; root-cause every confirmed defect first.

# UX Refinement Batch — 2026-07-03

**Prepared for:** Project-lead orchestrator
**Source:** Self-directed UI/UX refinement round (user delegated method control: "do some more ui ux refinements you controll the method feel free to use the understanderer").
**App state at intake:** v1.25.0 live (main `ac9a7e7`), 914 tests, DB v19, backup v6. Auros dark-teal design language (spec `Change docs/DESIGN.md`).
**Status:** READY. Documents written directly — no user confirmation round (explicitly waived). Every judgment call is a labelled `A-XXx` assumption the user can veto mid-flight. Creating these documents is NOT the same as dispatching to the orchestrator; that is a separate later user instruction.

---

## What this batch is (and is not)

This is a **refinement** round, not a feature round. The app absorbed five feature releases in ~48 h (v1.22.0–v1.25.0); this batch hunts where that growth hurt the day-to-day experience: information hierarchy, cross-screen consistency, friction, and small-screen behaviour. Every item is grounded in a specific layout/code file that was inspected. Nothing here adds a feature, changes the schema, or re-opens a user-ratified decision.

**Checked and deliberately NOT briefed** (so the orchestrator knows they were assessed):
- **F2 (bodyweight "0 kg" in Recap)** and **F3 (History list stuck on skeletons)** from `overnight-run-2026-07-02/STAGE4-FINDINGS.md` — already fixed in **v1.24.1** ("History list binds again, BW recap labels, honest date search"). Confirmed resolved; not re-briefed.
- **F1 (numeric keypad does not dismiss on outside tap)** — flagged in STAGE4-FINDINGS as a **likely Waydroid AOSP-IME artifact**, needs real-device confirmation, and Done/Back both dismiss. Left out because it may not reproduce on a real device and cannot be verified without a device (zero live/Waydroid budget). Flagged here for the user's awareness only.
- **Home-screen widget at small sizes** — `widget_today.xml` is already defensively coded (status line `GONE` when empty, `maxLines=1`+`ellipsize`). No refinement warranted.

---

## Items

| ID | Title | Type | Size | Brief | Cluster |
|----|-------|------|------|-------|---------|
| H1 | Home information hierarchy — surface the day's action first | Refine (density/hierarchy) | M | brief-H1-home-hierarchy.md | A (Home) |
| H2 | Home body-weight card — compact the daily footprint | Refine (density) | S/M | brief-H2-home-bodyweight-compaction.md | A (Home) |
| H3 | Home recovery card — hide when nothing is recovering | Refine (density) | S | brief-H3-home-recovery-hide-empty.md | A (Home) |
| H4 | Body-weight entry delete — confirm or undo (no silent data loss) | Fix (friction/safety) | S | brief-H4-bodyweight-delete-confirm.md | A (Home) |
| L1 | Log screen — target spec chips must not clip on small screens | Refine (small-screen) | S | brief-L1-log-spec-chip-wrap.md | B (Log) |
| C2 | Log/timer controls — replace emoji glyphs with the Auros icon set | Refine (consistency) | S/M | brief-C2-control-glyph-icons.md | B (Log) |
| C1 | Eyebrow section headers — one treatment across all screens | Refine (consistency) | M | brief-C1-eyebrow-unification.md | C (sweep, last) |
| C3 | Empty states — consistent lightweight treatment | Refine (consistency) | S | brief-C3-empty-state-consistency.md | C (sweep, last) |
| W1 | Monthly Wrapped — a persistent close affordance | Refine (friction) | S | brief-W1-wrapped-close-affordance.md | — |
| P1 | Program week strip — day chips must fit compact screens | Refine (small-screen) | S | brief-P1-week-daychip-fit.md | — |

10 items. All S/M — nothing L. Ordered by experience-impact-per-effort (H1 is the headline).

---

## Clustering, file ownership & build order

Grouped by the files each item edits, so the orchestrator can parallelise safely. The two **cross-cutting sweeps (C1, C2-portion, C3)** touch files the per-screen items also touch, so they run **last** to reconcile the final state instead of fighting merge conflicts.

**Cluster A — Home** · files: `fragment_home.xml`, `HomeFragment.kt` · **one worker, sequential H1 → H2 → H3 → H4**
All four edit the same two files. H1 (reorder) reshapes the layout first; H2/H3/H4 then adjust individual cards. Do not parallelise within the cluster.

**Cluster B — Log screen** · files: `fragment_log_workout.xml`, `LogWorkoutFragment.kt` · **one worker, L1 → C2**
L1 (chip row wrap) and C2 (glyph→icon on the log/timer controls) both edit the log layout; keep them in one worker to avoid a same-file collision.

**Standalone (parallel with A and B):**
- **W1** · `dialog_wrapped.xml`, `WrappedDialogFragment.kt` — independent.
- **P1** · `item_day_chip.xml`, `ProgramFragment.kt` — independent.

**Cluster C — cross-cutting consistency sweeps** · **run AFTER Clusters A/B/W/P have merged**
- **C1 (eyebrow unification)** touches ~30 layouts including `fragment_home.xml`, `fragment_log_workout.xml`, `dialog_wrapped.xml`. Running it after the per-screen items land means it standardises the already-refactored files. One worker.
- **C3 (empty states)** touches `fragment_home.xml`, `fragment_profile.xml`, and Home recovery rendering — overlaps Cluster A. Run after A. One worker. Can share the C1 worker (both are cross-cutting polish) or run sequentially C1 → C3.

### Build order
1. **Wave 1 (parallel):** Cluster A (Home), Cluster B (Log), W1, P1.
2. **Wave 2 (after Wave 1 merges):** C1 (eyebrow sweep), then C3 (empty-state sweep).

### Cross-group hazards
- `fragment_home.xml` is touched by H1/H2/H3/H4 **and** C1 **and** C3 → Home must be fully settled (Wave 1) before the sweeps (Wave 2) run.
- `fragment_log_workout.xml` is touched by L1/C2 **and** C1 → same ordering rule.
- `dialog_wrapped.xml` is touched by W1 **and** C1 → W1 in Wave 1, C1 in Wave 2.

---

## Assumptions applied (user may veto any)

- **A-H1a** — Target Home order (after the hero band): today's plan/Start-Workout card is raised **above** Weekly Challenges so the day's primary action is reliably above the fold; the XP status strip stays at the top; body-weight and recent-workouts sink to the bottom.
- **A-H1b** — The XP card stays visually prominent and vivid (gamification preference honoured); only its *position relative to the day's action* changes.
- **A-H2a** — The body-weight card collapses to a compact one-line glance (current weight + trend when available) with the weigh-in input revealed on demand, rather than showing the input field every day.
- **A-H3a** — The muscle-recovery card is **hidden entirely** when nothing is recovering (mirroring the app's other conditional cards), rather than showing an "all rested" line.
- **A-H4a** — Deleting a body-weight entry gets an **undo** affordance (snackbar) rather than a blocking confirm dialog, to keep the gesture fast while making loss recoverable.
- **A-L1a** — The three target spec chips (sets/reps/weight) are allowed to **wrap to a second line** on narrow screens instead of clipping.
- **A-C1a** — Unification direction: the **hero band** at the top of each tab keeps its beacon dot; **in-card section eyebrows** standardise to the dot-less `Widget.Auros.Eyebrow` treatment (the majority pattern, ~23 layouts vs ~9). This removes the dots currently on Home's in-card eyebrows.
- **A-C2a** — Scope of the glyph→icon swap is limited to the **functional controls** on the log screen and rest timer (pause, timer-recall, image-expand). Calculator glyphs on the weight keypad (+ − ✓ ⌫ C) stay as-is — they read as calculator semantics, not brand iconography.
- **A-C3a** — Empty-state consistency means unifying **copy voice and visual weight** of the bare in-card empty states (Home recent-workouts, Home recovery, Profile PRs) — a single muted line in the same tone. It does NOT mean adding heavy icon+title empty states to every card.
- **A-P1a** — On compact widths the day chips shrink/adapt (badge sizing responsive) rather than the row scrolling horizontally.

## User-ratified constraints respected (not touched)
First-ever lifts are baselines, not PRs · Stats muscle balance stays high-level · keypad tap-outside behaviour is the stage-3 item-16 spec · no rep PRs · no streak freeze · no Wrapped sharing · the 16 stage-3 items and 10 v1.25.0 picks are refined *around*, never re-litigated.

## Cross-cutting constraints (every brief)
- Build with `./build.sh` (not `./gradlew`). No commits, tags, or releases unless the user asks.
- No on-device / Waydroid / Maestro runs; no live Anthropic API calls. Verify via build + unit tests only.
- **No DB schema change** (DB stays v19), **no new dependencies**. This is polish.
- **Gamification accents stay vivid** (long-standing user preference) — never mute the XP/challenge/PR/achievement colours.
- Outcome-only briefs: they state the end result and acceptance criteria, never the implementation.

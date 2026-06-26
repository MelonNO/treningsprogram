# S6 — Sweep: Gamification & achievements

**Type:** Bug sweep (diagnose-first)
**Phase:** 1 (bug sweep & fixes)
**Cluster:** SW-C (analytics & display) — see INDEX.
**Outcome-only:** Defines *where to look* and *the bar to hold*. Workers diagnose and fix.

---

## Context

`data/repository/GamificationRepository.kt`, the `Achievement` entity, and daily-challenge logic (`dailyChallengesJson` preference). 200 predefined achievements across 8 categories. This area has **known, recorded issues beyond the count bug**: the count-display defect is its own fix brief **F1**; this sweep covers the rest of gamification correctness, including the two recorded duplicate-definition issues below.

> **Cross-reference (concrete user-tested item):** the new **XP log (U2)** attaches to this gamification surface (it adds XP-event capture + a tappable XP history). U2 is **Phase 2**, but this sweep should be aware of it and coordinate so U2's XP-event capture does not disturb the existing XP/level math this sweep verifies.

## The hunt (where to look)

- **Unlock correctness:** achievements unlock when (and only when) their conditions are met; no false unlocks, no missed unlocks across the 8 categories.
- **Known recorded issues to verify/resolve (diagnose, then fix per the all-tiers bar):**
  - Duplicate **display name "Diamond"** shared by `workouts_60` and `diamond_level`.
  - `combo_hercules` and `combo_strength` share **identical description AND unlock condition** (`sp>=5 && vol>=3000f`) so they always unlock together — likely one is mis-specified.
- **Daily challenges:** generation, rollover by day, persistence across restart, locale/day-boundary correctness.
- **Counts/stats interplay:** how achievement state interacts with profile display (count fix is F1; correctness of *unlock state* is here).

## Acceptance bar (ALL tiers apply)

Full severity bar: crashes & data-loss, functional correctness (false/missed unlocks, duplicate definitions), visual/layout, minor polish.

## Adversarial / edge states to exercise (on-device)

First-run (clean install — confirm correct baseline); day-boundary rollover for challenges; restart mid-day; conditions met exactly at threshold; upgrade-over-existing-data path (orphan-row scenario — count handled by F1, but verify no *unlock* corruption).

## Acceptance criteria ("Done when …")

- **Done when** unlock conditions across all categories have been exercised **and** code-reviewed, with findings recorded against the all-tiers bar.
- **Done when** the two recorded duplicate-definition issues (Diamond name; combo_hercules/combo_strength) are **diagnosed and resolved** so each achievement is distinct and correctly triggered.
- **Done when** daily challenges roll over and persist correctly across restarts and day boundaries.
- **Done when** each confirmed defect is **diagnosed to root cause before any fix**, then fixed and re-verified.

## Scope & constraints

- **Diagnose first.**
- **Coordination:** the achievements **count >200 / orphan-rows** defect is **F1** — this sweep must not duplicate F1's pruning fix, but should confirm F1 does not disturb valid unlock state.
- **Cross-cutting:** build via `./build.sh`; no commits/releases unless asked; on-device verification required (SEQUENCE.md).

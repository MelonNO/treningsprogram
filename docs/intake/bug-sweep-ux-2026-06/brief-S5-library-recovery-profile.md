# S5 — Sweep: Exercise library, recovery view, profile & summary screens

**Type:** Bug sweep (diagnose-first)
**Phase:** 1 (bug sweep & fixes)
**Cluster:** SW-C (analytics & display) — see INDEX.
**Outcome-only:** Defines *where to look* and *the bar to hold*. Workers diagnose and fix.

---

## Context

Mostly-read-only display screens, several brand-new in v1.8.0 (highest empty/first-run crash yield):
- **Exercise library (E3):** `ui/library` — `exerciseLibraryFragment` (list + filter) and `exerciseDetailFragment` (instructions/images over the bundled catalog).
- **Recovery view (C4):** muscle-group freshness (`MuscleRecovery`) — primarily the Home panel (covered for robustness in S1) plus any detail surface. **NOTE:** the recovery view is being **reworked by U1** (weighted secondary muscles, finer taxonomy, recovering-only display, tap→last session). **This sweep verifies the *reworked* recovery view (U1), not the old C4 behavior** — coordinate with U1 so this brief doesn't sweep behavior that is being replaced.
- **Profile:** `ui/profile/profileFragment` — stats, achievements count (note: the achievements **count bug** is its own fix brief **F1**; this sweep covers the rest of the profile screen).
- **Summary:** `ui/summary` post-workout summary screen.

## The hunt (where to look)

- **Library:** list rendering and scroll over the full bundled catalog; filter/search correctness (including no-match); detail screen for exercises with/without images/instructions; back-and-forth navigation.
- **Recovery (C4):** correctness of recovery-window coloring (recovering / ready / overdue) against last-trained data; behavior with no history / never-trained muscle groups.
- **Profile:** all displayed stats correct; achievements display (count handled by F1); empty/first-run.
- **Summary:** post-workout summary reflects the just-completed session accurately, including warm-up handling and zero-set edge cases.

## Acceptance bar (ALL tiers apply)

Full severity bar: crashes & data-loss, functional correctness, visual/layout, minor polish.

## Adversarial / edge states to exercise (on-device)

Library with empty filter results; rapid filter typing; detail for an exercise lacking media; rotation on library/detail/profile/summary; backgrounding/restore; first-run/empty everywhere; never-trained muscle groups in C4; completing a workout with only warm-up sets (summary edge).

## Acceptance criteria ("Done when …")

- **Done when** library, recovery, profile, and summary screens have been exercised on-device through the adversarial states **and** code-reviewed, with findings recorded against the all-tiers bar.
- **Done when** every screen renders a sensible empty/first-run state (no broken lists/charts, no crashes on missing media or zero history).
- **Done when** C4 recovery coloring is correct against last-trained data and the documented recovery windows.
- **Done when** each confirmed defect is **diagnosed to root cause before any fix**, then fixed and re-verified on-device.

## Scope & constraints

- **Diagnose first.**
- **Coordination:** the achievements **count** defect is **F1** (not this brief). Parallel-safe with most other sweeps (distinct screens).
- **Cross-cutting:** build via `./build.sh`; no commits/releases unless asked; on-device verification required (SEQUENCE.md).

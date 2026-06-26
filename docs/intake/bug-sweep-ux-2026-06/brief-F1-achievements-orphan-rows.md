# F1 — Fix: achievements count shows >200 (orphan rows)

**Type:** Bug fix (cause known)
**Phase:** 1 (bug sweep & fixes)
**Cluster:** SW-C (analytics & display) — see INDEX.
**Outcome-only:** Describes the desired end result, not the implementation.

> **DECISION REVERSAL (recorded):** the user previously said "don't apply" this fix (2026-06-23). In this batch the user has explicitly chosen to **fix it now**. This brief authorizes the fix.

---

## Context

`ui/profile` shows the achievements count as the **row count** of the `achievements` table, not a fixed total. `GamificationRepository.ensureAchievementsSeeded()` only ever inserts (`@Insert(onConflict = IGNORE)`) and never deletes. When achievement IDs were renamed/replaced across versions, old rows persisted in users' databases. A device that ran an earlier build can therefore show **more achievements than are defined** (e.g. 286 instead of 200). A clean install shows the correct count — so this only affects upgraded devices.

## Current (incorrect) behavior

The profile achievements count can exceed the number of currently-defined achievements (e.g. 286 vs 200) because orphaned rows from replaced/renamed IDs are never pruned.

## Correct behavior

The achievements count reflects exactly the currently-defined set of achievements, on both clean-install and upgraded devices, **without losing the unlock state of any still-valid achievement.**

## Acceptance criteria ("Done when …")

- **Done when** an upgraded device that previously showed an inflated count (e.g. 286) shows the correct current total after the fix.
- **Done when** orphaned achievement rows (IDs no longer in the defined set) no longer contribute to the displayed count.
- **Done when** the unlock state of every still-valid achievement is **preserved** through the fix.
- **Done when** a clean install is unaffected and still shows the correct count.

## Scope & constraints

- **In scope:** reconciling the persisted achievements table with the currently-defined set so the count is correct.
- **Out of scope:** the duplicate-definition issues (Diamond name; combo_hercules/combo_strength) — those belong to **S6**.
- **Coordination:** must not disturb valid unlock state; coordinate with **S6** which verifies unlock correctness.
- **Cross-cutting:** build via `./build.sh`; no commits/releases unless asked; on-device verification required (SEQUENCE.md).

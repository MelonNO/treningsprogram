# Brief B11 — Level titles past 20 (late-game ladder)

**Type:** Feature (cosmetic gamification)
**Cluster:** Standalone (trivial); safe filler for any worker

> Outcome-only brief: describes the end result and user experience. The "how" belongs to the orchestrator and its workers.

## Context
`GamificationRepository.levelTitle` tops out: "Legend" for 15–19, then a single "Transcendent" for everything 20+. With the sqrt XP curve, long-term users will sit in one title for a very long time — the title ladder simply ends. The user granted complete creative freedom on gamification flavor (standing grant, Q4 of the last round).

## What the user wants (end result)
1. The title ladder continues meaningfully past level 20 — distinct titles at sensible late-game milestones, so a long-term user still has a next title to reach.
2. Existing titles for levels 1–19 stay exactly as they are (users identify with their current title).
3. The new titles fit the app's existing tone (aspirational, slightly playful — "Iron Man", "Phenom", "Legend") and the Auros presentation.
4. Everywhere a title is displayed (profile, level-up overlay, celebration) shows the new ladder consistently.

## Acceptance criteria
- Done when levels 20+ resolve to a ladder of multiple distinct titles (creative freedom on names/spacing), unit-tested at the boundaries.
- Done when levels 1–19 are byte-identical to today.
- Done when no display surface still shows a stale hardcoded title.

## Scope and constraints
- **In scope:** the title mapping + any display constants.
- **Out of scope:** XP formula, level curve, achievements, per-level rewards.
- No DB schema change, no live API.
- Standing: build via `./build.sh`; no commits/releases unless asked; no on-device/automated UI tests.

## Assumptions (user may override)
- **A-L1:** covered by the standing creative-freedom grant — the builder names the titles; the ladder extends to at least ~level 40–50 before a final open-ended title.

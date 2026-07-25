# Brief 05 — Remove the stats block from the Profile tab

**Type:** Feature (removal)
**Cluster:** P (Profile: 4 + 5) — one worker with item 4; build against the post-release-2 Profile.

> Outcome-only brief. No confirmation round was possible (user asleep); assumptions are labelled.

## Context
Profile shows a four-stat block — total workouts, total sets, total volume, current streak (`tvStatWorkouts/Sets/Volume/Streak`) — that duplicates numbers the Stats sub-tab (and Home, for the streak) already presents. The user wants Profile de-duplicated.

## What the user wants (end result)
The stats block is **removed from Profile**. Profile keeps everything else: the XP/level card, the PR section (as reshaped by item 4), the achievements area (including release-2's R5 gallery/next-up strip), the XP log entry, and the Settings entry. The numbers themselves stay available on the Stats tab as today.

## Acceptance criteria
- Done when Profile no longer shows the workouts/sets/volume/streak block and the remaining sections flow cleanly with no dead gap.
- Done when the Stats tab still shows those figures unchanged.
- Done when nothing else on Profile regresses (XP animation targets, achievements section, navigation).

## Scope and constraints
- **In scope:** the Profile stats block only.
- **Out of scope:** Stats tab; the streak's other surfaces (Home, completion flow); any data/logic.

## Assumptions (user may veto)
- **A-05a:** the whole four-stat block goes (including the streak tile) — the streak remains visible on Home, so Profile losing it is acceptable de-duplication.

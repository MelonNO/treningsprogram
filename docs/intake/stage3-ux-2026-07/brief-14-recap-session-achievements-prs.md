# Brief 14 — Recap: show the achievements and PRs earned in that session

**Type:** Feature
**Cluster:** H1 (Recap overhaul: 3 → 9 → 14 → 10) — one worker, after item 9, before item 10 styles it.

> Outcome-only brief. No confirmation round was possible (user asleep); assumptions are labelled.

## Context
The Recap already has a "Heaviest weight" card (per-exercise session PRs + existing bests — `buildPrs`), so the PR half is partially present. **Achievements are absent entirely**: `Achievement` rows carry `unlockedAtMs` but no session link, and nothing on the Recap surfaces what a given session unlocked. Post-release-2, achievements have rarity tiers (feature batch R5) and the completion celebration shows unlocks (R6) — the Recap is the *retrospective* view of the same wins.

## What the user wants (end result)
Opening a session's recap also shows **what was earned in that session**:
- The **achievements unlocked** by that session (name, emoji, and — post-release-2 — their rarity styling, consistent with the R5 gallery).
- The **PRs achieved** in that session, kept/absorbed from the existing heaviest-weight card so PRs read as part of "earned this session" rather than a separate afterthought.
- Sessions that earned nothing simply don't show the section.

## Acceptance criteria
- Done when a session that unlocked achievements lists exactly those achievements in its recap, and neighboring sessions don't claim them.
- Done when the session's PRs appear with their numbers (exercise + new heaviest weight), and the existing-bests reference info the old card provided isn't lost.
- Done when a nothing-earned session shows no empty "earned" shell.
- Done when this works for **future sessions reliably**; for sessions completed before any reliable linkage existed, best-effort is acceptable but never wrong-positive (see A-14a).
- Attribution logic unit-tested off-device.

## Scope and constraints
- **In scope:** the Recap's earned-this-session content.
- **Out of scope:** when/how achievements unlock; the completion celebration (R6); challenges (user asked for achievements and PRs).

## Assumptions (user may veto)
- **A-14a:** achievements are attributed to a session by their unlock moment falling within that session's completion (timestamp match). Where historical data can't be attributed confidently, the recap omits rather than guesses — no false attribution. If a stronger link (e.g. recording the session id at unlock time going forward) is needed for reliability, adding it forward-only is in scope.
- **A-14b:** PR presentation folds into the new section (one "Earned this session" story) rather than duplicating the old card alongside it.

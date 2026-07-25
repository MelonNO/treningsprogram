# Brief 04 — Profile shows only the last 7 days' PRs

**Type:** Feature (behavior change)
**Cluster:** P (Profile: 4 + 5) — one worker; build against the post-release-2 Profile (the feature batch's R5 adds a "next up" achievements strip there).

> Outcome-only brief. No confirmation round was possible (user asleep); assumptions are labelled.

## Context
Profile's PR section (`ProfileViewModel.topPrs` ← `WorkoutSetDao.getTopPersonalRecords`) currently lists the **all-time** heaviest weight per exercise — a static trophy list that rarely changes. The user wants it to be a *recent-wins* surface instead.

## What the user wants (end result)
The Profile PR section shows **only PRs achieved within the last seven days**. Once a PR is more than seven days old it disappears from this section (it remains discoverable in the Progress tab's PR history / Recap — nothing is deleted, only this view changes).

## Acceptance criteria
- Done when a PR set 3 days ago appears, and the same entry no longer appears once 7 days have passed — with no action from the user (rolling window).
- Done when the section's title/copy reflects the new meaning (e.g. "PRs this week" — final copy free) so an empty week reads as motivating, not broken ("No PRs in the last 7 days" style empty state).
- Done when "PR" here matches the app's PR rule (new all-time heaviest weight for that exercise, beating a real prior best, warm-ups excluded).
- Done when the all-time PR data remains available where it lives today (Progress tab); only Profile's view narrows.

## Scope and constraints
- **In scope:** the Profile PR section's data window, title, and empty state.
- **Out of scope:** PR detection rules; Progress-tab PR history; XP/achievements.

## Assumptions (user may veto)
- **A-04a:** "last seven days" = a rolling 7-day window using the app's logical day boundary, evaluated whenever Profile renders.
- **A-04b:** entries show exercise + the new record weight (+ how recent); multiple PRs on the same exercise within the window show the latest/best.

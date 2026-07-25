---
name: project-pretree-changes
description: Uncommitted v1.6.0 pacing/loggedAtMs work was left in the working tree before Fix2 started
metadata:
  type: project
---

At the start of Fix2 (2026-06-23), the working tree already had uncommitted changes that are NOT part of Fix2 — they are the in-progress v1.6.0 "Recap & Trends" pacing feature: `loggedAtMs` column added to WorkoutSet (with MIGRATION_9_10, DB bumped to v10), `SessionPacing` added to SessionRecap, pacing computed in WorkoutRepository.buildSessionRecap, surfaced in HistoryRecapFragment, and loggedAtMs set in LogWorkoutViewModel.addSet calls.

**Why:** The user's standing policy is no unrequested commits / leave changes in the tree for review. These pre-existing edits must be preserved, not reverted or committed.

**How to apply:** When editing the same files (LogWorkoutViewModel, AppDatabase, DatabaseModule, WorkoutRepository, WorkoutSet), build ON TOP of these changes — don't clobber them. The git baseline build is GREEN with them present.

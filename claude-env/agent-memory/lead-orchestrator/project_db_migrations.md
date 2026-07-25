---
name: project-db-migrations
description: Room DB is versioned with explicit migrations and no destructive fallback — schema changes need a migration
metadata:
  type: project
---

`AppDatabase` (data/db/AppDatabase.kt) is at `version = 10` with explicit `MIGRATION_1_2` … `MIGRATION_9_10`, all wired in `di/DatabaseModule.kt` via `.addMigrations(...)`. There is NO `fallbackToDestructiveMigration()`.

**Why:** Real users have data; a missing migration crashes the app on launch after a schema change.

**How to apply:** Any work that adds/changes a column on a Room entity (WorkoutSet, PlannedExercise, WorkoutSession, etc.) MUST bump the version and add a matching `MIGRATION_N_{N+1}` registered in DatabaseModule. Migrations follow the pattern `ALTER TABLE x ADD COLUMN y INTEGER NOT NULL DEFAULT 0`. Prefer solutions that avoid schema changes (e.g. persisting in-progress entered-values as real WorkoutSet rows, or reusing existing columns) where possible. See [[map-active-workout-flow]].

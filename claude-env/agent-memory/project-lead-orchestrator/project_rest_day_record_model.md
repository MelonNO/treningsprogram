---
name: rest-day-record-model
description: Auto-logged REST/MISSED session records — data model + the no-sets invariant that keeps them out of training aggregates
metadata:
  type: project
---

Feature "auto-log a rest day when a day passes with nothing logged" — implemented on branch
`feature/auto-rest-day-logging` (NOT yet merged/shipped as of 2026-06-28; built + 567 unit tests green, on-device unverified per [[feedback_always_skip_waydroid]]).

Data model: `WorkoutSession.kind: String?` (nullable column, Room **v15→v16**, `ALTER TABLE workout_sessions ADD COLUMN kind TEXT`). null = real workout (chosen over a NOT-NULL default to match the backup-safe `planned_exercises.programId` precedent — old backups omit `kind` ⇒ Gson null ⇒ workout, no crash). Values `REST`/`MISSED` (constants on WorkoutSession). REST = empty non-scheduled day; MISSED = empty scheduled-training day.

**Load-bearing invariant (preserve in all future work):** REST/MISSED rows are `isCompleted=true` and **carry NO WorkoutSets, ever.** Their exclusion from every training aggregate (volume/PR/strength/1RM/streak/achievements/AI history) is NOT enforced by a `kind` filter anywhere — it works *only* because every aggregate joins through `workout_sets` (a row with no working set is invisible) and because `getAllCompleted`/`getRecentCompleted` additionally require a working set. **Why:** keeps the change minimal/non-invasive. **How to apply:** any NEW query that counts/reads `workout_sessions` DIRECTLY (not via a working-set join) — e.g. a "total days" or "session count" stat — MUST add `WHERE kind IS NULL` (or `kind NOT IN ('REST','MISSED')`), or it will count rest/missed as workouts. Same class of trap as the warm-up invariant ([[project_warmup_and_muscle_consistency]]).

Other anchors: classification + window math are pure in `domain/RestDayBackfill.kt` (tested in `RestDayBackfillTest`); orchestration in `WorkoutRepository.autoLogRestDays()` (mutex-guarded, idempotent via local epoch-day set, java.time/LocalDate, minSdk 26); triggered from `MainActivity.onStart()`. History Log tab uses a new `getHistoryTimeline()` (workouts-with-working-sets ∪ REST/MISSED) wired through `HistoryViewModel.filteredSessions`; `HistoryViewModel.allSessions` stays workouts-only (Stats totals/CSV). Count-mode "scheduled day" heuristic = weekdays the active program's plan assigns exercises to (`planned_exercises.dayOfWeek`); no plan ⇒ all REST.

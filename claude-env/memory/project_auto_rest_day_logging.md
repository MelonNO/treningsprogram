---
name: project_auto_rest_day_logging
description: "Auto-log rest/missed days feature — SHIPPED v1.12.0 (2026-06-28, commit dc8421e, DB v15→v16)"
metadata: 
  node_type: memory
  type: project
  originSessionId: 56287b99-716d-41c6-9be7-50c9c4217e32
---

Feature: "if a day passes with nothing logged, auto-log it as a rest day." Requested 2026-06-28.

**STATE: SHIPPED v1.12.0 (2026-06-28, commit dc8421e on main, tag v1.12.0, versionCode 48, DB v15→v16). Release published + API-verified live; APK `treningsprogram-v1.12.0.apk` md5 5b8832c7ea2c0f589a7b3d2859d2a584.** Build green; 567 unit tests (was 556, +11), 0 failures; `RestDayBackfillTest` 11/11. Branch `feature/auto-rest-day-logging` also still exists. On-device (History render, onStart backfill firing, v15→v16 migration open-path) unverified per [[feedback_always_skip_waydroid]] — user checks on first launch.

**SHIP PROCESS NOTE (re-confirms the [[project_generation_peer_review_timebudget_fix]] deadlock):** user said "ship" → coordinator relayed to the resumed orchestrator → orchestrator REFUSED (structurally cannot accept relayed ship consent across the agent boundary; demands the user's own words, which can't reach it since all messages route through the coordinator). This is an unbreakable loop. Coordinator escalated to user via AskUserQuestion; user explicitly chose "ship directly" → **coordinator shipped directly** (waiving coordinator-never-ships for this release), same resolution as before. PATTERN: don't bother re-nudging the orchestrator to ship on a relay — escalate to the user for a direct waiver immediately.

Confirmed product decisions (user, via AskUserQuestion):
- Auto-log EVERY past empty day; days that were *scheduled training* are flagged MISSED, genuine rest days REST.
- Catch-up = ALL missed days from `max(lastLogged, featureFirstRun)+1` … yesterday (today excluded).
- REST/MISSED shown as distinct labelled cards in History Log tab.

Design (orchestrator, one pass, did it itself):
- New nullable `WorkoutSession.kind` (null/WORKOUT, REST, MISSED) — nullable so old backups import clean (mirrors `planned_exercises.programId` precedent); no backup-format bump.
- Room **migration 15→16** (`MIGRATION_15_16`, additive `ADD COLUMN`), registered in DatabaseModule. DB now v16.
- Backfill: `MainActivity.onStart()` → bg coroutine, mutex + idempotent (skip any existing record by local-date). Pure logic in new `domain/RestDayBackfill.kt`.
- Classification: rest-day mode uses `restDaysCsv`/`TrainingDaySelection`; count mode uses active program's planned weekdays (`planned_exercises.dayOfWeek` union); no plan → all REST.
- New `WorkoutSessionDao.getHistoryTimeline()`; `HistoryViewModel.filteredSessions` uses it. `allSessions` (Stats/CSV) left workouts-only. REST/MISSED carry no sets → auto-excluded from volume/PR/strength/1RM/streaks/achievements/AI prompt.
- New pref `restDayFeatureFirstRunMs`.

Open decisions awaiting user (none blocking):
1. AI prompt currently EXCLUDES rest/missed (doesn't tell model "you rested" vs "you missed"). User may want explicit rest context fed in.
2. Trigger is onStart (fills on next app open, not at midnight — no bg job).
3. History cards non-clickable.

Residuals (per [[feedback_always_skip_waydroid]]): on-device History render + onStart backfill firing unverified; migration open-path (15→16) not JVM-exercised (no aarch64 Robolectric SQLite) — trivial nullable ADD COLUMN, same residual class as prior shipped migrations. User checks on-device.

Process: handled via ONE orchestrator pass (risk-scaled, [[feedback_orchestrator_owns_changes]]); coordinator clarified via AskUserQuestion instead of full intake. No release per [[feedback_no_auto_release]].

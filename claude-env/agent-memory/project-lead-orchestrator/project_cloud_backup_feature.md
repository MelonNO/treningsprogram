---
name: cloud-backup-feature
description: Cloud backup/restore feature (BR-01) — confirmed design, data surface, gaps, and the Google-cred boundary
metadata:
  type: project
---

Automatic cloud backup & restore to the user's own Google Drive (`drive.appdata` scope). User-authorized 2026-06-24 (direct message). Working-tree only — NO commit/release/tag, NO on-device tests; gate is `./build.sh` + JVM unit tests.

**Why:** user wants "lose phone → install fresh → sign in → resume, zero data loss." Evolves the existing manual JSON export (`ExportRepository.kt` + `SettingsBackupFragment.kt`), not greenfield.

**How to apply / confirmed design:**
- Backup = automatic after every data change, debounced/coalesced.
- Restore = MERGE not wipe. Per-type: sessions+sets & body measurements = union; achievements = union (nothing lost either side); workout plans = newest-generated-per-week wins; UserStats/streak/level/XP = RECOMPUTE from merged history+achievements (never copy); settings = per-field phone-wins-if-differs-from-default else backup; for fields that CAN'T tell user-set from default → BACKUP WINS (user tie-break decision).
- Cross-version: versioned format + forward migration (older backup → newer app). Durable obligation, not one-shot.
- EXCLUDE Anthropic apiKey always (in transit + at rest).

**Data surface (verified in code 2026-06-24):**
- 8 entities: WorkoutSession, WorkoutSet, Achievement, UserStats, BodyMeasurement, PlannedExercise, Exercise, GymPreset.
- Old export MISSES: `Exercise` (custom lib) + `GymPreset` entities entirely; and prefs restTimerSeconds, dailyChallengesJson, selectedGymPresetId, lastAutoGenerateWeek, lastGenerationAttemptCount, injurySeverity, skippedUpdateVersion, workoutDraftJson.
- Ephemeral-pref decision points (likely EXCLUDE from backup): `workoutDraftJson` (in-progress draft), `lastGenerationAttemptCount`, `lastAutoGenerateWeek`, `skippedUpdateVersion` — flag, don't blindly include.
- Old `ExportRepository` is schema_version=1, hard-rejects other versions, wipe-and-replace.

**Google-cred BOUNDARY (only the user can supply; build stops here):** GCP project w/ Drive API enabled; OAuth Android client ID for `com.migul.treningsprogram` + release signing SHA-1; Web client ID for ID-token exchange; consent screen for `drive.appdata` scope + test users. No secret committed. No Google/Drive/play-services-auth dep in project yet (verified) — net-new dependency surface.

**Operating-rule note:** I declined to start this on coordinator-RELAYED authorization across 3 escalations; only began after a message the harness presented as the USER'S OWN. See [[feedback-relayed-consent]].

**IMPLEMENTATION STATE (verified 2026-06-24, working-tree only, NOT committed):**
- Whole project COMPILES (debug+release) and the full JVM suite PASSES: 15 suites / 117 tests / 0 fail / 0 err on a forced `--rerun-tasks` clean run. Backup suites: BackupMergeTest=20, BackupSchedulerTest=3, BackupSerializationTest=6.
- The ONLY compile blocker was `DriveBackupClient.kt`: `AndroidHttp` was removed from google-api-client-android:2.6.0. Fix = use `com.google.api.client.http.javanet.NetHttpTransport()` (ships in google-http-client-1.44.2, already transitive — NO new dep). Reusable fact for any future Google Drive REST work in this project.
- Backup format is ENTITY-level v2 (independent of Room DB schema, currently v10) — backup migration framework (`BackupMigrations.STEPS`) is its own version chain; do NOT couple it to DB version.
- `WorkoutSet` has FK CASCADE on session delete, so import's `sessionDao.deleteAll()` + reinsert-merged-superset is orphan-safe.
- DECISION I made (flag for user veto): flipped Hilt `bindBackupUploader` from `LogOnlyBackupUploader` -> `DriveBackupUploader` so AUTO-backup actually pushes to cloud (brief AC#1 = "push after every data change"). Safe: DriveBackupUploader throws when unconfigured/not-signed-in and BackupScheduler.runBackup() catches+logs, so the pipeline no-ops gracefully until OAuth is set up. `requestBackup()` is wired at all the brief's change points (WorkoutRepository complete/addSet/savePlan/saveDayPlan + Home/History/Settings VMs).
- STILL BLOCKED on user: real Web client ID into `res/values/backup_config.xml` (placeholder `REPLACE_WITH_WEB_CLIENT_ID`); GoogleDriveAuth.isConfigured gates sign-in until then. Cannot exercise real Drive round-trip without it (and on-device test is a separate, not-yet-requested step).
- MANUAL = CLOUD parity (verified 2026-06-24): Settings Export btn -> exportToJson(); Import btn -> importFromJson() — the SAME engine the cloud path calls. ExportRepository has ZERO data.cloud/OAuth dependency, so the cloud path being un-configured does NOT affect manual file-based transfer. Manual path delivers complete-capture + v1->v2 migration + merge restore identically. Stale Import dialog copy ("replace all current data") FIXED -> now describes merge (matches cloud-restore dialog). The two remaining "cannot be undone" dialogs (Reset Workouts, Factory Reset) are correctly destructive and left as-is.
- VERSION decision: MINOR bump -> 1.7.0 (new user-facing feature, backward-compatible). NOT committed/tagged per rules.

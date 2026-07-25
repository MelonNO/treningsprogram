---
name: feature-backup-export
description: Existing manual export/import backup feature in treningsprogram — what it covers, gaps vs full device migration, and the open product decisions around it
metadata:
  type: project
---

The app already ships a **manual JSON export/import backup** feature (as of v1.6.2, schema_version 1).

**Where it lives:**
- `data/repository/ExportRepository.kt` — `exportToJson()` / `importFromJson(json)`. Serializes via Gson.
- `ui/settings/SettingsBackupFragment.kt` — Settings > Backup screen. Export = write JSON to cacheDir + ACTION_SEND share sheet ("Save backup via…"). Import = OpenDocument picker, confirm dialog, then wipe-and-replace. Also has Reset Workouts and Factory Reset buttons.

**What the backup currently includes:** WorkoutSession, WorkoutSet, Achievement, UserStats, BodyMeasurement, PlannedExercise, plus a subset of PreferencesManager fields (daysPerWeek, fitnessGoal, experienceLevel, sessionDurationMinutes, separateCardioDays, injuries, priorityMuscles, dislikedExercises, onboardingContext, wizardEquipment, hasCompletedOnboarding).

**What it currently EXCLUDES (gaps):**
- The Anthropic **API key** — deliberately excluded (commented "excluding API key").
- The **custom/edited Exercise library** (exercises table) — NOT exported. User-added or AI-resolved exercises would be lost on restore.
- **GymPreset** table (user-created gym presets) — NOT exported.
- Several PreferencesManager fields: restTimerSeconds, injurySeverity, selectedGymPresetId, dailyChallengesJson, lastAutoGenerateWeek, skippedUpdateVersion, workoutDraftJson, lastGenerationAttemptCount.

**Import semantics:** strictly **wipe-and-replace** (deletes all sessions/achievements/stats/measurements/planned, then inserts from file). No merge. Rejects any `schema_version != 1` (so a v1 file can't restore into a future v2 format, and vice versa — brittle across versions).

**Backup transfer is fully manual:** user must tap Export and save the file somewhere off-device BEFORE losing the phone. There is **no automatic/scheduled backup and no cloud sync** — so a literal "lose your phone" scenario loses everything since the last manual export. This is the core tension with the user's stated goal of "without any data loss."

**DB:** Room version 10, 8 entities, migrations 1→10 present (note: no MIGRATION_3_4 ordering issue — all present). exportSchema = false.

**Constraint:** A concurrent orchestrator task was modifying PreferencesManager (injuries feature) — implementation of any backup change must account for PreferencesManager churn.

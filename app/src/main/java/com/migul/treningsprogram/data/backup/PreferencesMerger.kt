package com.migul.treningsprogram.data.backup

/**
 * Per-field settings merge.
 *
 * Rule (user-confirmed): for each backup-eligible preference,
 *   - if the NEW phone's current value DIFFERS from that field's default -> the PHONE wins
 *     (the user actively set it on this device),
 *   - if the phone is still at default -> the BACKUP value is used,
 *   - for fields where we genuinely cannot distinguish "user-set" from "still default" -> BACKUP
 *     wins (user tie-break decision).
 *
 * `selectedGymPresetId` is special: after preset merge its backup value must be repointed through
 * the preset id remap before this resolution runs (handled by the repository), so by the time we
 * resolve it here, both `current` and `backup` are in the merged id space.
 *
 * Pure function on a [BackupPreferences] (current device snapshot) + a [BackupPreferences] (from the
 * backup). Returns the resolved [BackupPreferences] the repository then writes field-by-field.
 */
object PreferencesMerger {

    /**
     * Fields that fall into the "cannot distinguish user-set vs default" bucket and therefore
     * BACKUP-WINS unconditionally. Documented for orchestrator ratification.
     *
     * - dailyChallengesJson: default "" is indistinguishable from "user simply has no challenges
     *   today"; it's opaque per-day state, so we let the backup populate it (backup-wins).
     * - selectedGymPresetId: default -1 is a legitimate user choice ("no preset"), so a phone value
     *   of -1 cannot be read as "untouched"; backup-wins (after id remap).
     */
    val BACKUP_WINS_FIELDS = listOf("dailyChallengesJson", "selectedGymPresetId")

    fun merge(current: BackupPreferences, backup: BackupPreferences): BackupPreferences {
        val d = BackupPreferences() // all-defaults reference
        return BackupPreferences(
            daysPerWeek = pick(current.daysPerWeek, backup.daysPerWeek, d.daysPerWeek),
            fitnessGoal = pick(current.fitnessGoal, backup.fitnessGoal, d.fitnessGoal),
            experienceLevel = pick(current.experienceLevel, backup.experienceLevel, d.experienceLevel),
            sessionDurationMinutes = pick(current.sessionDurationMinutes, backup.sessionDurationMinutes, d.sessionDurationMinutes),
            separateCardioDays = pick(current.separateCardioDays, backup.separateCardioDays, d.separateCardioDays),
            injuries = pick(current.injuries, backup.injuries, d.injuries),
            injurySeverity = pick(current.injurySeverity, backup.injurySeverity, d.injurySeverity),
            priorityMuscles = pick(current.priorityMuscles, backup.priorityMuscles, d.priorityMuscles),
            dislikedExercises = pick(current.dislikedExercises, backup.dislikedExercises, d.dislikedExercises),
            onboardingContext = pick(current.onboardingContext, backup.onboardingContext, d.onboardingContext),
            wizardEquipment = pick(current.wizardEquipment, backup.wizardEquipment, d.wizardEquipment),
            // Onboarding flag: union — if EITHER side finished onboarding, treat as finished, so a
            // restored user is never forced back through the wizard.
            hasCompletedOnboarding = current.hasCompletedOnboarding || backup.hasCompletedOnboarding,
            restTimerSeconds = pick(current.restTimerSeconds, backup.restTimerSeconds, d.restTimerSeconds),
            // v4 additions — standard phone-wins-if-set:
            restDaysCsv = pick(current.restDaysCsv, backup.restDaysCsv, d.restDaysCsv),
            autoRebalanceEnabled = pick(current.autoRebalanceEnabled, backup.autoRebalanceEnabled, d.autoRebalanceEnabled),
            dayBoundaryHour = pick(current.dayBoundaryHour, backup.dayBoundaryHour, d.dayBoundaryHour),
            // v5 additions (manual rest mode) — standard phone-wins-if-set:
            manualRestEnabled = pick(current.manualRestEnabled, backup.manualRestEnabled, d.manualRestEnabled),
            manualRestHeavySeconds = pick(current.manualRestHeavySeconds, backup.manualRestHeavySeconds, d.manualRestHeavySeconds),
            manualRestAccessorySeconds = pick(current.manualRestAccessorySeconds, backup.manualRestAccessorySeconds, d.manualRestAccessorySeconds),
            // Ambiguous / backup-wins bucket:
            dailyChallengesJson = backup.dailyChallengesJson,
            selectedGymPresetId = backup.selectedGymPresetId,
            // v8 (QoL 2026-08 item 04): correction maps UNION-merge per key (device wins on
            // collision) — neither side's flags/re-matches should be lost by a restore.
            exerciseFlagsJson = com.migul.treningsprogram.data.ExerciseInfoCorrections.Codec
                .union(current.exerciseFlagsJson, backup.exerciseFlagsJson),
            exerciseOverridesJson = com.migul.treningsprogram.data.ExerciseInfoCorrections.Codec
                .union(current.exerciseOverridesJson, backup.exerciseOverridesJson),
            // v9 (body-progress 2026-08-04): profile height/sex — standard phone-wins-if-set. The
            // defaults ARE the "not set" sentinels, so a phone that has never filled them in
            // correctly inherits them from the backup.
            heightCm = pick(current.heightCm, backup.heightCm, d.heightCm),
            sex = pick(current.sex, backup.sex, d.sex)
        )
    }

    /** phone-wins-if-set: current differs from default -> current; else backup. */
    private fun <T> pick(current: T, backup: T, default: T): T =
        if (current != default) current else backup
}

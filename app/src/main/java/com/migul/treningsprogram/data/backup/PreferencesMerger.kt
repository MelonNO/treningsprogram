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
            // Ambiguous / backup-wins bucket:
            dailyChallengesJson = backup.dailyChallengesJson,
            selectedGymPresetId = backup.selectedGymPresetId
        )
    }

    /** phone-wins-if-set: current differs from default -> current; else backup. */
    private fun <T> pick(current: T, backup: T, default: T): T =
        if (current != default) current else backup
}

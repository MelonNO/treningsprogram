package com.migul.treningsprogram.data.repository

import com.google.gson.Gson
import com.migul.treningsprogram.data.backup.BackupEnvelope
import com.migul.treningsprogram.data.backup.BackupMerger
import com.migul.treningsprogram.data.backup.BackupMigrations
import com.migul.treningsprogram.data.backup.BackupPreferences
import com.migul.treningsprogram.data.backup.PreferencesMerger
import com.migul.treningsprogram.data.backup.CURRENT_BACKUP_VERSION
import com.migul.treningsprogram.data.db.dao.*
import com.migul.treningsprogram.data.preferences.PreferencesManager
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Versioned backup serializer + MERGE-on-restore engine.
 *
 * Export captures ALL user data EXCEPT the Anthropic API key, in a [CURRENT_BACKUP_VERSION]
 * envelope. Import parses + forward-migrates any supported older backup (v1 manual export → v2)
 * and MERGES it into the on-device data (never wipes). All merge rules live in
 * [com.migul.treningsprogram.data.backup.BackupMerger] / [PreferencesMerger] as pure functions;
 * this class only does the DB/prefs I/O around them.
 */
@Singleton
class ExportRepository @Inject constructor(
    private val workoutSessionDao: WorkoutSessionDao,
    private val workoutSetDao: WorkoutSetDao,
    private val achievementDao: AchievementDao,
    private val userStatsDao: UserStatsDao,
    private val plannedExerciseDao: PlannedExerciseDao,
    private val bodyMeasurementDao: BodyMeasurementDao,
    private val exerciseDao: ExerciseDao,
    private val gymPresetDao: GymPresetDao,
    private val programDao: ProgramDao,
    private val gamificationRepository: GamificationRepository,
    private val prefs: PreferencesManager,
    private val gson: Gson
) {

    // ---- Export ---------------------------------------------------------------------------------

    suspend fun exportToJson(): String {
        val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val envelope = BackupEnvelope(
            schemaVersion = CURRENT_BACKUP_VERSION,
            exportedAt = isoFmt.format(Date()),
            sessions = workoutSessionDao.getAllOnce(),
            sets = workoutSetDao.getAllOnce(),
            achievements = achievementDao.getAllOnce(),
            userStats = userStatsDao.get(),
            bodyMeasurements = bodyMeasurementDao.getAllOnce(),
            plannedExercises = plannedExerciseDao.getAllOnce(),
            exercises = exerciseDao.getAllExercisesOnce(),
            gymPresets = gymPresetDao.getAllOnce(),
            programs = programDao.getAllOnce(),
            preferences = snapshotPreferences()
        )
        return gson.toJson(envelope)
    }

    /** Current device's backup-eligible preferences (API key intentionally excluded). */
    private fun snapshotPreferences(): BackupPreferences = BackupPreferences(
        daysPerWeek = prefs.daysPerWeek,
        fitnessGoal = prefs.fitnessGoal,
        experienceLevel = prefs.experienceLevel,
        sessionDurationMinutes = prefs.sessionDurationMinutes,
        separateCardioDays = prefs.separateCardioDays,
        injuries = prefs.injuries,
        injurySeverity = prefs.injurySeverity,
        priorityMuscles = prefs.priorityMuscles,
        dislikedExercises = prefs.dislikedExercises,
        onboardingContext = prefs.onboardingContext,
        wizardEquipment = prefs.wizardEquipment,
        hasCompletedOnboarding = prefs.hasCompletedOnboarding,
        restTimerSeconds = prefs.restTimerSeconds,
        dailyChallengesJson = prefs.dailyChallengesJson,
        selectedGymPresetId = prefs.selectedGymPresetId
    )

    // ---- Import (MERGE) -------------------------------------------------------------------------

    suspend fun importFromJson(json: String) {
        val backup = BackupMigrations.parseAndMigrate(gson, json)

        // 1) Load existing on-device state.
        val existingSessions = workoutSessionDao.getAllOnce()
        val existingSets = workoutSetDao.getAllOnce()
        val existingAchievements = achievementDao.getAllOnce()
        val existingMeasurements = bodyMeasurementDao.getAllOnce()
        val existingPlanned = plannedExerciseDao.getAllOnce()
        val existingExercises = exerciseDao.getAllExercisesOnce()
        val existingPresets = gymPresetDao.getAllOnce()
        val existingPrograms = programDao.getAllOnce()

        // 2) Sessions + sets: id-collision-safe UNION (preserve session->sets linkage).
        val backupSetsBySession = backup.sets.groupBy { it.sessionId }
        val backupBundles = backup.sessions.map { s ->
            BackupMerger.SessionWithSets(s, backupSetsBySession[s.id].orEmpty())
        }
        val mergedWorkouts = BackupMerger.mergeWorkouts(existingSessions, existingSets, backupBundles)

        // 3) Other unions / per-week / per-name merges.
        val mergedMeasurements = BackupMerger.mergeBodyMeasurements(existingMeasurements, backup.bodyMeasurements)
        val mergedAchievements = BackupMerger.mergeAchievements(existingAchievements, backup.achievements)
        val mergedExercises = BackupMerger.mergeExercises(existingExercises, backup.exercises)
        val mergedPresets = BackupMerger.mergeGymPresets(existingPresets, backup.gymPresets)

        // E2: merge programs first, then repoint backup plan rows at the program they landed on
        // (via the backup-id remap) BEFORE merging plans, so program scoping survives the restore.
        val mergedPrograms = BackupMerger.mergePrograms(existingPrograms, backup.programs)
        val remappedBackupPlanned = backup.plannedExercises.map { row ->
            val pid = row.programId
            if (pid != null) row.copy(programId = mergedPrograms.backupIdRemap[pid] ?: pid) else row
        }
        val mergedPlanned = BackupMerger.mergePlannedExercises(existingPlanned, remappedBackupPlanned)

        // 4) Persist merged workout history (replace whole table with the merged superset).
        //    Sets are deleted via session CASCADE; rebuild both from the merged result.
        workoutSessionDao.deleteAll()
        workoutSessionDao.insertAll(mergedWorkouts.sessions)
        workoutSetDao.insertAll(mergedWorkouts.sets)

        bodyMeasurementDao.deleteAll()
        bodyMeasurementDao.insertAll(mergedMeasurements)

        achievementDao.insertAllReplace(mergedAchievements)

        // E2: programs before plans (plan rows reference a program id).
        programDao.deleteAll()
        mergedPrograms.programs.forEach { programDao.insertWithId(it) }

        plannedExerciseDao.deleteAll()
        plannedExerciseDao.insertAll(mergedPlanned)

        exerciseDao.deleteAll()
        exerciseDao.insertAll(mergedExercises)

        gymPresetDao.deleteAll()
        mergedPresets.presets.forEach { gymPresetDao.insertWithId(it) }

        // 5) Stats / streak / level / XP: RECOMPUTE from merged history (never copy from a side).
        gamificationRepository.recomputeStatsFromHistory(
            mergedWorkouts.sessions, mergedWorkouts.sets, mergedAchievements
        )

        // 6) Preferences: per-field resolution. Repoint the backup's selectedGymPresetId through
        //    the preset id remap first so it still references a real preset row.
        val remappedBackupPrefs = backup.preferences.copy(
            selectedGymPresetId = mergedPresets.backupIdRemap[backup.preferences.selectedGymPresetId]
                ?: backup.preferences.selectedGymPresetId
        )
        val resolved = PreferencesMerger.merge(snapshotPreferences(), remappedBackupPrefs)
        writePreferences(resolved)
    }

    private fun writePreferences(p: BackupPreferences) {
        prefs.daysPerWeek = p.daysPerWeek
        prefs.fitnessGoal = p.fitnessGoal
        prefs.experienceLevel = p.experienceLevel
        prefs.sessionDurationMinutes = p.sessionDurationMinutes
        prefs.separateCardioDays = p.separateCardioDays
        prefs.injuries = p.injuries
        prefs.injurySeverity = p.injurySeverity
        prefs.priorityMuscles = p.priorityMuscles
        prefs.dislikedExercises = p.dislikedExercises
        prefs.onboardingContext = p.onboardingContext
        prefs.wizardEquipment = p.wizardEquipment
        prefs.hasCompletedOnboarding = p.hasCompletedOnboarding
        prefs.restTimerSeconds = p.restTimerSeconds
        prefs.dailyChallengesJson = p.dailyChallengesJson
        prefs.selectedGymPresetId = p.selectedGymPresetId
        // NOTE: apiKey is never written from a backup.
    }
}

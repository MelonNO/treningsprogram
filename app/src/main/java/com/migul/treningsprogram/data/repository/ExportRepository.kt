package com.migul.treningsprogram.data.repository

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.migul.treningsprogram.data.db.dao.*
import com.migul.treningsprogram.data.db.entity.*
import com.migul.treningsprogram.data.preferences.PreferencesManager
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

private data class ExportPreferences(
    val daysPerWeek: Int,
    val fitnessGoal: String,
    val experienceLevel: String,
    val sessionDurationMinutes: Int,
    val separateCardioDays: Boolean,
    val injuries: String,
    val priorityMuscles: String,
    val dislikedExercises: String,
    val onboardingContext: String,
    val wizardEquipment: String,
    val hasCompletedOnboarding: Boolean
)

private data class ExportData(
    @SerializedName("schema_version") val schemaVersion: Int = 1,
    @SerializedName("exported_at") val exportedAt: String,
    val sessions: List<WorkoutSession>,
    val sets: List<WorkoutSet>,
    val achievements: List<Achievement>,
    @SerializedName("user_stats") val userStats: UserStats?,
    @SerializedName("body_measurements") val bodyMeasurements: List<BodyMeasurement>,
    @SerializedName("planned_exercises") val plannedExercises: List<PlannedExercise>,
    val preferences: ExportPreferences
)

@Singleton
class ExportRepository @Inject constructor(
    private val workoutSessionDao: WorkoutSessionDao,
    private val workoutSetDao: WorkoutSetDao,
    private val achievementDao: AchievementDao,
    private val userStatsDao: UserStatsDao,
    private val plannedExerciseDao: PlannedExerciseDao,
    private val bodyMeasurementDao: BodyMeasurementDao,
    private val prefs: PreferencesManager,
    private val gson: Gson
) {

    suspend fun exportToJson(): String {
        val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val data = ExportData(
            exportedAt = isoFmt.format(Date()),
            sessions = workoutSessionDao.getAllOnce(),
            sets = workoutSetDao.getAllOnce(),
            achievements = achievementDao.getAllOnce(),
            userStats = userStatsDao.get(),
            bodyMeasurements = bodyMeasurementDao.getAllOnce(),
            plannedExercises = plannedExerciseDao.getAllOnce(),
            preferences = ExportPreferences(
                daysPerWeek = prefs.daysPerWeek,
                fitnessGoal = prefs.fitnessGoal,
                experienceLevel = prefs.experienceLevel,
                sessionDurationMinutes = prefs.sessionDurationMinutes,
                separateCardioDays = prefs.separateCardioDays,
                injuries = prefs.injuries,
                priorityMuscles = prefs.priorityMuscles,
                dislikedExercises = prefs.dislikedExercises,
                onboardingContext = prefs.onboardingContext,
                wizardEquipment = prefs.wizardEquipment,
                hasCompletedOnboarding = prefs.hasCompletedOnboarding
            )
        )
        return gson.toJson(data)
    }

    suspend fun importFromJson(json: String) {
        val data = gson.fromJson(json, ExportData::class.java)
            ?: throw IllegalArgumentException("Invalid backup file")
        if (data.schemaVersion != 1) {
            throw IllegalArgumentException("Unsupported backup version: ${data.schemaVersion}")
        }

        // Clear all user data (sets cascade-deleted with sessions)
        workoutSessionDao.deleteAll()
        achievementDao.deleteAll()
        userStatsDao.deleteAll()
        bodyMeasurementDao.deleteAll()
        plannedExerciseDao.deleteAll()

        // Restore data
        workoutSessionDao.insertAll(data.sessions)
        workoutSetDao.insertAll(data.sets)
        achievementDao.insertAllReplace(data.achievements)
        data.userStats?.let { userStatsDao.upsert(it) }
        bodyMeasurementDao.insertAll(data.bodyMeasurements)
        plannedExerciseDao.insertAll(data.plannedExercises)

        // Restore preferences (excluding API key)
        val p = data.preferences ?: throw IllegalArgumentException("Backup file is missing preferences data")
        p.let { p ->
            prefs.daysPerWeek = p.daysPerWeek
            prefs.fitnessGoal = p.fitnessGoal
            prefs.experienceLevel = p.experienceLevel
            prefs.sessionDurationMinutes = p.sessionDurationMinutes
            prefs.separateCardioDays = p.separateCardioDays
            prefs.injuries = p.injuries
            prefs.priorityMuscles = p.priorityMuscles
            prefs.dislikedExercises = p.dislikedExercises
            prefs.onboardingContext = p.onboardingContext
            prefs.wizardEquipment = p.wizardEquipment
            prefs.hasCompletedOnboarding = p.hasCompletedOnboarding
        }
    }
}

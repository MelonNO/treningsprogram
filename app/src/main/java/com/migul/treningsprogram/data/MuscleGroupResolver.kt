package com.migul.treningsprogram.data

import com.migul.treningsprogram.data.db.AppDatabase

/**
 * Single source of truth for the muscle group STORED on a WorkoutSet: an exact match against
 * the bundled library (DEFAULT_EXERCISES) is authoritative; otherwise fall back to name-based
 * [MuscleClassifier.fromName]. Used identically at set-WRITE time (LogWorkoutViewModel) and by
 * the historical-backfill migration (AppDatabase.MIGRATION_14_15) so a re-derived historical set
 * carries exactly the group a newly-logged set of the same name would.
 */
object MuscleGroupResolver {
    fun resolve(exerciseName: String): String =
        AppDatabase.DEFAULT_EXERCISES.find { it.name == exerciseName }?.muscleGroup
            ?: MuscleClassifier.fromName(exerciseName)
}

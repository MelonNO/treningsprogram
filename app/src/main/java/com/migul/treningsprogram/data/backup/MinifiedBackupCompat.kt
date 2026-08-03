package com.migul.treningsprogram.data.backup

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * Backup portability (2026-08-03) — transparent reader for the HISTORICAL minified backup format.
 *
 * ## The bug this covers
 * Until this batch, the backup-reachable classes carried no `@SerializedName`/keep rules, so
 * RELEASE builds (R8 minified) exported JSON keyed by the obfuscated one-letter field names
 * ("c" = sessions, each session `{"a": id, "b": dateMs, …}`), while debug builds wrote the real
 * field names. Every backup the user has exported from a release build is in the minified format —
 * real data they depend on — so the import path must keep accepting it forever, alongside the new
 * stable format.
 *
 * ## Where the letter maps come from
 * The per-class letter → canonical-key maps below are read from the release R8 mapping file
 * (`app/build/outputs/mapping/release/mapping.txt`) of the affected releases and VERIFIED against
 * the user's actual v8 release export (every top-level key and per-entity key set matches).
 * Fields annotated with `@SerializedName` in the old code (`schema_version`, `exported_at`,
 * `user_stats`, `body_measurements`, `planned_exercises`, `gym_presets`, `exercise_notes`) always
 * serialized under their annotation name, so only the UN-annotated fields appear as letters.
 *
 * ## Cross-release validity of the maps (reasoned, and consistent with observed behaviour)
 * R8 assigns member names in declaration order, and every backup class only ever GREW by
 * appending fields, so the letter of each pre-existing field is stable across releases — which is
 * exactly why release-to-release restore happened to work before this fix. The one exception is
 * the [BackupEnvelope] itself: `programs`/`goals`/`exerciseNotes` were INSERTED before
 * `preferences` (v3 / v6), shifting the `preferences` letter over time. [envelopeMapFor] therefore
 * selects the era-correct envelope map from the backup's own `schema_version` (which is always a
 * readable, canonical key). Entity-level maps need no era logic: appended-only.
 *
 * ## Detection
 * A canonical backup ALWAYS has a top-level "sessions" array (Gson serializes the non-null list
 * even when empty); a minified backup NEVER does. [looksMinified] keys off exactly that, plus the
 * presence of at least one known minified envelope key, so a canonical file is never touched and
 * translation is a no-op for it. Unknown keys are passed through untouched.
 */
object MinifiedBackupCompat {

    // BackupEnvelope, current era (schema_version >= 6). From mapping.txt; verified against the
    // user's real v8 release export. a/b (schema_version / exported_at) are annotated and never
    // appear as letters; they are omitted deliberately.
    private val ENVELOPE_V6_PLUS = mapOf(
        "c" to "sessions",
        "d" to "sets",
        "e" to "achievements",
        "f" to "user_stats",
        "g" to "body_measurements",
        "h" to "planned_exercises",
        "i" to "exercises",
        "j" to "gym_presets",
        "k" to "programs",
        "l" to "goals",
        "m" to "exercise_notes",
        "n" to "preferences"
    )

    // v3..v5 era: goals/exerciseNotes did not exist yet, so preferences sat one letter after
    // programs. (Reasoned from the append/insert history; see class KDoc.)
    private val ENVELOPE_V3_TO_V5 = mapOf(
        "c" to "sessions",
        "d" to "sets",
        "e" to "achievements",
        "f" to "user_stats",
        "g" to "body_measurements",
        "h" to "planned_exercises",
        "i" to "exercises",
        "j" to "gym_presets",
        "k" to "programs",
        "l" to "preferences"
    )

    // v2 era (and the v1 wipe-and-replace export, which used the same top-level names):
    // no programs list at all.
    private val ENVELOPE_V2_AND_OLDER = mapOf(
        "c" to "sessions",
        "d" to "sets",
        "e" to "achievements",
        "f" to "user_stats",
        "g" to "body_measurements",
        "h" to "planned_exercises",
        "i" to "exercises",
        "j" to "gym_presets",
        "k" to "preferences"
    )

    private val WORKOUT_SESSION = letters("id", "dateMs", "durationMinutes", "notes", "isCompleted", "kind")
    private val WORKOUT_SET = letters(
        "id", "sessionId", "exerciseName", "muscleGroup", "setNumber", "reps", "weightKg",
        "isWarmup", "rpeLabel", "loggedAtMs"
    )
    private val ACHIEVEMENT = letters("id", "name", "description", "emoji", "isUnlocked", "unlockedAtMs")
    private val USER_STATS = letters(
        "id", "totalXp", "level", "currentStreak", "bestStreak", "totalWorkouts", "totalPrs",
        "lastWorkoutDateMs"
    )
    private val BODY_MEASUREMENT = letters("id", "dateMs", "weightKg")
    private val PLANNED_EXERCISE = letters(
        "id", "weekStart", "dayOfWeek", "orderInDay", "exerciseName", "sets", "targetReps",
        "targetWeightKg", "notes", "isLogged", "actualWeightKg", "actualReps", "actualSets",
        "recommendedRestSeconds", "exerciseDbId", "matchConfidence", "matchSource", "resolvedAt",
        "rationale", "programId"
    )
    private val EXERCISE = letters(
        "id", "name", "muscleGroup", "equipment", "exerciseDbId", "matchConfidence", "matchSource",
        "resolvedAt"
    )
    private val GYM_PRESET = letters(
        "id", "name", "equipmentJson", "notes", "barWeightKg", "dumbbellBarWeightKg", "platesCsv",
        "loadableDumbbells", "avoidExercisesJson"
    )
    private val PROGRAM = letters(
        "id", "name", "createdAtMs", "isActive", "mesocycleWeeks", "blockStartWeek",
        "isDeloadActive", "isFrozen"
    )
    private val LIFT_GOAL = letters(
        "id", "exerciseName", "targetWeightKg", "isE1rm", "targetDateMs", "createdAtMs", "status",
        "achievedAtMs"
    )
    private val EXERCISE_NOTE = letters("exerciseName", "note", "updatedAtMs")
    private val PREFERENCES = letters(
        "daysPerWeek", "fitnessGoal", "experienceLevel", "sessionDurationMinutes",
        "separateCardioDays", "injuries", "injurySeverity", "priorityMuscles", "dislikedExercises",
        "onboardingContext", "wizardEquipment", "hasCompletedOnboarding", "restTimerSeconds",
        "dailyChallengesJson", "selectedGymPresetId", "restDaysCsv", "autoRebalanceEnabled",
        "dayBoundaryHour", "manualRestEnabled", "manualRestHeavySeconds",
        "manualRestAccessorySeconds", "exerciseFlagsJson", "exerciseOverridesJson"
    )

    /** Declaration-order canonical names → their R8 letters ("a", "b", … in field order). */
    private fun letters(vararg names: String): Map<String, String> =
        names.withIndex().associate { (i, n) -> ('a' + i).toString() to n }

    /** True iff [root] is in the historical minified release format (see class KDoc). */
    fun looksMinified(root: JsonObject): Boolean =
        !root.has("sessions") &&
            (root.has("c") || root.has("d") || root.has("n") || root.has("k"))

    /**
     * Translate a minified backup tree to canonical keys IN PLACE and return it. Canonical input
     * comes back untouched. [schemaVersion] selects the era-correct envelope map (entity maps are
     * era-independent — appended-only classes).
     */
    fun translateIfMinified(root: JsonObject, schemaVersion: Int): JsonObject {
        if (!looksMinified(root)) return root

        val envelopeMap = envelopeMapFor(schemaVersion)
        renameKeys(root, envelopeMap)

        translateArray(root, "sessions", WORKOUT_SESSION)
        translateArray(root, "sets", WORKOUT_SET)
        translateArray(root, "achievements", ACHIEVEMENT)
        translateObject(root, "user_stats", USER_STATS)
        translateArray(root, "body_measurements", BODY_MEASUREMENT)
        translateArray(root, "planned_exercises", PLANNED_EXERCISE)
        translateArray(root, "exercises", EXERCISE)
        translateArray(root, "gym_presets", GYM_PRESET)
        translateArray(root, "programs", PROGRAM)
        translateArray(root, "goals", LIFT_GOAL)
        translateArray(root, "exercise_notes", EXERCISE_NOTE)
        translateObject(root, "preferences", PREFERENCES)
        return root
    }

    internal fun envelopeMapFor(schemaVersion: Int): Map<String, String> = when {
        schemaVersion >= 6 -> ENVELOPE_V6_PLUS
        schemaVersion >= 3 -> ENVELOPE_V3_TO_V5
        else -> ENVELOPE_V2_AND_OLDER
    }

    private fun translateArray(root: JsonObject, key: String, map: Map<String, String>) {
        val arr = root.get(key)?.takeIf { it.isJsonArray }?.asJsonArray ?: return
        val out = JsonArray()
        for (el in arr) {
            if (el.isJsonObject) renameKeys(el.asJsonObject, map)
            out.add(el)
        }
        root.add(key, out)
    }

    private fun translateObject(root: JsonObject, key: String, map: Map<String, String>) {
        val obj = root.get(key)?.takeIf { it.isJsonObject }?.asJsonObject ?: return
        renameKeys(obj, map)
    }

    /**
     * Rename [map]'s letter keys to their canonical names. A letter is only consumed when the
     * canonical key is absent (never clobbers) — which also makes translation idempotent and a
     * no-op on canonical objects, since no backup class has a real single-letter field name.
     */
    private fun renameKeys(obj: JsonObject, map: Map<String, String>) {
        for ((letter, canonical) in map) {
            if (obj.has(letter) && !obj.has(canonical)) {
                obj.add(canonical, obj.remove(letter))
            }
        }
    }
}

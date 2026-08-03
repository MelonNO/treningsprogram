package com.migul.treningsprogram.data.backup

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Forward-migration framework for backup envelopes.
 *
 * A backup is migrated as a raw [JsonObject] tree, one version at a time, until it reaches
 * [CURRENT_BACKUP_VERSION]. Each [MigrationStep] knows how to take the tree from `fromVersion`
 * to `fromVersion + 1`. Steps are applied in ascending order, so any older backup composes
 * cleanly up to the current shape. New versions only need to add a step — no existing step or
 * call site changes.
 */
object BackupMigrations {

    /** One forward step: migrate the JSON tree from [fromVersion] to [fromVersion] + 1. */
    interface MigrationStep {
        val fromVersion: Int
        fun migrate(root: JsonObject): JsonObject
    }

    /**
     * v1 -> v2.
     *
     * v1 (the original manual export) had no Exercise library, no GymPreset list, and a thinner
     * preferences object. The on-disk field names that already exist (sessions, sets,
     * achievements, user_stats, body_measurements, planned_exercises, preferences) are unchanged
     * in v2, so all we must do is:
     *   - introduce the new (empty) tables `exercises` and `gym_presets`, and
     *   - leave preferences as-is: any v1-missing pref keys are simply absent and will fall back
     *     to [BackupPreferences] defaults when Gson deserializes the object.
     * `selectedGymPresetId` referential integrity is preserved trivially: v1 never stored one, so
     * after migration it stays at its default (-1 = none) rather than dangling at a now-absent id.
     */
    private val V1_TO_V2 = object : MigrationStep {
        override val fromVersion = 1
        override fun migrate(root: JsonObject): JsonObject {
            if (!root.has("exercises")) root.add("exercises", com.google.gson.JsonArray())
            if (!root.has("gym_presets")) root.add("gym_presets", com.google.gson.JsonArray())
            root.addProperty("schema_version", 2)
            return root
        }
    }

    /**
     * v2 -> v3.
     *
     * v3 adds the E2 `programs` table (named saved programs + mesocycle/deload state). A v2 backup
     * simply has no programs, so introduce the new (empty) `programs` array; on restore the merge
     * engine will adopt the device's existing/default program. The planned_exercises.programId
     * column rides through whole-entity Gson untouched (absent in v2 ⇒ deserializes as null, exactly
     * like a pre-E2 row), so nothing else in the tree needs rewriting.
     */
    private val V2_TO_V3 = object : MigrationStep {
        override val fromVersion = 2
        override fun migrate(root: JsonObject): JsonObject {
            if (!root.has("programs")) root.add("programs", com.google.gson.JsonArray())
            root.addProperty("schema_version", 3)
            return root
        }
    }

    /**
     * v3 -> v4.
     *
     * v4 only widens the preferences object (restDaysCsv / autoRebalanceEnabled / dayBoundaryHour).
     * A v3 backup simply lacks those keys, and Gson fills them with the [BackupPreferences] field
     * defaults on deserialize — which is exactly the pre-v4 restore behaviour — so the step only
     * stamps the new version.
     */
    private val V3_TO_V4 = object : MigrationStep {
        override val fromVersion = 3
        override fun migrate(root: JsonObject): JsonObject {
            root.addProperty("schema_version", 4)
            return root
        }
    }

    /**
     * v4 -> v5.
     *
     * v5 (rest-UX 2026-07, item 4) only widens the preferences object again (manualRestEnabled /
     * manualRestHeavySeconds / manualRestAccessorySeconds). Absent keys deserialize to the
     * [BackupPreferences] field defaults (mode off, 3:00 / 1:30), so the step only stamps the
     * new version — the same pattern as v3 -> v4.
     */
    private val V4_TO_V5 = object : MigrationStep {
        override val fromVersion = 4
        override fun migrate(root: JsonObject): JsonObject {
            root.addProperty("schema_version", 5)
            return root
        }
    }

    /**
     * v5 -> v6.
     *
     * v6 (feature batch 2026-07-03) adds two new top-level lists: `goals` (N5 lift goals) and
     * `exercise_notes` (N7 per-exercise setup notes). A v5 backup simply has neither table, so
     * introduce them as empty arrays — the same pattern as v2 -> v3's `programs`.
     */
    private val V5_TO_V6 = object : MigrationStep {
        override val fromVersion = 5
        override fun migrate(root: JsonObject): JsonObject {
            if (!root.has("goals")) root.add("goals", com.google.gson.JsonArray())
            if (!root.has("exercise_notes")) root.add("exercise_notes", com.google.gson.JsonArray())
            root.addProperty("schema_version", 6)
            return root
        }
    }

    /**
     * v6 -> v7.
     *
     * v7 (QoL batch 2026-07-25, item 02) only widens the [GymPreset] rows with the nullable
     * `avoidExercisesJson` field (per-gym "exercises to avoid"). A v6 backup simply lacks the
     * field on each preset object, and Gson leaves it null on deserialize — exactly the DB
     * migration's NULL default — so the step only stamps the new version (same pattern as
     * v3 -> v4 / v4 -> v5).
     */
    private val V6_TO_V7 = object : MigrationStep {
        override val fromVersion = 6
        override fun migrate(root: JsonObject): JsonObject {
            root.addProperty("schema_version", 7)
            return root
        }
    }

    /**
     * v7 -> v8.
     *
     * v8 (QoL 2026-08 item 04) only widens the preferences object with the two exercise-info
     * correction maps (exerciseFlagsJson / exerciseOverridesJson). Absent keys deserialize to
     * "" — no corrections — so the step only stamps the new version (same pattern as v3 -> v4).
     */
    private val V7_TO_V8 = object : MigrationStep {
        override val fromVersion = 7
        override fun migrate(root: JsonObject): JsonObject {
            root.addProperty("schema_version", 8)
            return root
        }
    }

    /** Registry of all steps, keyed by the version they migrate FROM. */
    private val STEPS: Map<Int, MigrationStep> = listOf(
        V1_TO_V2,
        V2_TO_V3,
        V3_TO_V4,
        V4_TO_V5,
        V5_TO_V6,
        V6_TO_V7,
        V7_TO_V8
    ).associateBy { it.fromVersion }

    /**
     * Parse [json], migrate it up to [CURRENT_BACKUP_VERSION], and deserialize into a
     * [BackupEnvelope]. Throws [IllegalArgumentException] for malformed or unsupported input.
     */
    fun parseAndMigrate(gson: Gson, json: String): BackupEnvelope {
        val parsed = runCatching { JsonParser.parseString(json) }
            .getOrNull()
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?: throw IllegalArgumentException("Invalid backup file")

        var root = parsed
        // schema_version missing -> treat as the oldest known shape (v1) for forward compatibility.
        // (The key is readable even in the minified format — it was always @SerializedName'd.)
        var version = root.get("schema_version")?.takeIf { it.isJsonPrimitive }?.asInt ?: 1

        // Backup portability (2026-08-03): release builds used to export with R8-minified field
        // names. Translate such a file to canonical keys FIRST, so the migration steps below and
        // the final Gson bind (both keyed on canonical names) see the same tree a debug export
        // produces. Canonical files pass through untouched.
        root = MinifiedBackupCompat.translateIfMinified(root, version)

        if (version > CURRENT_BACKUP_VERSION) {
            throw IllegalArgumentException(
                "Backup was created by a newer app version (v$version); this app supports up to v$CURRENT_BACKUP_VERSION."
            )
        }

        while (version < CURRENT_BACKUP_VERSION) {
            val step = STEPS[version]
                ?: throw IllegalArgumentException("No migration path from backup version $version")
            root = step.migrate(root)
            version = root.get("schema_version")?.asInt ?: (version + 1)
        }

        return gson.fromJson(root, BackupEnvelope::class.java)
            ?: throw IllegalArgumentException("Invalid backup file")
    }
}

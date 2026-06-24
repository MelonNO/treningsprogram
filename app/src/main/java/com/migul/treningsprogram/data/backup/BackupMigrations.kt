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

    /** Registry of all steps, keyed by the version they migrate FROM. */
    private val STEPS: Map<Int, MigrationStep> = listOf(
        V1_TO_V2,
        V2_TO_V3
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
        var version = root.get("schema_version")?.takeIf { it.isJsonPrimitive }?.asInt ?: 1

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

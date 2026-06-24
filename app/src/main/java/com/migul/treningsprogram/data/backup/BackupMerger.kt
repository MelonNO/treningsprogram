package com.migul.treningsprogram.data.backup

import com.migul.treningsprogram.data.db.entity.Achievement
import com.migul.treningsprogram.data.db.entity.BodyMeasurement
import com.migul.treningsprogram.data.db.entity.Exercise
import com.migul.treningsprogram.data.db.entity.GymPreset
import com.migul.treningsprogram.data.db.entity.PlannedExercise
import com.migul.treningsprogram.data.db.entity.Program
import com.migul.treningsprogram.data.db.entity.WorkoutSession
import com.migul.treningsprogram.data.db.entity.WorkoutSet

/**
 * Pure, Room-free merge engine. Restore = MERGE into existing on-device data, never wipe.
 *
 * Every function takes the EXISTING (on-device) data plus the BACKUP data as plain lists and
 * returns the merged result, so the merge rules are unit-testable without a database. The
 * repository is responsible only for loading these lists, calling these functions, and persisting
 * the result.
 */
object BackupMerger {

    // ---------------------------------------------------------------------------------------------
    // Sessions + Sets: UNION, id-collision-safe, session->sets linkage preserved.
    // ---------------------------------------------------------------------------------------------

    /** A backup session paired with the backup sets that belong to it. */
    data class SessionWithSets(val session: WorkoutSession, val sets: List<WorkoutSet>)

    data class MergedWorkouts(
        val sessions: List<WorkoutSession>,
        val sets: List<WorkoutSet>
    )

    /**
     * Union existing + backup workout history.
     *
     * Rules:
     *  - Same CONTENT = same row -> skip the duplicate (de-dup by value, ignoring the surrogate id).
     *  - A backup row's `id` may collide with a DIFFERENT existing row. We must NOT let the backup
     *    overwrite that existing entity. Colliding-but-different backup sessions are re-keyed to a
     *    fresh id beyond every existing id, and their sets follow the remap so the session->sets
     *    link survives.
     *  - Sets are likewise de-duped by value and re-keyed when their id collides with a different
     *    existing set.
     *
     * @param existingSessions on-device sessions.
     * @param existingSets on-device sets.
     * @param backup backup sessions each bundled with their own sets (linked in the backup's id space).
     */
    fun mergeWorkouts(
        existingSessions: List<WorkoutSession>,
        existingSets: List<WorkoutSet>,
        backup: List<SessionWithSets>
    ): MergedWorkouts {
        val resultSessions = existingSessions.toMutableList()
        val resultSets = existingSets.toMutableList()

        val existingSessionById = existingSessions.associateBy { it.id }
        val existingSessionByContent = existingSessions
            .groupBy { sessionContentKey(it) }
        // Content keys of every set already in the merged result (existing + accepted backup),
        // used to de-dup by value as we go.
        val seenSetContent = existingSets.map { setContentKey(it) }.toMutableSet()

        var nextSessionId = ((existingSessions.maxOfOrNull { it.id } ?: 0L) + 1L)
        var nextSetId = ((existingSets.maxOfOrNull { it.id } ?: 0L) + 1L)

        for ((backupSession, backupSets) in backup) {
            // Resolve the session into the merged id space.
            val contentDup = existingSessionByContent[sessionContentKey(backupSession)]
                ?.firstOrNull()
            val finalSessionId: Long
            if (contentDup != null) {
                // Identical session already present -> reuse its id, do not add a duplicate row.
                finalSessionId = contentDup.id
            } else {
                val collidingDifferent = existingSessionById[backupSession.id]
                if (collidingDifferent == null) {
                    // Id free -> keep the backup's own id.
                    finalSessionId = backupSession.id
                    resultSessions.add(backupSession)
                } else {
                    // Id taken by a DIFFERENT session -> re-key to a fresh id.
                    finalSessionId = nextSessionId++
                    resultSessions.add(backupSession.copy(id = finalSessionId))
                }
            }

            // Bring the backup's sets across, re-pointed at finalSessionId.
            for (set in backupSets) {
                val relinked = set.copy(sessionId = finalSessionId)
                val key = setContentKey(relinked)
                // Identical set already present (after relinking) -> skip (de-dup by value).
                if (!seenSetContent.add(key)) continue
                // Assign an id that does not overwrite a different existing set.
                val keep = relinked.id != 0L && resultSets.none { it.id == relinked.id }
                val finalSet = if (keep) relinked else relinked.copy(id = nextSetId++)
                resultSets.add(finalSet)
            }
        }

        return MergedWorkouts(resultSessions, resultSets)
    }

    /** Content identity for a session, independent of the surrogate id. */
    private fun sessionContentKey(s: WorkoutSession): String =
        listOf(s.dateMs, s.durationMinutes, s.notes, s.isCompleted).joinToString("|")

    /** Content identity for a set, independent of the surrogate id but INCLUDING its sessionId. */
    private fun setContentKey(s: WorkoutSet): String =
        listOf(
            s.sessionId, s.exerciseName, s.muscleGroup, s.setNumber,
            s.reps, s.weightKg, s.isWarmup, s.rpeLabel, s.loggedAtMs
        ).joinToString("|")

    // ---------------------------------------------------------------------------------------------
    // BodyMeasurement: UNION, id-collision-safe (no children to relink).
    // ---------------------------------------------------------------------------------------------

    fun mergeBodyMeasurements(
        existing: List<BodyMeasurement>,
        backup: List<BodyMeasurement>
    ): List<BodyMeasurement> {
        val result = existing.toMutableList()
        val existingById = existing.associateBy { it.id }
        val seenContent = existing.map { bodyContentKey(it) }.toMutableSet()
        var nextId = ((existing.maxOfOrNull { it.id } ?: 0L) + 1L)

        for (m in backup) {
            if (!seenContent.add(bodyContentKey(m))) continue // value duplicate -> skip
            val collides = existingById[m.id] != null || result.any { it.id == m.id }
            val finalRow = if (m.id != 0L && !collides) m else m.copy(id = nextId++)
            result.add(finalRow)
        }
        return result
    }

    private fun bodyContentKey(m: BodyMeasurement): String = "${m.dateMs}|${m.weightKg}"

    // ---------------------------------------------------------------------------------------------
    // Achievement: UNION by String id. unlocked-wins; earliest unlockedAtMs wins.
    // ---------------------------------------------------------------------------------------------

    fun mergeAchievements(
        existing: List<Achievement>,
        backup: List<Achievement>
    ): List<Achievement> {
        val merged = LinkedHashMap<String, Achievement>()
        for (a in existing) merged[a.id] = a
        for (b in backup) {
            val cur = merged[b.id]
            if (cur == null) {
                merged[b.id] = b
                continue
            }
            // Keep any unlock from either side; never lose an unlock.
            val unlocked = cur.isUnlocked || b.isUnlocked
            // Earliest non-zero unlock timestamp wins.
            val unlockedAt = earliestUnlock(
                if (cur.isUnlocked) cur.unlockedAtMs else 0L,
                if (b.isUnlocked) b.unlockedAtMs else 0L
            )
            // Prefer existing metadata (name/desc/emoji) — it reflects the installed app version.
            merged[b.id] = cur.copy(isUnlocked = unlocked, unlockedAtMs = unlockedAt)
        }
        return merged.values.toList()
    }

    private fun earliestUnlock(a: Long, b: Long): Long {
        val nonZero = listOf(a, b).filter { it > 0L }
        return nonZero.minOrNull() ?: 0L
    }

    // ---------------------------------------------------------------------------------------------
    // PlannedExercise: newest-generated-plan-per-week wins, grouped by weekStart.
    // ---------------------------------------------------------------------------------------------

    /**
     * For each weekStart, whichever side's plan was generated more recently REPLACES the other
     * entirely for that week.
     *
     * Recency signal: PlannedExercise has no createdAt. The best available signal is
     * [PlannedExercise.resolvedAt] — set to the wall-clock time when a freshly generated plan's
     * exercises are bound to the local DB right after generation. We take the MAX resolvedAt across
     * a week's rows as that plan's "generated at". If a whole week's rows are all unresolved
     * (resolvedAt == 0 on both sides — e.g. a brand-new plan not yet resolved), we fall back to
     * preferring the EXISTING (on-device) plan for that week, since the user is actively on this
     * device. This fallback is flagged for orchestrator ratification.
     */
    fun mergePlannedExercises(
        existing: List<PlannedExercise>,
        backup: List<PlannedExercise>
    ): List<PlannedExercise> {
        // E2: plans are scoped to a program, so group by (programId, weekStart) — a week's plan in
        // program A must not replace the same week in program B. Pre-E2 / single-program data all
        // shares programId=null, so this reduces to the original per-week behaviour for them.
        val existingByKey = existing.groupBy { it.programId to it.weekStart }
        val backupByKey = backup.groupBy { it.programId to it.weekStart }
        val allKeys = (existingByKey.keys + backupByKey.keys)

        val result = mutableListOf<PlannedExercise>()
        for (key in allKeys) {
            val e = existingByKey[key].orEmpty()
            val b = backupByKey[key].orEmpty()
            when {
                e.isEmpty() -> result += b
                b.isEmpty() -> result += e
                else -> {
                    val eRecency = e.maxOf { it.resolvedAt }
                    val bRecency = b.maxOf { it.resolvedAt }
                    // Backup wins only when STRICTLY newer; ties / all-unresolved -> existing wins.
                    result += if (bRecency > eRecency) b else e
                }
            }
        }
        return result
    }

    // ---------------------------------------------------------------------------------------------
    // Exercise (custom/edited library): UNION by name (case-insensitive), existing wins on clash.
    // ---------------------------------------------------------------------------------------------

    /**
     * Exercises are identified by their NAME (the natural key the rest of the app joins on:
     * WorkoutSet.exerciseName, ExerciseDao.findByName). Union by lowercased name. If both sides
     * have the same name, keep the EXISTING row (its id is the one local sets/plans may reference
     * via name->row lookups, and it reflects the installed library). Backup-only names are added
     * with re-keyed ids so they never overwrite a different existing exercise.
     */
    fun mergeExercises(
        existing: List<Exercise>,
        backup: List<Exercise>
    ): List<Exercise> {
        val result = existing.toMutableList()
        val seenNames = existing.map { it.name.lowercase() }.toMutableSet()
        val usedIds = existing.map { it.id }.toMutableSet()
        var nextId = ((existing.maxOfOrNull { it.id } ?: 0L) + 1L)

        for (ex in backup) {
            if (!seenNames.add(ex.name.lowercase())) continue // name already present -> existing wins
            val finalRow = if (ex.id != 0L && usedIds.add(ex.id)) ex else ex.copy(id = nextId++)
            usedIds.add(finalRow.id)
            result.add(finalRow)
        }
        return result
    }

    // ---------------------------------------------------------------------------------------------
    // GymPreset: UNION by content, id-collision-safe. Returns the merged list AND an id remap so
    // selectedGymPresetId can be repointed to wherever a backup preset landed.
    // ---------------------------------------------------------------------------------------------

    data class MergedPresets(
        val presets: List<GymPreset>,
        /** backup preset id -> id it occupies in the merged set (for selectedGymPresetId fixup). */
        val backupIdRemap: Map<Long, Long>
    )

    fun mergeGymPresets(
        existing: List<GymPreset>,
        backup: List<GymPreset>
    ): MergedPresets {
        val result = existing.toMutableList()
        val existingById = existing.associateBy { it.id }
        val existingByContent = existing.associateBy { presetContentKey(it) }
        val usedIds = existing.map { it.id }.toMutableSet()
        var nextId = ((existing.maxOfOrNull { it.id } ?: 0L) + 1L)
        val remap = HashMap<Long, Long>()

        for (p in backup) {
            val contentDup = existingByContent[presetContentKey(p)]
            if (contentDup != null) {
                remap[p.id] = contentDup.id
                continue
            }
            val finalId = if (p.id != 0L && usedIds.add(p.id) && existingById[p.id] == null) {
                p.id
            } else {
                nextId++
            }
            usedIds.add(finalId)
            result.add(p.copy(id = finalId))
            remap[p.id] = finalId
        }
        return MergedPresets(result, remap)
    }

    private fun presetContentKey(p: GymPreset): String = "${p.name}|${p.equipmentJson}|${p.notes}"

    // ---------------------------------------------------------------------------------------------
    // Program (E2): UNION by name (case-insensitive), existing wins on clash. Returns the merged
    // list AND a backup-id -> merged-id remap so backup plan rows can be repointed at the program
    // they landed on. Preserves the "exactly one active program" invariant.
    // ---------------------------------------------------------------------------------------------

    data class MergedPrograms(
        val programs: List<Program>,
        /** backup program id -> id it occupies in the merged set (for planned_exercises.programId). */
        val backupIdRemap: Map<Long, Long>
    )

    /**
     * Programs are identified by NAME (the user-facing handle; ids are surrogate). Union by lowercased
     * name. If both sides have the same name, keep the EXISTING program (its id is what local plan
     * rows already reference, and it reflects the active device). Backup-only programs are added with
     * re-keyed ids (and forced inactive, since the device already has an active program), so a restore
     * never overwrites a different existing program nor introduces a second active one.
     *
     * The remap lets the caller repoint backup-only plan rows: a backup plan row with
     * `programId = X` should become `programId = backupIdRemap[X]`.
     */
    fun mergePrograms(
        existing: List<Program>,
        backup: List<Program>
    ): MergedPrograms {
        val result = existing.toMutableList()
        val seenNames = existing.map { it.name.lowercase() }.toMutableSet()
        val usedIds = existing.map { it.id }.toMutableSet()
        var nextId = ((existing.maxOfOrNull { it.id } ?: 0L) + 1L)
        val remap = HashMap<Long, Long>()
        val deviceHasActive = existing.any { it.isActive }

        for (p in backup) {
            val nameDup = existing.firstOrNull { it.name.equals(p.name, ignoreCase = true) }
            if (nameDup != null) {
                // Same-named program already on device -> existing wins; repoint to its id.
                remap[p.id] = nameDup.id
                continue
            }
            if (!seenNames.add(p.name.lowercase())) {
                // Two backup programs share a name -> route the second onto whatever the first got.
                val firstId = result.firstOrNull { it.name.equals(p.name, ignoreCase = true) }?.id
                if (firstId != null) { remap[p.id] = firstId; continue }
            }
            val finalId = if (p.id != 0L && usedIds.add(p.id)) p.id else nextId++
            usedIds.add(finalId)
            // A backup-only program is never made active if the device already has one active.
            result.add(p.copy(id = finalId, isActive = if (deviceHasActive) false else p.isActive))
            remap[p.id] = finalId
        }

        // Invariant: exactly one active program. If none ended up active (e.g. device had none and
        // no backup program was active), promote the first; if several, keep the first active only.
        val activeCount = result.count { it.isActive }
        val normalized = when {
            activeCount == 1 -> result
            result.isEmpty() -> result
            else -> {
                val firstActiveIdx = result.indexOfFirst { it.isActive }
                    .let { if (it >= 0) it else 0 }
                result.mapIndexed { i, prog -> prog.copy(isActive = i == firstActiveIdx) }
            }
        }
        return MergedPrograms(normalized, remap)
    }
}

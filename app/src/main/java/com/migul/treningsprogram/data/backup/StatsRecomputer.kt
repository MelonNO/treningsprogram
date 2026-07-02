package com.migul.treningsprogram.data.backup

import com.migul.treningsprogram.data.db.entity.Achievement
import com.migul.treningsprogram.data.db.entity.UserStats
import com.migul.treningsprogram.data.db.entity.WorkoutSession
import com.migul.treningsprogram.data.db.entity.WorkoutSet
import com.migul.treningsprogram.data.repository.GamificationRepository
import com.migul.treningsprogram.domain.StreakPolicy

/**
 * Deterministic, from-scratch recompute of [UserStats] from a MERGED history.
 *
 * After a merge we must NOT copy UserStats from either side (that would double-count XP/workouts).
 * Instead we replay the merged completed sessions in chronological order and re-derive everything
 * using the SAME formulas as [GamificationRepository.processWorkoutCompletion]:
 *   - baseXp = 50 per completed working session
 *   - setXp  = 5 per working (non-warmup) set
 *   - prXp   = 30 per personal record (heaviest-weight PR vs all prior completed sessions)
 *   - level via [GamificationRepository.xpToLevel]
 *   - streak via the R1 schedule-aware [StreakPolicy] (logical epoch-day granularity): planned
 *     rest days and day-gaps with no session rows are NEUTRAL (A-R2); only a MISSED placeholder
 *     between two training days breaks the chain, and a MISSED day after the LAST training day
 *     zeroes the current streak — the same "display freshness" rule the live app applies on
 *     foreground, so a merged recompute matches what the live app would show.
 *
 * The one XP source we cannot replay deterministically is the daily-challenge bonus (it depends on
 * which challenges were active on a given calendar day, which is not part of the backup). That is a
 * known, accepted minor under-count after a cross-device merge — flagged in the worker report.
 *
 * Pure function on in-memory lists so it is fully unit-testable without Room.
 */
object StatsRecomputer {

    /**
     * @param sessions all merged sessions (any completion state, INCLUDING the REST/MISSED
     *        placeholders — those drive the schedule-aware streak; they never count as workouts
     *        because they carry no sets).
     * @param sets all merged sets (linked to sessions by sessionId).
     * @param achievements the MERGED achievement set; [UserStats] is not driven by these but they
     *        are accepted for symmetry / future use and to keep the call site explicit.
     */
    fun recompute(
        sessions: List<WorkoutSession>,
        sets: List<WorkoutSet>,
        @Suppress("UNUSED_PARAMETER") achievements: List<Achievement> = emptyList()
    ): UserStats {
        val setsBySession: Map<Long, List<WorkoutSet>> = sets.groupBy { it.sessionId }

        // A session "counts" exactly like the live app: completed AND has >=1 working set.
        // Replay in chronological order; ties broken by id for determinism.
        val counted = sessions
            .filter { s ->
                s.isCompleted && (setsBySession[s.id]?.any { !it.isWarmup } == true)
            }
            .sortedWith(compareBy({ it.dateMs }, { it.id }))

        // R1: every logical day carrying a MISSED placeholder — the streak's chain-break signal.
        val missedEpochDays: Set<Long> = sessions
            .filter { it.kind == WorkoutSession.KIND_MISSED }
            .map { startOfDayEpoch(it.dateMs) }
            .toSet()

        var totalXp = 0
        var totalWorkouts = 0
        var totalPrs = 0
        var currentStreak = 0
        var bestStreak = 0
        var lastTrainingDayEpoch: Long? = null
        var lastWorkoutDateMs = 0L

        // Running best heaviest working weight per exercise, used to detect PRs exactly like the
        // live app (first-ever performance establishes the baseline and is NOT a PR).
        val bestWeightSoFar = HashMap<String, Float>()

        for (session in counted) {
            val sessionSets = setsBySession[session.id].orEmpty()
            val workingSets = sessionSets.filter { !it.isWarmup }
            if (workingSets.isEmpty()) continue

            // PRs in this session: per exercise, this session's heaviest working set beats the
            // best seen in all strictly-prior counted sessions.
            val thisSessionMax: Map<String, Float> = workingSets
                .groupBy { it.exerciseName }
                .mapValues { (_, s) -> s.maxOf { it.weightKg } }

            var sessionPrs = 0
            for ((name, currentMax) in thisSessionMax) {
                val prev = bestWeightSoFar[name]
                if (GamificationRepository.isWeightPr(currentMax, prev)) sessionPrs++
            }
            // Fold this session's maxima into the running record AFTER PR detection.
            for ((name, currentMax) in thisSessionMax) {
                val prev = bestWeightSoFar[name]
                if (prev == null || currentMax > prev) bestWeightSoFar[name] = currentMax
            }

            val baseXp = 50
            val setXp = workingSets.size * 5
            val prXp = sessionPrs * 30
            totalXp += baseXp + setXp + prXp
            totalWorkouts += 1
            totalPrs += sessionPrs

            val dayEpoch = startOfDayEpoch(session.dateMs)
            currentStreak = StreakPolicy.nextStreakOnWorkout(
                currentStreak = currentStreak,
                lastWorkoutEpochDay = lastTrainingDayEpoch,
                todayEpochDay = dayEpoch,
                missedEpochDays = missedEpochDays
            )
            bestStreak = maxOf(bestStreak, currentStreak)
            lastTrainingDayEpoch = maxOf(lastTrainingDayEpoch ?: Long.MIN_VALUE, dayEpoch)
            lastWorkoutDateMs = maxOf(lastWorkoutDateMs, session.dateMs)
        }

        // R1 display freshness, replayed: a MISSED day AFTER the last training day means the chain
        // is broken now — the merged stats must show the same 0 the live app would.
        val lastDay = lastTrainingDayEpoch
        if (lastDay != null && missedEpochDays.any { it > lastDay }) {
            currentStreak = 0
        }

        return UserStats(
            id = 1,
            totalXp = totalXp,
            level = GamificationRepository.xpToLevel(totalXp),
            currentStreak = currentStreak,
            bestStreak = bestStreak,
            totalWorkouts = totalWorkouts,
            totalPrs = totalPrs,
            lastWorkoutDateMs = lastWorkoutDateMs
        )
    }

    /**
     * Logical local epoch-day (Item 7 day boundary), matching the live streak math in
     * [GamificationRepository] so a recomputed streak equals what the app would grant incrementally.
     */
    private fun startOfDayEpoch(ms: Long): Long =
        com.migul.treningsprogram.domain.DayBoundary.logicalEpochDay(ms)
}

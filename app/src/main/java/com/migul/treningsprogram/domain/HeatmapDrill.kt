package com.migul.treningsprogram.domain

/**
 * Stage-3 item 11 — resolves a tapped volume-heatmap cell (muscle row × week column) to the
 * workout session the Recap should open.
 *
 * A cell aggregates a whole week of one muscle, so a single session must be chosen:
 * A-11a picks the MOST RECENT session in that week containing working sets for that muscle —
 * the least surprising single representative.
 *
 * Pure math over pre-shifted LOGICAL timestamps (the heatmap buckets logical millis into
 * Monday weeks; callers must pass the same), so it is unit-testable off-device.
 */
object HeatmapDrill {

    /** One candidate: a session that trained [muscleGroup], with its LOGICAL date millis. */
    data class Row(val sessionId: Long, val logicalDateMs: Long, val muscleGroup: String)

    /**
     * The most recent session in the week starting [weekStartMs] (Monday 00:00 local, same
     * bucketing as [VolumeHeatmap.mondayOf]) with working sets for [muscle], or null when the
     * cell is empty.
     */
    fun resolve(rows: List<Row>, muscle: String, weekStartMs: Long): Long? =
        rows.filter { it.muscleGroup == muscle && VolumeHeatmap.mondayOf(it.logicalDateMs) == weekStartMs }
            .maxByOrNull { it.logicalDateMs }
            ?.sessionId
}

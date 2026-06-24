package com.migul.treningsprogram.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * B1: an automatically generated, plain-language weekly coaching summary, persisted so the user
 * can scroll back through previous weeks. Exactly one row per ISO week (see [weekKey], the
 * locale-independent isoWeekKey()); the once-per-week guard skips generation if a row already
 * exists for the current week.
 */
@Entity(tableName = "weekly_summaries")
data class WeeklySummary(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weekKey: String,        // ISO week key, e.g. "2026-W26"
    val createdAtMs: Long,
    val summaryText: String
)

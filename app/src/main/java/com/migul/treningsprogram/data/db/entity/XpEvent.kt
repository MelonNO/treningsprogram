package com.migul.treningsprogram.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * U2: a single XP-earning event, recorded going forward from the U2 ship. Each row captures WHAT
 * earned the XP ([reason], human-readable), HOW MUCH ([amount], the exact XP granted — never
 * altered by this feature), and WHEN ([timestampMs]). The XP log screen renders these newest-first.
 *
 * Forward-recording only: rows are inserted at the live award point (workout completion). No
 * historical backfill exists, so the log simply starts accumulating from first completion after
 * this ships; before any event the screen shows an empty state.
 */
@Entity(tableName = "xp_events")
data class XpEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    val amount: Int,
    val reason: String,
    val sessionId: Long? = null
)

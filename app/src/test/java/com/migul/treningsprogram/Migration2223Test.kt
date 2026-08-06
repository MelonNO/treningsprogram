package com.migul.treningsprogram

import com.migul.treningsprogram.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode

/**
 * Item 05 (2026-08-06) — proves `MIGRATION_22_23` runs on a REAL SQLite engine and produces exactly
 * the table shape Room expects for
 * [com.migul.treningsprogram.data.db.entity.ExerciseFeedback].
 *
 * This is the failure mode worth guarding: Room validates the on-disk schema when it opens the
 * database, so a column that is nullable here but non-null in the entity (or vice versa) crashes on
 * the user's device at the first launch after the upgrade and nowhere earlier. The migration is
 * also asserted to be purely ADDITIVE — an existing table with rows must come through untouched.
 */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class Migration2223Test {

    /** A v22-shaped database: an existing table with rows, so additivity can be checked. */
    private fun freshV22Db(): JdbcSupportDb {
        val db = JdbcSupportDb.inMemory()
        db.execSQL(
            "CREATE TABLE exercise_notes (exerciseName TEXT NOT NULL, note TEXT NOT NULL, " +
                "updatedAtMs INTEGER NOT NULL, PRIMARY KEY(exerciseName))"
        )
        db.execSQL("INSERT INTO exercise_notes VALUES ('Bench Press', 'pin height 7', 1000)")
        return db
    }

    @Test fun createsExerciseFeedback_withTheShapeRoomExpects() {
        val db = freshV22Db()
        AppDatabase.MIGRATION_22_23.migrate(db)

        val cols = db.columnsOf("exercise_feedback")
        assertEquals(setOf("exerciseName", "reasonKey", "note", "updatedAtMs"), cols.keys)
        // Every field on the entity is non-null Kotlin → every column must be NOT NULL.
        assertEquals("TEXT" to true, cols["exerciseName"])
        assertEquals("TEXT" to true, cols["reasonKey"])
        assertEquals("TEXT" to true, cols["note"])
        assertEquals("INTEGER" to true, cols["updatedAtMs"])
        // Name-keyed identity, like exercise_notes.
        assertEquals(listOf("exerciseName"), db.primaryKeyOf("exercise_feedback"))
        db.close()
    }

    @Test fun isPurelyAdditive_existingDataUntouched() {
        val db = freshV22Db()
        AppDatabase.MIGRATION_22_23.migrate(db)

        db.query("SELECT exerciseName, note FROM exercise_notes").use { c ->
            assertTrue("the pre-existing row must survive the upgrade", c.moveToNext())
            assertEquals("Bench Press", c.getString(0))
            assertEquals("pin height 7", c.getString(1))
            assertEquals("no extra rows may appear", false, c.moveToNext())
        }
        db.close()
    }

    @Test fun isIdempotent_reRunningDoesNotThrow() {
        // CREATE TABLE IF NOT EXISTS — a partially-applied upgrade must be safe to retry.
        val db = freshV22Db()
        AppDatabase.MIGRATION_22_23.migrate(db)
        AppDatabase.MIGRATION_22_23.migrate(db)
        assertTrue(db.tableExists("exercise_feedback"))
        db.close()
    }

    @Test fun theNewTableAcceptsAndReadsBackARow() {
        val db = freshV22Db()
        AppDatabase.MIGRATION_22_23.migrate(db)
        db.execSQL(
            "INSERT INTO exercise_feedback VALUES ('Dumbbell Single-Leg RDL', 'TOO_HARD', 'no balance', 5000)"
        )
        db.query("SELECT reasonKey, note, updatedAtMs FROM exercise_feedback").use { c ->
            assertTrue(c.moveToNext())
            assertEquals("TOO_HARD", c.getString(0))
            assertEquals("no balance", c.getString(1))
            assertEquals(5000L, c.getLong(2))
        }
        db.close()
    }
}

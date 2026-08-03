package com.migul.treningsprogram

import android.database.Cursor
import android.database.MatrixCursor
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.SupportSQLiteStatement
import com.migul.treningsprogram.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

/**
 * 2026-08 classifier additions — proves MIGRATION_20_21 (the data-only muscleGroup backfill) is
 * data-SAFE on a REAL SQLite engine: it re-derives ONLY the denormalised muscleGroup column with
 * the extended classifier (incline/decline press → Chest, landmine press / pull-apart → Shoulders,
 * pull-through → Legs, pullover → Back, and the "Cable Crunch" Cardio→Core word-boundary fix),
 * and never touches reps/weightKg/exerciseName. Same rationale + xerial-sqlite-jdbc adapter as
 * [R2BackfillMigrationTest] / [Migration1920Test].
 */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class Migration2021Test {

    private class JdbcDb(private val conn: Connection) : SupportSQLiteDatabase {
        override fun execSQL(sql: String) { conn.createStatement().use { it.execute(sql) } }
        override fun execSQL(sql: String, bindArgs: Array<out Any?>) {
            conn.prepareStatement(sql).use { ps ->
                bindArgs.forEachIndexed { i, v -> ps.setObject(i + 1, v) }
                ps.execute()
            }
        }
        override fun query(query: String): Cursor =
            conn.createStatement().use { st -> st.executeQuery(query).toMatrixCursor() }
        override fun query(query: String, bindArgs: Array<out Any?>): Cursor =
            conn.prepareStatement(query).use { ps ->
                bindArgs.forEachIndexed { i, v -> ps.setObject(i + 1, v) }
                ps.executeQuery().toMatrixCursor()
            }
        override fun close() = conn.close()
        override val isOpen: Boolean get() = !conn.isClosed
        private fun ResultSet.toMatrixCursor(): MatrixCursor {
            val md = metaData
            val cols = Array(md.columnCount) { md.getColumnLabel(it + 1) }
            val mc = MatrixCursor(cols)
            while (next()) {
                val row = arrayOfNulls<Any?>(cols.size)
                for (i in cols.indices) row[i] = getObject(i + 1)
                mc.addRow(row)
            }
            return mc
        }
        private fun unsupported(): Nothing = throw UnsupportedOperationException("not needed for the migration test")
        override fun compileStatement(sql: String): SupportSQLiteStatement = unsupported()
        override fun beginTransaction() = unsupported()
        override fun beginTransactionNonExclusive() = unsupported()
        override fun beginTransactionWithListener(l: android.database.sqlite.SQLiteTransactionListener) = unsupported()
        override fun beginTransactionWithListenerNonExclusive(l: android.database.sqlite.SQLiteTransactionListener) = unsupported()
        override fun endTransaction() = unsupported()
        override fun setTransactionSuccessful() = unsupported()
        override fun inTransaction(): Boolean = unsupported()
        override val isDbLockedByCurrentThread: Boolean get() = unsupported()
        override fun yieldIfContendedSafely(): Boolean = unsupported()
        override fun yieldIfContendedSafely(sleepAfterYieldDelayMillis: Long): Boolean = unsupported()
        override var version: Int
            get() = unsupported()
            set(value) = unsupported()
        override val maximumSize: Long get() = unsupported()
        override fun setMaximumSize(numBytes: Long): Long = unsupported()
        override var pageSize: Long
            get() = unsupported()
            set(value) = unsupported()
        override fun query(query: SupportSQLiteQuery): Cursor = unsupported()
        override fun query(query: SupportSQLiteQuery, cancellationSignal: android.os.CancellationSignal?): Cursor = unsupported()
        override fun insert(table: String, conflictAlgorithm: Int, values: android.content.ContentValues): Long = unsupported()
        override fun delete(table: String, whereClause: String?, whereArgs: Array<out Any?>?): Int = unsupported()
        override fun update(table: String, conflictAlgorithm: Int, values: android.content.ContentValues, whereClause: String?, whereArgs: Array<out Any?>?): Int = unsupported()
        override val isReadOnly: Boolean get() = unsupported()
        override fun needUpgrade(newVersion: Int): Boolean = unsupported()
        override val path: String? get() = unsupported()
        override fun setLocale(locale: java.util.Locale) = unsupported()
        override fun setMaxSqlCacheSize(cacheSize: Int) = unsupported()
        override fun setForeignKeyConstraintsEnabled(enabled: Boolean) = unsupported()
        override fun enableWriteAheadLogging(): Boolean = unsupported()
        override fun disableWriteAheadLogging() = unsupported()
        override val isWriteAheadLoggingEnabled: Boolean get() = unsupported()
        override val attachedDbs: List<android.util.Pair<String, String>>? get() = unsupported()
        override val isDatabaseIntegrityOk: Boolean get() = unsupported()
    }

    /** A v20-shaped workout_sets table (schema unchanged since v14 — data-only migrations). */
    private fun openDb(): JdbcDb {
        val conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        val db = JdbcDb(conn)
        db.execSQL(
            "CREATE TABLE workout_sets (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "sessionId INTEGER NOT NULL, exerciseName TEXT NOT NULL, muscleGroup TEXT NOT NULL DEFAULT '', " +
                "setNumber INTEGER NOT NULL, reps INTEGER NOT NULL, weightKg REAL NOT NULL, " +
                "isWarmup INTEGER NOT NULL DEFAULT 0, rpeLabel TEXT NOT NULL DEFAULT '', loggedAtMs INTEGER NOT NULL DEFAULT 0)"
        )
        return db
    }

    private fun insert(db: JdbcDb, name: String, oldGroup: String, reps: Int, weight: Double) {
        db.execSQL(
            "INSERT INTO workout_sets (sessionId, exerciseName, muscleGroup, setNumber, reps, weightKg, isWarmup, loggedAtMs) " +
                "VALUES (1, ?, ?, 1, ?, ?, 0, 123456789)",
            arrayOf<Any?>(name, oldGroup, reps, weight)
        )
    }

    private fun groupOf(db: JdbcDb, name: String): String =
        db.query("SELECT muscleGroup FROM workout_sets WHERE exerciseName = ?", arrayOf<Any?>(name)).use { c ->
            c.moveToFirst(); c.getString(0)
        }

    @Test fun backfill_correctsGroups_keepsRepsWeightsNames_andIsIdempotent() {
        val db = openDb()
        // Seed rows under what v20 would have stored, with KNOWN reps/weight.
        insert(db, "Incline Dumbbell Press", "", 8, 30.0)          // "" → Chest
        insert(db, "Incline Barbell Press", "", 10, 50.0)          // "" → Chest (classifier path)
        insert(db, "Cable Crunch", "Cardio", 15, 25.0)             // "run"-in-"crunch" bug → Core
        insert(db, "Barbell Bench Press", "Chest", 5, 100.0)       // already correct → untouched
        insert(db, "Landmine Press", "", 8, 40.0)                  // "" → Shoulders
        insert(db, "Cable Pull-Through", "", 12, 35.0)             // "" → Legs
        insert(db, "Dumbbell Pullover", "", 10, 22.5)              // "" → Back
        insert(db, "Band Pull-Apart", "", 20, 0.0)                 // "" → Shoulders
        insert(db, "Ankle Alphabet", "", 1, 0.0)                   // intentional "" stays ""

        AppDatabase.MIGRATION_20_21.migrate(db)

        assertEquals("Chest", groupOf(db, "Incline Dumbbell Press"))
        assertEquals("Chest", groupOf(db, "Incline Barbell Press"))
        assertEquals("Core", groupOf(db, "Cable Crunch"))
        assertEquals("Chest", groupOf(db, "Barbell Bench Press"))
        assertEquals("Shoulders", groupOf(db, "Landmine Press"))
        assertEquals("Legs", groupOf(db, "Cable Pull-Through"))
        assertEquals("Back", groupOf(db, "Dumbbell Pullover"))
        assertEquals("Shoulders", groupOf(db, "Band Pull-Apart"))
        assertEquals("", groupOf(db, "Ankle Alphabet"))

        // Logged reps/weights/names are NEVER touched (row count proves no rename/delete).
        db.query(
            "SELECT reps, weightKg FROM workout_sets WHERE exerciseName = ?",
            arrayOf<Any?>("Incline Dumbbell Press")
        ).use { c ->
            c.moveToFirst(); assertEquals(8, c.getInt(0)); assertEquals(30.0, c.getDouble(1), 0.001)
        }
        db.query(
            "SELECT reps, weightKg FROM workout_sets WHERE exerciseName = ?",
            arrayOf<Any?>("Cable Crunch")
        ).use { c ->
            c.moveToFirst(); assertEquals(15, c.getInt(0)); assertEquals(25.0, c.getDouble(1), 0.001)
        }
        db.query("SELECT COUNT(*) AS n FROM workout_sets").use { c ->
            c.moveToFirst(); assertEquals(9, c.getInt(0))
        }

        // Idempotent: a second run yields identical groups.
        AppDatabase.MIGRATION_20_21.migrate(db)
        assertEquals("Chest", groupOf(db, "Incline Dumbbell Press"))
        assertEquals("Core", groupOf(db, "Cable Crunch"))
        assertEquals("Chest", groupOf(db, "Barbell Bench Press"))
        db.close()
    }
}

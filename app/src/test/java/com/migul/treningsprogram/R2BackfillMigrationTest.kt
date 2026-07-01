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
 * R2 — proves MIGRATION_16_17 (the v1.16.0 recognition-overhaul backfill) is data-SAFE on a REAL
 * SQLite engine: it re-derives ONLY the denormalised muscleGroup column with the corrected
 * classifier, and never touches reps/weightKg. Same rationale + xerial-sqlite-jdbc adapter as
 * [R1BackfillMigrationTest] (Robolectric's own SQLite has no aarch64 native on this Pi host).
 */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class R2BackfillMigrationTest {

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

    @Test fun backfill_corrects_group_keeps_reps_weight_and_is_idempotent() {
        val db = openDb()
        // Seed rows under the OLD (wrong) group v1.15.0 would have stored, with KNOWN reps/weight.
        insert(db, "Dumbbell Seated Shoulder Press (Seated on Bench, Neutral Grip)", "Chest", 10, 22.5)   // → Shoulders
        insert(db, "Single-Leg Calf Raise with Hand Support on Bench (Ankle Rehab — Bodyweight)", "Chest", 20, 0.0) // → Legs
        insert(db, "Pallof Press — Dumbbell Hold (Standing, DB Held at Chest, Anti-Rotation Brace)", "Chest", 12, 15.0) // → Core
        insert(db, "Barbell Good Morning", "", 8, 40.0)                                                     // → Legs
        insert(db, "Ankle Alphabet / Controlled Ankle Circles (Seated — Ankle Rehab)", "Cardio", 1, 0.0)  // → "" (excluded)
        insert(db, "Bench Press", "Chest", 5, 100.0)                                                        // → Chest (unchanged)

        AppDatabase.MIGRATION_16_17.migrate(db)

        assertEquals("Shoulders", groupOf(db, "Dumbbell Seated Shoulder Press (Seated on Bench, Neutral Grip)"))
        assertEquals("Legs", groupOf(db, "Single-Leg Calf Raise with Hand Support on Bench (Ankle Rehab — Bodyweight)"))
        assertEquals("Core", groupOf(db, "Pallof Press — Dumbbell Hold (Standing, DB Held at Chest, Anti-Rotation Brace)"))
        assertEquals("Legs", groupOf(db, "Barbell Good Morning"))
        assertEquals("", groupOf(db, "Ankle Alphabet / Controlled Ankle Circles (Seated — Ankle Rehab)"))
        assertEquals("Chest", groupOf(db, "Bench Press"))

        // Logged reps/weight are NEVER touched.
        db.query(
            "SELECT reps, weightKg FROM workout_sets WHERE exerciseName = ?",
            arrayOf<Any?>("Dumbbell Seated Shoulder Press (Seated on Bench, Neutral Grip)")
        ).use { c ->
            c.moveToFirst(); assertEquals(10, c.getInt(0)); assertEquals(22.5, c.getDouble(1), 0.001)
        }
        // Idempotent: a second run yields identical groups.
        AppDatabase.MIGRATION_16_17.migrate(db)
        assertEquals("Shoulders", groupOf(db, "Dumbbell Seated Shoulder Press (Seated on Bench, Neutral Grip)"))
        assertEquals("Legs", groupOf(db, "Barbell Good Morning"))
        db.close()
    }
}

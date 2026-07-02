package com.migul.treningsprogram

import android.database.Cursor
import android.database.MatrixCursor
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.SupportSQLiteStatement
import com.migul.treningsprogram.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

/**
 * Feature batch 2026-07-03 — proves MIGRATION_18_19 runs on a REAL SQLite engine and creates the
 * two new tables (`lift_goals`, `exercise_notes`) with exactly the columns the Room entities
 * declare, is additive (touches no other table), and is idempotent (CREATE IF NOT EXISTS). Same
 * xerial-sqlite-jdbc adapter rationale as [R2BackfillMigrationTest] (no aarch64 native for
 * Robolectric's own SQLite on this host).
 */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class Migration1819Test {

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

    private fun columnsOf(db: JdbcDb, table: String): List<String> =
        db.query("PRAGMA table_info($table)").use { c ->
            val cols = mutableListOf<String>()
            while (c.moveToNext()) cols.add(c.getString(c.getColumnIndexOrThrow("name")))
            cols
        }

    @Test fun creates_both_tables_with_expected_columns_and_is_idempotent() {
        val db = JdbcDb(DriverManager.getConnection("jdbc:sqlite::memory:"))
        // A pre-existing v18 table must survive untouched (additive migration).
        db.execSQL("CREATE TABLE gym_presets (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL)")
        db.execSQL("INSERT INTO gym_presets (name) VALUES ('Home Gym')")

        AppDatabase.MIGRATION_18_19.migrate(db)

        assertEquals(
            listOf("id", "exerciseName", "targetWeightKg", "isE1rm", "targetDateMs",
                "createdAtMs", "status", "achievedAtMs"),
            columnsOf(db, "lift_goals")
        )
        assertEquals(listOf("exerciseName", "note", "updatedAtMs"), columnsOf(db, "exercise_notes"))

        // Insert/read works on the created shape.
        db.execSQL(
            "INSERT INTO lift_goals (exerciseName, targetWeightKg, isE1rm, targetDateMs, createdAtMs, status, achievedAtMs) " +
                "VALUES ('Bench Press', 100.0, 0, 0, 42, 'active', 0)"
        )
        db.execSQL("INSERT INTO exercise_notes (exerciseName, note, updatedAtMs) VALUES ('Bench Press', 'pin 7', 9)")
        db.query("SELECT COUNT(*) AS n FROM lift_goals").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }

        // Idempotent + pre-existing data untouched.
        AppDatabase.MIGRATION_18_19.migrate(db)
        db.query("SELECT COUNT(*) AS n FROM lift_goals").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
        db.query("SELECT name FROM gym_presets").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("Home Gym", c.getString(0))
        }
        db.close()
    }
}

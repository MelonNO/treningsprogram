package com.migul.treningsprogram

import android.database.Cursor
import android.database.MatrixCursor
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.SupportSQLiteStatement
import com.migul.treningsprogram.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

/**
 * Body-progress batch 2026-08-04 (brief 02) — proves MIGRATION_21_22 runs on a REAL SQLite engine.
 *
 * What matters here is that the migration is purely ADDITIVE: it creates `body_metrics` with the
 * exact column shape Room generates for [com.migul.treningsprogram.data.db.entity.BodyMetric]
 * (nullable girth columns, non-null id/dateMs), and it does not touch `body_measurements` — the
 * body-WEIGHT series that calorie estimates, the weigh-in reminder, the AI prompt line and
 * relative strength all still read. Same xerial-sqlite-jdbc adapter rationale as Migration1920Test.
 */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class Migration2122Test {

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

    /** A v21-shaped database: the body_measurements table as MIGRATION_5_6 created it, with rows. */
    private fun freshV21Db(): JdbcDb {
        val db = JdbcDb(DriverManager.getConnection("jdbc:sqlite::memory:"))
        db.execSQL(
            "CREATE TABLE body_measurements (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "dateMs INTEGER NOT NULL, weightKg REAL NOT NULL)"
        )
        db.execSQL("INSERT INTO body_measurements (dateMs, weightKg) VALUES (1000, 80.5)")
        db.execSQL("INSERT INTO body_measurements (dateMs, weightKg) VALUES (2000, 79.5)")
        return db
    }

    private fun columnsOf(db: JdbcDb, table: String): Map<String, Pair<String, Boolean>> {
        val out = mutableMapOf<String, Pair<String, Boolean>>()
        db.query("PRAGMA table_info($table)").use { c ->
            while (c.moveToNext()) {
                val name = c.getString(c.getColumnIndexOrThrow("name"))
                val type = c.getString(c.getColumnIndexOrThrow("type"))
                val notNull = c.getInt(c.getColumnIndexOrThrow("notnull")) == 1
                out[name] = type to notNull
            }
        }
        return out
    }

    @Test fun createsBodyMetrics_withTheShapeRoomExpects() {
        val db = freshV21Db()
        AppDatabase.MIGRATION_21_22.migrate(db)

        val cols = columnsOf(db, "body_metrics")
        assertEquals(setOf("id", "dateMs", "waistCm", "neckCm", "hipCm"), cols.keys)
        // Non-null Kotlin fields -> NOT NULL columns.
        assertEquals("INTEGER" to true, cols["id"])
        assertEquals("INTEGER" to true, cols["dateMs"])
        // Nullable Float? -> REAL with NO not-null constraint. Getting this wrong crashes Room's
        // open-time schema validation on the user's device, not here.
        assertEquals("REAL" to false, cols["waistCm"])
        assertEquals("REAL" to false, cols["neckCm"])
        assertEquals("REAL" to false, cols["hipCm"])
        db.close()
    }

    @Test fun bodyMeasurements_isCompletelyUntouched() {
        val db = freshV21Db()
        val before = columnsOf(db, "body_measurements")
        AppDatabase.MIGRATION_21_22.migrate(db)

        assertEquals("the weight series must keep its exact shape", before, columnsOf(db, "body_measurements"))
        db.query("SELECT COUNT(*) FROM body_measurements").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(2, c.getInt(0))
        }
        db.query("SELECT weightKg FROM body_measurements ORDER BY dateMs ASC").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(80.5, c.getDouble(0), 0.0001)
        }
        db.close()
    }

    @Test fun girthColumns_acceptNullsAndValues() {
        val db = freshV21Db()
        AppDatabase.MIGRATION_21_22.migrate(db)

        // A waist-only entry — exactly the case that could NOT live in body_measurements, whose
        // weightKg is NOT NULL. This is the whole reason the girths got their own table.
        db.execSQL("INSERT INTO body_metrics (dateMs, waistCm) VALUES (3000, 88.5)")
        db.execSQL("INSERT INTO body_metrics (dateMs, waistCm, neckCm, hipCm) VALUES (4000, 90.0, 38.0, 99.0)")

        db.query("SELECT neckCm FROM body_metrics WHERE dateMs = 3000").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue("a girth-only entry leaves the others NULL", c.isNull(0))
        }
        db.query("SELECT waistCm, neckCm, hipCm FROM body_metrics WHERE dateMs = 4000").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(90.0, c.getDouble(0), 0.0001)
            assertEquals(38.0, c.getDouble(1), 0.0001)
            assertEquals(99.0, c.getDouble(2), 0.0001)
        }
        db.close()
    }

    @Test fun migration_isIdempotent() {
        val db = freshV21Db()
        AppDatabase.MIGRATION_21_22.migrate(db)
        db.execSQL("INSERT INTO body_metrics (dateMs, waistCm) VALUES (3000, 88.5)")
        // CREATE TABLE IF NOT EXISTS — a re-run must not throw or drop the row.
        AppDatabase.MIGRATION_21_22.migrate(db)
        db.query("SELECT COUNT(*) FROM body_metrics").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
        db.close()
    }

    @Test fun migratingAnEmptyV21Db_yieldsAnEmptyTable() {
        val db = JdbcDb(DriverManager.getConnection("jdbc:sqlite::memory:"))
        AppDatabase.MIGRATION_21_22.migrate(db)
        db.query("SELECT COUNT(*) FROM body_metrics").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        assertNull(null)
        db.close()
    }
}

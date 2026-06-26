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
 * R1 — proves MIGRATION_14_15 is data-SAFE on a REAL SQLite engine (it re-derives only the
 * muscleGroup column; reps/weight/etc. are never touched) and is idempotent.
 *
 * Robolectric's own SQLite needs a native runtime that has NO Linux/aarch64 build (this is an
 * ARM Raspberry Pi host), so FrameworkSQLiteOpenHelperFactory crashes here. Instead we run the
 * migration against the xerial sqlite-jdbc engine (which DOES bundle a Linux/aarch64 native) via
 * a thin [SupportSQLiteDatabase] adapter. Conscrypt is turned OFF because its OpenSSL native also
 * lacks an aarch64 build and would otherwise crash Robolectric's bootstrap; the backfill needs no
 * TLS. Robolectric is kept only so the pure-Java [MatrixCursor] shadow is available.
 */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class R1BackfillMigrationTest {

    /**
     * Minimal real-SQLite [SupportSQLiteDatabase] backed by a single xerial JDBC connection.
     * Only the methods MIGRATION_14_15 and this test exercise are implemented; the rest of the
     * (large) interface is intentionally unsupported.
     */
    private class JdbcDb(private val conn: Connection) : SupportSQLiteDatabase {

        override fun execSQL(sql: String) {
            conn.createStatement().use { it.execute(sql) }
        }

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

        // ── Unused SupportSQLiteDatabase surface ────────────────────────────────────────────
        private fun unsupported(): Nothing =
            throw UnsupportedOperationException("not needed for the migration test")

        override fun compileStatement(sql: String): SupportSQLiteStatement = unsupported()
        override fun beginTransaction() = unsupported()
        override fun beginTransactionNonExclusive() = unsupported()
        override fun beginTransactionWithListener(
            transactionListener: android.database.sqlite.SQLiteTransactionListener
        ) = unsupported()
        override fun beginTransactionWithListenerNonExclusive(
            transactionListener: android.database.sqlite.SQLiteTransactionListener
        ) = unsupported()
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
        override fun query(query: SupportSQLiteQuery, cancellationSignal: android.os.CancellationSignal?): Cursor =
            unsupported()
        override fun insert(table: String, conflictAlgorithm: Int, values: android.content.ContentValues): Long =
            unsupported()
        override fun delete(table: String, whereClause: String?, whereArgs: Array<out Any?>?): Int = unsupported()
        override fun update(
            table: String,
            conflictAlgorithm: Int,
            values: android.content.ContentValues,
            whereClause: String?,
            whereArgs: Array<out Any?>?
        ): Int = unsupported()
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

    private fun openV14LikeDb(): JdbcDb {
        // Single shared connection keeps the :memory: database alive for the whole test.
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

    private fun insert(db: JdbcDb, name: String, wrongGroup: String, reps: Int, weight: Double) {
        db.execSQL(
            "INSERT INTO workout_sets (sessionId, exerciseName, muscleGroup, setNumber, reps, weightKg, isWarmup, loggedAtMs) " +
                "VALUES (1, ?, ?, 1, ?, ?, 0, 123456789)",
            arrayOf<Any?>(name, wrongGroup, reps, weight)
        )
    }

    private fun groupOf(db: JdbcDb, name: String): String =
        db.query("SELECT muscleGroup FROM workout_sets WHERE exerciseName = ?", arrayOf<Any?>(name)).use { c ->
            c.moveToFirst(); c.getString(0)
        }

    @Test fun backfill_corrects_group_keeps_reps_weight_and_is_idempotent() {
        val db = openV14LikeDb()
        // Seed rows under WRONG/blank groups with KNOWN reps/weight.
        insert(db, "Chest-Supported Dumbbell Row (Incline Bench)", "Chest", 10, 60.0)     // → Back
        insert(db, "Dumbbell Bent-Over Rear Delt Fly", "Chest", 12, 12.5)                 // → Shoulders
        insert(db, "Tibialis Raise (Seated, Heels on Floor, Toes Lift)", "", 20, 0.0)     // → Legs
        insert(db, "Hand-Supported Single-Leg Balance Hold (Wall Touch)", "", 30, 0.0)    // → "" (excluded)
        insert(db, "Bench Press", "Chest", 5, 100.0)                                       // → Chest (unchanged)
        insert(db, "Incline Dumbbell Press", "", 8, 30.0)                                  // DEFAULT_EXERCISE → Chest

        AppDatabase.MIGRATION_14_15.migrate(db)

        assertEquals("Back", groupOf(db, "Chest-Supported Dumbbell Row (Incline Bench)"))
        assertEquals("Shoulders", groupOf(db, "Dumbbell Bent-Over Rear Delt Fly"))
        assertEquals("Legs", groupOf(db, "Tibialis Raise (Seated, Heels on Floor, Toes Lift)"))
        assertEquals("", groupOf(db, "Hand-Supported Single-Leg Balance Hold (Wall Touch)"))
        assertEquals("Chest", groupOf(db, "Bench Press"))
        assertEquals("Chest", groupOf(db, "Incline Dumbbell Press"))

        // Logged reps/weight are NEVER touched.
        db.query(
            "SELECT reps, weightKg FROM workout_sets WHERE exerciseName = ?",
            arrayOf<Any?>("Chest-Supported Dumbbell Row (Incline Bench)")
        ).use { c ->
            c.moveToFirst(); assertEquals(10, c.getInt(0)); assertEquals(60.0, c.getDouble(1), 0.001)
        }
        // Idempotent: a second run yields identical groups.
        AppDatabase.MIGRATION_14_15.migrate(db)
        assertEquals("Back", groupOf(db, "Chest-Supported Dumbbell Row (Incline Bench)"))
        assertEquals("Shoulders", groupOf(db, "Dumbbell Bent-Over Rear Delt Fly"))
        db.close()
    }
}

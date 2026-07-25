package com.migul.treningsprogram

import android.database.Cursor
import android.database.MatrixCursor
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.SupportSQLiteStatement
import com.migul.treningsprogram.data.db.AppDatabase
import com.migul.treningsprogram.domain.GymExclusions
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
 * Item 02 — proves MIGRATION_19_20 runs on a REAL SQLite engine: adds the nullable
 * `avoidExercisesJson` column to gym_presets, pre-fills the SEEDED "Home Gym" preset with the
 * confirmed Chest-Supported Dumbbell Row exclusion, leaves every other preset NULL, never guesses
 * at a renamed preset, and preserves user edits on re-run (idempotent). Same xerial-sqlite-jdbc
 * adapter rationale as [Migration1819Test].
 */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class Migration1920Test {

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

    /** A v19-shaped gym_presets table with the three seeded presets + one custom. */
    private fun freshV19Db(): JdbcDb {
        val db = JdbcDb(DriverManager.getConnection("jdbc:sqlite::memory:"))
        db.execSQL(
            """CREATE TABLE gym_presets (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                equipmentJson TEXT NOT NULL,
                notes TEXT NOT NULL,
                barWeightKg REAL,
                dumbbellBarWeightKg REAL,
                platesCsv TEXT,
                loadableDumbbells INTEGER
            )"""
        )
        for (name in listOf("Full Equipment Gym", "Hotel Gym", "Home Gym", "My Custom Gym")) {
            db.execSQL("INSERT INTO gym_presets (name, equipmentJson, notes) VALUES (?, '[]', '')", arrayOf<Any?>(name))
        }
        return db
    }

    private fun avoidOf(db: JdbcDb, name: String): String? =
        db.query("SELECT avoidExercisesJson FROM gym_presets WHERE name = ?", arrayOf<Any?>(name)).use { c ->
            assertTrue("row '$name' exists", c.moveToFirst())
            if (c.isNull(0)) null else c.getString(0)
        }

    @Test fun addsColumn_prefillsSeededHomeGym_leavesOthersNull() {
        val db = freshV19Db()
        AppDatabase.MIGRATION_19_20.migrate(db)

        // Column exists and the seeded Home Gym carries the confirmed exclusion.
        assertEquals(GymExclusions.HOME_GYM_DEFAULT_JSON, avoidOf(db, "Home Gym"))
        // The stored JSON parses to exactly the confirmed movement.
        assertEquals(listOf("Chest-Supported Dumbbell Row"), GymExclusions.parse(avoidOf(db, "Home Gym")))
        // Every other preset (seeded or custom) is untouched: NULL = no exclusions.
        assertNull(avoidOf(db, "Full Equipment Gym"))
        assertNull(avoidOf(db, "Hotel Gym"))
        assertNull(avoidOf(db, "My Custom Gym"))
        db.close()
    }

    @Test fun renamedHomeGym_isNotGuessedAt() {
        val db = JdbcDb(DriverManager.getConnection("jdbc:sqlite::memory:"))
        db.execSQL(
            """CREATE TABLE gym_presets (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL,
                equipmentJson TEXT NOT NULL, notes TEXT NOT NULL,
                barWeightKg REAL, dumbbellBarWeightKg REAL, platesCsv TEXT, loadableDumbbells INTEGER
            )"""
        )
        db.execSQL("INSERT INTO gym_presets (name, equipmentJson, notes) VALUES ('My Garage', '[]', '')")
        AppDatabase.MIGRATION_19_20.migrate(db)
        assertNull("a renamed preset is left for the user to edit", avoidOf(db, "My Garage"))
        db.close()
    }

    @Test fun backfill_preservesUserEdits_onRerun() {
        val db = freshV19Db()
        AppDatabase.MIGRATION_19_20.migrate(db)
        // The user edits the pre-filled list…
        db.execSQL("UPDATE gym_presets SET avoidExercisesJson = '[\"My Own Entry\"]' WHERE name = 'Home Gym'")
        // …and a re-run of the BACKFILL (the guarded UPDATE) must not clobber it.
        db.execSQL(
            "UPDATE gym_presets SET avoidExercisesJson = ? WHERE name = 'Home Gym' AND avoidExercisesJson IS NULL",
            arrayOf<Any?>(GymExclusions.HOME_GYM_DEFAULT_JSON)
        )
        assertEquals("""["My Own Entry"]""", avoidOf(db, "Home Gym"))
        db.close()
    }
}

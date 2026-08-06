package com.migul.treningsprogram

import android.database.Cursor
import android.database.MatrixCursor
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.SupportSQLiteStatement
import org.sqlite.SQLiteDataSource
import java.sql.Connection
import java.sql.ResultSet

/**
 * A thin [SupportSQLiteDatabase] over a real SQLite engine (xerial sqlite-jdbc), so Room `Migration`
 * objects can be executed and inspected in a plain JVM unit test.
 *
 * Why this exists: Room validates the on-disk schema against the entity definitions when it opens
 * the database. A migration whose CREATE TABLE differs from what Room generates — a missing NOT
 * NULL, a wrong affinity — crashes on the USER'S DEVICE at first launch after the upgrade, and
 * nowhere earlier. Running the migration against a real engine and diffing `PRAGMA table_info`
 * catches that here instead. Robolectric's bundled Android-SQLite has no usable native build in
 * this environment, which is why the JDBC engine is used rather than the framework one.
 *
 * Only the members the migration tests actually need are implemented; everything else throws, so an
 * unexpected call surfaces loudly rather than silently returning a wrong default. Extracted from the
 * copy embedded in the earlier migration tests (`Migration1920Test`, `Migration2122Test`) — those
 * are left untouched on purpose; this is used by newer tests.
 */
class JdbcSupportDb(private val conn: Connection) : SupportSQLiteDatabase {

    companion object {
        /**
         * An empty in-memory database.
         *
         * Uses [SQLiteDataSource] rather than `DriverManager` on purpose: `DriverManager` resolves
         * drivers against the CALLING class's classloader, which makes it order-sensitive when
         * Robolectric-sandboxed and plain JVM tests share one suite (a plain test touching it first
         * leaves the sandboxed migration tests unable to find the driver). The data source carries
         * no global state, so it is safe from either side.
         */
        fun inMemory(): JdbcSupportDb =
            JdbcSupportDb(SQLiteDataSource().apply { url = "jdbc:sqlite::memory:" }.connection)
    }

    /** `column name -> (declared type, isNotNull)` for [table] — what Room's validator compares. */
    fun columnsOf(table: String): Map<String, Pair<String, Boolean>> {
        val out = mutableMapOf<String, Pair<String, Boolean>>()
        query("PRAGMA table_info($table)").use { c ->
            while (c.moveToNext()) {
                out[c.getString(c.getColumnIndexOrThrow("name"))] =
                    c.getString(c.getColumnIndexOrThrow("type")) to
                        (c.getInt(c.getColumnIndexOrThrow("notnull")) == 1)
            }
        }
        return out
    }

    /** The primary-key column names of [table], in key order. */
    fun primaryKeyOf(table: String): List<String> {
        val out = sortedMapOf<Int, String>()
        query("PRAGMA table_info($table)").use { c ->
            while (c.moveToNext()) {
                val pk = c.getInt(c.getColumnIndexOrThrow("pk"))
                if (pk > 0) out[pk] = c.getString(c.getColumnIndexOrThrow("name"))
            }
        }
        return out.values.toList()
    }

    fun tableExists(table: String): Boolean =
        query("SELECT name FROM sqlite_master WHERE type='table' AND name='$table'")
            .use { it.moveToNext() }

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

    private fun unsupported(): Nothing =
        throw UnsupportedOperationException("not needed for the migration tests")

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

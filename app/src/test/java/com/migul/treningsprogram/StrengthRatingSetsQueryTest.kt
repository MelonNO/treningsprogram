package com.migul.treningsprogram

import com.migul.treningsprogram.data.db.dao.RatingSetRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sqlite.SQLiteDataSource
import java.io.File
import java.sql.Connection

/**
 * Brief 02 (2026-08-07) — `WorkoutSetDao.getRatingSets` is the only part of the strength rating
 * that is not pure Kotlin, so it is the only part `StrengthRatingTest` cannot reach. Two acceptance
 * criteria live entirely inside this SQL:
 *
 *  - **warm-up sets never contribute to a rating** (decision D5), and
 *  - only **completed** sessions count.
 *
 * As with `Item04StrengthHistoryBestRepsTest`, the query text is read out of the DAO source at run
 * time and executed against a real SQLite engine (xerial sqlite-jdbc — the same engine the app runs
 * on), so these assertions cannot drift away from the shipped SQL.
 *
 * It also stands as the regression guard for the v1.36.0 class of bug: this query deliberately has
 * NO aggregate at all, so there is no bare-column-with-MAX hazard to get wrong. If someone later
 * "optimises" it into a `MAX(weightKg)` with a bare `reps`, [theQueryStaysUnaggregated] fails.
 */
class StrengthRatingSetsQueryTest {

    private val SHIPPED_SQL: String by lazy { extractShippedQuery() }

    private fun daoSourceFile(): File? {
        val rel = "src/main/java/com/migul/treningsprogram/data/db/dao/WorkoutSetDao.kt"
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            for (candidate in listOf(File(dir, rel), File(dir, "app/$rel"))) {
                if (candidate.isFile) return candidate
            }
            dir = dir.parentFile
        }
        return null
    }

    private fun extractShippedQuery(): String {
        val src = daoSourceFile()
            ?: throw AssertionError("could not locate WorkoutSetDao.kt from ${System.getProperty("user.dir")}")
        val text = src.readText()
        val fnIdx = text.indexOf("suspend fun getRatingSets")
        assertTrue("getRatingSets not found in ${src.path}", fnIdx > 0)
        val before = text.substring(0, fnIdx)
        val end = before.lastIndexOf("\"\"\")")
        val openMarker = "@Query(\"\"\""
        val start = before.lastIndexOf(openMarker, end)
        assertTrue("could not parse the @Query block for getRatingSets", start in 0 until end)
        return before.substring(start + openMarker.length, end)
    }

    /** See the note in `Item04StrengthHistoryBestRepsTest.openDb` on why this avoids DriverManager. */
    private fun openDb(): Connection {
        val conn = SQLiteDataSource().apply { url = "jdbc:sqlite::memory:" }.connection
        conn.createStatement().use { st ->
            st.executeUpdate(
                "CREATE TABLE workout_sessions (id INTEGER PRIMARY KEY, dateMs INTEGER NOT NULL, " +
                    "isCompleted INTEGER NOT NULL)"
            )
            st.executeUpdate(
                "CREATE TABLE workout_sets (id INTEGER PRIMARY KEY, sessionId INTEGER NOT NULL, " +
                    "exerciseName TEXT NOT NULL, weightKg REAL NOT NULL, reps INTEGER NOT NULL, " +
                    "isWarmup INTEGER NOT NULL)"
            )
        }
        return conn
    }

    private fun Connection.session(id: Int, dateMs: Long, completed: Boolean = true) =
        prepareStatement("INSERT INTO workout_sessions VALUES (?,?,?)").use {
            it.setInt(1, id); it.setLong(2, dateMs); it.setInt(3, if (completed) 1 else 0); it.execute()
        }

    private fun Connection.set(
        sessionId: Int, name: String, kg: Float, reps: Int, warmup: Boolean = false,
    ) = prepareStatement(
        "INSERT INTO workout_sets (sessionId, exerciseName, weightKg, reps, isWarmup) VALUES (?,?,?,?,?)"
    ).use {
        it.setInt(1, sessionId); it.setString(2, name); it.setFloat(3, kg)
        it.setInt(4, reps); it.setInt(5, if (warmup) 1 else 0); it.execute()
    }

    private fun Connection.run(sinceMs: Long): List<RatingSetRow> {
        val out = mutableListOf<RatingSetRow>()
        prepareStatement(SHIPPED_SQL.replace(":sinceMs", "?")).use { ps ->
            ps.setLong(1, sinceMs)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    out.add(
                        RatingSetRow(
                            sessionId = rs.getLong("sessionId"),
                            exerciseName = rs.getString("exerciseName"),
                            weightKg = rs.getFloat("weightKg"),
                            reps = rs.getInt("reps"),
                            dateMs = rs.getLong("dateMs"),
                        )
                    )
                }
            }
        }
        return out
    }

    @Test fun warmupSetsNeverReachTheRatingEngine() {
        openDb().use { db ->
            db.session(1, 5_000L)
            db.set(1, "Bench Press", 140f, 3, warmup = true)   // heavier than the working set
            db.set(1, "Bench Press", 100f, 5)
            val rows = db.run(0L)
            assertEquals(1, rows.size)
            assertEquals(100f, rows.single().weightKg, 1e-4f)
        }
    }

    @Test fun anIncompleteSessionIsNotRated() {
        openDb().use { db ->
            db.session(1, 5_000L, completed = false)
            db.set(1, "Bench Press", 100f, 5)
            assertTrue(db.run(0L).isEmpty())
        }
    }

    @Test fun setsBeforeTheWindowAreNotReturned() {
        openDb().use { db ->
            db.session(1, 1_000L); db.set(1, "Bench Press", 100f, 5)
            db.session(2, 9_000L); db.set(2, "Bench Press", 110f, 5)
            val rows = db.run(5_000L)
            assertEquals(1, rows.size)
            assertEquals(110f, rows.single().weightKg, 1e-4f)
        }
    }

    @Test fun zeroRepSetsAreExcludedButZeroWeightBodyweightSetsSurvive() {
        openDb().use { db ->
            db.session(1, 5_000L)
            db.set(1, "Pull-Up", 0f, 10)        // bodyweight — MUST survive, it rates
            db.set(1, "Plank", 0f, 0)           // a timed/placeholder row — no reps, no rating
            val rows = db.run(0L)
            assertEquals(1, rows.size)
            assertEquals("Pull-Up", rows.single().exerciseName)
        }
    }

    @Test fun everyReturnedRowCarriesItsSessionSoTierUpCanExcludeOne() {
        openDb().use { db ->
            db.session(1, 5_000L); db.set(1, "Bench Press", 100f, 5)
            db.session(2, 6_000L); db.set(2, "Bench Press", 110f, 5)
            val rows = db.run(0L)
            assertEquals(setOf(1L, 2L), rows.map { it.sessionId }.toSet())
            assertEquals(setOf(5_000L, 6_000L), rows.map { it.dateMs }.toSet())
        }
    }

    /**
     * The v1.36.0 guard. That bug was `MAX(weightKg)` beside a bare `reps`, which SQLite documents
     * as returning an undefined row when the max ties. This query dodges it by not aggregating at
     * all — "best" is decided in Kotlin, where it can mean highest e1RM rather than heaviest.
     */
    @Test fun theQueryStaysUnaggregated() {
        val sql = SHIPPED_SQL.lowercase()
        listOf("max(", "min(", "group by", "sum(", "avg(").forEach {
            assertTrue(
                "getRatingSets must stay unaggregated — found '$it'. Pick the best set in Kotlin.",
                !sql.contains(it)
            )
        }
    }

    @Test fun theQueryFiltersWarmupsAndCompletionInSql() {
        val sql = SHIPPED_SQL.lowercase().replace(" ", "")
        assertTrue("warm-ups must be excluded in SQL", sql.contains("iswarmup=0"))
        assertTrue("only completed sessions may rate", sql.contains("iscompleted=1"))
    }
}

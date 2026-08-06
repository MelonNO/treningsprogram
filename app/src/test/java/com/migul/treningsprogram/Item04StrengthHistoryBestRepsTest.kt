package com.migul.treningsprogram

import com.migul.treningsprogram.data.db.dao.StrengthPoint
import com.migul.treningsprogram.domain.StallDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sqlite.SQLiteDataSource
import java.io.File
import java.sql.Connection

/**
 * Item 04 (2026-08-06), suspect 2 — `WorkoutSetDao.getStrengthHistory` must report the BEST reps at
 * the session's heaviest weight, not an arbitrary set's.
 *
 * The query used to read `MAX(ws.weightKg) AS maxWeight, ws.reps AS bestReps` and rely on SQLite's
 * bare-column rule. That rule only pins the source row when the extreme is UNIQUE; SQLite documents
 * the row as undefined when several rows tie, and in practice returns the first one inserted. A
 * session of several sets at the same top weight — precisely the user's 26 kg dumbbell incline
 * press — therefore reported the reps of whichever set was logged FIRST. Rep progress at a constant
 * weight could be completely invisible, no matter how good the plateau rule downstream is.
 *
 * These tests run the **real query text, extracted from the DAO source file**, against a real SQLite
 * engine (xerial sqlite-jdbc, already a test dependency and the same engine the app runs on), so
 * the assertions cannot drift away from the shipped SQL. [oldQueryProvesTheRegressionWasReal] keeps
 * the pre-fix SQL alongside it to document exactly what was wrong.
 */
class Item04StrengthHistoryBestRepsTest {

    /** The pre-fix query, kept only as the regression witness. Do NOT reintroduce it. */
    private val OLD_SQL = """
        SELECT s.dateMs AS dateMs, MAX(ws.weightKg) AS maxWeight, ws.reps AS bestReps
        FROM workout_sets ws JOIN workout_sessions s ON ws.sessionId = s.id
        WHERE ws.exerciseName = :name AND s.isCompleted = 1 AND ws.weightKg > 0 AND ws.isWarmup = 0
        GROUP BY ws.sessionId ORDER BY s.dateMs ASC
    """

    /**
     * The SQL actually shipped, read out of `WorkoutSetDao.kt` so this test can never assert
     * against a stale copy. Falls back to the expected text only if the source cannot be located.
     */
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
        val fnIdx = text.indexOf("suspend fun getStrengthHistory")
        assertTrue("getStrengthHistory not found in ${src.path}", fnIdx > 0)
        val before = text.substring(0, fnIdx)
        val end = before.lastIndexOf("\"\"\")")
        val openMarker = "@Query(\"\"\""
        val start = before.lastIndexOf(openMarker, end)
        assertTrue("could not parse the @Query block for getStrengthHistory", start in 0 until end)
        return before.substring(start + openMarker.length, end)
    }

    // ── harness ─────────────────────────────────────────────────────────────────────────────

    /**
     * Opens the in-memory engine via [SQLiteDataSource] rather than `DriverManager`, deliberately.
     * `DriverManager` resolves a registered driver against the CALLING class's classloader, and
     * this is a plain (non-Robolectric) test: if it were the first thing in the suite to touch
     * `DriverManager`, the sibling migration tests — which run inside Robolectric's sandbox
     * classloader — would afterwards fail with "No suitable driver found". Verified: that is
     * exactly what happened before this was changed. The data source holds no global state, so
     * test-execution order cannot matter.
     */
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

    /** Sets are inserted in the given order — insertion order is exactly what the old bug leaked. */
    private fun Connection.sets(sessionId: Int, name: String, vararg weightByReps: Pair<Float, Int>, warmup: Boolean = false) =
        weightByReps.forEach { (w, r) ->
            prepareStatement("INSERT INTO workout_sets (sessionId, exerciseName, weightKg, reps, isWarmup) VALUES (?,?,?,?,?)").use {
                it.setInt(1, sessionId); it.setString(2, name); it.setFloat(3, w); it.setInt(4, r)
                it.setInt(5, if (warmup) 1 else 0); it.execute()
            }
        }

    private fun Connection.run(sql: String, name: String): List<StrengthPoint> {
        val out = mutableListOf<StrengthPoint>()
        prepareStatement(sql.replace(":name", "?")).use { ps ->
            ps.setString(1, name)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    out.add(
                        StrengthPoint(
                            dateMs = rs.getLong("dateMs"),
                            maxWeight = rs.getFloat("maxWeight"),
                            bestReps = rs.getInt("bestReps")
                        )
                    )
                }
            }
        }
        return out
    }

    private val LIFT = "Dumbbell Incline Bench Press"

    /** The user's reported history, with each 26 kg session's BEST set logged last. */
    private fun Connection.seedUsersCase() {
        session(1, 1_000L); sets(1, LIFT, 28f to 5)
        session(2, 2_000L); sets(2, LIFT, 26f to 7, 26f to 8)
        session(3, 3_000L); sets(3, LIFT, 26f to 7, 26f to 9)
    }

    // ── the fix ─────────────────────────────────────────────────────────────────────────────

    @Test fun bestRepsIsTheBestSetAtTheTopWeight_notTheFirstOneLogged() {
        openDb().use { db ->
            db.seedUsersCase()
            val history = db.run(SHIPPED_SQL, LIFT)
            assertEquals(3, history.size)
            assertEquals(listOf(28f, 26f, 26f), history.map { it.maxWeight })
            assertEquals(
                "bestReps must be the best set at the top weight, not the first logged",
                listOf(5, 8, 9), history.map { it.bestReps }
            )
        }
    }

    @Test fun endToEnd_theUsersCaseIsNotReportedAsAPlateau() {
        openDb().use { db ->
            db.seedUsersCase()
            assertFalse(StallDetector.isStalled(db.run(SHIPPED_SQL, LIFT)))
        }
    }

    @Test fun oldQueryProvesTheRegressionWasReal() {
        // Witness for the diagnosis: with the pre-fix SQL both 26 kg sessions collapse to their
        // FIRST set (7 reps), the rep progress vanishes, and the lift is reported as plateaued.
        openDb().use { db ->
            db.seedUsersCase()
            val old = db.run(OLD_SQL, LIFT)
            assertEquals(listOf(5, 7, 7), old.map { it.bestReps })
            assertTrue("the old query reproduces the user's false plateau", StallDetector.isStalled(old))
        }
    }

    // ── invariants that must survive the change ─────────────────────────────────────────────

    @Test fun maxWeightIsStillTheHeaviestSet_andRepsAreTakenAtThatWeight() {
        openDb().use { db ->
            // A long light back-off set must not hijack bestReps away from the top weight.
            db.session(1, 1_000L); db.sets(1, LIFT, 20f to 15, 26f to 5, 26f to 7)
            val p = db.run(SHIPPED_SQL, LIFT).single()
            assertEquals(26f, p.maxWeight, 0.001f)
            assertEquals(7, p.bestReps)
        }
    }

    @Test fun warmupSetsAreStillExcluded() {
        openDb().use { db ->
            db.session(1, 1_000L)
            db.sets(1, LIFT, 40f to 12, warmup = true)
            db.sets(1, LIFT, 26f to 6)
            val p = db.run(SHIPPED_SQL, LIFT).single()
            assertEquals("a heavier WARM-UP must not become the session's top weight", 26f, p.maxWeight, 0.001f)
            assertEquals(6, p.bestReps)
        }
    }

    @Test fun bodyweightSetsAreStillExcluded_soBodyweightLiftsRemainUnchanged() {
        // Confirmed decision 4d: bodyweight lifts are out of scope and must stay unable to reach
        // the plateau rule at all. The 0 kg filter is what guarantees that.
        openDb().use { db ->
            db.session(1, 1_000L); db.sets(1, "Pull-Up", 0f to 10)
            db.session(2, 2_000L); db.sets(2, "Pull-Up", 0f to 10)
            db.session(3, 3_000L); db.sets(3, "Pull-Up", 0f to 10)
            assertTrue("bodyweight-only history must yield no strength points at all",
                db.run(SHIPPED_SQL, "Pull-Up").isEmpty())
        }
    }

    @Test fun incompleteSessionsAreStillExcluded() {
        openDb().use { db ->
            db.session(1, 1_000L); db.sets(1, LIFT, 26f to 6)
            db.session(2, 2_000L, completed = false); db.sets(2, LIFT, 40f to 10)
            val history = db.run(SHIPPED_SQL, LIFT)
            assertEquals(1, history.size)
            assertEquals(26f, history.single().maxWeight, 0.001f)
        }
    }

    @Test fun otherExercisesDoNotLeakIn() {
        openDb().use { db ->
            db.session(1, 1_000L)
            db.sets(1, LIFT, 26f to 6)
            db.sets(1, "Barbell Bench Press", 100f to 3)
            val p = db.run(SHIPPED_SQL, LIFT).single()
            assertEquals(26f, p.maxWeight, 0.001f)
            assertEquals(6, p.bestReps)
        }
    }
}

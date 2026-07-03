package com.migul.treningsprogram

import com.migul.treningsprogram.data.db.dao.BodyMeasurementDao
import com.migul.treningsprogram.data.db.entity.BodyMeasurement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H4 — deleting a Home body-weight entry is recoverable (Undo).
 *
 * The Home card's long-press delete now offers a snackbar Undo that re-inserts the exact same
 * [BodyMeasurement]. This locks the data-safety contract that the Undo path relies on: a
 * delete-by-entity followed by an insert-of-the-same-entity restores the row with its original
 * id, date and value, and never duplicates it — mirroring the DAO calls
 * HomeViewModel.deleteBodyMeasurement / restoreBodyMeasurement make.
 */
class H4BodyWeightUndoTest {

    /** In-memory stand-in for the real DAO's delete(entity)/insert(entity) semantics. */
    private class FakeBodyMeasurementDao : BodyMeasurementDao {
        val rows = mutableListOf<BodyMeasurement>()
        private val stream = MutableStateFlow<List<BodyMeasurement>>(emptyList())
        private fun publish() { stream.value = rows.sortedByDescending { it.dateMs } }
        override fun getAll(): Flow<List<BodyMeasurement>> = stream
        override suspend fun insert(m: BodyMeasurement): Long {
            rows.add(m); publish(); return m.id
        }
        override suspend fun delete(m: BodyMeasurement) {
            rows.removeAll { it.id == m.id }; publish()
        }
        override suspend fun getAllOnce(): List<BodyMeasurement> = rows.sortedBy { it.dateMs }
        override suspend fun insertAll(measurements: List<BodyMeasurement>) {
            rows.addAll(measurements); publish()
        }
        override suspend fun deleteAll() { rows.clear(); publish() }
    }

    @Test fun deleteThenUndoRestoresTheSameEntry() = runBlocking {
        val dao = FakeBodyMeasurementDao()
        val a = BodyMeasurement(id = 1, dateMs = 1_000L, weightKg = 80.5f)
        val b = BodyMeasurement(id = 2, dateMs = 2_000L, weightKg = 79.0f)
        dao.insert(a); dao.insert(b)

        // delete (the long-press)
        dao.delete(b)
        assertEquals(1, dao.rows.size)
        assertFalse("deleted entry must be gone", dao.rows.any { it.id == b.id })

        // undo (the snackbar action) — re-insert the exact same object
        dao.insert(b)
        assertEquals(2, dao.rows.size)
        val restored = dao.rows.first { it.id == b.id }
        // id, date and value are all preserved so the trend line + AI context see it unchanged
        assertEquals(b, restored)
        assertEquals(2_000L, restored.dateMs)
        assertEquals(79.0f, restored.weightKg, 0.0001f)
    }

    @Test fun undoDoesNotDuplicateTheEntry() = runBlocking {
        val dao = FakeBodyMeasurementDao()
        val a = BodyMeasurement(id = 7, dateMs = 5_000L, weightKg = 72.3f)
        dao.insert(a)
        dao.delete(a)
        dao.insert(a) // undo
        assertEquals("exactly one row after delete+undo", 1, dao.rows.size)
        assertTrue(dao.rows.single() == a)
    }
}

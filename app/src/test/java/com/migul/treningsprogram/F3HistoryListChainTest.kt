package com.migul.treningsprogram

import com.migul.treningsprogram.domain.HistorySearch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.Executors

/**
 * F3 (v1.24.1) regression — the History "Sessions" list flow chain, in the FIXED single-layer
 * shape used by HistoryViewModel.filteredSessions:
 *
 *   combine(daoFlow, searchQuery, logDateRange) { filter } .stateIn(scope, WhileSubscribed, null)
 *
 * The v1.24.0 chain layered a second stateIn(WhileSubscribed) between the DAO flow and the
 * combine, threading a null "loading" sentinel through the transform; on device that chain never
 * delivered its first emission, leaving the list permanently on skeletons (stage-4 finding F3).
 * This test locks the fixed shape's contract: a fragment-style collector that subscribes before
 * the first DB emission sees null (skeleton) and then real content on the default entry path,
 * and search/range changes re-emit — regardless of emission/subscription order.
 *
 * The fake DAO flow mirrors CoroutinesRoom.createFlow: the query result is produced on a
 * background context and the flow then stays live (awaiting invalidation) without completing.
 */
class F3HistoryListChainTest {

    private val mainThread = Executors.newSingleThreadExecutor { r -> Thread(r, "fake-main") }
    private val mainDispatcher = mainThread.asCoroutineDispatcher()

    private val zone = ZoneId.of("Europe/Oslo")
    private fun ms(dt: LocalDateTime): Long = dt.atZone(zone).toInstant().toEpochMilli()

    // Two "sessions" (dateMs stands in for the entity): Thu 02 Jul and Wed 24 Jun 2026.
    private val thu = ms(LocalDateTime.of(2026, 7, 2, 17, 45))
    private val wed = ms(LocalDateTime.of(2026, 6, 24, 18, 0))

    @After fun tearDown() { mainThread.shutdownNow() }

    private class Harness(
        val filtered: StateFlow<List<Long>?>,
        val query: MutableStateFlow<String>,
        val range: MutableStateFlow<com.migul.treningsprogram.domain.DateRangeFilter.Range?>,
        val received: MutableList<List<Long>?>,
    )

    /** Builds the fixed-shape chain + a fragment-style collector, then runs [body]. */
    private fun runChain(
        emitBeforeSubscribe: Boolean,
        body: suspend (Harness) -> Unit,
    ) = runBlocking {
        val vmScope = CoroutineScope(SupervisorJob() + mainDispatcher)
        val collectorScope = CoroutineScope(SupervisorJob() + mainDispatcher)
        val allowEmit = CompletableDeferred<Unit>()

        val daoFlow: Flow<List<Long>> = flow {
            coroutineScope {
                val result = Channel<List<Long>>()
                launch(Dispatchers.IO) {
                    allowEmit.await()
                    result.send(listOf(thu, wed))
                }
                emitAll(result.consumeAsFlow()) // never completes, like a Room flow
            }
        }

        val query = MutableStateFlow("")
        val range = MutableStateFlow<com.migul.treningsprogram.domain.DateRangeFilter.Range?>(null)

        // The exact shape of the fixed HistoryViewModel.filteredSessions.
        val filtered: StateFlow<List<Long>?> =
            combine(daoFlow, query, range) { sessions, q, r ->
                sessions.filter {
                    HistorySearch.matches(it, q, r, cutoffHour = 4, zone = zone, locale = Locale.UK)
                } as List<Long>?
            }.stateIn(vmScope, SharingStarted.WhileSubscribed(5000), null)

        if (emitBeforeSubscribe) allowEmit.complete(Unit)

        val received = java.util.Collections.synchronizedList(mutableListOf<List<Long>?>())
        collectorScope.launch { filtered.collect { received.add(it) } }
        delay(100) // let the collector attach (and, in the before-subscribe case, data race ahead)

        if (!emitBeforeSubscribe) allowEmit.complete(Unit)

        try {
            body(Harness(filtered, query, range, received))
        } finally {
            collectorScope.cancel()
            vmScope.cancel()
        }
    }

    @Test
    fun defaultEntry_skeletonThenContent_noInteractionNeeded() = runChain(emitBeforeSubscribe = false) { h ->
        val content = withTimeout(5000) { h.filtered.filterNotNull().first() }
        assertEquals(listOf(thu, wed), content)
        // The fragment-style collector saw the loading sentinel first, then real content —
        // never a permanent skeleton.
        assertEquals(null, h.received.first())
        assertEquals(listOf(thu, wed), h.received.last())
    }

    @Test
    fun contentReadyBeforeSubscribe_stillBinds() = runChain(emitBeforeSubscribe = true) { h ->
        val content = withTimeout(5000) { h.filtered.filterNotNull().first() }
        assertEquals(listOf(thu, wed), content)
        assertEquals(listOf(thu, wed), h.received.last())
    }

    @Test
    fun searchAndRangeChanges_reEmitFilteredContent() = runChain(emitBeforeSubscribe = false) { h ->
        withTimeout(5000) { h.filtered.filterNotNull().first() } // initial bind

        h.query.value = "24 Jun"
        withTimeout(5000) { h.filtered.first { it == listOf(wed) } }

        h.query.value = "Nothing matches this"
        withTimeout(5000) { h.filtered.first { it != null && it.isEmpty() } }

        h.query.value = "" // reset → full list again
        withTimeout(5000) { h.filtered.first { it == listOf(thu, wed) } }

        // Range apply → only the July session; reset → full list (worker #2's failing matrix).
        val jul2 = java.time.LocalDate.of(2026, 7, 2).toEpochDay()
        h.range.value = com.migul.treningsprogram.domain.DateRangeFilter.Range(jul2, jul2)
        withTimeout(5000) { h.filtered.first { it == listOf(thu) } }
        h.range.value = null
        withTimeout(5000) { h.filtered.first { it == listOf(thu, wed) } }
    }
}

package com.migul.treningsprogram

import com.migul.treningsprogram.data.backup.BackupScheduler
import com.migul.treningsprogram.data.backup.BackupSnapshotSource
import com.migul.treningsprogram.data.backup.BackupUploader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Guards the coalescing contract of [BackupScheduler]: a burst of rapid [BackupScheduler.requestBackup]
 * calls inside the debounce window must collapse into exactly ONE [BackupUploader.upload]; calls
 * separated by more than the window must each produce their own upload; and [BackupScheduler.suspendRequests]
 * must drop requests entirely.
 *
 * The project does not depend on `kotlinx-coroutines-test` (and we may not add Gradle deps), so this
 * uses a real single-threaded dispatcher + a short window instead of virtual time. The scheduler's
 * window, scope, snapshot source and uploader are all injected, which is what makes that possible.
 * A single-thread executor guarantees the debounce collector is subscribed before we emit, and that
 * timing is deterministic.
 */
class BackupSchedulerTest {

    // Small but comfortably larger than the synchronous burst it has to swallow.
    private val windowMs = 200L

    private val executor = Executors.newSingleThreadExecutor()
    private val dispatcher = executor.asCoroutineDispatcher()
    private val scope = CoroutineScope(dispatcher)

    private val uploadCount = AtomicInteger(0)
    private val lastJson = java.util.concurrent.atomic.AtomicReference<String?>(null)

    private val source = BackupSnapshotSource { "snapshot-${uploadCount.get()}" }
    private val uploader = object : BackupUploader {
        override suspend fun upload(json: String) {
            lastJson.set(json)
            uploadCount.incrementAndGet()
        }
    }

    private fun newScheduler() = BackupScheduler(
        scope = scope,
        source = source,
        uploader = uploader,
        debounceMillis = windowMs
    )

    @After fun tearDown() {
        executor.shutdownNow()
    }

    @Test fun rapidBurstCollapsesToOneUpload() = runBlocking {
        val scheduler = newScheduler()
        // Give the launched debounce collector a moment to subscribe before emitting.
        delay(50)

        // 10 requests fired back-to-back, far faster than the 200ms window.
        repeat(10) { scheduler.requestBackup() }

        // Before the window elapses, nothing should have uploaded.
        delay(windowMs / 2)
        assertEquals("no upload should fire before the debounce window elapses", 0, uploadCount.get())

        // After the window (plus a safety margin), exactly one upload.
        delay(windowMs + 200)
        assertEquals("a burst must coalesce into exactly one upload", 1, uploadCount.get())
        assertEquals("the produced snapshot json should be uploaded", "snapshot-0", lastJson.get())
    }

    @Test fun twoBurstsSeparatedByGapProduceTwoUploads() = runBlocking {
        val scheduler = newScheduler()
        delay(50)

        repeat(5) { scheduler.requestBackup() }
        delay(windowMs + 200)              // let the first burst settle and fire
        assertEquals(1, uploadCount.get())

        repeat(5) { scheduler.requestBackup() }
        delay(windowMs + 200)              // let the second burst settle and fire
        assertEquals("a distinct burst after the window must fire again", 2, uploadCount.get())
    }

    @Test fun suspendedRequestsAreDropped() = runBlocking {
        val scheduler = newScheduler()
        delay(50)

        scheduler.suspendRequests()
        repeat(10) { scheduler.requestBackup() }   // all ignored — simulates a restore/bulk-import
        delay(windowMs + 200)
        assertEquals("requests while suspended must not trigger any upload", 0, uploadCount.get())

        scheduler.resumeRequests()
        scheduler.requestBackup()
        delay(windowMs + 200)
        assertEquals("after resume, a request fires normally", 1, uploadCount.get())
    }
}

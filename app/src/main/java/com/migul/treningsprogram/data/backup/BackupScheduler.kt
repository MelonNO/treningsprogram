package com.migul.treningsprogram.data.backup

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Produces a fully-serialized backup JSON for the scheduler to upload.
 *
 * This thin seam (bound in Hilt to `ExportRepository::exportToJson`) keeps [BackupScheduler]
 * independent of the concrete export engine, so the scheduler can be unit-tested with a trivial
 * fake source and a fake [BackupUploader] — no DB/DAO graph required.
 */
fun interface BackupSnapshotSource {
    suspend fun snapshotJson(): String
}

/**
 * Debounced / coalesced "back up after every user data change" trigger.
 *
 * Every user-data mutation in the app (a completed workout, a logged set, a generated plan, an
 * achievement unlock, a body-weight entry, a settings/profile save) calls [requestBackup]. Rather
 * than pushing a backup per change — which would hammer the cloud during a workout where sets are
 * logged seconds apart — requests are funnelled through a [MutableSharedFlow] and **debounced**:
 * only after [debounceMillis] of quiet does the scheduler actually serialize and upload ONE backup.
 * A burst of N rapid [requestBackup] calls within the window collapses into a single
 * [BackupUploader.upload].
 *
 * ## Restore is intentionally NOT wired here
 * A merge-import (restore) writes a large amount of data in one go. If those writes called
 * [requestBackup] we'd immediately push the just-restored data back up — a pointless storm, and a
 * risk of clobbering the cloud copy with a half-applied merge. The guard is structural: [requestBackup]
 * is wired ONLY into genuine user-initiated mutations and is deliberately absent from the restore
 * path (`ExportRepository.importFromJson` and the `importBackup` / `restoreFromCloud` ViewModel
 * calls). For belt-and-braces an import can additionally bracket itself with
 * [suspendRequests]/[resumeRequests].
 *
 * ## Threading / lifecycle
 * The debounce pipeline runs on an injected application-scoped [CoroutineScope] (see `BackupModule`),
 * so it outlives any single screen and survives ViewModel teardown mid-burst.
 *
 * ## Testability
 * [debounceMillis], the [scope], the [source] and the [uploader] are all injected, so a JVM unit
 * test can supply its own scope + a tiny window + a counting fake uploader and assert that N rapid
 * requests produce exactly ONE upload. See `BackupSchedulerTest`.
 *
 * ## Future enhancement
 * For backups that must survive process death / run while the app is closed, a future version could
 * enqueue a `WorkManager` job on debounce-fire instead of uploading inline. That is deliberately NOT
 * done here to avoid adding a Gradle dependency; the coalescing contract would be unchanged.
 */
@Singleton
class BackupScheduler @Inject constructor(
    @Named(BACKUP_SCOPE) private val scope: CoroutineScope,
    private val source: BackupSnapshotSource,
    private val uploader: BackupUploader,
    @Named(BACKUP_DEBOUNCE_MS) private val debounceMillis: Long
) {
    /**
     * Drop-on-overflow because every emission carries the same meaning ("state changed, back up").
     * We never need to queue more than one pending request; debounce only cares about the *latest*
     * tick's timing, so coalescing at the buffer is correct and avoids any backpressure.
     */
    private val requests = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    @Volatile private var suspended = false

    @OptIn(FlowPreview::class)
    private fun startPipeline() {
        requests
            .debounce(debounceMillis)
            .onEach { runBackup() }
            .launchIn(scope)
    }

    init {
        startPipeline()
    }

    /**
     * Request a backup. Cheap and fire-and-forget — safe to call from any data-change path. Bursts
     * are coalesced by the [debounceMillis] window into a single eventual upload. No-op while
     * [suspendRequests] is in effect.
     */
    fun requestBackup() {
        if (suspended) return
        // tryEmit can't suspend; with DROP_OLDEST it always "succeeds" in registering the latest tick.
        requests.tryEmit(Unit)
    }

    /**
     * Temporarily ignore [requestBackup] calls. Primarily a defensive hook for a bulk import/restore
     * so it can guarantee no backup is triggered by its own writes even if a future call site forgets.
     * Pair with [resumeRequests].
     */
    fun suspendRequests() { suspended = true }

    /** Re-enable [requestBackup] after [suspendRequests]. Does NOT itself request a backup. */
    fun resumeRequests() { suspended = false }

    private suspend fun runBackup() {
        try {
            val json = source.snapshotJson()
            uploader.upload(json)
        } catch (t: Throwable) {
            // Never let a single failed backup tear down the long-lived debounce pipeline.
            Log.w(TAG, "Backup attempt failed; will retry on next data change.", t)
        }
    }

    companion object {
        private const val TAG = "BackupScheduler"

        /** Hilt @Named qualifier for the application-scoped CoroutineScope the pipeline runs on. */
        const val BACKUP_SCOPE = "backupScope"

        /** Hilt @Named qualifier for the debounce window in millis (overridable in tests). */
        const val BACKUP_DEBOUNCE_MS = "backupDebounceMs"

        /**
         * Production debounce window. A few seconds is long enough to swallow a flurry of set logs
         * during an active workout, short enough that the user's data is safely in the cloud soon
         * after they stop touching the app.
         */
        const val DEFAULT_DEBOUNCE_MS = 3_000L
    }
}

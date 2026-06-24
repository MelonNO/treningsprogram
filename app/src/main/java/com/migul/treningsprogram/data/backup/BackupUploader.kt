package com.migul.treningsprogram.data.backup

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The cloud-upload SEAM.
 *
 * [BackupScheduler] produces a serialized backup JSON (via the export engine) and hands it to an
 * implementation of this interface for the actual network push. The trigger/coalescing layer and
 * the real uploader are intentionally decoupled so the cloud worker can drop a Drive-backed
 * implementation in without touching the scheduler.
 *
 * Contract for implementers:
 *  - [upload] is `suspend`; do your network I/O on an IO dispatcher.
 *  - It may be invoked from a background application-scoped coroutine (see [BackupScheduler]).
 *  - It should be idempotent-friendly: the scheduler already coalesces bursts into a single call,
 *    but transient failures are the implementer's concern (retry/backoff lives in the uploader).
 *  - Throwing is tolerated — the scheduler isolates failures so one bad upload can't kill the
 *    debounce loop — but prefer to handle/log your own errors.
 */
interface BackupUploader {
    /** Push one fully-serialized backup [json] to the cloud. */
    suspend fun upload(json: String)
}

/**
 * Default, dependency-free [BackupUploader] bound in Hilt so the app compiles and runs WITHOUT a
 * real cloud backend. It only logs that an upload would have happened; no network, no Google/Drive
 * code. The cloud worker replaces the Hilt binding (see [BackupModule]) with the real uploader.
 */
@Singleton
class LogOnlyBackupUploader @Inject constructor() : BackupUploader {
    override suspend fun upload(json: String) {
        Log.i(TAG, "Backup ready to upload (${json.length} chars). No cloud uploader bound — skipping push.")
    }

    companion object {
        private const val TAG = "BackupUploader"
    }
}

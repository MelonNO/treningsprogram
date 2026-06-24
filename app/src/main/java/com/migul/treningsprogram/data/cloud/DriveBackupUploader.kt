package com.migul.treningsprogram.data.cloud

import com.migul.treningsprogram.data.backup.BackupUploader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * REAL, Drive-backed implementation of the parallel worker's [BackupUploader] seam
 * (`suspend fun upload(json: String)`), plus a restore-side `downloadLatest()` the scheduler
 * doesn't use but the Settings UI does.
 *
 * It pulls the currently signed-in Google account from [GoogleDriveAuth] and delegates the
 * actual Drive I/O to [DriveBackupClient] (appDataFolder, app-private).
 *
 * ## Binding to the seam (remaining one-line wiring)
 * The other worker's `di/BackupModule.kt` currently binds the seam to `LogOnlyBackupUploader`.
 * To activate cloud uploads from the auto-backup scheduler, that binding should point here:
 * ```
 * @Binds @Singleton
 * abstract fun bindBackupUploader(impl: DriveBackupUploader): BackupUploader
 * ```
 * That file is owned by the scheduler worker, so this worker does NOT edit it — the swap is the
 * documented final integration step (see report). The Settings UI already calls this class
 * directly, so manual cloud backup/restore works without the binding swap.
 *
 * Throws [NotSignedInException] if there is no signed-in account with the `drive.appdata`
 * scope, and [NotConfiguredException] if the Web client ID hasn't been set yet — callers
 * (the scheduler or the UI) decide how to surface those. Both are thrown out of [upload]; the
 * scheduler already isolates uploader failures so a not-signed-in state can't kill its loop.
 */
@Singleton
class DriveBackupUploader @Inject constructor(
    private val auth: GoogleDriveAuth,
    private val client: DriveBackupClient
) : BackupUploader {

    class NotConfiguredException : IllegalStateException("Cloud backup is not configured (missing Web client ID).")
    class NotSignedInException : IllegalStateException("Not signed in to Google for cloud backup.")

    /** [BackupUploader.upload]: serialize-then-push is the scheduler's job; we just push [json]. */
    override suspend fun upload(json: String) {
        if (!auth.isConfigured) throw NotConfiguredException()
        val account = auth.lastSignedInAccount() ?: throw NotSignedInException()
        client.upload(account, json)
    }

    /** Matches `BackupUploader.downloadLatest()`. */
    suspend fun downloadLatest(): String? {
        if (!auth.isConfigured) throw NotConfiguredException()
        val account = auth.lastSignedInAccount() ?: throw NotSignedInException()
        return client.downloadLatest(account)
    }

    /** Convenience for the UI's last-backup status. Null if unconfigured / not signed in. */
    suspend fun lastBackupTime(): String? {
        if (!auth.isConfigured) return null
        val account = auth.lastSignedInAccount() ?: return null
        return client.lastBackupTime(account)
    }
}

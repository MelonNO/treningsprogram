package com.migul.treningsprogram.di

import com.migul.treningsprogram.data.backup.BackupScheduler
import com.migul.treningsprogram.data.backup.BackupSnapshotSource
import com.migul.treningsprogram.data.backup.BackupUploader
import com.migul.treningsprogram.data.cloud.DriveBackupUploader
import com.migul.treningsprogram.data.repository.ExportRepository
import dagger.Binds
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Named
import javax.inject.Singleton

/**
 * Hilt wiring for the debounced backup trigger:
 *  - a long-lived application-scoped [CoroutineScope] for the debounce pipeline,
 *  - the debounce window constant,
 *  - the default (log-only) [BackupUploader] binding.
 *
 * The cloud worker swaps in the real uploader by replacing the [bindBackupUploader] binding with
 * a binding to its Drive implementation; nothing else in this module needs to change.
 */
@Module
@InstallIn(SingletonComponent::class)
object BackupModule {

    /**
     * Application-scoped scope for the [BackupScheduler] debounce pipeline. SupervisorJob so a
     * failed child can't cancel the whole scope; Dispatchers.Default because the work is the
     * (CPU-bound) JSON serialize plus a suspend hand-off to the uploader, which does its own IO.
     */
    @Provides
    @Singleton
    @Named(BackupScheduler.BACKUP_SCOPE)
    fun provideBackupScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Named(BackupScheduler.BACKUP_DEBOUNCE_MS)
    fun provideBackupDebounceMs(): Long = BackupScheduler.DEFAULT_DEBOUNCE_MS

    /**
     * Adapts the existing [ExportRepository] serializer to the [BackupSnapshotSource] seam. Lazy so
     * the (DAO-heavy) repository graph isn't constructed until the first debounce actually fires.
     */
    @Provides
    @Singleton
    fun provideBackupSnapshotSource(exportRepository: Lazy<ExportRepository>): BackupSnapshotSource =
        BackupSnapshotSource { exportRepository.get().exportToJson() }
}

/** Interface bindings for the backup seam. Separate module because @Binds requires an abstract class. */
@Module
@InstallIn(SingletonComponent::class)
abstract class BackupBindingsModule {

    /**
     * Real cloud uploader: pushes the debounced auto-backup to the user's Google Drive
     * appDataFolder. It degrades gracefully — when cloud backup is unconfigured (placeholder
     * Web client ID) or no account is signed in, [DriveBackupUploader.upload] throws and
     * [BackupScheduler] isolates/logs that failure, so the auto-backup pipeline never crashes
     * while the feature is not yet set up. ([LogOnlyBackupUploader] remains as a fallback impl
     * but is no longer bound.)
     */
    @Binds
    @Singleton
    abstract fun bindBackupUploader(impl: DriveBackupUploader): BackupUploader
}

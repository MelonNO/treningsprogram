package com.migul.treningsprogram.data.cloud

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uploads / downloads a single JSON backup file in the user's Drive **appDataFolder**
 * (the app-private Application Data folder — invisible in the user's normal Drive UI and
 * inaccessible to other apps).
 *
 * This is the REAL Drive-backed implementation of the cloud-backup uploader seam. It exposes
 * the shape the parallel `BackupUploader` interface is expected to have:
 *   - `suspend fun upload(json: String)`
 *   - `suspend fun downloadLatest(): String?`
 * Once that interface lands in the tree, bind this class to it in a Hilt module (it is a
 * `@Singleton` and already injectable). Coordinated by NAME only — do not import the other
 * worker's files until both are present.
 *
 * ## Google Cloud setup the USER must complete (code STOPS here until done)
 * 1. Create / pick a Google Cloud project.
 * 2. Enable the **Google Drive API**.
 * 3. Configure the OAuth consent screen; add the scope
 *    `https://www.googleapis.com/auth/drive.appdata`.
 * 4. Create an **Android** OAuth client: package `com.migul.treningsprogram` + the signing
 *    SHA-1 (debug and/or release). Required for sign-in to succeed on-device.
 * 5. Create a **Web application** OAuth client and paste its client ID into
 *    `res/values/backup_config.xml` → `default_web_client_id`.
 * Until step 5 is done [GoogleDriveAuth.isConfigured] is false and the UI refuses to sign in.
 */
@Singleton
class DriveBackupClient @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private companion object {
        const val BACKUP_FILE_NAME = "backup.json"
        const val APP_DATA_SPACE = "appDataFolder"
        const val MIME_JSON = "application/json"
        const val APP_NAME = "Treningsprogram"
    }

    /**
     * Build a Drive service bound to the given signed-in account using
     * `GoogleAccountCredential.usingOAuth2(context, listOf(DriveScopes.DRIVE_APPDATA))`.
     */
    private fun driveFor(account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(DriveScopes.DRIVE_APPDATA)
        )
        credential.selectedAccount = account.account
        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName(APP_NAME).build()
    }

    /** Find the existing backup file id in appDataFolder, or null if none exists yet. */
    private fun findBackupFileId(drive: Drive): String? {
        val result = drive.files().list()
            .setSpaces(APP_DATA_SPACE)
            .setQ("name = '$BACKUP_FILE_NAME'")
            .setFields("files(id, name, modifiedTime)")
            .setOrderBy("modifiedTime desc")
            .setPageSize(10)
            .execute()
        return result.files?.firstOrNull()?.id
    }

    /**
     * Upload (create or overwrite) the single `backup.json` in appDataFolder.
     *
     * Mirrors `BackupUploader.upload(json)`.
     */
    suspend fun upload(account: GoogleSignInAccount, json: String): Unit = withContext(Dispatchers.IO) {
        val drive = driveFor(account)
        val content = ByteArrayContent(MIME_JSON, json.toByteArray(Charsets.UTF_8))
        val existingId = findBackupFileId(drive)
        if (existingId == null) {
            val metadata = DriveFile().apply {
                name = BACKUP_FILE_NAME
                parents = listOf(APP_DATA_SPACE)
            }
            drive.files().create(metadata, content)
                .setFields("id")
                .execute()
        } else {
            // Overwrite content; name/parents stay the same.
            drive.files().update(existingId, DriveFile(), content)
                .setFields("id")
                .execute()
        }
    }

    /**
     * Download the latest `backup.json` from appDataFolder, or null if no backup exists.
     *
     * Mirrors `BackupUploader.downloadLatest()`.
     */
    suspend fun downloadLatest(account: GoogleSignInAccount): String? = withContext(Dispatchers.IO) {
        val drive = driveFor(account)
        val fileId = findBackupFileId(drive) ?: return@withContext null
        val out = ByteArrayOutputStream()
        drive.files().get(fileId).executeMediaAndDownloadTo(out)
        out.toString("UTF-8")
    }

    /** Last-modified time (RFC 3339) of the cloud backup, or null if none. */
    suspend fun lastBackupTime(account: GoogleSignInAccount): String? = withContext(Dispatchers.IO) {
        val drive = driveFor(account)
        val result = drive.files().list()
            .setSpaces(APP_DATA_SPACE)
            .setQ("name = '$BACKUP_FILE_NAME'")
            .setFields("files(id, modifiedTime)")
            .setOrderBy("modifiedTime desc")
            .setPageSize(1)
            .execute()
        result.files?.firstOrNull()?.modifiedTime?.toStringRfc3339()
    }
}

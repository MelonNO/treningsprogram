package com.migul.treningsprogram.data.cloud

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import com.migul.treningsprogram.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around [GoogleSignIn] that requests ONLY the app-private Drive
 * `drive.appdata` scope (the Application Data folder — the user's normal Drive files are
 * never visible to this app).
 *
 * ## Configuration (USER ACTION REQUIRED)
 * The Web OAuth client ID is read from the string resource `R.string.default_web_client_id`
 * in `res/values/backup_config.xml`. Until the user replaces the placeholder value
 * (`REPLACE_WITH_WEB_CLIENT_ID`) with a real Web client ID, [isConfigured] returns false and
 * the UI must NOT attempt sign-in (it would crash / fail). See [DriveBackupClient] for the
 * full Google Cloud setup checklist.
 */
@Singleton
class GoogleDriveAuth @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** Drive scope: app-private Application Data folder only. */
    private val appDataScope = Scope(DriveScopes.DRIVE_APPDATA)

    /** Raw value of the configured Web client ID (may be the placeholder). */
    private val webClientId: String
        get() = context.getString(R.string.default_web_client_id)

    /**
     * True once the user has pasted a real Web OAuth client ID into
     * `res/values/backup_config.xml`. While false, callers should surface a
     * "cloud backup not configured" message rather than launching sign-in.
     */
    val isConfigured: Boolean
        get() = webClientId.isNotBlank() && webClientId != PLACEHOLDER_CLIENT_ID

    private fun signInOptions(): GoogleSignInOptions {
        val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(appDataScope)
        // requestIdToken requires a real Web client ID; only set it when configured so that an
        // accidental call while unconfigured can't throw on the placeholder string.
        if (isConfigured) {
            builder.requestIdToken(webClientId)
        }
        return builder.build()
    }

    /** A configured [GoogleSignInClient] for launching the sign-in intent. */
    fun signInClient(): GoogleSignInClient =
        GoogleSignIn.getClient(context, signInOptions())

    /** Intent to start the interactive Google sign-in flow (launch via ActivityResult). */
    fun signInIntent(): Intent = signInClient().signInIntent

    /** The account already signed in on this device, with the appdata scope granted, or null. */
    fun lastSignedInAccount(): GoogleSignInAccount? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        return if (GoogleSignIn.hasPermissions(account, appDataScope)) account else null
    }

    /** True if there is a usable signed-in account with the appdata scope. */
    fun isSignedIn(): Boolean = lastSignedInAccount() != null

    /** Sign the user out (clears the cached account). */
    fun signOut(onComplete: () -> Unit = {}) {
        signInClient().signOut().addOnCompleteListener { onComplete() }
    }

    companion object {
        const val PLACEHOLDER_CLIENT_ID = "REPLACE_WITH_WEB_CLIENT_ID"
    }
}

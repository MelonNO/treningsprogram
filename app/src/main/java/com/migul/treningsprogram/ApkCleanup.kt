package com.migul.treningsprogram

import android.os.Environment
import java.io.File

/**
 * B05: launch-time cleanup of downloaded self-update APKs.
 *
 * The app self-updates by downloading `treningsprogram-<version>.apk` into the device's public
 * Downloads folder (see MainActivity.downloadAndInstall / SettingsAboutFragment.startDownload) and
 * launching the system installer. Android does not reliably signal when an install finishes, so we
 * clean these up on app launch instead.
 *
 * Safety: an APK is deleted ONLY when its embedded version is already installed (or older) — i.e.
 * the update it carried has completed. An APK whose version is NEWER than the currently-installed
 * versionName means the install did NOT complete (failed/partial), so it is KEPT for the user to
 * retry. Files that don't match the app's own naming pattern are never touched.
 */
object ApkCleanup {

    /** Matches the download filename produced by the update flow: `treningsprogram-<version>.apk`. */
    private val APK_NAME_PATTERN = Regex("""^treningsprogram-(.+)\.apk$""", RegexOption.IGNORE_CASE)

    /**
     * Parse the version string from a `treningsprogram-<version>.apk` filename.
     * Returns the raw version text between the prefix and `.apk` (e.g. "v1.9.1"), or null if the
     * name does not match the app's own update-APK pattern.
     */
    fun parseVersionFromFileName(fileName: String): String? {
        val captured = APK_NAME_PATTERN.find(fileName.trim())?.groupValues?.get(1)?.trim()
        return captured?.takeIf { it.isNotBlank() }
    }

    /**
     * Decide whether a downloaded update APK is safe to delete.
     *
     * Delete iff the filename matches the app's update-APK pattern AND the parsed version is NOT
     * newer than the currently-installed [installedVersionName] (i.e. parsed <= installed — the
     * update has completed, or the file is stale/older). Keep (return false) when:
     *  - the filename isn't one of the app's own update APKs (unparseable), or
     *  - the parsed version is newer than installed (install did not complete → preserve for retry).
     *
     * Reuses [UpdateChecker.isNewer] for the comparison so version semantics (and the leading-`v`
     * tolerance) stay identical to the update-prompt logic.
     */
    fun shouldDelete(fileName: String, installedVersionName: String): Boolean {
        val apkVersion = parseVersionFromFileName(fileName) ?: return false
        // isNewer(apkVersion, installed) == true  → APK carries a newer version than installed
        //                                           → install did NOT complete → KEEP.
        // Otherwise apkVersion <= installed → completed (or stale) → safe to DELETE.
        return !UpdateChecker.isNewer(apkVersion, installedVersionName)
    }

    /**
     * Scan the public Downloads directory for the app's own update APKs and delete any whose
     * version has already been installed (or is older). Best-effort and silent: any I/O / Security
     * failure is swallowed so startup is never affected.
     *
     * @param installedVersionName the running app's versionName (e.g. "1.9.1").
     * @param downloadsDir override the directory to scan (defaults to public Downloads); injectable for tests.
     * @return the number of files deleted.
     */
    fun cleanup(
        installedVersionName: String,
        downloadsDir: File? = null,
    ): Int {
        val dir = try {
            downloadsDir ?: Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        } catch (_: Exception) {
            return 0
        }
        val files = try {
            dir?.listFiles() ?: return 0
        } catch (_: Exception) {
            return 0
        }
        var deleted = 0
        for (file in files) {
            try {
                if (!file.isFile) continue
                if (shouldDelete(file.name, installedVersionName)) {
                    if (file.delete()) deleted++
                }
            } catch (_: Exception) {
                // Skip this file; never let one bad entry abort the sweep.
            }
        }
        return deleted
    }
}

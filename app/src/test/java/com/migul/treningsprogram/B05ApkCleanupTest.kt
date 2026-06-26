package com.migul.treningsprogram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B05: safety-critical tests for the downloaded-update-APK cleanup decision.
 *
 * The cleanup deletes an APK ONLY when the version it carries is already installed (or older).
 * An APK whose version is NEWER than installed means the install did NOT complete (failed/partial)
 * and MUST be preserved so the user can retry. Anything not matching the app's own naming pattern
 * is never eligible for deletion.
 *
 * These cover the two pure functions in [ApkCleanup]: filename -> version parsing, and the
 * should-delete decision. (The file-system sweep itself is thin glue verified on-device.)
 */
class B05ApkCleanupTest {

    // ---- parseVersionFromFileName --------------------------------------------------------------

    @Test fun parse_standardTaggedName_extractsVersion() {
        assertEquals("v1.9.1", ApkCleanup.parseVersionFromFileName("treningsprogram-v1.9.1.apk"))
    }

    @Test fun parse_noVPrefix_extractsVersion() {
        assertEquals("1.9.1", ApkCleanup.parseVersionFromFileName("treningsprogram-1.9.1.apk"))
    }

    @Test fun parse_twoComponentVersion_extracts() {
        assertEquals("v2.0", ApkCleanup.parseVersionFromFileName("treningsprogram-v2.0.apk"))
    }

    @Test fun parse_caseInsensitivePrefixAndExtension() {
        assertEquals("v1.0.0", ApkCleanup.parseVersionFromFileName("Treningsprogram-v1.0.0.APK"))
    }

    @Test fun parse_unrelatedFile_returnsNull() {
        assertNull(ApkCleanup.parseVersionFromFileName("some-other-app-1.0.apk"))
        assertNull(ApkCleanup.parseVersionFromFileName("photo.jpg"))
        assertNull(ApkCleanup.parseVersionFromFileName("treningsprogram.apk")) // no version segment
        assertNull(ApkCleanup.parseVersionFromFileName("treningsprogram-v1.9.1.apk.tmp")) // not .apk
        assertNull(ApkCleanup.parseVersionFromFileName("notes-treningsprogram-v1.9.1.apk")) // prefix junk
    }

    @Test fun parse_blankVersionSegment_returnsNull() {
        assertNull(ApkCleanup.parseVersionFromFileName("treningsprogram-.apk"))
    }

    @Test fun parse_trimsSurroundingWhitespace() {
        assertEquals("v1.9.1", ApkCleanup.parseVersionFromFileName("  treningsprogram-v1.9.1.apk  "))
    }

    // ---- shouldDelete: DELETE when version already installed (or older) -------------------------

    @Test fun shouldDelete_exactlyInstalledVersion_deletes() {
        // Update completed: APK version == installed → safe to remove.
        assertTrue(ApkCleanup.shouldDelete("treningsprogram-v1.9.1.apk", "1.9.1"))
    }

    @Test fun shouldDelete_olderVersion_deletes() {
        // Stale leftover from a past update → remove.
        assertTrue(ApkCleanup.shouldDelete("treningsprogram-v1.8.0.apk", "1.9.1"))
    }

    @Test fun shouldDelete_muchOlderVersion_deletes() {
        assertTrue(ApkCleanup.shouldDelete("treningsprogram-v1.0.0.apk", "1.9.1"))
    }

    @Test fun shouldDelete_installedHasMorePatchComponents_olderApkDeletes() {
        assertTrue(ApkCleanup.shouldDelete("treningsprogram-v1.9.apk", "1.9.1"))
    }

    // ---- shouldDelete: KEEP when version newer than installed (install not completed) -----------

    @Test fun shouldDelete_newerVersion_keeps() {
        // Failed/partial install: APK carries a version newer than installed → preserve for retry.
        assertFalse(ApkCleanup.shouldDelete("treningsprogram-v2.0.0.apk", "1.9.1"))
    }

    @Test fun shouldDelete_oneMinorNewer_keeps() {
        assertFalse(ApkCleanup.shouldDelete("treningsprogram-v1.10.0.apk", "1.9.1"))
    }

    @Test fun shouldDelete_onePatchNewer_keeps() {
        assertFalse(ApkCleanup.shouldDelete("treningsprogram-v1.9.2.apk", "1.9.1"))
    }

    @Test fun shouldDelete_newerWithExtraComponent_keeps() {
        assertFalse(ApkCleanup.shouldDelete("treningsprogram-v1.9.1.1.apk", "1.9.1"))
    }

    // ---- shouldDelete: never touch non-app files / unparseable names ----------------------------

    @Test fun shouldDelete_unrelatedFile_keeps() {
        assertFalse(ApkCleanup.shouldDelete("some-other-app-9.9.9.apk", "1.9.1"))
        assertFalse(ApkCleanup.shouldDelete("vacation.jpg", "1.9.1"))
        assertFalse(ApkCleanup.shouldDelete("document.pdf", "1.9.1"))
    }

    @Test fun shouldDelete_appNameButNoVersion_keeps() {
        assertFalse(ApkCleanup.shouldDelete("treningsprogram.apk", "1.9.1"))
    }

    @Test fun shouldDelete_garbageVersionSegment_keepsBecauseNotNewerCheck() {
        // Non-numeric version components parse to 0 via isNewer; "0 <= installed" → delete.
        // Documents the chosen behavior: a malformed-but-pattern-matching name with an
        // effective-zero version is treated as old and removed (not newer, so not preserved).
        assertTrue(ApkCleanup.shouldDelete("treningsprogram-vBETA.apk", "1.9.1"))
    }

    // ---- consistency: shouldDelete agrees with isNewer -----------------------------------------

    @Test fun shouldDelete_isComplementOfIsNewer_forParsedNames() {
        val installed = "1.9.1"
        val names = listOf(
            "treningsprogram-v1.8.0.apk",
            "treningsprogram-v1.9.1.apk",
            "treningsprogram-v1.9.2.apk",
            "treningsprogram-v2.0.0.apk",
        )
        for (name in names) {
            val version = ApkCleanup.parseVersionFromFileName(name)!!
            assertEquals(
                "shouldDelete must be the inverse of isNewer for $name",
                !UpdateChecker.isNewer(version, installed),
                ApkCleanup.shouldDelete(name, installed)
            )
        }
    }
}

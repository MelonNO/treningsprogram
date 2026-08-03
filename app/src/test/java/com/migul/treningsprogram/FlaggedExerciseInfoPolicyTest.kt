package com.migul.treningsprogram

import com.migul.treningsprogram.ui.log.FlaggedExerciseInfoPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Flagged-info batch 2026-08 item 01 — once an exercise's DB match is flagged as wrong, the
 * info sheet hides everything sourced from the matched entry (pictures, instructions,
 * metadata) and shows an explanation instead. Unflagged exercises are completely unaffected.
 *
 * The policy is pure and is exactly what [ExerciseInfoBottomSheet] consults, so these tests
 * cover the shipped decision for every entry point (the sheet computes the same inputs no
 * matter which screen opened it). Name, Coach's note, and the History performed-sets section
 * are deliberately outside the policy — the sheet always shows them.
 */
class FlaggedExerciseInfoPolicyTest {

    // ── Flagged: everything entry-sourced hidden, notice shown ────────────────────────────────

    @Test
    fun `flagged with db entry hides images, details and fallbacks, shows notice`() {
        val s = FlaggedExerciseInfoPolicy.sectionsFor(
            flagged = true, hasDbEntry = true, hasStaticEntry = false, hasImageSource = true
        )
        assertFalse(s.images)
        assertFalse(s.dbDetails)
        assertFalse(s.staticDetails)
        assertFalse(s.genericFallback)
        assertTrue(s.hiddenNotice)
    }

    @Test
    fun `flagged hides the static-catalog fallback too (D2 - would repeat the offense)`() {
        val s = FlaggedExerciseInfoPolicy.sectionsFor(
            flagged = true, hasDbEntry = false, hasStaticEntry = true, hasImageSource = true
        )
        assertFalse(s.images)
        assertFalse(s.staticDetails)
        assertTrue(s.hiddenNotice)
    }

    @Test
    fun `flagged with no matched entry at all still shows only the notice`() {
        val s = FlaggedExerciseInfoPolicy.sectionsFor(
            flagged = true, hasDbEntry = false, hasStaticEntry = false, hasImageSource = false
        )
        assertFalse(s.genericFallback)
        assertTrue(s.hiddenNotice)
    }

    @Test
    fun `flagged never shows any section regardless of available sources`() {
        for (db in listOf(true, false)) for (st in listOf(true, false)) for (img in listOf(true, false)) {
            val s = FlaggedExerciseInfoPolicy.sectionsFor(
                flagged = true, hasDbEntry = db, hasStaticEntry = st, hasImageSource = img
            )
            assertEquals(
                FlaggedExerciseInfoPolicy.Sections(
                    images = false, dbDetails = false, staticDetails = false,
                    genericFallback = false, hiddenNotice = true
                ),
                s
            )
        }
    }

    // ── Unflagged: pre-existing display completely unaffected ─────────────────────────────────

    @Test
    fun `unflagged db match shows images and db details, no notice`() {
        val s = FlaggedExerciseInfoPolicy.sectionsFor(
            flagged = false, hasDbEntry = true, hasStaticEntry = true, hasImageSource = true
        )
        assertTrue(s.images)
        assertTrue(s.dbDetails)
        assertFalse(s.staticDetails)      // db branch wins, as before
        assertFalse(s.genericFallback)
        assertFalse(s.hiddenNotice)
    }

    @Test
    fun `unflagged static-only match shows static details`() {
        val s = FlaggedExerciseInfoPolicy.sectionsFor(
            flagged = false, hasDbEntry = false, hasStaticEntry = true, hasImageSource = true
        )
        assertTrue(s.images)
        assertFalse(s.dbDetails)
        assertTrue(s.staticDetails)
        assertFalse(s.genericFallback)
        assertFalse(s.hiddenNotice)
    }

    @Test
    fun `unflagged nothing matched shows the generic fallback`() {
        val s = FlaggedExerciseInfoPolicy.sectionsFor(
            flagged = false, hasDbEntry = false, hasStaticEntry = false, hasImageSource = false
        )
        assertFalse(s.images)
        assertFalse(s.dbDetails)
        assertFalse(s.staticDetails)
        assertTrue(s.genericFallback)
        assertFalse(s.hiddenNotice)
    }

    @Test
    fun `unflagged without image source shows no image block`() {
        val s = FlaggedExerciseInfoPolicy.sectionsFor(
            flagged = false, hasDbEntry = true, hasStaticEntry = false, hasImageSource = false
        )
        assertFalse(s.images)
        assertTrue(s.dbDetails)
    }

    // ── Lifecycle: unflag / re-match restore the display (flag state is the only input) ───────

    @Test
    fun `unflagging restores the exact pre-flag display`() {
        val before = FlaggedExerciseInfoPolicy.sectionsFor(
            flagged = false, hasDbEntry = true, hasStaticEntry = false, hasImageSource = true
        )
        val whileFlagged = FlaggedExerciseInfoPolicy.sectionsFor(
            flagged = true, hasDbEntry = true, hasStaticEntry = false, hasImageSource = true
        )
        val after = FlaggedExerciseInfoPolicy.sectionsFor(
            flagged = false, hasDbEntry = true, hasStaticEntry = false, hasImageSource = true
        )
        assertTrue(whileFlagged.hiddenNotice)
        assertEquals(before, after)
    }
}

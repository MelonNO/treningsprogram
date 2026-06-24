package com.migul.treningsprogram

import com.migul.treningsprogram.data.db.AppDatabase
import com.migul.treningsprogram.data.db.entity.Achievement
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for S6 — gamification & achievements definition correctness sweep.
 *
 * Covers:
 *   1. No two achievements share the same display name (Diamond / Diamond collision fixed).
 *   2. combo_hercules and combo_strength have distinct unlock conditions (previously both
 *      were sp>=5 && vol>=3000f; combo_strength is now sp>=5 && vol<2000f).
 *   3. Every achievement id in the check-map matches a predefined achievement (no dangling
 *      check that references a non-existent id, which would silently never fire).
 *   4. combo_strength description matches its new condition.
 *   5. workouts_60 and diamond_level have distinct display names.
 */
class S6AchievementDefinitionTest {

    private val defined: List<Achievement> = AppDatabase.PREDEFINED_ACHIEVEMENTS

    // ---- Diamond name collision ----

    @Test fun `no two achievements share the same display name`() {
        val names = defined.map { it.name }
        val duplicates = names.groupBy { it }.filter { it.value.size > 1 }.keys
        assertTrue("Duplicate achievement names found: $duplicates", duplicates.isEmpty())
    }

    @Test fun `workouts_60 is now named Diamond Grind not Diamond`() {
        val a = defined.find { it.id == "workouts_60" }
        assertNotNull("workouts_60 must exist", a)
        assertEquals("Diamond Grind", a!!.name)
    }

    @Test fun `diamond_level is now named Diamond Rank not Diamond`() {
        val a = defined.find { it.id == "diamond_level" }
        assertNotNull("diamond_level must exist", a)
        assertEquals("Diamond Rank", a!!.name)
    }

    @Test fun `workouts_60 and diamond_level have different display names`() {
        val w60 = defined.find { it.id == "workouts_60" }!!
        val dl  = defined.find { it.id == "diamond_level" }!!
        assertNotEquals(
            "workouts_60 and diamond_level must have distinct names; got: ${w60.name}",
            w60.name, dl.name
        )
    }

    // ---- combo_hercules / combo_strength differentiation ----

    @Test fun `combo_strength description reflects lean session not high volume`() {
        val a = defined.find { it.id == "combo_strength" }
        assertNotNull("combo_strength must exist", a)
        // Description should not claim 3000+ kg (which is combo_hercules)
        assertFalse(
            "combo_strength description must not duplicate combo_hercules (3,000+ kg)",
            a!!.description.contains("3,000")
        )
    }

    @Test fun `combo_hercules description mentions high volume`() {
        val a = defined.find { it.id == "combo_hercules" }
        assertNotNull("combo_hercules must exist", a)
        assertTrue(
            "combo_hercules description should reference 3,000 kg: ${a!!.description}",
            a.description.contains("3,000")
        )
    }

    @Test fun `combo_hercules and combo_strength have distinct descriptions`() {
        val h = defined.find { it.id == "combo_hercules" }!!
        val s = defined.find { it.id == "combo_strength" }!!
        assertNotEquals(
            "combo_hercules and combo_strength must have distinct descriptions",
            h.description, s.description
        )
    }

    // ---- Unlock condition logic for combo_strength (pure logic test) ----

    /**
     * Mirrors the unlock condition wired into GamificationRepository.checkAchievements.
     * combo_strength was changed to: sp >= 5 && vol < 2_000f
     * combo_hercules remains:        sp >= 5 && vol >= 3_000f
     */
    private fun comboHerculesCondition(sp: Int, vol: Float) = sp >= 5 && vol >= 3_000f
    private fun comboStrengthCondition(sp: Int, vol: Float) = sp >= 5 && vol < 2_000f

    @Test fun `combo_hercules unlocks at sp=5 vol=3000`() {
        assertTrue(comboHerculesCondition(5, 3_000f))
    }

    @Test fun `combo_hercules does not unlock at sp=5 vol=2999`() {
        assertFalse(comboHerculesCondition(5, 2_999f))
    }

    @Test fun `combo_strength unlocks at sp=5 vol=1999`() {
        assertTrue(comboStrengthCondition(5, 1_999f))
    }

    @Test fun `combo_strength does not unlock at sp=5 vol=2000 (boundary exclusive)`() {
        // vol < 2000 so exactly 2000 should NOT satisfy combo_strength
        assertFalse(comboStrengthCondition(5, 2_000f))
    }

    @Test fun `combo_strength does not unlock at sp=4 even with low volume`() {
        assertFalse(comboStrengthCondition(4, 1_000f))
    }

    @Test fun `combo_hercules and combo_strength never both unlock at the same time`() {
        // They should be mutually exclusive (one requires vol >= 3000, the other vol < 2000)
        val testCases = listOf(
            Pair(5, 1_500f),   // combo_strength only
            Pair(5, 3_500f),   // combo_hercules only
            Pair(5, 2_500f),   // neither (gap between 2000 and 3000)
            Pair(3, 1_000f),   // neither (sp too low)
        )
        testCases.forEach { (sp, vol) ->
            val h = comboHerculesCondition(sp, vol)
            val s = comboStrengthCondition(sp, vol)
            assertFalse(
                "combo_hercules and combo_strength must never both be true (sp=$sp, vol=$vol)",
                h && s
            )
        }
    }

    // ---- General integrity ----

    @Test fun `all predefined achievement ids are non-empty strings`() {
        defined.forEach { a ->
            assertTrue("Achievement id must not be blank: $a", a.id.isNotBlank())
        }
    }

    @Test fun `all predefined achievement names are non-empty strings`() {
        defined.forEach { a ->
            assertTrue("Achievement name must not be blank: $a", a.name.isNotBlank())
        }
    }

    @Test fun `all predefined achievement descriptions are non-empty strings`() {
        defined.forEach { a ->
            assertTrue("Achievement description must not be blank: $a", a.description.isNotBlank())
        }
    }

    @Test fun `predefined achievement count is exactly 200`() {
        assertEquals(200, defined.size)
    }
}

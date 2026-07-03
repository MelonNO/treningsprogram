package com.migul.treningsprogram

import com.migul.treningsprogram.data.repository.GamificationRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B11 — the level-title ladder continues past 20. Levels 1–19 are byte-identical to the
 * pre-B11 ladder; 20+ resolves to a sequence of distinct late-game titles ending in an
 * open-ended final title at 100+.
 */
class B11LevelTitlesTest {

    @Test fun `levels 1-19 are unchanged`() {
        val legacy = mapOf(
            1 to "Rookie", 2 to "Novice", 3 to "Trainee", 4 to "Athlete", 5 to "Competitor",
            6 to "Warrior", 7 to "Champion", 8 to "Iron Man", 9 to "Elite", 10 to "Master",
            11 to "Expert", 12 to "Veteran", 13 to "Pro", 14 to "Phenom",
            15 to "Legend", 16 to "Legend", 17 to "Legend", 18 to "Legend", 19 to "Legend"
        )
        legacy.forEach { (level, title) ->
            assertEquals("level $level", title, GamificationRepository.levelTitle(level))
        }
    }

    @Test fun `level 20 keeps Transcendent so no existing user is renamed`() {
        assertEquals("Transcendent", GamificationRepository.levelTitle(20))
        assertEquals("Transcendent", GamificationRepository.levelTitle(24))
    }

    @Test fun `ladder boundaries past 24`() {
        assertEquals("Juggernaut", GamificationRepository.levelTitle(25))
        assertEquals("Juggernaut", GamificationRepository.levelTitle(29))
        assertEquals("Titan", GamificationRepository.levelTitle(30))
        assertEquals("Colossus", GamificationRepository.levelTitle(35))
        assertEquals("Immortal", GamificationRepository.levelTitle(40))
        assertEquals("Demigod", GamificationRepository.levelTitle(45))
        assertEquals("Ascendant", GamificationRepository.levelTitle(50))
        assertEquals("Ascendant", GamificationRepository.levelTitle(59))
        assertEquals("Mythic", GamificationRepository.levelTitle(60))
        assertEquals("Eternal", GamificationRepository.levelTitle(75))
        assertEquals("Eternal", GamificationRepository.levelTitle(99))
        assertEquals("Apex", GamificationRepository.levelTitle(100))
        assertEquals("Apex", GamificationRepository.levelTitle(250))
    }

    @Test fun `20 plus is a real ladder - multiple distinct titles`() {
        val titles = (20..100).map { GamificationRepository.levelTitle(it) }.distinct()
        assertTrue("expected at least 8 distinct late-game titles, got $titles", titles.size >= 8)
    }
}

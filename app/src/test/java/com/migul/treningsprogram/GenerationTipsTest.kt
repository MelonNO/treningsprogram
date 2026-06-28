package com.migul.treningsprogram

import com.migul.treningsprogram.ui.common.GenerationTips
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P5: the rotating wait copy. [GenerationTips.tip] indexes into a non-empty message list and wraps
 * around forever, so a wait screen can keep advancing an integer counter without bounds checks.
 */
class GenerationTipsTest {

    @Test fun `there is wait copy to show`() {
        assertTrue(GenerationTips.messages.isNotEmpty())
        assertTrue(GenerationTips.messages.all { it.isNotBlank() })
    }

    @Test fun `tip wraps around the message list`() {
        val n = GenerationTips.messages.size
        assertEquals(GenerationTips.messages[0], GenerationTips.tip(0))
        assertEquals(GenerationTips.messages[0], GenerationTips.tip(n))      // wrap forward
        assertEquals(GenerationTips.messages[1 % n], GenerationTips.tip(n + 1))
    }

    @Test fun `negative indices are handled (floorMod, never crashes)`() {
        // floorMod keeps the index in range even if a counter were ever negative.
        assertEquals(GenerationTips.messages.last(), GenerationTips.tip(-1))
    }
}

package com.migul.treningsprogram

import com.migul.treningsprogram.data.ExerciseInfoCorrections
import com.migul.treningsprogram.data.ExerciseInfoCorrections.Codec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * QoL 2026-08 item 04 — the pure serialization/merge/formatting logic behind the mismatch flags
 * and re-match overrides (the SharedPreferences wrapper around it is a thin I/O shell).
 */
class ExerciseInfoCorrectionsCodecTest {

    // ── Flag encoding ─────────────────────────────────────────────────────────────────────────

    @Test fun `flag roundtrips multi-word names and ids`() {
        val encoded = Codec.encodeFlag("Zottman Curl (Slow Eccentric)", "Cross_Body_Hammer_Curl")
        val flag = Codec.decodeFlag(encoded)
        assertEquals("Zottman Curl (Slow Eccentric)", flag.exerciseName)
        assertEquals("Cross_Body_Hammer_Curl", flag.matchedDbId)
    }

    @Test fun `flag roundtrips the no-match marker`() {
        val flag = Codec.decodeFlag(Codec.encodeFlag("Mystery Movement", ExerciseInfoCorrections.NO_MATCH))
        assertEquals("Mystery Movement", flag.exerciseName)
        assertEquals(ExerciseInfoCorrections.NO_MATCH, flag.matchedDbId)
    }

    @Test fun `legacy value without separator degrades to no-match`() {
        val flag = Codec.decodeFlag("Just A Name")
        assertEquals("Just A Name", flag.exerciseName)
        assertEquals(ExerciseInfoCorrections.NO_MATCH, flag.matchedDbId)
    }

    // ── Map serialization ─────────────────────────────────────────────────────────────────────

    @Test fun `map roundtrips through json`() {
        val map = linkedMapOf("zottman curl" to "v1", "hammer curl" to "v2")
        assertEquals(map, Codec.parse(Codec.serialize(map)))
    }

    @Test fun `empty map serializes to empty string`() {
        // "" is the BackupPreferences default, so no-corrections stays indistinguishable from
        // a pre-v8 backup — exactly what the migration expects.
        assertEquals("", Codec.serialize(emptyMap()))
        assertTrue(Codec.parse("").isEmpty())
        assertTrue(Codec.parse(null).isEmpty())
        assertTrue(Codec.parse("not json at all").isEmpty())
    }

    // ── Backup union merge (A1) ───────────────────────────────────────────────────────────────

    @Test fun `union keeps both sides`() {
        val device = Codec.serialize(mapOf("a" to "1"))
        val backup = Codec.serialize(mapOf("b" to "2"))
        val merged = Codec.parse(Codec.union(device, backup))
        assertEquals(mapOf("a" to "1", "b" to "2"), merged)
    }

    @Test fun `union collision - device wins`() {
        val device = Codec.serialize(mapOf("a" to "device"))
        val backup = Codec.serialize(mapOf("a" to "backup"))
        assertEquals(mapOf("a" to "device"), Codec.parse(Codec.union(device, backup)))
    }

    @Test fun `union of two empties stays empty string`() {
        assertEquals("", Codec.union("", ""))
    }

    @Test fun `union with one empty side passes the other through`() {
        val one = Codec.serialize(mapOf("a" to "1"))
        assertEquals(mapOf("a" to "1"), Codec.parse(Codec.union(one, "")))
        assertEquals(mapOf("a" to "1"), Codec.parse(Codec.union("", one)))
    }

    // ── Copy text (D4) ────────────────────────────────────────────────────────────────────────

    @Test fun `copy text is one mapping per line`() {
        val text = Codec.copyText(
            listOf(
                ExerciseInfoCorrections.Flag("Zottman Curl", "Wide-Grip_Rear_Pull-Up"),
                ExerciseInfoCorrections.Flag("Mystery Movement", ExerciseInfoCorrections.NO_MATCH),
            )
        )
        assertEquals(
            "Zottman Curl -> Wide-Grip_Rear_Pull-Up\nMystery Movement -> (no match)",
            text
        )
    }
}

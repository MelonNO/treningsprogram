package com.migul.treningsprogram

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P5 (generation-quality overhaul 2026-07) — auditable §6 metadata.
 *
 * The generated plan now DECLARES per-exercise / per-day / per-week metadata alongside the core plan
 * fields. This test pins the two acceptance criteria that are unit-checkable without a live model:
 *  1. The app parses the RICHER shape WITHOUT failing — a parser that only knows the CORE fields (the
 *     pre-P5 shape, and any old/lean response) still parses a metadata-rich response, because Gson
 *     ignores unmapped fields. So adding metadata never breaks parsing.
 *  2. The declared numbers are CONSUMABLE — a parser that models the §6 fields reads them back
 *     (role, work/rest/setup seconds, estimatedMinutes, per-day estimate + within-window, and the
 *     per-week blockState + volume summary — the single continue/new signal P1 shares).
 *
 * Mirrors the local-mirror pattern of [ProgramJsonParsingTest] / S3ParsingTest (production classes are
 * private). The mirrors below copy the exact production field shape.
 */
class P5MetadataTest {

    private val gson = Gson()

    // A metadata-rich response exactly like the P5 output contract instructs the model to emit.
    private val richJson = """
    {
      "rationale": "Kept your bench and squat and progressed the loads; varied the accessory rows by grip.",
      "days": [
        {
          "dayOfWeek": 1,
          "name": "Upper",
          "dayEstimateMinutes": 58,
          "withinDurationWindow": true,
          "exercises": [
            {
              "name": "Barbell Bench Press",
              "sets": 4,
              "targetReps": "5-8",
              "targetWeightKg": 82.5,
              "notes": "RPE 8 (~2 RIR); double progression +reps then +2.5 kg",
              "recommendedRestSeconds": 180,
              "role": "primary compound",
              "movementPattern": "horizontal push",
              "primaryMuscles": ["chest"],
              "secondaryMuscles": ["triceps", "front delts"],
              "countsAsHardSets": 4,
              "workSeconds": 160,
              "restSeconds": 180,
              "setupSeconds": 60,
              "estimatedMinutes": 12,
              "injuryModification": ""
            }
          ]
        }
      ],
      "week": {
        "weeklyVolumeSummary": {"chest": 12, "back": 14},
        "movementPatternSummary": ["horizontal push", "vertical pull", "squat", "hinge"],
        "durationSummary": "all days within the 60-min window",
        "blockState": "continue",
        "constraintNotes": ""
      }
    }
    """.trimIndent()

    // ── AC 1: a CORE-only parser (pre-P5 shape) tolerates the richer response ────────────────────────

    private data class CoreExJson(
        val name: String = "",
        val sets: Int = 3,
        @SerializedName("targetReps") val targetReps: String = "8-12",
        @SerializedName("targetWeightKg") val targetWeightKg: Float = 0f,
        val notes: String = "",
        val recommendedRestSeconds: Int = 90
    )
    private data class CoreDayJson(
        val dayOfWeek: Int = 1,
        val name: String = "",
        val exercises: List<CoreExJson> = emptyList()
    )
    private data class CoreProgramJson(val days: List<CoreDayJson> = emptyList(), val rationale: String = "")

    @Test fun coreParser_ignoresMetadata_andReadsCorePlanIntact() {
        val p = gson.fromJson(richJson, CoreProgramJson::class.java)
        assertEquals(1, p.days.size)
        assertEquals(1, p.days[0].exercises.size)
        val ex = p.days[0].exercises[0]
        assertEquals("Barbell Bench Press", ex.name)
        assertEquals(4, ex.sets)
        assertEquals("5-8", ex.targetReps)
        assertEquals(82.5f, ex.targetWeightKg)
        assertEquals(180, ex.recommendedRestSeconds)
        assertTrue("rationale still read", p.rationale.contains("progressed"))
    }

    // ── AC 2: a metadata-aware parser reads the declared §6 numbers back ─────────────────────────────

    private data class MetaExJson(
        val name: String = "",
        val sets: Int = 3,
        @SerializedName("targetReps") val targetReps: String = "8-12",
        @SerializedName("targetWeightKg") val targetWeightKg: Float = 0f,
        val notes: String = "",
        val recommendedRestSeconds: Int = 90,
        val role: String = "",
        val movementPattern: String = "",
        val primaryMuscles: List<String> = emptyList(),
        val secondaryMuscles: List<String> = emptyList(),
        val countsAsHardSets: Int = 0,
        val workSeconds: Int = 0,
        val restSeconds: Int = 0,
        val setupSeconds: Int = 0,
        val estimatedMinutes: Float = 0f,
        val injuryModification: String = ""
    )
    private data class MetaDayJson(
        val dayOfWeek: Int = 1,
        val name: String = "",
        val exercises: List<MetaExJson> = emptyList(),
        val dayEstimateMinutes: Float = 0f,
        val withinDurationWindow: Boolean = false
    )
    private data class MetaWeekJson(
        val weeklyVolumeSummary: Map<String, Int> = emptyMap(),
        val movementPatternSummary: List<String> = emptyList(),
        val durationSummary: String = "",
        val blockState: String = "",
        val constraintNotes: String = ""
    )
    private data class MetaProgramJson(
        val days: List<MetaDayJson> = emptyList(),
        val rationale: String = "",
        val week: MetaWeekJson? = null
    )

    @Test fun metadataParser_readsDeclaredNumbers() {
        val p = gson.fromJson(richJson, MetaProgramJson::class.java)
        val day = p.days[0]
        assertEquals(58f, day.dayEstimateMinutes)
        assertTrue(day.withinDurationWindow)
        val ex = day.exercises[0]
        assertEquals("primary compound", ex.role)
        assertEquals("horizontal push", ex.movementPattern)
        assertEquals(listOf("chest"), ex.primaryMuscles)
        assertEquals(4, ex.countsAsHardSets)
        assertEquals(160, ex.workSeconds)
        assertEquals(180, ex.restSeconds)
        assertEquals(60, ex.setupSeconds)
        assertEquals(12f, ex.estimatedMinutes)
        // Per-week metadata: blockState is the single continue/new signal shared with P1.
        requireNotNull(p.week)
        assertEquals("continue", p.week!!.blockState)
        assertEquals(12, p.week!!.weeklyVolumeSummary["chest"])
        assertEquals(4, p.week!!.movementPatternSummary.size)
    }

    // ── Robustness: an OLD/lean response with NONE of the metadata still parses through the meta model ─

    @Test fun leanResponse_parsesThroughMetadataModel_withNeutralDefaults() {
        val lean = """{"days":[{"dayOfWeek":1,"name":"Mon","exercises":[{"name":"Squat","sets":4,"targetReps":"6-8","targetWeightKg":100.0,"notes":"RPE 8; +2.5kg","recommendedRestSeconds":180}]}]}"""
        val p = gson.fromJson(lean, MetaProgramJson::class.java)
        val ex = p.days[0].exercises[0]
        assertEquals("Squat", ex.name)
        assertEquals(100f, ex.targetWeightKg)
        // Metadata absent ⇒ neutral defaults, no crash.
        assertEquals("", ex.role)
        assertEquals(0, ex.workSeconds)
        assertEquals(0f, p.days[0].dayEstimateMinutes)
        // `week` absent ⇒ null (Gson leaves nullable object null).
        org.junit.Assert.assertNull(p.week)
    }
}

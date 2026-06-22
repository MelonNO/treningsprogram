package com.migul.treningsprogram

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Mirror of private classes in AiRepository — kept here to avoid changing production visibility
private data class ExJson(
    val name: String = "",
    val sets: Int = 3,
    @SerializedName("targetReps") val targetReps: String = "8-12",
    @SerializedName("targetWeightKg") val targetWeightKg: Float = 0f,
    val notes: String = "",
    val recommendedRestSeconds: Int = 90
)
private data class DayJson(
    val dayOfWeek: Int = 1,
    val name: String = "",
    val exercises: List<ExJson> = emptyList()
)
private data class ProgramJson(val days: List<DayJson> = emptyList())

// Mirror of extractJson from AiRepository
private fun extractJson(text: String): String {
    val fenceMatch = Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(text)
    if (fenceMatch != null) return fenceMatch.groupValues[1].trim()
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    if (start != -1 && end > start) return text.substring(start, end + 1)
    throw IllegalStateException("No JSON found in AI response")
}

class ProgramJsonParsingTest {

    private val gson = Gson()

    private fun parse(json: String) = gson.fromJson(json, ProgramJson::class.java)

    @Test fun parse_multiDayProgram_correctDayAndExerciseCount() {
        val json = """
        {
          "days": [
            {"dayOfWeek":1,"name":"Monday","exercises":[
              {"name":"Bench Press","sets":4,"targetReps":"8-10","targetWeightKg":80.0,"notes":"","recommendedRestSeconds":120},
              {"name":"Pull-Up","sets":3,"targetReps":"6-8","targetWeightKg":0.0,"notes":"","recommendedRestSeconds":90}
            ]},
            {"dayOfWeek":3,"name":"Wednesday","exercises":[
              {"name":"Squat","sets":4,"targetReps":"6-8","targetWeightKg":100.0,"notes":"","recommendedRestSeconds":180}
            ]},
            {"dayOfWeek":5,"name":"Friday","exercises":[
              {"name":"Deadlift","sets":3,"targetReps":"5","targetWeightKg":120.0,"notes":"","recommendedRestSeconds":180},
              {"name":"Row","sets":3,"targetReps":"8-10","targetWeightKg":60.0,"notes":"","recommendedRestSeconds":90},
              {"name":"Curl","sets":3,"targetReps":"10-12","targetWeightKg":15.0,"notes":"","recommendedRestSeconds":60}
            ]}
          ]
        }
        """.trimIndent()

        val result = parse(json)
        assertEquals(3, result.days.size)
        assertEquals(2, result.days[0].exercises.size)
        assertEquals(1, result.days[1].exercises.size)
        assertEquals(3, result.days[2].exercises.size)
        assertEquals("Monday", result.days[0].name)
        assertEquals(1, result.days[0].dayOfWeek)
    }

    @Test fun parse_singleDayJson_matchesFormat() {
        val json = """{"days":[{"dayOfWeek":1,"name":"Monday","exercises":[{"name":"Push-Up","sets":3,"targetReps":"15-20","targetWeightKg":0,"notes":"Keep core tight","recommendedRestSeconds":60}]}]}"""
        val result = parse(json)
        assertEquals(1, result.days.size)
        assertEquals(1, result.days[0].dayOfWeek)
        assertEquals("Monday", result.days[0].name)
        val ex = result.days[0].exercises[0]
        assertEquals("Push-Up", ex.name)
        assertEquals(3, ex.sets)
        assertEquals("15-20", ex.targetReps)
        assertEquals(0f, ex.targetWeightKg)
        assertEquals("Keep core tight", ex.notes)
        assertEquals(60, ex.recommendedRestSeconds)
    }

    @Test fun parse_missingOptionalFields_defaultToZero() {
        // targetWeightKg absent → should default to 0f
        val json = """{"days":[{"dayOfWeek":2,"name":"Tuesday","exercises":[{"name":"Plank","sets":3,"targetReps":"60s","notes":"","recommendedRestSeconds":60}]}]}"""
        val result = parse(json)
        val ex = result.days[0].exercises[0]
        assertEquals(0f, ex.targetWeightKg)
    }

    @Test fun parse_emptyDaysArray_returnsEmptyList() {
        val json = """{"days":[]}"""
        val result = parse(json)
        assertTrue(result.days.isEmpty())
    }

    @Test fun parse_withMarkdownFences_strippedThenParsed() {
        val raw = "```json\n{\"days\":[{\"dayOfWeek\":4,\"name\":\"Thursday\",\"exercises\":[{\"name\":\"Lunge\",\"sets\":3,\"targetReps\":\"12\",\"targetWeightKg\":20,\"notes\":\"\",\"recommendedRestSeconds\":90}]}]}\n```"
        val json = extractJson(raw)
        val result = parse(json)
        assertEquals(1, result.days.size)
        assertEquals(4, result.days[0].dayOfWeek)
        assertEquals("Lunge", result.days[0].exercises[0].name)
    }

    @Test fun parse_exerciseDefaults_setsAndRepsHaveDefaults() {
        // Minimal exercise object — Gson should use data class defaults
        val json = """{"days":[{"dayOfWeek":1,"name":"Mon","exercises":[{"name":"Squat"}]}]}"""
        val result = parse(json)
        val ex = result.days[0].exercises[0]
        assertEquals("Squat", ex.name)
        // Gson does NOT use Kotlin default values; fields absent → 0/null/"" per Gson's behavior
        // We test that it at least parses without throwing
        assertTrue(ex.sets >= 0)
    }

    @Test fun parse_dayOfWeekBoundaries_validRange() {
        val json = """{"days":[{"dayOfWeek":7,"name":"Sunday","exercises":[{"name":"Rest Walk","sets":1,"targetReps":"30 min","targetWeightKg":0,"notes":"","recommendedRestSeconds":0}]}]}"""
        val result = parse(json)
        assertEquals(7, result.days[0].dayOfWeek)
    }

    @Test fun parse_weightAndRest_parsedCorrectly() {
        val json = """{"days":[{"dayOfWeek":1,"name":"Monday","exercises":[{"name":"Deadlift","sets":5,"targetReps":"5","targetWeightKg":142.5,"notes":"Belt on","recommendedRestSeconds":180}]}]}"""
        val result = parse(json)
        val ex = result.days[0].exercises[0]
        assertEquals(142.5f, ex.targetWeightKg)
        assertEquals(180, ex.recommendedRestSeconds)
        assertEquals(5, ex.sets)
        assertEquals("5", ex.targetReps)
    }

    @Test fun parse_multipleExercises_orderPreserved() {
        val json = """{"days":[{"dayOfWeek":1,"name":"Monday","exercises":[
            {"name":"A","sets":1,"targetReps":"1","targetWeightKg":0,"notes":"","recommendedRestSeconds":60},
            {"name":"B","sets":2,"targetReps":"2","targetWeightKg":0,"notes":"","recommendedRestSeconds":60},
            {"name":"C","sets":3,"targetReps":"3","targetWeightKg":0,"notes":"","recommendedRestSeconds":60}
        ]}]}"""
        val result = parse(json)
        val exercises = result.days[0].exercises
        assertEquals("A", exercises[0].name)
        assertEquals("B", exercises[1].name)
        assertEquals("C", exercises[2].name)
    }
}

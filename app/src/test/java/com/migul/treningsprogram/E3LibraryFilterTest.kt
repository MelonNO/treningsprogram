package com.migul.treningsprogram

import com.migul.treningsprogram.data.DbExerciseEntry
import com.migul.treningsprogram.ui.library.ExerciseLibraryFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the E3 exercise-library browse/search filter logic.
 * Pure JVM — no Android dependency.
 */
class E3LibraryFilterTest {

    private fun entry(
        id: String,
        name: String,
        primary: List<String> = emptyList(),
        secondary: List<String> = emptyList(),
        equipment: String? = null,
        level: String? = null,
        category: String? = null,
        images: List<String> = emptyList(),
        instructions: List<String> = emptyList()
    ) = DbExerciseEntry(
        id = id,
        name = name,
        normalizedName = name.lowercase(),
        tokenSet = name.lowercase().split(" ").toSet(),
        primaryMuscles = primary,
        secondaryMuscles = secondary,
        equipment = equipment,
        level = level,
        category = category,
        images = images,
        instructions = instructions
    )

    private val catalog = listOf(
        entry("bench", "Barbell Bench Press", primary = listOf("chest"),
            secondary = listOf("triceps", "shoulders"), equipment = "barbell"),
        entry("squat", "Barbell Squat", primary = listOf("quadriceps"),
            secondary = listOf("glutes"), equipment = "barbell"),
        entry("curl", "Dumbbell Bicep Curl", primary = listOf("biceps"),
            equipment = "dumbbell"),
        entry("pushup", "Push-Up", primary = listOf("chest"),
            secondary = listOf("triceps"), equipment = "body only"),
        // Entry with missing fields: no muscles, no equipment, no images, no instructions.
        entry("mystery", "Mystery Move")
    )

    @Test fun emptyQuery_returnsAll() {
        val result = ExerciseLibraryFilter.filter(catalog, query = "")
        assertEquals(catalog.size, result.size)
    }

    @Test fun nullQuery_returnsAll() {
        val result = ExerciseLibraryFilter.filter(catalog)
        assertEquals(catalog.size, result.size)
    }

    @Test fun search_matchesByName_caseInsensitive() {
        val result = ExerciseLibraryFilter.filter(catalog, query = "bench")
        assertEquals(1, result.size)
        assertEquals("Barbell Bench Press", result.first().name)
    }

    @Test fun search_partialAndCaseInsensitive() {
        val result = ExerciseLibraryFilter.filter(catalog, query = "BARBELL")
        assertEquals(2, result.size)
        assertTrue(result.all { it.name.contains("Barbell") })
    }

    @Test fun search_noMatch_returnsEmpty() {
        val result = ExerciseLibraryFilter.filter(catalog, query = "zzznomatch")
        assertTrue(result.isEmpty())
    }

    @Test fun filter_byMuscleGroup_primary() {
        val result = ExerciseLibraryFilter.filter(catalog, muscle = "chest")
        assertEquals(setOf("Barbell Bench Press", "Push-Up"), result.map { it.name }.toSet())
    }

    @Test fun filter_byMuscleGroup_matchesSecondary() {
        // "triceps" is only a secondary muscle on bench + push-up
        val result = ExerciseLibraryFilter.filter(catalog, muscle = "triceps")
        assertEquals(setOf("Barbell Bench Press", "Push-Up"), result.map { it.name }.toSet())
    }

    @Test fun filter_byMuscleGroup_caseInsensitive() {
        val result = ExerciseLibraryFilter.filter(catalog, muscle = "CHEST")
        assertEquals(2, result.size)
    }

    @Test fun filter_byEquipment() {
        val result = ExerciseLibraryFilter.filter(catalog, equipment = "barbell")
        assertEquals(setOf("Barbell Bench Press", "Barbell Squat"), result.map { it.name }.toSet())
    }

    @Test fun filter_byEquipment_caseInsensitive() {
        val result = ExerciseLibraryFilter.filter(catalog, equipment = "Dumbbell")
        assertEquals(1, result.size)
        assertEquals("Dumbbell Bicep Curl", result.first().name)
    }

    @Test fun filter_combined_queryMuscleEquipment() {
        val result = ExerciseLibraryFilter.filter(
            catalog, query = "barbell", muscle = "chest", equipment = "barbell"
        )
        assertEquals(1, result.size)
        assertEquals("Barbell Bench Press", result.first().name)
    }

    @Test fun filter_combined_noOverlap_returnsEmpty() {
        // dumbbell + chest don't co-occur in the catalog
        val result = ExerciseLibraryFilter.filter(catalog, muscle = "chest", equipment = "dumbbell")
        assertTrue(result.isEmpty())
    }

    @Test fun filter_handlesEntriesMissingFields_gracefully() {
        // The "Mystery Move" entry has no muscles/equipment — must not crash and must be
        // excluded by any muscle/equipment filter, but included by a name match.
        val byName = ExerciseLibraryFilter.filter(catalog, query = "mystery")
        assertEquals(1, byName.size)
        assertEquals("Mystery Move", byName.first().name)

        val byMuscle = ExerciseLibraryFilter.filter(catalog, muscle = "chest")
        assertTrue(byMuscle.none { it.id == "mystery" })

        val byEquip = ExerciseLibraryFilter.filter(catalog, equipment = "barbell")
        assertTrue(byEquip.none { it.id == "mystery" })
    }

    @Test fun filter_blankMuscleAndEquipment_ignored() {
        val result = ExerciseLibraryFilter.filter(catalog, muscle = "  ", equipment = "")
        assertEquals(catalog.size, result.size)
    }

    @Test fun results_sortedByName() {
        val result = ExerciseLibraryFilter.filter(catalog)
        val names = result.map { it.name }
        assertEquals(names.sortedBy { it.lowercase() }, names)
    }

    @Test fun muscleGroups_distinctSortedCapitalized() {
        val groups = ExerciseLibraryFilter.muscleGroups(catalog)
        // From primary + secondary across the catalog, deduped, capitalized.
        assertEquals(
            listOf("Biceps", "Chest", "Glutes", "Quadriceps", "Shoulders", "Triceps"),
            groups
        )
    }

    @Test fun equipmentOptions_distinctSorted_skipsNull() {
        val options = ExerciseLibraryFilter.equipmentOptions(catalog)
        // "Mystery Move" has null equipment and must be skipped.
        assertEquals(listOf("Barbell", "Body only", "Dumbbell"), options)
    }

    @Test fun emptyCatalog_returnsEmpty() {
        assertTrue(ExerciseLibraryFilter.filter(emptyList(), query = "anything").isEmpty())
        assertTrue(ExerciseLibraryFilter.muscleGroups(emptyList()).isEmpty())
        assertTrue(ExerciseLibraryFilter.equipmentOptions(emptyList()).isEmpty())
    }
}

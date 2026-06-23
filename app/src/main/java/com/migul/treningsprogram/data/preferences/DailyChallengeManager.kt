package com.migul.treningsprogram.data.preferences

import com.google.gson.Gson
import com.migul.treningsprogram.data.db.entity.WorkoutSet
import com.migul.treningsprogram.domain.model.DailyChallenge
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

private data class ChallengeTemplate(val id: String, val name: String, val description: String, val bonusXp: Int)
private data class DailyChallengesState(val date: String, val challenges: List<DailyChallenge>)  // 'date' field now holds ISO week key

@Singleton
class DailyChallengeManager @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val gson: Gson
) {
    companion object {
        private val ALL_TEMPLATES = listOf(
            ChallengeTemplate("complete_workout", "Commitment",    "Complete a workout this week",      100),
            ChallengeTemplate("sets_10",          "Volume Day",    "Log 10+ sets in one session",        75),
            ChallengeTemplate("sets_15",          "High Volume",   "Log 15+ sets in one session",       100),
            ChallengeTemplate("sets_20",          "Max Effort",    "Log 20+ sets in one session",       150),
            ChallengeTemplate("set_pr",           "Record Day",    "Set a personal record today",       150),
            ChallengeTemplate("chest_day",        "Push Day",      "Log a chest exercise",               50),
            ChallengeTemplate("back_day",         "Pull Day",      "Log a back exercise",                50),
            ChallengeTemplate("leg_day",          "Leg Day",       "Never skip leg day",                 75),
            ChallengeTemplate("arms_day",         "Pump Day",      "Log an arms exercise",               50),
            ChallengeTemplate("core_day",         "Core Day",      "Log a core exercise",                50),
            ChallengeTemplate("exercises_3",      "Variety Pack",  "Log 3+ different exercises",         75),
            ChallengeTemplate("exercises_5",      "Full Body",     "Log 5+ different exercises",        100),
        )
    }

    fun getTodayChallenges(): List<DailyChallenge> {
        val thisWeek = isoWeekKey()
        val stored = preferencesManager.dailyChallengesJson
        if (stored.isNotEmpty()) {
            runCatching {
                val state = gson.fromJson(stored, DailyChallengesState::class.java)
                if (state.date == thisWeek) return state.challenges
            }
        }
        val rng = Random(thisWeek.hashCode().toLong())
        val selected = ALL_TEMPLATES.shuffled(rng).take(3).map { t ->
            DailyChallenge(t.id, t.name, t.description, t.bonusXp)
        }
        saveChallenges(thisWeek, selected)
        return selected
    }

    fun completeChallenges(sets: List<WorkoutSet>, hasPr: Boolean): List<DailyChallenge> {
        val thisWeek = isoWeekKey()
        val current = getTodayChallenges()
        val muscleGroups = sets.map { it.muscleGroup }.toSet()
        val exerciseNames = sets.map { it.exerciseName }.toSet()

        val newlyCompleted = mutableListOf<DailyChallenge>()
        val updated = current.map { ch ->
            if (ch.isCompleted) return@map ch
            val done = when (ch.id) {
                "complete_workout" -> true
                "sets_10"          -> sets.size >= 10
                "sets_15"          -> sets.size >= 15
                "sets_20"          -> sets.size >= 20
                "set_pr"           -> hasPr
                "chest_day"        -> "Chest" in muscleGroups
                "back_day"         -> "Back" in muscleGroups
                "leg_day"          -> "Legs" in muscleGroups
                "arms_day"         -> "Arms" in muscleGroups
                "core_day"         -> "Core" in muscleGroups
                "exercises_3"      -> exerciseNames.size >= 3
                "exercises_5"      -> exerciseNames.size >= 5
                else               -> false
            }
            if (done) { val c = ch.copy(isCompleted = true); newlyCompleted.add(c); c } else ch
        }
        saveChallenges(thisWeek, updated)
        return newlyCompleted
    }

    private fun saveChallenges(weekKey: String, challenges: List<DailyChallenge>) {
        preferencesManager.dailyChallengesJson = gson.toJson(DailyChallengesState(weekKey, challenges))
    }
}

/**
 * Locale-independent ISO-week key (e.g. "2026-W26") scoping the weekly challenge rotation.
 *
 * Uses Locale.ROOT with explicit Monday-first / minimal-4-days-in-first-week rules so the key
 * — and the RNG seed derived from it — is identical regardless of the device locale, and always
 * uses Latin digits. For Monday-first / ISO locales this is byte-identical to the previous
 * default-locale "yyyy-'W'ww" formatting, so existing stored keys still match and challenges do
 * not re-roll on upgrade. (Same locale bug as autoGenWeekKey, on the separate challenge key.)
 */
fun isoWeekKey(date: Date = Date()): String =
    SimpleDateFormat("yyyy-'W'ww", Locale.ROOT).apply {
        calendar.firstDayOfWeek = Calendar.MONDAY
        calendar.minimalDaysInFirstWeek = 4
    }.format(date)

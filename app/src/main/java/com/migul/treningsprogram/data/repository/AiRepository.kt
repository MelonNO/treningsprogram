package com.migul.treningsprogram.data.repository

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.migul.treningsprogram.data.ExerciseDbResolver
import com.migul.treningsprogram.data.PromptLog
import com.migul.treningsprogram.data.RejectionLog
import com.migul.treningsprogram.data.ResolveHints
import com.migul.treningsprogram.data.api.ClaudeApiService
import com.migul.treningsprogram.data.api.model.ClaudeRequest
import com.migul.treningsprogram.data.db.entity.PlannedExercise
import com.migul.treningsprogram.data.db.entity.WorkoutSession
import com.migul.treningsprogram.domain.model.OnboardingQuestion
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

// Onboarding question JSON model
private data class OQJson(
    val id: String = "",
    val question: String = "",
    val type: String = "text",
    val options: List<String> = emptyList()
)
private data class OQsJson(val questions: List<OQJson> = emptyList())

// Top-level private classes so Gson can instantiate them without issues
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

data class GenerationResult(val exercises: List<PlannedExercise>, val attemptCount: Int, val rejectionReasons: List<String> = emptyList())

private data class ValidationResult(val accepted: Boolean, val reason: String = "")

@Singleton
class AiRepository @Inject constructor(
    private val claudeApi: ClaudeApiService,
    private val workoutRepository: WorkoutRepository,
    private val gson: Gson,
    private val resolver: ExerciseDbResolver,
    private val promptLog: PromptLog,
    private val rejectionLog: RejectionLog
) {
    companion object {
        const val MAX_GENERATION_ATTEMPTS = 3
    }

    suspend fun getOnboardingQuestions(
        goal: String,
        experience: String,
        daysPerWeek: Int,
        sessionDurationMinutes: Int,
        separateCardioDays: Boolean,
        equipment: List<String>
    ): Result<List<OnboardingQuestion>> = runCatching {
        val equipStr = if (equipment.isEmpty()) "Bodyweight only" else equipment.joinToString(", ")
        val cardioStr = if (separateCardioDays) "Yes — cardio on its own dedicated day" else "No — cardio can be combined"
        val prompt = """
You are a personal trainer conducting a brief onboarding interview for a new client.

ALREADY KNOWN — do NOT ask about any of these:
- Goal: $goal
- Experience: $experience
- Training days per week: $daysPerWeek
- Session duration: $sessionDurationMinutes minutes
- Separate cardio days: $cardioStr
- Available equipment: $equipStr

Generate exactly 4 SHORT, FOCUSED questions that dig into information NOT covered above.
Good topics: specific body-part focus, injury or movement restrictions, exercise dislikes, sleep/recovery habits, nutrition basics, sport or activity outside the gym.
BAD topics (already answered): how many days, how long, goal, experience, cardio preference, equipment.

Return ONLY valid JSON — no prose, no markdown fences:
{
  "questions": [
    {"id": "q1", "question": "...", "type": "text"},
    {"id": "q2", "question": "...", "type": "choice", "options": ["Option A", "Option B", "Option C"]}
  ]
}
type must be "text" for a free-form answer or "choice" for a single-select list.
        """.trimIndent()

        val responseText = claudeApi.sendMessage(
            ClaudeRequest(messages = listOf(ClaudeRequest.Message(content = prompt)))
        ).text()
        promptLog.add("onboarding_questions", prompt, responseText)
        val json = extractJson(responseText)
        val parsed = gson.fromJson(json, OQsJson::class.java)
        parsed.questions.map { OnboardingQuestion(it.id, it.question, it.type, it.options) }
    }

    private val variationThemes = listOf(
        "DUMBBELL-DOMINANT — prefer dumbbell variations over barbell where possible this week",
        "UNILATERAL FOCUS — favour single-arm rows, split squats, lunges, single-leg RDLs over bilateral equivalents",
        "POSTERIOR CHAIN EMPHASIS — prioritise hip hinges, rows, rear-delt, and hamstring work; keep pressing secondary",
        "COMPOUND ONLY — minimise isolation; every exercise should be multi-joint",
        "HIGH REP / TIME UNDER TENSION — emphasise 12–20 rep ranges and controlled tempo",
        "ANTAGONIST PAIRING — pair opposing muscle groups within sessions (e.g. chest+back, quads+hamstrings)",
        "MOVEMENT PATTERN VARIETY — include at least one hinge, one squat pattern, one carry or core-stability exercise across the week",
        "MACHINE & CABLE FOCUS — rely on machines and cables for isolation; use free weights for compounds only"
    )

    private val splitSuggestions = mapOf(
        2 to listOf("Full Body A/B"),
        3 to listOf("Full Body A/B/C", "Push/Pull/Full Body"),
        4 to listOf("Upper/Lower ×2", "Push/Pull/Legs+Upper", "Full Body ×4"),
        5 to listOf("Upper/Lower/Push/Pull/Full Body", "Push/Pull/Legs + 2×Upper", "Body-part with overlap"),
        6 to listOf("Push/Pull/Legs ×2", "Upper/Lower ×3"),
        7 to listOf("Push/Pull/Legs ×2 + rest day", "Body-part ×6 + rest")
    )

    suspend fun generateAdaptedProgram(
        daysPerWeek: Int,
        goal: String,
        experience: String,
        sessionDurationMinutes: Int = 60,
        equipment: List<String> = emptyList(),
        equipmentNotes: String = "",
        separateCardioDays: Boolean = false,
        injuries: String = "",
        priorityMuscles: String = "",
        dislikedExercises: String = "",
        onboardingContext: String = "",
        onProgress: (String) -> Unit = {}
    ): Result<GenerationResult> = runCatching {
        val sessions = workoutRepository.getRecentSessions(12)
        val (history, recentExercises) = buildSessionHistory(sessions)
        val previousPlan = buildPreviousPlanContext()
        val variationTheme = variationThemes.random()
        val splitSuggestion = splitSuggestions[daysPerWeek]?.random()
            ?: splitSuggestions.entries.minByOrNull { kotlin.math.abs(it.key - daysPerWeek) }?.value?.random() ?: ""
        val rejectionReasons = mutableListOf<String>()

        for (attempt in 1..MAX_GENERATION_ATTEMPTS) {
            if (attempt == 1) {
                onProgress("Generating your plan…")
            } else {
                onProgress("Attempt $attempt of $MAX_GENERATION_ATTEMPTS: refining plan…")
            }
            val prompt = buildPrompt(
                history, daysPerWeek, goal, experience,
                sessionDurationMinutes, equipment, equipmentNotes,
                separateCardioDays, rejectionReasons.lastOrNull() ?: "",
                injuries, priorityMuscles, dislikedExercises, onboardingContext,
                previousPlan, recentExercises, variationTheme, splitSuggestion
            )
            val responseText = claudeApi.sendMessage(
                ClaudeRequest(messages = listOf(ClaudeRequest.Message(content = prompt)))
            ).text()
            promptLog.add("generate_attempt_$attempt", prompt, responseText)
            val cleanJson = extractJson(responseText)
            val exercises = parseProgram(cleanJson)

            onProgress("Reviewing plan for quality…")
            val validation = validateProgram(cleanJson, daysPerWeek, goal, experience)
            if (validation.accepted) {
                val logAttempts = rejectionReasons.mapIndexed { i, r ->
                    RejectionLog.Attempt(i + 1, r, false)
                }
                rejectionLog.addSession(logAttempts, succeeded = true)
                return@runCatching GenerationResult(exercises, attempt, rejectionReasons.toList())
            }
            rejectionReasons.add(validation.reason)
            onProgress("Attempt $attempt rejected: ${validation.reason}")
            if (attempt == MAX_GENERATION_ATTEMPTS) {
                val logAttempts = rejectionReasons.mapIndexed { i, r ->
                    RejectionLog.Attempt(i + 1, r, i == rejectionReasons.lastIndex)
                }
                rejectionLog.addSession(logAttempts, succeeded = false)
                val reasons = rejectionReasons.mapIndexed { i, r -> "Attempt ${i + 1}: $r" }.joinToString("\n")
                throw IllegalStateException("Program rejected after $MAX_GENERATION_ATTEMPTS attempts.\n$reasons")
            }
        }
        throw IllegalStateException("Unexpected state")
    }

    private suspend fun validateProgram(
        planJson: String,
        daysPerWeek: Int,
        goal: String,
        experience: String
    ): ValidationResult {
        val prompt = """
You are a sports science peer reviewer. Evaluate the following AI-generated weekly training program.

Goal: $goal | Experience: $experience | Training days: $daysPerWeek

PROGRAM JSON:
$planJson

Check ALL of the following. Reject only on genuine structural violations — do not penalise reasonable coaching choices or exercise variation.

1. DAYS: The plan contains exactly $daysPerWeek training days.
2. RECOVERY: No primary muscle group (chest, back, quads, hamstrings/glutes, shoulders) is trained on consecutive days. Heavy leg days need ≥48 h between them.
3. ORDER: Within each session, compound exercises appear before isolation exercises for the same muscle group.
4. GOAL ALIGNMENT — rep ranges must broadly match the goal:
   - Strength: primary compounds 2–6 reps; accessories 6–10 reps. Do NOT reject for 8–10 rep accessory work.
   - Hypertrophy: 6–12 reps, rest ≤120 s. A plan is invalid only if rest values consistently exceed 120 s.
   - Endurance: 15–30 reps, 30–60 s rest, includes 2–3 cardio sessions.
   - Weight Loss: 10–20 reps, 60–90 s rest, includes ≥2 cardio sessions.
5. VOLUME: Every major muscle group trained at least once (not zero). No muscle group assigned an unreasonably excessive number of sets (>25/week).
6. STRUCTURE: No obvious errors (e.g. all sessions are chest-only, cardio placed the day before heavy legs).
7. EXERCISE COUNT: Beginner ≤5/session, Intermediate ≤7/session, Advanced ≤8/session.

Respond with ONLY valid JSON — no prose, no markdown fences:
{"accepted": true}
OR
{"accepted": false, "reason": "one or two sentences on the most critical issue only"}
        """.trimIndent()

        return runCatching {
            val responseText = claudeApi.sendMessage(
                ClaudeRequest(messages = listOf(ClaudeRequest.Message(content = prompt)))
            ).text()
            promptLog.add("validate", prompt, responseText)
            val json = extractJson(responseText)
            val obj = gson.fromJson(json, com.google.gson.JsonObject::class.java)
            val accepted = obj.get("accepted")?.asBoolean ?: false
            val reason = obj.get("reason")?.asString ?: ""
            ValidationResult(accepted, reason)
        }.getOrElse {
            ValidationResult(accepted = true)
        }
    }

    private suspend fun buildSessionHistory(sessions: List<WorkoutSession>): Pair<String, Set<String>> {
        if (sessions.isEmpty()) return Pair("No previous workout history — this is a new user.", emptySet())
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val twoWeeksAgo = System.currentTimeMillis() - 14L * 24 * 60 * 60 * 1000

        data class ExerciseEntry(val dateMs: Long, val maxWeight: Float, val totalReps: Int)
        val exerciseTrends = mutableMapOf<String, MutableList<ExerciseEntry>>()
        val recentExercises = mutableSetOf<String>()

        val sessionDetails = buildString {
            sessions.forEach { session ->
                val sets = workoutRepository.getSetsForSessionOnce(session.id).filter { !it.isWarmup }
                appendLine("Session ${fmt.format(Date(session.dateMs))} (${session.durationMinutes} min):")
                sets.groupBy { it.exerciseName }.forEach { (exercise, exerciseSets) ->
                    val detail = exerciseSets.joinToString(", ") { "${it.reps}×${it.weightKg}kg" }
                    appendLine("  $exercise: $detail")
                    exerciseTrends.getOrPut(exercise) { mutableListOf() }.add(
                        ExerciseEntry(session.dateMs, exerciseSets.maxOf { it.weightKg }, exerciseSets.sumOf { it.reps })
                    )
                    if (session.dateMs >= twoWeeksAgo) recentExercises.add(exercise)
                }
            }
        }

        val trends = buildString {
            appendLine("EXERCISE TRENDS (for progressive overload decisions):")
            exerciseTrends.entries
                .filter { it.value.size >= 2 }
                .sortedByDescending { it.value.size }
                .take(12)
                .forEach { (exercise, entries) ->
                    val sorted = entries.sortedBy { it.dateMs }
                    val first = sorted.first()
                    val last = sorted.last()
                    val weightDelta = last.maxWeight - first.maxWeight
                    val trend = when {
                        weightDelta > 2.5f -> "PROGRESSING (+${weightDelta}kg over ${sorted.size} sessions)"
                        weightDelta < -2.5f -> "REGRESSING (${weightDelta}kg)"
                        else -> "PLATEAUED (${sorted.size} sessions at ~${last.maxWeight}kg)"
                    }
                    appendLine("  $exercise: last ${last.maxWeight}kg — $trend")
                }
        }

        return Pair("$sessionDetails\n$trends", recentExercises)
    }

    private suspend fun buildPreviousPlanContext(): String {
        val weekStart = workoutRepository.getLatestPlanWeekStart() ?: return ""
        val all = workoutRepository.getAllPlannedOnce()
        val forWeek = all.filter { it.weekStart == weekStart }
        if (forWeek.isEmpty()) return ""
        val dayNames = mapOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun")
        return buildString {
            appendLine("LAST GENERATED PROGRAM (exercises used — vary these in the new plan):")
            forWeek.groupBy { it.dayOfWeek }.toSortedMap().forEach { (day, exList) ->
                val label = dayNames[day] ?: "Day $day"
                val names = exList.sortedBy { it.orderInDay }.joinToString(", ") { it.exerciseName }
                appendLine("  $label: $names")
            }
        }.trim()
    }

    private fun buildPrompt(
        history: String,
        daysPerWeek: Int,
        goal: String,
        experience: String,
        sessionDurationMinutes: Int = 60,
        equipment: List<String> = emptyList(),
        equipmentNotes: String = "",
        separateCardioDays: Boolean = false,
        previousRejectionReason: String = "",
        injuries: String = "",
        priorityMuscles: String = "",
        dislikedExercises: String = "",
        onboardingContext: String = "",
        previousPlan: String = "",
        recentExercises: Set<String> = emptySet(),
        variationTheme: String = "",
        splitSuggestion: String = ""
    ): String {
        val goalLower = goal.lowercase()

        val equipLower = equipment.map { it.lowercase() }
        val hasCable    = equipLower.any { "cable" in it }
        val hasBarbell  = equipLower.any { "barbell" in it || "olympic bar" in it || "ez bar" in it }
        val hasRack     = equipLower.any { "squat rack" in it || "power rack" in it || "rack" in it || "cage" in it }
        val hasMachines = equipLower.any { "machine" in it || "leg press" in it || "smith" in it }
        val hasPullBar  = equipLower.any { "pull" in it && ("bar" in it || "up" in it) } || hasBarbell
        val hasBands    = equipLower.any { "band" in it || "resistance" in it }
        val hasBench    = equipLower.any { "bench" in it }

        val equipmentStr = if (equipment.isEmpty()) "Bodyweight only — no equipment" else equipment.joinToString(", ")

        // What the user physically cannot do — hard block list derived from equipment
        val forbidden = buildList {
            if (!hasCable) addAll(listOf("Tricep Pushdown", "Rope Pushdown", "Cable Fly", "Cable Row",
                "Cable Curl", "Cable Tricep Extension", "Cable Lateral Raise", "Lat Pulldown", "Seated Cable Row"))
            if (!hasCable && !hasBands) add("Face Pull")
            if (!hasBands) addAll(listOf("Resistance Band Row", "Band Row", "Band Pull-down",
                "Band Face Pull", "Resistance Band Curl", "Band Lateral Raise", "Banded Squat",
                "Banded Glute Bridge", "Band Overhead Press", "Band Tricep Extension"))
            if (!hasMachines) addAll(listOf("Leg Press", "Leg Curl", "Leg Extension",
                "Smith Machine Squat", "Hack Squat Machine", "Chest Press Machine", "Shoulder Press Machine"))
            if (!hasBarbell) addAll(listOf("Barbell Row", "Barbell Curl", "Barbell Squat", "Barbell Deadlift",
                "Barbell Overhead Press", "Barbell Hip Thrust"))
            if (hasBarbell && !hasRack) addAll(listOf(
                "Barbell Squat", "Back Squat", "Front Squat", "High Bar Squat", "Low Bar Squat",
                "Rack Pull", "Pin Pull", "Box Squat", "Overhead Squat",
                "Barbell Lunge (rack)", "Barbell Step-up (rack)"
            ))
            if (!hasPullBar) add("Pull-up")
            if (!hasBench) add("Bench Press")
        }.distinct()

        val rackNote = if (hasBarbell && !hasRack)
            "\n\nIMPORTANT: The user has a barbell but NO squat rack. Exercises requiring a rack (back squat, front squat, rack pull, box squat, overhead squat) are STRICTLY FORBIDDEN. Barbell exercises that don't need a rack (deadlift, row, bench press on floor/low bench, hip thrust, curl, overhead press from floor) are allowed."
        else ""

        val cardioInstruction = if (separateCardioDays)
            "Cardio must be on its OWN dedicated day — never combined with strength work."
        else
            "Cardio may follow an upper-body session. Never schedule it the day before or after heavy legs."

        val rejectionBlock = if (previousRejectionReason.isNotBlank()) """
══════════════════════════════════════════
PREVIOUS PLAN REJECTED — FIX THIS FIRST
══════════════════════════════════════════
Reason: "$previousRejectionReason"
Your new plan must directly address this. A plan with the same flaw will be rejected again.

""" else ""

        // Merge recent logged exercises + last generated plan exercises into one blacklist
        val planExercises = if (previousPlan.isNotBlank()) {
            Regex(":\\s*(.+)").findAll(previousPlan).flatMap { m ->
                m.groupValues[1].split(",").map { it.trim() }
            }.filter { it.isNotBlank() }.toSet()
        } else emptySet()
        val blacklist = (recentExercises + planExercises).toSortedSet()

        val blacklistBlock = if (blacklist.isNotEmpty()) """
══════════════════════════════════════════
EXERCISE BLACKLIST — DO NOT USE THESE
══════════════════════════════════════════
The following exercises were already used in recent sessions or last week's plan. You MUST choose different exercises for the same muscle groups this week. This is the single most important constraint in this prompt.
${blacklist.joinToString("\n") { "  • $it" }}

If equipment genuinely leaves only one option for a muscle group, you may reuse that exercise once but must note "only available option" in the notes field.
""" else ""

        val previousPlanBlock = if (previousPlan.isNotBlank()) """
──────────────────────────────────────────
For reference, last week's plan was:
$previousPlan
──────────────────────────────────────────
""" else ""

        val themeBlock = if (variationTheme.isNotBlank()) """
══════════════════════════════════════════
THIS WEEK'S VARIATION DIRECTIVE
══════════════════════════════════════════
$variationTheme
Apply this to break out of default exercise patterns. This is mandatory — if you keep defaulting to the same canonical exercises (e.g. bench press, pull-ups, barbell squat every single week), you are failing this directive.
""" else ""

        val splitBlock = if (splitSuggestion.isNotBlank()) """
Consider using this split structure this week: $splitSuggestion
(You may deviate if another structure better serves the goal and recovery, but justify variation.)
""" else ""

        return """
You are an expert strength & conditioning coach. Design a $daysPerWeek-day weekly training program tailored to the user below.

CRITICAL: Do NOT follow gym-culture day conventions (e.g., chest on Monday, back on Tuesday, arms on Friday). Assign muscle groups to days based purely on recovery logic. A good coach rotates exercises every single week — the same muscle can be trained with completely different exercises each week.
$rejectionBlock$blacklistBlock$themeBlock
══════════════════════════════════════════
WORKOUT HISTORY
══════════════════════════════════════════
$history

══════════════════════════════════════════
USER PROFILE
══════════════════════════════════════════
Goal: $goal
Experience: $experience
Days/week: $daysPerWeek
Session target: $sessionDurationMinutes min
Available equipment: $equipmentStr
${if (injuries.isNotBlank()) "Injuries/limitations: $injuries" else ""}
${if (priorityMuscles.isNotBlank()) "Priority muscle groups: $priorityMuscles" else ""}
${if (dislikedExercises.isNotBlank()) "Exclude these exercises: $dislikedExercises" else ""}
${if (onboardingContext.isNotBlank()) "Additional context: $onboardingContext" else ""}
${if (equipmentNotes.isNotBlank()) "Equipment notes: $equipmentNotes" else ""}

══════════════════════════════════════════
FORBIDDEN EXERCISES (equipment not available)
══════════════════════════════════════════
${if (forbidden.isEmpty()) "None — all exercise types are available." else "Do NOT prescribe any of these: ${forbidden.joinToString(", ")}"}$rackNote

══════════════════════════════════════════
GOAL PRINCIPLES
══════════════════════════════════════════
${when {
    goalLower.contains("strength") -> """
Goal: build maximal strength in compound lifts.
- Prioritise low-rep, high-load work on the big lifts (squat, deadlift, press, row).
- Rep ranges: 2–6 for primary compounds, 6–10 for accessories.
- Rest: 3–5 min between heavy sets, 2–3 min for accessories.
- Volume: lower (8–12 sets/muscle/week) — intensity beats volume here.
- Progressive overload: add weight (2.5–5 kg) when all reps are completed with ≥2 RIR.
- Hypertrophy work is secondary — keep isolation low.
- Leave 1–2 RIR on every working set. Never train to failure on compounds.""".trimIndent()

    goalLower.contains("hypertrophy") -> """
Goal: maximise muscle growth.
- Rep ranges: 6–12 (sweet spot 8–12), moderate load (~65–80% 1RM), 1–3 RIR.
- Rest: 60–120 s between sets — do not exceed 120 s.
- Train each muscle group at least twice per week.
- Weekly volume: 12–20 sets per muscle group across both sessions.
- Order: 1–2 compounds first per session, then isolation work.
- Progressive overload: add weight once the top of the rep range is reached with ≥2 RIR.""".trimIndent()

    goalLower.contains("endurance") -> """
Goal: muscular and cardiovascular endurance.
- Resistance work: 2–3 sets, 15–30 reps, light load (~40–60% 1RM), 30–60 s rest.
- Cardio is the priority — include 2–3 dedicated cardio sessions per week.
- Choose exercises that complement aerobic capacity: circuits, supersets, bodyweight work welcome.
- Progressive overload: add reps or sets before increasing load.
- Volume per muscle: lower (6–10 sets/week) — cardio takes the majority of the session.""".trimIndent()

    goalLower.contains("weight") || goalLower.contains("loss") -> """
Goal: fat loss and body recomposition.
- Prioritise compound multi-joint movements (highest calorie burn and muscle retention).
- Rep ranges: 10–20, rest 60–90 s (keep heart rate elevated).
- Include 2 cardio sessions per week — mix HIIT and steady-state.
- Supersets of non-competing muscle groups are encouraged to increase density.
- Avoid extremely heavy low-rep work — form and metabolic stress matter more than 1RM.""".trimIndent()

    else -> """
Goal: general health and fitness.
- Balanced mix of compound and isolation work.
- Rep ranges: 8–15, rest 90–120 s.
- Hit all major muscle groups across the week.""".trimIndent()
}}

══════════════════════════════════════════
WEEKLY STRUCTURE
══════════════════════════════════════════
Organise $daysPerWeek days to distribute muscle group stimulus optimally for the stated goal.
- Each major muscle group (chest, back, quads, hamstrings/glutes, shoulders) should be trained at least once, ideally twice per week.
- Never train the same primary muscle group on consecutive days.
- Heavy leg sessions (squat, deadlift) need at least 48 h before the next leg session.
- $cardioInstruction
- Space the days evenly across the week where possible.
$splitBlock$previousPlanBlock

══════════════════════════════════════════
SESSION DESIGN RULES
══════════════════════════════════════════
- Order: compound exercises before isolation for the same muscle group.
- Start each session with the most demanding movement.
- Exercise count per session: Beginner ≤ 5, Intermediate ≤ 7, Advanced ≤ 8.
- Sets per exercise: Beginner 2–3, Intermediate 3–4, Advanced 3–5.
${when {
    experience.lowercase().contains("beginner") -> "- Stick to fundamental, easy-to-learn movements. Avoid complex or high-skill exercises."
    experience.lowercase().contains("intermediate") -> "- Include some variation and unilateral work. Technique is established."
    else -> "- Advanced techniques (drop sets, rest-pause, tempo work) are appropriate where beneficial."
}}
${if (injuries.isNotBlank()) """
- Injury constraint: avoid any exercise that loads, compresses, or aggravates the reported injury.
- Where safe, include 1–2 low-load rehab or prehab exercises targeting the injured area.""" else ""}
${if (priorityMuscles.isNotBlank()) """
- Priority muscles ($priorityMuscles): allocate at least 2 extra sets compared to non-priority groups. Train them twice per week.""" else ""}

══════════════════════════════════════════
PROGRESSIVE OVERLOAD
══════════════════════════════════════════
Use the workout history above to set weights and reps intelligently:
- Progressing (weight increasing across sessions): add 2.5 kg (intermediate/advanced) or 5 kg (beginner) to targetWeightKg.
- Plateaued (3+ sessions at same weight): increase targetReps to the top of the range, or note a technique cue to break the plateau.
- Regressing: reduce weight 5–10% and note "deload — rebuild form".
- No history: set a conservative baseline (~55–65% estimated 1RM) and note "establish baseline".

══════════════════════════════════════════
CARDIO
══════════════════════════════════════════
For cardio exercises, use these name conventions so the app can identify them: Easy Jog, Outdoor Run, Tempo Run, Interval Run.
Cardio JSON fields: sets=1, targetReps = duration or distance only (e.g. "30 min", "5 km", "6×400m"), targetWeightKg=0, recommendedRestSeconds=60.

══════════════════════════════════════════
OUTPUT — valid JSON only, no prose, no markdown fences
══════════════════════════════════════════
{
  "days": [
    {
      "dayOfWeek": 1,
      "name": "Day name",
      "exercises": [
        {
          "name": "Exercise Name",
          "sets": 4,
          "targetReps": "8-10",
          "targetWeightKg": 80.0,
          "notes": "Brief coaching cue",
          "recommendedRestSeconds": 120
        }
      ]
    }
  ]
}

dayOfWeek: 1=Monday … 7=Sunday. Output exactly $daysPerWeek days, spaced optimally across the week.
        """.trimIndent()
    }

    suspend fun generateSingleDayProgram(
        dayOfWeek: Int,
        equipment: List<String>,
        equipmentNotes: String = "",
        goal: String,
        experience: String,
        sessionDurationMinutes: Int,
        existingWeekPlan: List<PlannedExercise>,
        injuries: String = "",
        priorityMuscles: String = "",
        dislikedExercises: String = "",
        muscleFocus: String = "",
        onProgress: (String) -> Unit = {}
    ): Result<List<PlannedExercise>> = runCatching {
        val weekStart = thisMonday()
        val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        val dayName = dayNames.getOrElse(dayOfWeek - 1) { "Day $dayOfWeek" }

        val weekContext = existingWeekPlan.filter { it.dayOfWeek != dayOfWeek }
            .groupBy { it.dayOfWeek }
            .entries.sortedBy { it.key }
            .joinToString("\n") { (day, exs) ->
                val dname = dayNames.getOrElse(day - 1) { "Day $day" }
                "  $dname: ${exs.joinToString(", ") { it.exerciseName }}"
            }
            .ifBlank { "  (No other days scheduled)" }

        val equipStr = if (equipment.isEmpty()) "Bodyweight only — no equipment" else equipment.joinToString(", ")

        onProgress("Generating $dayName exercises…")

        val repRange = when {
            goal.lowercase().contains("strength") -> "3–6 reps (compounds), 6–10 reps (accessories)"
            goal.lowercase().contains("endurance") -> "15–25 reps, shorter rest"
            goal.lowercase().contains("loss") || goal.lowercase().contains("weight") -> "10–20 reps, moderate rest"
            else -> "8–12 reps (hypertrophy)"
        }

        val prompt = buildString {
            appendLine("You are an expert personal trainer. Generate a single training day workout for $dayName.")
            appendLine()
            appendLine("GOAL: $goal")
            appendLine("EXPERIENCE: $experience")
            appendLine("SESSION DURATION: $sessionDurationMinutes minutes")
            appendLine("TARGET REP RANGE: $repRange")
            appendLine("AVAILABLE EQUIPMENT: $equipStr")
            if (equipmentNotes.isNotBlank()) appendLine("EQUIPMENT NOTES: $equipmentNotes")
            appendLine()
            if (injuries.isNotBlank()) {
                appendLine("INJURIES AND LIMITATIONS (HARD CONSTRAINTS):")
                appendLine("- Do NOT program any exercise that aggravates: $injuries")
                appendLine("- Include 1-2 light rehab/strengthening accessories where appropriate")
                appendLine()
            }
            if (priorityMuscles.isNotBlank()) {
                appendLine("PRIORITY MUSCLE GROUPS: $priorityMuscles — add extra volume for these")
                appendLine()
            }
            if (dislikedExercises.isNotBlank()) {
                appendLine("EXERCISES TO EXCLUDE (NEVER include): $dislikedExercises")
                appendLine()
            }
            appendLine("REST OF THE WEEK (already scheduled — avoid training the SAME primary muscle group on immediately adjacent days):")
            appendLine(weekContext)
            appendLine()
            if (muscleFocus.isNotBlank() && muscleFocus != "Full body") {
                appendLine("MUSCLE FOCUS FOR THIS DAY: $muscleFocus")
                appendLine("All exercises must primarily target $muscleFocus (complementary muscles are fine as secondary). Do not spread across unrelated muscle groups.")
                appendLine()
            } else if (muscleFocus == "Full body") {
                appendLine("MUSCLE FOCUS FOR THIS DAY: Full body — include at least one exercise for each of: push, pull, legs, core.")
                appendLine()
            }
            appendLine("INSTRUCTIONS:")
            appendLine("- Generate 4–7 exercises only using the listed equipment")
            appendLine("- Start with compound movements, finish with isolation")
            appendLine("- Check the rest of week context and avoid muscle group conflicts on adjacent days")
            appendLine("- Set/rep targets must match the goal's rep range above")
            appendLine()
            append("Return ONLY valid JSON, no prose, no markdown fences:")
            appendLine("""{"days":[{"dayOfWeek":$dayOfWeek,"name":"$dayName","exercises":[{"name":"Exercise Name","sets":3,"targetReps":"8-12","targetWeightKg":0,"notes":"coaching cue","recommendedRestSeconds":90}]}]}""")
        }

        val responseText = claudeApi.sendMessage(
            ClaudeRequest(messages = listOf(ClaudeRequest.Message(content = prompt)))
        ).text()
        promptLog.add("single_day_${dayName.lowercase()}", prompt, responseText)

        val cleanJson = extractJson(responseText)
        val exercises = parseProgram(cleanJson).filter { it.dayOfWeek == dayOfWeek }
        if (exercises.isEmpty()) throw IllegalStateException("No exercises returned for $dayName")
        // Re-stamp correct weekStart (parseProgram calls thisMonday() internally)
        exercises.map { it.copy(weekStart = weekStart) }
    }

    private fun parseProgram(cleanJson: String): List<PlannedExercise> {
        val weekStart = thisMonday()
        val program = gson.fromJson(cleanJson, ProgramJson::class.java)
        val now = System.currentTimeMillis()
        return (program.days ?: emptyList()).flatMap { day ->
            (day.exercises ?: emptyList()).mapIndexed { index, ex ->
                val resolveResult = resolver.resolve(ex.name, ResolveHints())
                PlannedExercise(
                    weekStart = weekStart,
                    dayOfWeek = day.dayOfWeek,
                    orderInDay = index,
                    exerciseName = ex.name,
                    sets = ex.sets,
                    targetReps = ex.targetReps,
                    targetWeightKg = ex.targetWeightKg,
                    notes = ex.notes,
                    recommendedRestSeconds = ex.recommendedRestSeconds.coerceAtMost(180),
                    exerciseDbId = resolveResult?.dbId,
                    matchConfidence = resolveResult?.confidence ?: -1f,
                    matchSource = resolveResult?.source?.name?.lowercase() ?: "none",
                    resolvedAt = now
                )
            }
        }
    }

    private fun extractJson(text: String): String {
        val fenceMatch = Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(text)
        if (fenceMatch != null) return fenceMatch.groupValues[1].trim()
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start != -1 && end > start) return text.substring(start, end + 1)
        throw IllegalStateException("No JSON found in AI response")
    }
}

package com.migul.treningsprogram.data.repository

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.migul.treningsprogram.data.api.ClaudeApiService
import com.migul.treningsprogram.data.api.model.ClaudeRequest
import com.migul.treningsprogram.data.db.entity.PlannedExercise
import com.migul.treningsprogram.data.db.entity.WorkoutSession
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

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

data class GenerationResult(val exercises: List<PlannedExercise>, val attemptCount: Int)

private data class ValidationResult(val accepted: Boolean, val reason: String = "")

@Singleton
class AiRepository @Inject constructor(
    private val claudeApi: ClaudeApiService,
    private val workoutRepository: WorkoutRepository,
    private val gson: Gson
) {
    companion object {
        const val MAX_GENERATION_ATTEMPTS = 3
    }

    suspend fun generateAdaptedProgram(
        daysPerWeek: Int,
        goal: String,
        experience: String,
        sessionDurationMinutes: Int = 60,
        equipment: List<String> = emptyList(),
        equipmentNotes: String = "",
        separateCardioDays: Boolean = false
    ): Result<GenerationResult> = runCatching {
        val sessions = workoutRepository.getRecentSessions(12)
        val history = buildSessionHistory(sessions)
        var lastRejectionReason = ""

        for (attempt in 1..MAX_GENERATION_ATTEMPTS) {
            val prompt = buildPrompt(
                history, daysPerWeek, goal, experience,
                sessionDurationMinutes, equipment, equipmentNotes,
                separateCardioDays, lastRejectionReason
            )
            val responseText = claudeApi.sendMessage(
                ClaudeRequest(messages = listOf(ClaudeRequest.Message(content = prompt)))
            ).text()
            val cleanJson = extractJson(responseText)
            val exercises = parseProgram(cleanJson)

            val validation = validateProgram(cleanJson, daysPerWeek, goal, experience)
            if (validation.accepted) {
                return@runCatching GenerationResult(exercises, attempt)
            }
            lastRejectionReason = validation.reason
            if (attempt == MAX_GENERATION_ATTEMPTS) {
                throw IllegalStateException(
                    "Program rejected after $MAX_GENERATION_ATTEMPTS attempts. Last issue: $lastRejectionReason"
                )
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
You are a sports science peer reviewer. Evaluate the following AI-generated weekly workout program for scientific validity.

Goal: $goal | Experience: $experience | Training days: $daysPerWeek

PROGRAM JSON:
$planJson

Assess ALL of the following:
1. The plan contains exactly $daysPerWeek training days.
2. No muscle group (chest, back, legs, shoulders) is trained on consecutive days without at least 48 h rest.
3. Within each session, compound exercises appear before isolation exercises for the same muscle group.
4. Rep and set ranges match the stated goal (e.g. strength = low reps/high weight, hypertrophy = moderate reps).
5. Weekly volume per muscle group is within reasonable bounds (not zero for a primary muscle, not excessively high).
6. There are no obvious structural errors (e.g. only upper body days all week, cardio placed after heavy legs).

Respond with ONLY valid JSON — no prose, no markdown fences:
{"accepted": true}
OR
{"accepted": false, "reason": "one concise sentence describing the most critical issue"}
        """.trimIndent()

        return runCatching {
            val responseText = claudeApi.sendMessage(
                ClaudeRequest(messages = listOf(ClaudeRequest.Message(content = prompt)))
            ).text()
            val json = extractJson(responseText)
            val obj = gson.fromJson(json, com.google.gson.JsonObject::class.java)
            val accepted = obj.get("accepted")?.asBoolean ?: false
            val reason = obj.get("reason")?.asString ?: ""
            ValidationResult(accepted, reason)
        }.getOrElse {
            ValidationResult(accepted = true)
        }
    }

    private suspend fun buildSessionHistory(sessions: List<WorkoutSession>): String {
        if (sessions.isEmpty()) return "No previous workout history — this is a new user."
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        // Collect all sets grouped by exercise across all sessions for trend analysis
        data class ExerciseEntry(val dateMs: Long, val maxWeight: Float, val totalReps: Int)
        val exerciseTrends = mutableMapOf<String, MutableList<ExerciseEntry>>()

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
                }
            }
        }

        // Build trend summary for the AI
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

        return "$sessionDetails\n$trends"
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
        previousRejectionReason: String = ""
    ): String {
        val goalLower = goal.lowercase()

        val goalParams = when {
            goalLower.contains("strength") -> """
STRENGTH parameters (apply strictly):
  - Sets: 4-6 | Reps: 2-6 | Intensity: ~80-95% 1RM | RIR: 1-2
  - Rest (primary compounds): 240-300s | Rest (accessory): 150-180s | Rest (isolation): 90-120s
  - Session: 2-3 heavy compounds FIRST, then 2-3 accessory lifts. Limit isolation work.
  - Progressive overload: when all reps are completed with RIR ≥2, increase weight by 2.5-5kg next session
  - Weekly sets per muscle: lower end of volume targets (8-12 sets) — quality beats quantity""".trimIndent()

            goalLower.contains("hypertrophy") -> """
HYPERTROPHY parameters (apply strictly):
  - Sets: 3-5 | Reps: 6-15 (sweet spot 8-12) | Intensity: ~65-80% 1RM | RIR: 1-3
  - Rest (compounds): 120-180s | Rest (isolation): 60-90s
  - CRITICAL: Every muscle group MUST be trained at least 2× per week — protein synthesis peaks at 48h
  - Session: 1-2 heavy compounds first, then 2-4 isolation/machine exercises
  - Progressive overload: add weight when you reach the TOP of the rep range at RIR ≥2
  - Weekly sets per muscle: mid-to-upper range (12-20 sets) distributed across 2 sessions""".trimIndent()

            goalLower.contains("endurance") -> """
ENDURANCE parameters (apply strictly):
  - Sets: 2-3 | Reps: 15-30 | Intensity: ~40-60% 1RM | RIR: 3-5
  - Rest: 45-60s (keep rest short to build aerobic capacity)
  - Include significant cardio: 2-3 cardio sessions per week
  - Progressive overload: add reps or sets before increasing weight
  - Weekly sets per muscle: lower volume (6-10 sets) — cardio takes priority""".trimIndent()

            goalLower.contains("weight") || goalLower.contains("loss") -> """
WEIGHT LOSS parameters (apply strictly):
  - Sets: 3-4 | Reps: 10-20 | Rest: 60-90s (metabolic stress is the goal)
  - Emphasise compound multi-joint movements — they burn the most calories
  - Include 2 cardio sessions per week (mix of HIIT and steady-state)
  - Superset non-competing muscle groups where session time allows
  - Progressive overload: maintain weight, prioritise form and rep quality""".trimIndent()

            else -> """
GENERAL FITNESS parameters:
  - Sets: 3-4 | Reps: 8-15 | Rest: 90-120s
  - Mix of compound and isolation exercises""".trimIndent()
        }

        val splitGuidance = when {
            daysPerWeek <= 2 -> """
TRAINING SPLIT — FULL BODY A + FULL BODY B (2 days):
  Schedule: Mon (A) + Thu (B) — minimum 72h between sessions.

  FULL BODY A — MUST contain ALL of:
    Push (chest): Bench Press or DB Press
    Pull (back/biceps): Barbell Row or DB Row
    Legs (quad dominant): Squat or Goblet Squat
    Core: Plank or Ab exercise
    Optional: Lateral Raise or Bicep Curl

  FULL BODY B — MUST contain ALL of:
    Push (shoulders): Overhead Press
    Pull (back/lats): Pull-up or Lat Pulldown
    Legs (hinge): Romanian Deadlift or Deadlift
    Core: different from A
    Optional: Tricep exercise or Calf Raise

  RULE: Every session must have at least 1 push exercise AND 1 pull exercise."""

            daysPerWeek == 3 -> when {
                goalLower.contains("hypertrophy") -> """
TRAINING SPLIT — FULL BODY A/B/C (3 days, hypertrophy):
  Schedule: Mon / Wed / Fri

  DAY A — MUST contain:
    Chest push: Bench Press (4 sets)
    Back pull: Barbell Row (4 sets)
    Legs: Squat (3 sets)
    Shoulders: Lateral Raise (3 sets)
    Arms: Bicep Curl (2-3 sets)
  DAY A MUST NOT contain: Deadlift, OHP, Leg Press (saved for B/C)

  DAY B — MUST contain:
    Shoulder push: Overhead Press (4 sets)
    Back pull: Lat Pulldown or Pull-up (4 sets)
    Legs: Romanian Deadlift (3 sets)
    Chest: Incline DB Press (3 sets)
    Arms: Tricep Extension (2-3 sets)
  DAY B MUST NOT contain: Bench Press, Squat (saved for A/C)

  DAY C — MUST contain:
    Chest: DB Fly or Cable Fly if cable available (3 sets)
    Back: DB Row or Cable Row if cable available, or Face Pull (3 sets)
    Legs: Leg Press + Leg Curl if machines available, else Bulgarian Split Squat + Nordic Curl or DB RDL (3 sets each)
    Core: Ab Rollout or Hanging Leg Raise (3 sets)
    Optional: Hammer Curl, Lateral Raise"""
                else -> """
TRAINING SPLIT — PUSH / PULL / LEGS (3 days):
  Schedule: Mon (Push) / Wed (Pull) / Fri (Legs)

  PUSH DAY — MUST contain: chest, shoulders, triceps
    Primary: Bench Press or DB Floor Press (4 sets) — CHEST exercise
    Secondary: Overhead Press or DB Shoulder Press (3 sets) — SHOULDER exercise
    Accessory: Incline DB Press or DB Fly (chest), Lateral Raise (shoulder), Tricep exercise (Pushdown if cable available, else Overhead DB Extension or Dip)
    PUSH DAY MUST NOT contain: any rows, pull-ups, face pulls, bicep curls

  PULL DAY — MUST contain: back (width + thickness), biceps
    Primary: Deadlift or Barbell/DB Row (4 sets)
    Secondary: Pull-up or Lat Pulldown (use whichever equipment is available; if neither, use Band Pull-down) (3 sets)
    Accessory: DB Row or Resistance Band Row (3 sets), Face Pull or Rear Delt Fly (3 sets), Bicep Curl (2-3 sets)
    PULL DAY MUST NOT contain: bench press, overhead press, tricep exercises

  LEGS DAY — MUST contain: quads, hamstrings, glutes, calves, core
    Primary: Squat (4 sets)
    Secondary: Romanian Deadlift (3 sets)
    Accessory: Leg Press (3 sets), Leg Curl (3 sets), Calf Raise (3 sets), Core (Plank or Ab)
    LEGS DAY MUST NOT contain: upper body pushing or pulling exercises"""
            }

            daysPerWeek == 4 -> """
TRAINING SPLIT — UPPER A / LOWER A / UPPER B / LOWER B (4 days):
  Schedule: Mon (Upper A) / Tue (Lower A) / Thu (Upper B) / Fri (Lower B)
  Every muscle trained exactly 2× per week — the evidence-based optimum.

  UPPER A — horizontal push + horizontal pull + arms:
    MUST contain: Bench Press or DB Floor Press (chest), Barbell/DB Row (back), Incline DB Press (chest), Bicep Curl, Tricep Extension
    MUST NOT contain: Squat, Deadlift, OHP, Pull-up
    Optional: Rear Delt Fly or Face Pull (if cable/bands), Lateral Raise

  LOWER A — quad dominant + posterior + core:
    MUST contain: Squat (quads), Romanian Deadlift (hamstrings/glutes), Calf Raise, Core exercise
    MUST NOT contain: Bench Press, Rows, Curls, Presses
    Optional: Leg Press (if machines available), else Bulgarian Split Squat; Leg Curl (machine) or Nordic Curl

  UPPER B — vertical push + vertical pull + shoulders:
    MUST contain: Overhead Press or DB Shoulder Press (shoulders), Pull-up or Lat Pulldown or DB Pull-over (back-width), DB Row or Resistance Band Row (back-thickness), Lateral Raise
    MUST NOT contain: Squat, Deadlift, Leg exercises
    Optional: Hammer Curl, Rear Delt Fly, Tricep exercise (Dip, Overhead Extension, or Pushdown if cable)
    NOTE: Upper B targets SAME muscles as Upper A but with DIFFERENT exercise selection

  LOWER B — posterior chain + unilateral:
    MUST contain: Deadlift or Hip Thrust (glutes/hamstrings), Bulgarian Split Squat or Lunge (unilateral quads), Calf Raise
    MUST NOT contain: upper body exercises
    Optional: Nordic Curl or Leg Curl, Core, Glute Bridge"""

            daysPerWeek == 5 -> """
TRAINING SPLIT — PUSH A / PULL A / LEGS / UPPER B / CARDIO (5 days):
  Schedule: Mon (Push A) / Tue (Pull A) / Wed (Legs) / Thu (Upper B) / Fri (Cardio)

  PUSH A — chest + shoulders + triceps:
    MUST contain: Bench Press or DB Floor Press, Overhead Press or DB Shoulder Press, Incline DB Press, Lateral Raise, Tricep exercise (Pushdown if cable available, else Overhead DB Extension or Dip)
    MUST NOT contain: any back/bicep exercises

  PULL A — back + biceps:
    MUST contain: Deadlift or Barbell/DB Row, Pull-up or Lat Pulldown (substitute DB Pull-over or Band Pull-down if neither available), Row variation, Bicep Curl
    MUST NOT contain: any pushing or leg exercises

  LEGS — quads + hamstrings + glutes + calves + core:
    MUST contain: Squat, Romanian Deadlift, Calf Raise, Core
    Include Leg Press or Leg Curl only if machines are available; otherwise substitute Bulgarian Split Squat and Nordic Curl
    MUST NOT contain: upper body exercises

  UPPER B — full upper body (second hit for chest + back + shoulders this week):
    MUST contain at least ONE chest exercise (e.g. DB Fly or Incline Press) AND ONE back exercise (e.g. Face Pull or Seated Row) AND ONE shoulder exercise (e.g. DB Shoulder Press or Lateral Raise)
    MUST NOT contain: Squat, Deadlift, Leg exercises

  CARDIO — dedicated cardiovascular session:
    Contains ONLY cardio: Outdoor Run, Interval Run, Tempo Run, or Easy Jog"""

            else -> """
TRAINING SPLIT — PUSH / PULL / LEGS × 2 (6 days — PPL twice per week):
  Schedule: Mon (Push A) / Tue (Pull A) / Wed (Legs A) / Thu (Push B) / Fri (Pull B) / Sat (Legs B) / Sun (Rest)

  PUSH A (chest emphasis): Bench Press or DB Floor Press, Incline DB Press, DB Fly or Cable Fly if cable available, OHP or DB Shoulder Press, Lateral Raise, Tricep exercise (Pushdown if cable, else Overhead DB Extension or Dip)
    PUSH DAYS MUST NOT contain: Rows, Pull-ups, Deadlifts, Bicep Curls

  PULL A (back width): Pull-up or Lat Pulldown (use whichever is available; else Band Pull-down), DB Row or Barbell Row, Face Pull or Rear Delt Fly (use band/cable if available), Bicep Curl, Hammer Curl
    PULL DAYS MUST NOT contain: Bench Press, OHP, Chest exercises, Tricep exercises, Squats

  LEGS A (quad dominant): Squat, Romanian Deadlift, Leg Press (if available) else Bulgarian Split Squat, Leg Curl (machine) or Nordic Curl, Calf Raise, Core
    LEGS DAYS MUST NOT contain: upper body exercises

  PUSH B (shoulder emphasis): Overhead Press or DB Shoulder Press, Arnold Press, DB Lateral Raise, Incline Press, Tricep Dip or Overhead DB Extension, DB Fly or Cable Fly if cable available
  PULL B (back thickness): Barbell/DB Row, Seated Row or DB Row, Deadlift, Face Pull or Rear Delt Fly, Preacher Curl or Incline Curl
  LEGS B (posterior): Hip Thrust, Bulgarian Split Squat, RDL, Nordic Curl or Leg Curl if machine available, Calf Raise

  Every muscle group trained twice per week with different exercise selection each time."""
        }

        val cardioInstruction = if (separateCardioDays) {
            "SEPARATE CARDIO DAYS ENABLED: Cardio must be on its OWN dedicated day — NEVER on the same day as strength work."
        } else {
            "Cardio may be placed after upper body sessions. NEVER on the day before or after heavy leg sessions."
        }

        val restByGoal = when {
            goalLower.contains("strength") -> "Primary compounds: 150-180s | Secondary compounds: 120-150s | Isolation: 60-90s | Core: 45-60s | Cardio: 60s"
            goalLower.contains("endurance") -> "All exercises: 30-45s | Cardio: 60s"
            goalLower.contains("weight") -> "Compounds: 60-90s | Isolation: 45-60s | Cardio: 60s"
            else -> "Primary compounds: 120-150s | Secondary/machines: 90-120s | Isolation: 60-90s | Core: 45-60s | Cardio: 60s"
        }
        // Hard cap: no recommendedRestSeconds value should exceed 180 (3 min) — enforced in parsing

        val equipLower = equipment.map { it.lowercase() }
        val hasCable    = equipLower.any { "cable" in it }
        val hasBarbell  = equipLower.any { "barbell" in it || "olympic bar" in it || "ez bar" in it }
        val hasDumbbells= equipLower.any { "dumbbell" in it || " db" in it }
        val hasMachines = equipLower.any { "machine" in it || "leg press" in it || "smith" in it }
        val hasPullBar  = equipLower.any { "pull" in it && ("bar" in it || "up" in it) } || hasBarbell
        val hasBands    = equipLower.any { "band" in it || "resistance" in it }
        val hasBench    = equipLower.any { "bench" in it }

        val forbidden = buildList {
            if (!hasCable) addAll(listOf(
                "Tricep Pushdown", "Rope Pushdown", "Cable Fly", "Cable Row",
                "Cable Curl", "Cable Tricep Extension", "Cable Lateral Raise",
                "Lat Pulldown",   // requires cable stack
                "Seated Cable Row"
            ))
            if (!hasCable && !hasBands) add("Face Pull")
            if (!hasMachines) addAll(listOf(
                "Leg Press", "Leg Curl", "Leg Extension",
                "Smith Machine Squat", "Hack Squat Machine",
                "Chest Press Machine", "Shoulder Press Machine"
            ))
            if (!hasBarbell) addAll(listOf(
                "Barbell Row", "Barbell Curl", "Barbell Squat",
                "Overhead Press"   // can be done with DBs, see alternatives below
            ))
            if (!hasPullBar) add("Pull-up")
            if (!hasBench) add("Bench Press")
        }.distinct()

        val forbiddenBlock = if (forbidden.isNotEmpty()) """
══════════════════════════════════════════
FORBIDDEN EXERCISES — MISSING EQUIPMENT
══════════════════════════════════════════
The following exercises MUST NOT appear anywhere in the program because the required equipment is not available.
This overrides everything else in this prompt, including split guidance.
${forbidden.joinToString(", ")}

When a split guideline names a forbidden exercise, substitute the closest available alternative:
  • Tricep Pushdown / Cable Tricep Extension → Overhead Tricep Extension (DB or band), Dip, Close-Grip Push-up
  • Cable Row / Seated Cable Row → DB Row, Bent-Over Row, Resistance Band Row
  • Cable Fly → DB Fly, Pec Deck (if machine available)
  • Lat Pulldown → Pull-up, Band Pull-down
  • Face Pull → Band Face Pull, Rear Delt DB Fly
  • Leg Press / Leg Extension / Leg Curl → Bulgarian Split Squat, DB Lunge, Nordic Curl, Glute Bridge
  • Bench Press (if no bench) → DB Floor Press, Push-up
  • Pull-up (if no pull-up bar) → Resistance Band Pull-down, Inverted Row under a table""" else ""

        val rejectionBlock = if (previousRejectionReason.isNotBlank()) """
══════════════════════════════════════════
PREVIOUS PLAN WAS REJECTED — YOU MUST FIX THIS
══════════════════════════════════════════
A scientific reviewer rejected the last plan for this reason:
"$previousRejectionReason"
Your new plan MUST address and correct this specific issue. Failure to do so will result in rejection again.

""" else ""

        return """
You are an expert sports scientist and S&C coach. Build a science-based $daysPerWeek-day weekly program from the history and principles below.
$rejectionBlock

══════════════════════════════════════════
WORKOUT HISTORY (with trend analysis)
══════════════════════════════════════════
$history

══════════════════════════════════════════
USER PROFILE
══════════════════════════════════════════
Goal: $goal | Experience: $experience | Days/week: $daysPerWeek | Session target: $sessionDurationMinutes min

══════════════════════════════════════════
AVAILABLE EQUIPMENT
══════════════════════════════════════════
${if (equipment.isEmpty()) "None — bodyweight only" else equipment.joinToString(", ")}
${if (equipmentNotes.isNotBlank()) "Limitations: $equipmentNotes" else ""}
RULE: Only programme exercises that can be done with the equipment listed above.$forbiddenBlock

══════════════════════════════════════════
GOAL-SPECIFIC PARAMETERS
══════════════════════════════════════════
$goalParams

══════════════════════════════════════════
TRAINING SPLIT
══════════════════════════════════════════
$splitGuidance

══════════════════════════════════════════
WEEKLY VOLUME TARGETS (sets per muscle group across ALL sessions)
══════════════════════════════════════════
Chest: 10-20 | Back: 10-20 | Quads: 10-16 | Hamstrings/Glutes: 8-14
Shoulders: 10-18 | Biceps: 8-14 | Triceps: 8-16 | Core: 6-12 | Calves: 8-14
Strength goal → aim at lower end. Hypertrophy → mid-to-upper end.
With fewer days, reduce proportionally and prioritise compound overlap.

══════════════════════════════════════════
EXERCISE SELECTION HIERARCHY (ORDER within each session)
══════════════════════════════════════════
1. Bilateral barbell compound: Squat, Deadlift, Bench Press, Barbell Row, Overhead Press, Pull-up
2. Unilateral / dumbbell compound: Bulgarian Split Squat, Lunge, DB Press, Single-arm Row, RDL
3. Machine / cable compound (ONLY if that equipment is in AVAILABLE EQUIPMENT above): Leg Press, Lat Pulldown, Cable Row, Chest Press
4. Isolation: Curl, Extension, Fly, Lateral Raise, Leg Curl, Leg Extension, Calf Raise
5. Core: Plank, Ab Rollout, Hanging Leg Raise, Russian Twist
RULE: NEVER place an isolation exercise before a compound for the same muscle group.
RULE: Start every session with the heaviest, highest-demand movement.

══════════════════════════════════════════
PROGRESSIVE OVERLOAD (apply to each exercise in history)
══════════════════════════════════════════
PROGRESSING trend in history → increase targetWeightKg by 2.5 kg (intermediate/advanced) or 5 kg (beginner)
PLATEAUED trend (3+ sessions same weight) → increase targetReps to top of range OR swap for harder variation
REGRESSING trend → reduce weight 5-10% and set notes to "deload — rebuild form"
No history for exercise → conservative starting weight (~55-65% estimated 1RM); note "establish baseline"
RULE: Leave 1-2 RIR on working sets of compound lifts — never train compounds to absolute failure.

══════════════════════════════════════════
RECOVERY RULES (MANDATORY)
══════════════════════════════════════════
- NEVER train the same primary muscle group on consecutive days
- Heavy leg sessions (Squat, Deadlift) need 48-72 h before the next leg session
- High-intensity cardio (Interval Run) must NOT fall the day before or after heavy legs
- $cardioInstruction

══════════════════════════════════════════
CARDIO
══════════════════════════════════════════
Include 1-2 cardio sessions. Names to use: Easy Jog, Outdoor Run, Tempo Run, Interval Run.
Cardio JSON: sets=1, targetReps = duration string only (e.g. "30 min", "6×400m"), targetWeightKg=0, recommendedRestSeconds=60
RULE: targetReps for cardio must be ONLY the duration/distance — no extra words like "reps" or "sets".

══════════════════════════════════════════
REST TIMES (recommendedRestSeconds)
══════════════════════════════════════════
$restByGoal

══════════════════════════════════════════
OUTPUT — valid JSON only, no prose, no markdown fences
══════════════════════════════════════════
{
  "days": [
    {
      "dayOfWeek": 1,
      "name": "Upper A — Horizontal Push/Pull",
      "exercises": [
        {
          "name": "Bench Press",
          "sets": 4,
          "targetReps": "8-10",
          "targetWeightKg": 80.0,
          "notes": "Primary compound — 1-2 RIR, full rest",
          "recommendedRestSeconds": 150
        },
        {
          "name": "Barbell Row",
          "sets": 4,
          "targetReps": "8-10",
          "targetWeightKg": 75.0,
          "notes": "Antagonist pair with bench",
          "recommendedRestSeconds": 150
        },
        {
          "name": "Lateral Raise",
          "sets": 4,
          "targetReps": "12-15",
          "targetWeightKg": 10.0,
          "notes": "Isolation — strict form, 1 RIR",
          "recommendedRestSeconds": 60
        }
      ]
    },
    {
      "dayOfWeek": 3,
      "name": "Cardio",
      "exercises": [
        {
          "name": "Outdoor Run",
          "sets": 1,
          "targetReps": "30 min",
          "targetWeightKg": 0.0,
          "notes": "Zone 2 — conversational pace",
          "recommendedRestSeconds": 60
        }
      ]
    }
  ]
}

dayOfWeek: 1=Monday … 7=Sunday. Output exactly $daysPerWeek days, spaced optimally across the week.
        """.trimIndent()
    }

    private fun parseProgram(cleanJson: String): List<PlannedExercise> {
        val weekStart = thisMonday()
        val program = gson.fromJson(cleanJson, ProgramJson::class.java)
        return program.days.flatMap { day ->
            day.exercises.mapIndexed { index, ex ->
                PlannedExercise(
                    weekStart = weekStart,
                    dayOfWeek = day.dayOfWeek,
                    orderInDay = index,
                    exerciseName = ex.name,
                    sets = ex.sets,
                    targetReps = ex.targetReps,
                    targetWeightKg = ex.targetWeightKg,
                    notes = ex.notes,
                    recommendedRestSeconds = ex.recommendedRestSeconds.coerceAtMost(180)
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

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
import com.migul.treningsprogram.domain.StallDetector
import com.migul.treningsprogram.domain.WorkoutTimeEstimator
import com.migul.treningsprogram.domain.model.OnboardingQuestion
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

// ── F3: transient-failure classification and retry ──────────────────────────────────────────────

/**
 * Returns true when [t] is a transient error that is safe to retry:
 *   - [SocketTimeoutException] / [IOException] — network-level failure (timeout, dropped connection)
 *   - [HttpException] with status 5xx or 429 (server error / rate-limited)
 *
 * Non-retryable: [HttpException] with 4xx codes (auth errors 401, bad request 400, etc.) indicate
 * a problem with the request itself that a retry will not fix.
 *
 * This is a package-level (non-member) function so it can be unit-tested without an
 * [AiRepository] instance.
 */
fun isTransientAiError(t: Throwable): Boolean = when (t) {
    is SocketTimeoutException -> true
    is IOException -> true
    is HttpException -> t.code() >= 500 || t.code() == 429
    else -> false
}

/**
 * Returns a concise, user-friendly message for an AI network failure. Avoids surfacing raw Java
 * exception class names to the user.
 */
fun friendlyAiErrorMessage(t: Throwable): String = when {
    t is SocketTimeoutException ->
        "The AI request timed out. Please check your connection and try again."
    t is IOException ->
        "Network error while reaching the AI. Please check your connection and try again."
    t is HttpException && t.code() == 429 ->
        "AI rate limit reached. Please wait a moment and try again."
    t is HttpException && t.code() == 401 ->
        "Invalid API key. Please check your key in Settings."
    t is HttpException && t.code() >= 500 ->
        "The AI service returned a server error (${t.code()}). Please try again."
    else -> t.message ?: "AI request failed. Please try again."
}

/**
 * Executes [block] up to [maxAttempts] times, retrying immediately on transient failures
 * (as classified by [isTransientAiError]). Non-transient failures propagate immediately on
 * the first occurrence. On the final attempt a transient failure is re-thrown.
 *
 * Package-level (non-member) so it can be called from any suspend context and unit-tested
 * independently of [AiRepository].
 */
suspend fun <T> withAiRetry(maxAttempts: Int = 2, block: suspend () -> T): T {
    var lastException: Throwable? = null
    repeat(maxAttempts) { attempt ->
        try {
            return block()
        } catch (t: Throwable) {
            if (!isTransientAiError(t)) throw t   // non-transient: fail fast
            lastException = t
            // On last attempt fall through to re-throw; otherwise retry immediately
        }
    }
    throw lastException!!
}

// ── S3: robust extraction of a JSON object from raw model output ─────────────────────────────────
//
// The model occasionally wraps its JSON in markdown fences, prepends/appends prose ("Here is your
// program:"), truncates the response, or leaves a trailing comma before a `}` / `]`. These helpers
// are package-level (non-member) pure functions so the whole parsing seam is unit-testable WITHOUT an
// [AiRepository] instance (matching the F3 isTransientAiError / withAiRetry pattern), and so the tests
// exercise the REAL production logic rather than a hand-copied mirror.

/**
 * Strips markdown fences from [text] so the brace scanner sees the bare payload.
 *  - A COMPLETE fenced block (opening ``` AND closing ```): the inner content is returned verbatim
 *    (trimmed). This preserves the historical contract that fenced JSON is taken as-is.
 *  - An OPENING fence only (closing fence truncated away — the common partial-response case): the
 *    opening ```/```json marker is removed and any stray ``` are blanked, leaving the body for the
 *    brace scanner to recover from.
 *  - No fence: returned unchanged.
 */
internal fun stripJsonFences(text: String): String {
    val complete = Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(text)
    if (complete != null) return complete.groupValues[1].trim()
    // Opening fence only / stray fences: remove the leading marker, blank any leftover backticks.
    return text.replaceFirst(Regex("```(?:json)?"), " ").replace("```", " ")
}

/**
 * Returns the first BALANCED `{ … }` span in [s] (brace-depth aware, ignoring braces inside JSON
 * strings and escaped quotes), or null when no balanced object can be found (e.g. a truncated
 * response whose closing brace was cut off). More robust than first-`{`/last-`}` because it ignores
 * trailing prose and refuses to "succeed" on a span that closes on a nested brace.
 */
internal fun balancedJsonSpan(s: String): String? {
    val start = s.indexOf('{')
    if (start < 0) return null
    var depth = 0
    var inString = false
    var escaped = false
    for (i in start until s.length) {
        val c = s[i]
        if (inString) {
            when {
                escaped -> escaped = false
                c == '\\' -> escaped = true
                c == '"' -> inString = false
            }
        } else {
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return s.substring(start, i + 1)
                }
            }
        }
    }
    return null
}

/**
 * Removes a trailing comma that sits immediately before a closing `}` or `]` (string/escape aware,
 * so commas inside string values are preserved). Gson tolerates trailing commas in arrays but THROWS
 * on a trailing comma in an object, and a trailing array comma can even materialise a phantom null
 * element — sanitising both makes minor model JSON-dirt parse cleanly.
 */
internal fun stripTrailingCommas(s: String): String {
    val out = StringBuilder(s.length)
    var inString = false
    var escaped = false
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (inString) {
            out.append(c)
            when {
                escaped -> escaped = false
                c == '\\' -> escaped = true
                c == '"' -> inString = false
            }
            i++
            continue
        }
        if (c == '"') {
            inString = true
            out.append(c)
            i++
            continue
        }
        if (c == ',') {
            var j = i + 1
            while (j < s.length && s[j].isWhitespace()) j++
            if (j < s.length && (s[j] == '}' || s[j] == ']')) {
                i++          // drop this trailing comma
                continue
            }
        }
        out.append(c)
        i++
    }
    return out.toString()
}

/**
 * Extracts a clean JSON object string from raw model output, hardened against fence/prose/dirt:
 *  1. strip a complete fence (inner returned) or an opening-only fence;
 *  2. recover the first BALANCED `{ … }` span (ignores leading/trailing prose);
 *  3. fall back to first-`{`/last-`}` when no balanced span exists;
 *  4. otherwise throw [IllegalStateException] — a genuinely JSON-free or irrecoverably-truncated
 *     response must STILL surface a clear error (never a silent empty success).
 * A residual span that is still malformed (e.g. truncated mid-object) is returned here and rejected
 * downstream by gson, which also surfaces as a thrown failure.
 */
internal fun extractJsonOrThrow(text: String): String {
    val body = stripJsonFences(text)
    balancedJsonSpan(body)?.let { return it.trim() }
    val start = body.indexOf('{')
    val end = body.lastIndexOf('}')
    if (start != -1 && end > start) return body.substring(start, end + 1).trim()
    throw IllegalStateException("No JSON found in AI response")
}

// ── B10: non-throwing extraction + truncation detection ──────────────────────────────────────────
//
// The generation loop must treat a no-JSON / truncated / unparseable response as a REJECTED ATTEMPT
// that retries (the same path quality/duration rejections take), NOT as a thrown error that escapes
// the loop and strands the user with a stall. These pure helpers are the non-throwing seam for that:
// they classify the response so the loop can decide retry-vs-accept without try/catch around control
// flow. Kept package-level (like the S3/F3 helpers) so they are unit-testable without an instance.

/**
 * Like [extractJsonOrThrow] but returns null instead of throwing when no JSON object can be found.
 * The loop uses this so a prose-only / JSON-free response becomes a retryable rejection rather than
 * an exception that escapes the per-attempt loop.
 */
internal fun extractJsonOrNull(text: String): String? =
    runCatching { extractJsonOrThrow(text) }.getOrNull()

/**
 * Heuristically detects a TRUNCATED / cut-off AI response — one that ran out of room before the JSON
 * was completed. Two independent signals, either of which flags truncation:
 *  1. [stopReason] == "max_tokens" — the API itself says it stopped at the output-token cap. This is
 *     authoritative and catches the worst case (a wall of planning prose that never reached the JSON,
 *     so [text] contains no `{` at all).
 *  2. JSON STARTED BUT NEVER CLOSED — the (fence-stripped) body has an opening `{` but no brace-balanced
 *     span (string/escape aware). That means the object began and was cut off mid-way; the API flag
 *     may be absent (older payloads, proxies) so this is the structural backstop.
 *
 * A response with NO `{` at all and stopReason != "max_tokens" is classified as "no JSON" (handled by
 * [extractJsonOrNull] returning null), not as "truncated" — the distinction only affects the message
 * shown; both are retryable.
 */
internal fun isLikelyTruncated(text: String, stopReason: String?): Boolean {
    if (stopReason == "max_tokens") return true
    val body = stripJsonFences(text)
    val opened = body.indexOf('{') >= 0
    return opened && balancedJsonSpan(body) == null
}

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

// B2: `rationale` is a top-level sibling of `days` — the model's own plain-language reasoning
// for the plan it just produced. Absent on old responses ⇒ defaults to "" (neutral state).
private data class ProgramJson(val days: List<DayJson> = emptyList(), val rationale: String = "")

data class GenerationResult(
    val exercises: List<PlannedExercise>,
    val attemptCount: Int,
    val rejectionReasons: List<String> = emptyList(),
    val rationale: String = ""
)

/**
 * E2 (L1 + M2): mesocycle / deload position of the active program, conveyed to the generation
 * prompt so the AI knows it is producing week N of a periodized block and whether THIS week is a
 * stall-triggered deload. Progression itself stays the existing adaptive weekly generation (L1) —
 * this only adds awareness, not a fixed ramp. The neutral default ([NONE]) reproduces the
 * pre-E2 prompt exactly (plain program, no block, no deload), so non-block users are unaffected.
 */
data class MesocycleContext(
    /** > 0 ⇒ periodized block of this many weeks; 0 ⇒ plain program (no block phrasing). */
    val mesocycleWeeks: Int = 0,
    /** 1-based week within the current block (1 if unknown / not a block). */
    val weekInBlock: Int = 1,
    /** True ⇒ this week is a stall/fatigue-triggered deload (M2). */
    val isDeload: Boolean = false,
    /** Names of currently stalled lifts driving the deload, for the prompt (may be empty). */
    val stalledLifts: List<String> = emptyList()
) {
    /**
     * The mesocycle / deload directive injected into the generation prompt. Pure (no instance / API),
     * so the wording is unit-testable.
     *
     * - Plain program (no block, no deload) ⇒ "" so the prompt is byte-for-byte the pre-E2 prompt.
     * - In a block ⇒ tells the model it is producing week N of an M-week mesocycle and to progress
     *   week-to-week from the logged performance (adaptive, NOT a fixed ramp — decision L1).
     * - Deload week ⇒ instructs a genuine deload (reduced volume/intensity) and names the stalled
     *   lifts driving it (M2).
     */
    fun promptBlock(): String {
        if (mesocycleWeeks <= 0 && !isDeload) return ""
        return buildString {
            appendLine()
            appendLine("══════════════════════════════════════════")
            appendLine("MESOCYCLE / PERIODIZATION")
            appendLine("══════════════════════════════════════════")
            if (mesocycleWeeks > 0) {
                val week = weekInBlock.coerceAtLeast(1)
                appendLine(
                    "This program is a $week-of-$mesocycleWeeks-week mesocycle BLOCK. You are producing WEEK $week of $mesocycleWeeks."
                )
                appendLine(
                    "Progress week-to-week from the user's ACTUAL logged performance below (progressive overload) — " +
                        "this is adaptive weekly progression, NOT a fixed deterministic ramp. Build sensibly on last week's plan."
                )
            }
            if (isDeload) {
                appendLine(
                    "THIS WEEK IS A DELOAD (triggered by detected plateaus/accumulated fatigue, NOT a fixed calendar week). " +
                        "Reduce overall volume and intensity meaningfully: drop working sets ~30-50%, lower loads ~10-20% " +
                        "(or take ~3-4 RIR), keep movement quality high, and avoid training to failure. The goal is recovery " +
                        "and breaking the plateau, not new PRs."
                )
                if (stalledLifts.isNotEmpty()) {
                    appendLine("Plateaued lifts driving this deload: ${stalledLifts.joinToString(", ")}.")
                }
            }
        }.trimEnd()
    }

    companion object {
        val NONE = MesocycleContext()
    }
}

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

        val responseText = withAiRetry {
            claudeApi.sendMessage(
                ClaudeRequest(messages = listOf(ClaudeRequest.Message(content = prompt)))
            ).text()
        }
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
        injurySeverity: String = "",
        priorityMuscles: String = "",
        dislikedExercises: String = "",
        onboardingContext: String = "",
        mesocycle: MesocycleContext = MesocycleContext.NONE,
        // B08: explicit REST weekdays (1=Mon…7=Sun). Non-empty ⇒ the plan must train on EXACTLY the
        // complement of these and never on a rest day (deterministically enforced below). Empty ⇒
        // count mode (the AI chooses which days are rest, pre-B08 behaviour).
        restDays: Set<Int> = emptySet(),
        // B09: days already trained THIS week, passed as fixed context so the model reproduces them
        // and rebalances the regenerated days around them. The loop never lets these be rejected on a
        // duration miss (we keep the real logged rows and discard the model's echo of these days).
        lockedExercises: List<PlannedExercise> = emptyList(),
        onProgress: (String) -> Unit = {}
    ): Result<GenerationResult> = runCatching {
        val lockedDays = lockedExercises.map { it.dayOfWeek }.toSet()
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
                injuries, injurySeverity, priorityMuscles, dislikedExercises, onboardingContext,
                previousPlan, recentExercises, variationTheme, splitSuggestion, mesocycle,
                restDays, lockedExercises
            )
            val response = withAiRetry {
                claudeApi.sendMessage(
                    ClaudeRequest(messages = listOf(ClaudeRequest.Message(content = prompt)))
                )
            }
            val responseText = response.text()
            promptLog.add("generate_attempt_$attempt", prompt, responseText)

            // ── B10: no-JSON / truncated / unparseable response → RETRYABLE rejection ──────────────
            // Previously extractJson(responseText) called extractJsonOrThrow, which THROWS on a
            // prose-only / cut-off response. That throw escaped this loop (it is inside the for-loop
            // but not inside any try/catch) and propagated out of generateAdaptedProgram's runCatching
            // as Result.failure — but WITHOUT ever recording an attempt or retrying, unlike a quality
            // rejection which stays in the loop. Result: the model emitting planning prose that ran out
            // of room before the JSON would STALL (no plan, no retry). Now such a response is handled
            // EXACTLY like a quality/duration rejection: a reason is recorded, the loop retries, and
            // only after MAX_GENERATION_ATTEMPTS does it surface the same clear onFailure error.
            val cleanJson = extractJsonOrNull(responseText)
            // parseProgram can still throw on a residual-but-malformed span (gson) — keep that inside
            // the loop too, so it becomes a rejected attempt rather than an escape.
            val exercises = cleanJson?.let { runCatching { parseProgram(it) }.getOrNull() }
            if (cleanJson == null || exercises == null) {
                val truncated = isLikelyTruncated(responseText, response.stopReason)
                val parseRejectionReason = when {
                    truncated ->
                        "The response was cut off before a complete plan was produced (ran out of room). " +
                            "Lead with the JSON plan — keep any reasoning brief so the JSON is fully emitted."
                    cleanJson == null ->
                        "No usable JSON plan was found in the response. " +
                            "Return the JSON object (the {\"rationale\": …, \"days\": […]} plan), not only prose."
                    else ->
                        "The JSON plan could not be parsed. " +
                            "Return a single valid JSON object matching the required shape."
                }
                rejectionReasons.add(parseRejectionReason)
                onProgress("Attempt $attempt rejected: $parseRejectionReason")
                if (attempt == MAX_GENERATION_ATTEMPTS) {
                    val logAttempts = rejectionReasons.mapIndexed { i, r ->
                        RejectionLog.Attempt(i + 1, r, i == rejectionReasons.lastIndex)
                    }
                    rejectionLog.addSession(logAttempts, succeeded = false)
                    val reasons = rejectionReasons.mapIndexed { i, r -> "Attempt ${i + 1}: $r" }.joinToString("\n")
                    throw IllegalStateException("Program rejected after $MAX_GENERATION_ATTEMPTS attempts.\n$reasons")
                }
                continue
            }

            // An empty/no-exercise plan (well-formed JSON but {"days":[]} or all-empty days) would
            // otherwise pass the duration check vacuously and could be SAVED as an empty plan — that
            // is a silent failure. Treat it as a rejected attempt so it retries and, if it never
            // recovers, throws after MAX_GENERATION_ATTEMPTS instead of persisting nothing.
            val emptyPlanReason = if (exercises.isEmpty())
                "The plan contained no exercises. Produce $daysPerWeek full training days with exercises."
            else ""

            // ── B08: deterministic REST-DAY check (HARD) ─────────────────────────
            // In rest-day mode the user pinned specific rest weekdays; the plan must train on EXACTLY
            // their complement. Reject (retryable, inside the loop) any plan that schedules training
            // on a rest day OR omits a required training day. Empty restDays ⇒ count mode ⇒ no check.
            // B09: locked (already-logged) days are exempt — they are preserved regardless of the
            // rest-day setting (e.g. a day trained before it was marked a rest day).
            val restDayReason = com.migul.treningsprogram.domain.TrainingDaySelection.scheduleViolation(
                exercises.map { it.dayOfWeek }.toSet(), restDays, lockedDays
            ) ?: ""

            // ── Deterministic ±10 min duration check (AUTHORITATIVE) ──────────────
            // Each training day must estimate within ±10 min of the session target,
            // using the SAME time-estimate formula the Program screen shows.
            // B09: locked (already-logged) days are SKIPPED — we keep the real logged rows and discard
            // the model's echo of them, so a duration miss on a day we won't persist must not reject.
            val durationReason = exercises
                .groupBy { it.dayOfWeek }
                .toSortedMap()
                .filterKeys { it !in lockedDays }
                .mapNotNull { (day, dayExercises) ->
                    val est = WorkoutTimeEstimator.estimateDayMinutes(dayExercises)
                    if (est < sessionDurationMinutes - 10 || est > sessionDurationMinutes + 10) {
                        "Day $day estimates ~$est min; target $sessionDurationMinutes ±10 " +
                            "(${sessionDurationMinutes - 10}–${sessionDurationMinutes + 10}). " +
                            "Trim sets/exercises or rest."
                    } else null
                }
                .joinToString(" ")

            // Deterministic (Kotlin) rejections, in priority order: empty plan, B08 rest-day violation,
            // then the ±10-min duration miss. Any of these makes the plan un-acceptable regardless of
            // the LLM review, so we surface the most critical one AND skip the costly review when set.
            val deterministicReason = when {
                emptyPlanReason.isNotEmpty() -> emptyPlanReason
                restDayReason.isNotEmpty() -> restDayReason
                durationReason.isNotEmpty() -> durationReason
                else -> ""
            }
            // Skip the (costly) LLM review when a deterministic check already failed — it can never
            // be accepted anyway.
            val validation = if (deterministicReason.isEmpty()) {
                onProgress("Reviewing plan for quality…")
                validateProgram(cleanJson, daysPerWeek, sessionDurationMinutes, goal, experience, injuries, injurySeverity)
            } else ValidationResult(accepted = false, reason = deterministicReason)
            // A plan is accepted only when every deterministic check AND the LLM review pass.
            if (deterministicReason.isEmpty() && validation.accepted) {
                val logAttempts = rejectionReasons.mapIndexed { i, r ->
                    RejectionLog.Attempt(i + 1, r, false)
                }
                rejectionLog.addSession(logAttempts, succeeded = true)
                // B2: extract the model's own rationale from the SAME accepted response.
                val rationale = parseRationale(cleanJson)
                return@runCatching GenerationResult(exercises, attempt, rejectionReasons.toList(), rationale)
            }
            // Prefer the named deterministic reason; fall back to the LLM validator reason.
            val rejectionReason = deterministicReason.ifEmpty { validation.reason }
            rejectionReasons.add(rejectionReason)
            onProgress("Attempt $attempt rejected: $rejectionReason")
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

    /**
     * B1: a short natural-language WEEKLY COACHING SUMMARY grounded in the user's real logged data.
     *
     * This is a SEPARATE Claude call (not entangled with [generateAdaptedProgram]'s response shape).
     * It REUSES the existing history-context machinery ([buildSessionHistory] over
     * [WorkoutRepository.getRecentSessions], which folds in per-exercise trends, the STALLED-LIFTS
     * signal, and weekly-volume context) so the readout is specific to the user. Returns the summary
     * text; the caller persists it as a [com.migul.treningsprogram.data.db.entity.WeeklySummary] row.
     *
     * Caller is responsible for the "too little data" guard (skip when there are no completed
     * sessions) — this method assumes there is something worth summarising.
     */
    suspend fun generateWeeklySummary(
        goal: String,
        experience: String,
        daysPerWeek: Int
    ): Result<String> = runCatching {
        val sessions = workoutRepository.getRecentSessions(12)
        val (history, _) = buildSessionHistory(sessions)
        val prompt = """
You are the user's personal strength coach writing their WEEKLY check-in. You have looked at their actual logged training below. Write a short, warm, plain-language summary (about 4–7 sentences, no markdown, no bullet lists, no headings) of how their PAST WEEK of training went.

Ground EVERYTHING in the real data — name actual exercises and muscle groups and real changes. Cover, where the data supports it:
- What progressed (lifts that went up, PRs, consistency / streak).
- What slipped or stalled (plateaued lifts, under-trained muscle groups, skipped sessions).
- Plan adherence at a high level.
- One short, encouraging forward-looking note for next week.

Be specific and personal — NOT generic motivation. If a lift is flagged as stalled or plateaued in the data, mention it by name and what you'd do about it. Speak directly to the user ("you").

USER PROFILE
Goal: $goal | Experience: $experience | Training days/week: $daysPerWeek

WORKOUT HISTORY (most recent first)
$history

Respond with ONLY the summary text — no JSON, no markdown fences, no preamble like "Here is your summary".
        """.trimIndent()

        val responseText = withAiRetry {
            claudeApi.sendMessage(
                ClaudeRequest(messages = listOf(ClaudeRequest.Message(content = prompt)))
            ).text()
        }
        promptLog.add("weekly_summary", prompt, responseText)
        val summary = responseText.trim()
        if (summary.isBlank()) throw IllegalStateException("Empty weekly summary returned")
        summary
    }

    private suspend fun validateProgram(
        planJson: String,
        daysPerWeek: Int,
        sessionDurationMinutes: Int,
        goal: String,
        experience: String,
        injuries: String = "",
        injurySeverity: String = ""
    ): ValidationResult {
        // Effective severity (only meaningful when injuries are present). Legacy/unspecified ⇒ cautious Moderate.
        val sev = injurySeverity.ifBlank { "Moderate" }
        val injuryCheck = if (injuries.isNotBlank())
            "10. INJURY GATING (HARD — reject), severity = $sev for \"$injuries\". Judge against THIS severity:\n" +
            "   • SEVERE → the aggravating category must be EXCLUDED outright. REJECT if any genuinely contraindicated movement is present, e.g. jogging / high-impact cardio or a loaded single-leg balance movement (Bulgarian / rear-foot-elevated split squat, single-leg RDL, step-up) for a bad ankle; deep loaded knee flexion or high-impact plyo for a bad knee; overhead or behind-neck pressing for a bad shoulder. Bilateral / low-impact substitutes only.\n" +
            "   • MODERATE → the worst aggravators must be SUBSTITUTED with a safer same-muscle variant AND 1–2 rehab/prehab moves for the area should be present. REJECT if a clear aggravator is left in unmodified; do NOT reject a sensible substitution.\n" +
            "   • MILD → training AROUND the aggravator is allowed, but light rehab/strengthening on the injured area is REQUIRED. Light, controlled rehab/strengthening of a MILD injury is CORRECT — do NOT flag it as a violation; only reject if the program omits the area entirely or prescribes an obviously reckless load.\n" +
            "   Apply the appropriate tier PER injury if several are listed with differing severity (the stated level is the overall/worst). ALWAYS reject INCOHERENT injury handling regardless of tier: a single-leg / rear-foot-elevated / Bulgarian split squat carrying \"both feet down\" / \"bilateral contact at all times\" cues is physically impossible — the fix is to SUBSTITUTE a genuinely bilateral movement (goblet/sumo squat, leg press, hand-supported split squat), not to keep the single-leg exercise with contradictory cues. Goal: reject real contraindications, do NOT over-reject appropriate light rehab."
        else
            "10. INJURY GATING: no injuries reported — skip."
        val prompt = """
You are a sports science peer reviewer. Evaluate the following AI-generated weekly training program against the programming principles below.

Goal: $goal | Experience: $experience | Training days: $daysPerWeek${if (injuries.isNotBlank()) " | Injuries: $injuries (severity: $sev)" else ""}

PROGRAM JSON:
$planJson

Check ALL of the following. Reject on genuine violations — do not penalise reasonable coaching choices or exercise variation. Items marked HARD are always grounds for rejection. When rejecting, name the single most critical issue only.

1. SAFETY — LOADED HINGE REP CAPS (HARD): No barbell hinge (deadlift, RDL, sumo DL, good morning, Pendlay-style) is prescribed above 8 reps, AND no loaded dumbbell hinge (DB RDL, DB stiff-leg deadlift, DB good morning) is prescribed above 12 reps. Bodyweight/light hip-extension work (hip thrust, glute bridge, back extension, leg curl, kettlebell swing) is exempt and may run high-rep. Higher-rep posterior-chain work must be routed to that exempt list, NOT to a barbell pull.
2. DAYS: The plan contains exactly $daysPerWeek training days.
3. RECOVERY: No primary muscle group (chest, back, quads, hamstrings/glutes, shoulders) is trained on consecutive days. Heavy leg days need ≥48 h between them.
4. ROLE-BASED REP RANGES (HARD if monotone): rep ranges must vary by exercise role within a session — primary compounds lower-rep, isolation higher-rep. Reject any session that applies one identical rep range to every exercise. Ranges must broadly match the goal:
   - Strength: primary compounds 3–6, accessories 6–10, isolation 8–12. Do NOT reject for 8–10 rep accessory work.
   - Hypertrophy: compounds 6–10, accessories 8–12, isolation 10–15, rest ≤120 s. Invalid only if rest values consistently exceed 120 s.
   - Endurance: compounds 8–12, isolation 15–20, 30–60 s rest, includes 2–3 cardio sessions.
   - Weight Loss: 10–20 reps, 60–90 s rest, includes ≥2 cardio sessions.
5. ORDER & HIERARCHY: Within each session, compounds appear before isolation for the same muscle group, and the session leads with a primary compound rather than a flat list of identical-volume exercises.
6. EFFORT & PROGRESSION (HARD): Every exercise's notes must state a target effort (RIR or RPE) AND a progression rule. Reject if exercises only say "establish baseline" or carry no effort target.
7. VOLUME: Every major muscle group is trained at least once (not zero). No muscle exceeds ~10 hard sets in a single session, nor ~25 sets/week. No session exceeds ~18–20 total working sets (cardio/prehab excluded) — flag sessions of 22–23+ sets that run long and accrue fatigue even when no single muscle is over its cap.
8. DE-DUPLICATION: No two near-identical movement patterns within one session (e.g. RDL + single-leg stiff-leg deadlift). No obvious structural errors (all-chest sessions, cardio the day before heavy legs).
9. WEEKLY BALANCE: The week includes direct lateral-delt + rear-delt work and at least one knee-dominant quad movement; posterior-chain work is not stacked with no quad movement.
$injuryCheck
11. EXERCISE COUNT: Beginner ≤5/session, Intermediate ≤7/session, Advanced ≤8/session.
12. NOTES: Notes assert no incorrect mechanisms (e.g. "calf raises strengthen ankle stabilisers", "targets the inner chest"). Reject any use of "peak" as a NOUN for muscle shape ("bicep peak", "peak stretch", "increases bicep peak"). "Peak contraction" as a squeeze cue (hold the shortened position) is allowed.
13. TIME BUDGET (belt-and-suspenders; a deterministic Kotlin check is authoritative): each training day should estimate within ±10 min of $sessionDurationMinutes min (window ${sessionDurationMinutes - 10}–${sessionDurationMinutes + 10}). Rough per-exercise estimate ≈ sets × (reps × 3 s work + rest seconds between sets) + ~60 s setup; cardio ≈ its duration (targetReps minutes/distance) + ~60 s. A day that clearly runs far over or under that window should be flagged.

Respond with ONLY valid JSON — no prose, no markdown fences:
{"accepted": true}
OR
{"accepted": false, "reason": "one or two sentences on the most critical issue only"}
        """.trimIndent()

        return runCatching {
            val responseText = withAiRetry {
                claudeApi.sendMessage(
                    ClaudeRequest(messages = listOf(ClaudeRequest.Message(content = prompt)))
                ).text()
            }
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

        // STALLED-LIFT signal (B3). Detected locally via StallDetector on the full per-exercise
        // strength history (warm-ups already excluded by getStrengthHistory): a lift is stalled only
        // when its estimated 1RM has not improved across the last StallDetector.STALL_WINDOW
        // consecutive sessions. This is double-progression-aware — reps climbing at the same load
        // raise e1RM and so do NOT flag — and uses the same Epley helper as the rest of the app.
        // Surfaced to the AI so the new program addresses each plateau with a deload / rep-scheme
        // change / variation.
        val stalledLifts = exerciseTrends.keys.filter { exercise ->
            StallDetector.isStalled(workoutRepository.getStrengthHistory(exercise))
        }
        val stallBlock = if (stalledLifts.isEmpty()) "" else buildString {
            appendLine()
            appendLine(
                "STALLED LIFTS (no est-1RM improvement over the last " +
                    "${StallDetector.STALL_WINDOW} sessions — address with a deload / rep-scheme " +
                    "change / exercise variation):"
            )
            stalledLifts.forEach { appendLine("  $it") }
        }.trimEnd()

        return Pair("$sessionDetails\n$trends$stallBlock", recentExercises)
    }

    private suspend fun buildPreviousPlanContext(): String {
        val weekStart = workoutRepository.getLatestPlanWeekStart() ?: return ""
        // E2: previous-plan context must come from the ACTIVE program only, so generation varies
        // against the right program's last week (not another program's rows sharing the weekStart).
        val forWeek = workoutRepository.getActiveProgramPlanForWeek(weekStart)
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
        injurySeverity: String = "",
        priorityMuscles: String = "",
        dislikedExercises: String = "",
        onboardingContext: String = "",
        previousPlan: String = "",
        recentExercises: Set<String> = emptySet(),
        variationTheme: String = "",
        splitSuggestion: String = "",
        mesocycle: MesocycleContext = MesocycleContext.NONE,
        restDays: Set<Int> = emptySet(),
        lockedExercises: List<PlannedExercise> = emptyList()
    ): String {
        val goalLower = goal.lowercase()
        // B08: when specific rest days are pinned, training must land on EXACTLY their complement.
        val restDayBlock = buildRestDayBlock(restDays)
        // B09: already-trained days this week, fed back as fixed context so the model reproduces them
        // and rebalances the regenerated days around them.
        val lockedDaysBlock = buildLockedDaysBlock(lockedExercises)
        // E2: mesocycle / deload awareness block (L1 + M2). Empty for plain programs (no block,
        // no deload) so non-block users get the exact pre-E2 prompt.
        val mesocycleBlock = buildMesocycleBlock(mesocycle)
        // Effective severity (only used when injuries non-blank). Legacy/unspecified ⇒ cautious Moderate.
        val sev = injurySeverity.ifBlank { "Moderate" }

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

        val safetyBlock = """
══════════════════════════════════════════
HARD SAFETY RULES — NEVER VIOLATE (override every other directive, including the variation directive)
══════════════════════════════════════════
1. LOADED HIP-HINGE REP CAPS (three tiers):
   • Barbell hinges (deadlift, RDL, sumo deadlift, good morning, Pendlay-style): cap at ≤8 reps. NEVER prescribe barbell deadlift or barbell RDL at 9+ reps.
   • Loaded DUMBBELL hinges (DB RDL, DB stiff-leg deadlift, DB good morning): cap at ≤12 reps (lower absolute load → lower spinal risk).
   • Bodyweight/light hip-extension work (hip thrust, glute bridge, back extension, leg curl, kettlebell swing) is EXEMPT and may run high-rep.
   If the goal calls for higher-rep posterior-chain work, ROUTE it to the exempt list (hip thrust, back extension, lying/seated leg curl, glute bridge, kettlebell swing) — NEVER to a barbell pull.
2. Heavy compounds use a controlled ~2 s eccentric — NOT a deliberate 3 s+ slow eccentric. Reserve slow/tempo eccentrics for isolation movements only.
3. Heavy hinges (deadlift, RDL, good morning): do NOT program to failure — leave ≥2 RIR.
4. Injuries change exercise SELECTION, not just the notes (see the INJURY HARD-CONSTRAINTS block below).
"""

        // Prominent injury block — sits up with the other hard constraints, driven by reported severity.
        val injuryHardBlock = if (injuries.isNotBlank()) """
══════════════════════════════════════════
INJURY HARD-CONSTRAINTS — severity: $sev — NEVER VIOLATE
══════════════════════════════════════════
Reported injuries/limitations: "$injuries". This drives exercise SELECTION (not just notes). Apply the tier for the stated severity. If several injuries are listed with differing severity, apply the appropriate tier PER injury — the level here is the overall/worst.
${when (sev) {
    "Severe" -> """
SEVERE → EXCLUDE the aggravating category ENTIRELY (do not merely substitute — leave it out):
- Bad ankle ⇒ NO jogging / high-impact cardio (use stationary bike, rowing, incline walk) AND NO loaded single-leg balance work (Bulgarian / rear-foot-elevated split squat, single-leg RDL, step-ups). Use BILATERAL substitutes only (goblet/sumo squat, leg press, hand-supported split squat).
- Bad knee ⇒ NO deep loaded knee flexion and NO high-impact plyo; substitute leg press at partial ROM and box squat to a comfortable depth.
- Bad shoulder ⇒ NO overhead pressing and NO behind-neck work; substitute landmine press, incline press, neutral-grip variants."""
    "Mild" -> """
MILD → you MAY train AROUND the worst aggravators, but you MUST still include light rehabilitation / strengthening on the injured area, staged as progression (do NOT omit the area entirely):
- Bad ankle ⇒ keep bilateral work primary; add light calf/ankle stability work and (if used) hand-supported single-leg progressions; prefer low-impact cardio (bike, row, incline walk) over jogging.
- Bad knee ⇒ keep knee-friendly ROM; add light terminal-knee-extension / controlled leg work as rehab.
- Bad shoulder ⇒ keep pressing in pain-free ranges; add light external-rotation / scapular rehab. Stage all of this as light progression, not a loaded baseline."""
    else -> """
MODERATE → SUBSTITUTE the safer same-muscle variant for the worst aggravators, AND include 1–2 rehab/prehab moves for the area:
- Bad ankle ⇒ substitute a genuinely BILATERAL movement (goblet/sumo squat, leg press, hand-supported split squat) for loaded single-leg balance work (Bulgarian / rear-foot-elevated split squat, single-leg RDL, step-ups); prefer low-impact cardio over jogging.
- Bad knee ⇒ substitute leg press / box squat to comfortable depth for deep loaded knee flexion; avoid high-impact plyo.
- Bad shoulder ⇒ substitute landmine / incline / neutral-grip pressing for overhead and behind-neck work.
Include light rehab/prehab for the injured area, staged as progression."""
}}
CROSS-TIER RULES (apply at every severity): substitutes must be GENUINELY bilateral where bilateral is intended — a rear-foot-elevated / Bulgarian split squat is single-leg BY DEFINITION, so NEVER keep it and append "both feet down" / "bilateral contact at all times" cues (physically incoherent); if a single-leg movement is genuinely wanted, allow FIXED external support (hand on wall/bench) staged as light rehab. Always prefer low-impact cardio (bike, row, incline walk) over jogging for any lower-limb injury.
""" else ""

        return """
You are an expert strength & conditioning coach. Design a $daysPerWeek-day weekly training program tailored to the user below.

CRITICAL: Do NOT follow gym-culture day conventions (e.g., chest on Monday, back on Tuesday, arms on Friday). Assign muscle groups to days based purely on recovery logic. A good coach rotates exercises every single week — the same muscle can be trained with completely different exercises each week.
$safetyBlock$injuryHardBlock$restDayBlock$lockedDaysBlock$mesocycleBlock$rejectionBlock$blacklistBlock$themeBlock
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
GOAL → REP RANGE BY EXERCISE ROLE (deterministic)
══════════════════════════════════════════
Assign every exercise a ROLE — primary compound, accessory, or isolation — and pick its rep range from the table for this goal. The SAME role under the SAME goal must always get the SAME range across regenerations; do not let the variation directive drift rep ranges. NEVER apply one rep range to a whole session — each session must show a primary→accessory→isolation spread of ranges. Hinge caps override the table (hard safety rule above): barbell hinges (deadlift, RDL, sumo DL, good morning) stay ≤8 reps; loaded dumbbell hinges (DB RDL, DB stiff-leg, DB good morning) stay ≤12 reps; bodyweight/light hip-extension work (hip thrust, glute bridge, back extension, leg curl, KB swing) is exempt and may run high-rep.
${when {
    goalLower.contains("strength") -> """
Goal: build maximal strength in compound lifts.
- Rep ranges by role: primary compounds 3–6 / accessories 6–10 / isolation 8–12.
- Rest: 3–5 min between heavy sets, 2–3 min for accessories.
- Weekly volume: lower (8–12 hard sets/muscle) — intensity beats volume here. Keep isolation low.
- Effort: ~2–3 RIR on compounds, ~1–2 RIR on isolation. Never train compounds to failure.
- Progression: add weight (2.5–5 kg) when all reps are completed with ≥2 RIR.""".trimIndent()

    goalLower.contains("hypertrophy") -> """
Goal: maximise muscle growth.
- Rep ranges by role: primary compounds 6–10 / accessories 8–12 / isolation 10–15. Moderate load (~65–80% 1RM).
- Rest: 60–120 s between sets — do not exceed 120 s.
- Train each muscle group at least twice per week. Weekly volume 10–20 hard sets/muscle across both sessions.
- Order: 1–2 compounds first per session, then accessory/isolation work.
- Effort: ~2–3 RIR compounds, ~1–2 RIR isolation.
- Progression: double progression — add reps to the top of the range across all sets, then +load and reset to the bottom.""".trimIndent()

    goalLower.contains("endurance") -> """
Goal: muscular and cardiovascular endurance.
- Rep ranges by role: compounds 8–12 (barbell hinges still ≤8) / isolation 15–20. Light load (~40–60% 1RM), 2–3 sets, 30–60 s rest.
- Cardio is the priority — include 2–3 dedicated cardio sessions per week.
- Choose exercises that complement aerobic capacity: circuits, supersets, bodyweight work welcome.
- Weekly volume per muscle: lower (6–10 hard sets/week) — cardio takes the majority of the session.
- Progression: add reps or sets before increasing load.""".trimIndent()

    goalLower.contains("weight") || goalLower.contains("loss") -> """
Goal: fat loss and body recomposition.
- Rep ranges by role: compounds 8–12 (barbell hinges still ≤8) / accessories 10–15 / isolation 12–20. Rest 60–90 s (keep heart rate elevated).
- Prioritise compound multi-joint movements (highest calorie burn and muscle retention).
- Include 2 cardio sessions per week — mix HIIT and steady-state. Supersets of non-competing muscle groups welcome.
- Effort: ~1–2 RIR. Progression: add reps, then load. Avoid extremely heavy low-rep work.""".trimIndent()

    else -> """
Goal: general health and fitness.
- Rep ranges by role: primary compounds 6–10 / accessories 8–12 / isolation 10–15 (barbell hinges ≤8). Rest 90–120 s.
- Balanced mix of compound and isolation work; hit all major muscle groups across the week.
- Effort: ~2–3 RIR. Progression: double progression.""".trimIndent()
}}

══════════════════════════════════════════
WEEKLY STRUCTURE
══════════════════════════════════════════
Organise $daysPerWeek days to distribute muscle group stimulus optimally for the stated goal.
- WEEKLY VOLUME: aim ${if (experience.lowercase().contains("beginner")) "6–12" else "10–20"} hard sets per trained muscle. Beyond ~20–25 sets/week is diminishing returns.
- PER-SESSION VOLUME: cap any single muscle at ~8–10 hard sets in one session. Excess in-session sets are junk volume — split them across the week, do not stack one brutal session.
- PER-SESSION TOTAL VOLUME: keep total working sets per session ≤ ~18–20 (cardio/prehab excluded). Sessions of 22–23 sets run long and accrue fatigue even when no single muscle is over its cap.
- FREQUENCY: train each major muscle ~2×/week so weekly volume is spread, not crammed into a single session.
- Each major muscle group (chest, back, quads, hamstrings/glutes, shoulders) should be trained at least once, ideally twice per week.
- Never train the same primary muscle group on consecutive days.
- Heavy leg sessions (squat, deadlift) need at least 48 h before the next leg session.
- WEEKLY PATTERN BALANCE: across the week include horizontal push + vertical push, horizontal pull + vertical pull, a knee-dominant (squat/quad) + a hip-dominant (hinge) lower movement, and DIRECT lateral-delt + rear-delt work. Do not stack RDL + SLDL + hip thrust + deadlift + lunge in one week with no knee-dominant quad movement.
- $cardioInstruction
- Space the days evenly across the week where possible.
$splitBlock$previousPlanBlock

══════════════════════════════════════════
TIME BUDGET (applies PER training day)
══════════════════════════════════════════
The session target is $sessionDurationMinutes min. EACH training day MUST estimate within ±10 min of that — aim $sessionDurationMinutes, accept ${sessionDurationMinutes - 10}–${sessionDurationMinutes + 10} min. Self-size on attempt 1 using this estimate:
- Per strength exercise ≈ sets × (reps × 3 s work + rest seconds between sets) + ~60 s setup.
- Per cardio exercise ≈ its duration (the targetReps minutes/distance) + ~60 s.
- A day's estimate = the sum of its exercises.
Add or remove accessory work, or adjust sets/rest, to land inside the window. This NEVER overrides the per-muscle (~8–10 sets) or per-session-total (~18–20 working sets) caps or any other rule above — trim within those limits.

══════════════════════════════════════════
SESSION DESIGN RULES
══════════════════════════════════════════
- Use a PRIMARY → ACCESSORY hierarchy: lead each session with a main compound (heavier, lower-rep), then accessories and isolation (lighter, higher-rep). Avoid the flat "6 exercises × identical sets × identical reps" template.
- DE-DUPLICATE movement patterns: no two near-identical patterns in one session (e.g. do not pair RDL with single-leg stiff-leg deadlift, or barbell bench with dumbbell bench as two separate slots).
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
- INJURY GATING (changes SELECTION, not just notes): apply the INJURY HARD-CONSTRAINTS block above at its stated severity ($sev) for "$injuries". At SEVERE, EXCLUDE the aggravating category outright; at MODERATE, REPLACE the worst aggravators with safer same-muscle variants; at MILD, train AROUND them but still include light rehab. Do NOT merely add a caution note to a risky exercise.
  • e.g. ankle instability → down-rank/exclude loaded single-leg balance work (Bulgarian/rear-foot-elevated split squat, single-leg RDL, step-ups). When you down-rank these, SUBSTITUTE a genuinely BILATERAL movement (goblet/sumo squat, leg press, hand-supported split squat) — do NOT keep the single-leg exercise and bolt on contradictory cues. A rear-foot-elevated / Bulgarian split squat is single-leg BY DEFINITION; NEVER append "both feet down" / "bilateral contact at all times" to it (physically incoherent). If a single-leg movement is genuinely wanted, allow FIXED external support (hand on wall/bench) and stage it as light rehab progression, not a loaded baseline. For cardio prefer low-impact (bike, row, incline walk) over jogging.
- Where safe (REQUIRED at MILD/MODERATE), include 1–2 low-load rehab/prehab exercises targeting the injured area, staged as light progression rather than a loaded baseline.""" else ""}
${if (priorityMuscles.isNotBlank()) """
- Priority muscles ($priorityMuscles): allocate at least 2 extra sets compared to non-priority groups. Train them twice per week.""" else ""}

══════════════════════════════════════════
PROGRESSION & EFFORT (every exercise)
══════════════════════════════════════════
EVERY exercise's notes MUST state (a) a target effort as RIR or RPE, and (b) a concrete progression rule. "Establish baseline" alone is NOT acceptable.
- Default effort: ~2–3 RIR on compounds, ~1–2 RIR on isolation.
- Default progression: DOUBLE PROGRESSION — add reps to the top of the range across all sets, then add load and reset to the bottom of the range.
Use the workout history above to set starting weights and reps intelligently:
- Progressing (weight increasing across sessions): add 2.5 kg (intermediate/advanced) or 5 kg (beginner) to targetWeightKg.
- Plateaued (3+ sessions at same weight): push reps to the top of the range, or note a technique cue, before adding load.
- Regressing: reduce weight 5–10% and note "deload — rebuild form".
- No history: set a conservative baseline (~55–65% estimated 1RM); still give the RIR + progression rule, not just "establish baseline".

══════════════════════════════════════════
CARDIO
══════════════════════════════════════════
For cardio exercises, use these name conventions so the app can identify them: Easy Jog, Outdoor Run, Tempo Run, Interval Run.
Cardio JSON fields: sets=1, targetReps = duration or distance only (e.g. "30 min", "5 km", "6×400m"), targetWeightKg=0, recommendedRestSeconds=60.

══════════════════════════════════════════
NOTES FIELD
══════════════════════════════════════════
Keep notes concise, but they MUST contain the target effort (RIR/RPE) AND the progression rule. Common exercise names are fine ("lateral raise", "calf raise"), but NEVER assert incorrect mechanisms (e.g. "calf raises strengthen ankle stabilisers", "targets the inner chest", "increases bicep peak"). Do NOT use "peak" as a NOUN for muscle shape ("bicep peak", "peak stretch"). "Peak contraction" as a squeeze cue (hold the shortened position) IS allowed.

══════════════════════════════════════════
SELF-CHECK BEFORE OUTPUT — silently fix any failures, then emit
══════════════════════════════════════════
- No barbell hinge above 8 reps; no loaded DB hinge above 12 reps.
- Rep ranges vary by exercise role within each session (not monotone).
- Every exercise's notes carry an RIR/RPE target AND a progression rule.
- No muscle exceeds ~10 hard sets in a session; total working sets per session ≤ ~20; weekly volume within range.
- No duplicate movement pattern within a session.
- injury_flags applied to SELECTION, not just notes (and no rear-foot-elevated / single-leg movement carries "both feet down" cues).
- Weekly plan includes direct lateral + rear-delt work and a knee-dominant quad movement.
- Notes assert no incorrect mechanisms and use no "peak" muscle-shape noun.

══════════════════════════════════════════
OUTPUT — valid JSON only, no prose, no markdown fences
══════════════════════════════════════════
Do your reasoning silently (or fold it into the "rationale" field below) — do NOT write a long visible planning preamble before the JSON. Your FIRST output character must be the opening "{" of the JSON object, and you must emit the ENTIRE object through its closing "}" in one go. A response that runs out of room before the JSON is complete cannot be used.
ALSO include a top-level "rationale" string (a sibling of "days"): a concise, plain-language explanation (2–4 sentences) of WHAT changed in this plan versus the user's recent training / last week's plan and WHY — your own coaching reasoning, referencing the ACTUAL exercises and changes in the plan you just produced (e.g. "Added posterior-chain volume because your hamstrings lagged; swapped barbell bench for dumbbell to ease the flagged shoulder; bumped squat load after three progressing sessions."). Speak directly to the user, no jargon. If there is no meaningful history to compare against (new user), briefly explain how the plan was built for their goal instead.
{
  "rationale": "Short plain-language explanation of what changed in this plan and why.",
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
          "notes": "RPE 8 (~2 RIR). Double progression: build to 10 reps across all sets, then +2.5 kg and reset to 8.",
          "recommendedRestSeconds": 120
        }
      ]
    }
  ]
}

dayOfWeek: 1=Monday … 7=Sunday. ${
            if (restDays.isNotEmpty())
                "Output training on EXACTLY these weekdays: ${com.migul.treningsprogram.domain.TrainingDaySelection.dayNames(com.migul.treningsprogram.domain.TrainingDaySelection.trainingDaysFrom(restDays))} — one day object per training weekday, and NEVER a day object on a rest day (${com.migul.treningsprogram.domain.TrainingDaySelection.dayNames(restDays)})."
            else
                "Output exactly $daysPerWeek days, spaced optimally across the week."
        }
        """.trimIndent()
    }

    /**
     * B08: the FIXED-SCHEDULE directive when the user pinned specific rest days. Empty when no rest
     * days are pinned (count mode ⇒ byte-for-byte the pre-B08 prompt). The deterministic Kotlin check
     * in [generateAdaptedProgram] is authoritative; this just steers the model to comply first time.
     */
    private fun buildRestDayBlock(restDays: Set<Int>): String {
        if (restDays.isEmpty()) return ""
        val training = com.migul.treningsprogram.domain.TrainingDaySelection.trainingDaysFrom(restDays)
        return """

══════════════════════════════════════════
FIXED WEEKLY SCHEDULE — HARD (override day-placement choices)
══════════════════════════════════════════
The user has chosen specific REST days. You MUST train ONLY on the remaining weekdays:
- TRAINING days (one day object each, these exact dayOfWeek values): ${com.migul.treningsprogram.domain.TrainingDaySelection.dayNames(training)}
- REST days (NO day object, NO training): ${com.migul.treningsprogram.domain.TrainingDaySelection.dayNames(restDays)}
Produce exactly ${training.size} training days, one per listed training weekday. Never place a workout on a rest day. Distribute muscle groups/recovery across the available training days.
"""
    }

    /**
     * B09: the ALREADY-TRAINED (locked) days block for a preserve-logged regeneration. Lists each
     * logged day's exercises and instructs the model to reproduce those days verbatim and rebalance
     * the OTHER training days around them. Empty when nothing is locked (a fresh full generation).
     */
    private fun buildLockedDaysBlock(lockedExercises: List<PlannedExercise>): String {
        if (lockedExercises.isEmpty()) return ""
        val byDay = lockedExercises.groupBy { it.dayOfWeek }.toSortedMap()
        val lines = byDay.entries.joinToString("\n") { (day, exs) ->
            val label = com.migul.treningsprogram.domain.TrainingDaySelection.dayName(day)
            val items = exs.sortedBy { it.orderInDay }.joinToString(", ") { e ->
                val w = if (e.targetWeightKg > 0f) " @${e.targetWeightKg}kg" else ""
                "${e.exerciseName} ${e.sets}×${e.targetReps}$w"
            }
            "  $label: $items"
        }
        return """

══════════════════════════════════════════
ALREADY TRAINED THIS WEEK — FIXED, DO NOT CHANGE THESE DAYS
══════════════════════════════════════════
The user has already trained these days this week. Reproduce them EXACTLY (same dayOfWeek, same exercises) in your output, and design EVERY OTHER training day around them:
$lines
Rebalance the remaining days against this already-trained work: manage recovery (don't put the same primary muscle the day before/after a fixed day), spread weekly volume accounting for what was already done, and do NOT repeat the fixed days' exercises elsewhere this week.
"""
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
        injurySeverity: String = "",
        priorityMuscles: String = "",
        dislikedExercises: String = "",
        muscleFocus: String = "",
        onProgress: (String) -> Unit = {}
    ): Result<List<PlannedExercise>> = runCatching {
        val weekStart = thisMonday()
        // Effective severity (only used when injuries non-blank). Legacy/unspecified ⇒ cautious Moderate.
        val sev = injurySeverity.ifBlank { "Moderate" }
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
            goal.lowercase().contains("strength") -> "primary compounds 3–6, accessories 6–10, isolation 8–12"
            goal.lowercase().contains("endurance") -> "compounds 8–12 (barbell hinges ≤8), isolation 15–20, shorter rest"
            goal.lowercase().contains("loss") || goal.lowercase().contains("weight") -> "compounds 8–12 (barbell hinges ≤8), accessories 10–15, isolation 12–20"
            else -> "primary compounds 6–10, accessories 8–12, isolation 10–15 (hypertrophy)"
        }

        val prompt = buildString {
            appendLine("You are an expert personal trainer. Generate a single training day workout for $dayName.")
            appendLine()
            appendLine("GOAL: $goal")
            appendLine("EXPERIENCE: $experience")
            appendLine("SESSION DURATION: $sessionDurationMinutes minutes")
            appendLine("TIME BUDGET: this day MUST estimate within ±10 min of $sessionDurationMinutes (aim $sessionDurationMinutes, accept ${sessionDurationMinutes - 10}–${sessionDurationMinutes + 10}). Estimate ≈ per strength exercise: sets × (reps × 3 s work + rest seconds between sets) + ~60 s setup; per cardio exercise: its duration + ~60 s. Adjust accessories/sets/rest to hit the window (without exceeding ~10 sets/muscle or ~18–20 working sets total).")
            appendLine("TARGET REP RANGE: $repRange")
            appendLine("AVAILABLE EQUIPMENT: $equipStr")
            if (equipmentNotes.isNotBlank()) appendLine("EQUIPMENT NOTES: $equipmentNotes")
            appendLine()
            appendLine("HARD SAFETY RULES (never violate):")
            appendLine("- Loaded hip-hinge rep caps (three tiers): barbell hinges (deadlift, RDL, sumo DL, good morning) ≤8 reps; loaded DUMBBELL hinges (DB RDL, DB stiff-leg, DB good morning) ≤12 reps; bodyweight/light hip-extension work (hip thrust, glute bridge, back extension, leg curl, KB swing) is exempt and may run high-rep. For higher-rep posterior chain, route to the exempt list — never to a barbell pull. Never output barbell deadlift/RDL at 9+ reps.")
            appendLine("- Slow/tempo eccentrics on isolation only; heavy compounds use a controlled ~2 s eccentric and are not taken to failure (leave ≥2 RIR).")
            appendLine()
            if (injuries.isNotBlank()) {
                appendLine("INJURIES AND LIMITATIONS — severity: $sev (HARD CONSTRAINTS — change SELECTION, not just notes) for: $injuries")
                when (sev) {
                    "Severe" -> {
                        appendLine("- SEVERE → EXCLUDE the aggravating category ENTIRELY (do not merely substitute): bad ankle ⇒ NO jogging/high-impact cardio (use bike, rowing, incline walk) AND NO loaded single-leg balance work (Bulgarian/rear-foot-elevated split squat, single-leg RDL, step-ups) — bilateral substitutes only (goblet/sumo squat, leg press, hand-supported split squat); bad knee ⇒ NO deep loaded knee flexion / high-impact plyo (sub leg press partial ROM, box squat to comfortable depth); bad shoulder ⇒ NO overhead/behind-neck pressing (sub landmine/incline press, neutral-grip).")
                    }
                    "Mild" -> {
                        appendLine("- MILD → you MAY train AROUND the worst aggravators, but you MUST still include light rehabilitation/strengthening on the injured area, staged as progression (do NOT omit the area). Keep bilateral/pain-free work primary; prefer low-impact cardio over jogging for any lower-limb injury.")
                    }
                    else -> {
                        appendLine("- MODERATE → SUBSTITUTE the safer same-muscle variant for the worst aggravators AND include 1–2 rehab/prehab moves: bad ankle ⇒ substitute a genuinely BILATERAL movement (goblet/sumo squat, leg press, hand-supported split squat) for loaded single-leg balance work (Bulgarian/rear-foot-elevated split squat, single-leg RDL, step-ups); bad knee ⇒ sub leg press/box squat to comfortable depth for deep loaded knee flexion; bad shoulder ⇒ sub landmine/incline/neutral-grip for overhead/behind-neck pressing. Prefer low-impact cardio over jogging.")
                    }
                }
                appendLine("- ALWAYS (every tier): a rear-foot-elevated / Bulgarian split squat is single-leg BY DEFINITION — never keep it and append \"both feet down\" / \"bilateral contact at all times\" cues (physically incoherent); SUBSTITUTE a genuinely bilateral movement instead. If a single-leg movement is genuinely wanted, allow FIXED external support (hand on wall/bench) staged as light rehab.")
                appendLine("- Include 1-2 light rehab/strengthening accessories for the area (REQUIRED at Mild/Moderate)")
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
            appendLine("- Use a primary → accessory hierarchy: lead with a main compound (heavier, lower-rep), then accessories/isolation (lighter, higher-rep). Do NOT apply one rep range to every exercise.")
            appendLine("- No duplicate movement patterns in the session (e.g. don't pair RDL with single-leg stiff-leg deadlift)")
            appendLine("- Start with compound movements, finish with isolation")
            appendLine("- Check the rest of week context and avoid muscle group conflicts on adjacent days")
            appendLine("- Set/rep targets must match the role-based rep ranges above; cap any single muscle at ~10 hard sets, and keep total working sets for the session ≤ ~18–20 (cardio/prehab excluded)")
            appendLine("- Every exercise's notes MUST include a target effort (RIR/RPE) AND a progression rule (default: double progression). Notes may use common exercise names but must not assert incorrect mechanisms, and must NOT use \"peak\" as a noun for muscle shape (\"bicep peak\", \"peak stretch\"); \"peak contraction\" as a squeeze cue is allowed.")
            appendLine()
            append("Return ONLY valid JSON, no prose, no markdown fences:")
            appendLine("""{"days":[{"dayOfWeek":$dayOfWeek,"name":"$dayName","exercises":[{"name":"Exercise Name","sets":3,"targetReps":"8-12","targetWeightKg":0,"notes":"RPE 8 (~2 RIR); double progression: +reps to top of range, then +load","recommendedRestSeconds":90}]}]}""")
        }

        val response = withAiRetry {
            claudeApi.sendMessage(
                ClaudeRequest(messages = listOf(ClaudeRequest.Message(content = prompt)))
            )
        }
        val responseText = response.text()
        promptLog.add("single_day_${dayName.lowercase()}", prompt, responseText)

        // B10: this single-attempt path has no retry loop, so a no-JSON / truncated response surfaces
        // via its own onFailure (a clear error, not a stall). Use the non-throwing extractor + the
        // shared truncation signal so the message tells the user WHY it failed (cut off vs no JSON)
        // rather than a generic "No JSON found". B08/B09 build on this same hardened seam.
        val cleanJson = extractJsonOrNull(responseText)
            ?: throw IllegalStateException(
                if (isLikelyTruncated(responseText, response.stopReason))
                    "Couldn't generate $dayName — the response was cut off before a plan was produced. Please try again."
                else
                    "Couldn't generate a valid plan for $dayName. Please try again."
            )
        val exercises = parseProgram(cleanJson).filter { it.dayOfWeek == dayOfWeek }
        if (exercises.isEmpty()) throw IllegalStateException("No exercises returned for $dayName")
        // Re-stamp correct weekStart (parseProgram calls thisMonday() internally)
        exercises.map { it.copy(weekStart = weekStart) }
    }

    private fun parseProgram(cleanJson: String): List<PlannedExercise> {
        val weekStart = thisMonday()
        // Tolerate minor model JSON-dirt (trailing comma before } / ]) that gson would otherwise reject.
        val program = gson.fromJson(stripTrailingCommas(cleanJson), ProgramJson::class.java)
            ?: throw IllegalStateException("AI response did not contain a JSON object")
        // A balanced/extracted object with no usable days is a failed generation, not a silent empty
        // plan — surface it so the existing onFailure path tells the user instead of saving nothing.
        val days = program.days ?: emptyList()
        val now = System.currentTimeMillis()
        return days.filterNotNull().flatMap { day ->
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

    // B2: pull the top-level "rationale" string out of the generation response. Missing/blank ⇒ ""
    // (neutral state). Kept separate from parseProgram so the single-day path is unaffected.
    private fun parseRationale(cleanJson: String): String =
        runCatching { gson.fromJson(cleanJson, ProgramJson::class.java).rationale.trim() }
            .getOrDefault("")

    /** E2 (L1 + M2): the mesocycle / deload directive injected into the generation prompt. */
    private fun buildMesocycleBlock(m: MesocycleContext): String = m.promptBlock()

    // Delegates to the hardened package-level [extractJsonOrThrow]; kept as a member so existing call
    // sites are unchanged.
    private fun extractJson(text: String): String = extractJsonOrThrow(text)
}

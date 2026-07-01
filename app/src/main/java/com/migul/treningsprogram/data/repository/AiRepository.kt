package com.migul.treningsprogram.data.repository

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.migul.treningsprogram.data.ExerciseDbResolver
import com.migul.treningsprogram.data.MuscleClassifier
import com.migul.treningsprogram.data.PromptLog
import com.migul.treningsprogram.data.RejectionLog
import com.migul.treningsprogram.data.ResolveHints
import com.migul.treningsprogram.data.api.ClaudeApiService
import com.migul.treningsprogram.data.api.model.ClaudeRequest
import com.migul.treningsprogram.data.api.model.ClaudeResponse
import com.migul.treningsprogram.data.db.entity.PlannedExercise
import com.migul.treningsprogram.data.db.entity.WorkoutSession
import com.migul.treningsprogram.domain.StallDetector
import com.migul.treningsprogram.domain.WorkoutTimeEstimator
import com.migul.treningsprogram.domain.model.OnboardingQuestion
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
 * H1: retry policy for the GENERATION call specifically. Identical to [isTransientAiError] EXCEPT a
 * [SocketTimeoutException] is NOT retryable.
 *
 * A generation request is non-streaming with a large output budget (maxTokens=16384). If a call hits
 * the OkHttp callTimeout (240s) it means the model was still generating a too-large/too-slow response —
 * re-issuing it IMMEDIATELY with no backoff just times out again, burning ~2×240≈480s (~8 min) before
 * any error surfaces (the observed "stuck on Attempt 2 of 3" stall). Treating a SocketTimeout as
 * NON-retryable makes that case fail fast at ONE callTimeout with a specific, user-visible "timed out"
 * error. Genuine transient blips (5xx / 429 / a fast-failing IOException like a connection reset) are
 * STILL retried, so reliability for real hiccups is unchanged. Used ONLY by the generation call; every
 * other caller keeps the default [isTransientAiError].
 */
fun isTransientGenerationError(t: Throwable): Boolean =
    isTransientAiError(t) && t !is SocketTimeoutException

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
suspend fun <T> withAiRetry(
    maxAttempts: Int = 2,
    isRetryable: (Throwable) -> Boolean = ::isTransientAiError,
    block: suspend () -> T
): T {
    var lastException: Throwable? = null
    repeat(maxAttempts) { attempt ->
        try {
            return block()
        } catch (t: Throwable) {
            if (!isRetryable(t)) throw t   // non-retryable: fail fast
            lastException = t
            // On last attempt fall through to re-throw; otherwise retry immediately
        }
    }
    throw lastException!!
}

// ── H1: overall generation wall-clock deadline ───────────────────────────────────────────────────
//
// Generation must ALWAYS reach a terminal outcome — never sit on "Attempt N of 3" indefinitely. OkHttp
// callTimeout (240s) bounds each individual call, but an immediate no-backoff retry of a timed-out
// generation call could burn ~2×240s, and across attempts the wall-clock could stack much higher. We
// cap the WHOLE generate flow with a single overall deadline and convert a timeout into a clear,
// terminal, user-visible error via the existing onFailure path (instead of a frozen progress counter).

/** Friendly, terminal message shown when the overall generation deadline is hit. */
internal const val GENERATION_TIMEOUT_MESSAGE =
    "Generation took too long and was stopped. Please check your connection and try again."

/**
 * Overall wall-clock budget for one [AiRepository.generateAdaptedProgram] call (all attempts).
 *
 * Arithmetic: a NORMAL run makes 2 sequential API calls (attempt-1 generate + the LLM quality review);
 * a fully-rejected run makes ≤3 generate calls (the verify step is skipped whenever a deterministic
 * check fails) plus at most one verify. Each call is bounded by OkHttp callTimeout=240s, but a
 * SUCCESSFUL call returns far faster than that pathological ceiling — even on a slow link 2–3 successful
 * calls land comfortably inside 360s. The degenerate case (a stalled generate call) is now handled
 * primarily by [isTransientGenerationError] (a timed-out generate is NOT re-issued, so it fails fast at
 * one 240s callTimeout with a specific error); this 360s ceiling is the belt-and-suspenders backstop —
 * well under the previously observed ~8 min (~480s+) stall — for any residual accumulation or non-network
 * hang. 360s leaves margin so a legitimate slow-link multi-attempt run is not falsely cut off.
 */
internal const val GENERATION_OVERALL_DEADLINE_MS: Long = 360_000L

/**
 * Runs [block] under an overall [timeoutMs] wall-clock deadline. A timeout is converted into a
 * terminal, friendly [IllegalStateException] (NOT a raw [kotlinx.coroutines.TimeoutCancellationException])
 * so the caller's `runCatching` turns it into `Result.failure` and the existing onFailure path shows a
 * clear message. The catch is intentionally NARROW (only [kotlinx.coroutines.TimeoutCancellationException]):
 * a genuine external cancellation propagates as an ordinary [kotlinx.coroutines.CancellationException],
 * which this does NOT catch, so normal coroutine cancellation is never swallowed here. Package-level +
 * generic so the timeout-to-message contract is unit-testable without an [AiRepository] instance.
 */
internal suspend fun <T> withGenerationDeadline(
    timeoutMs: Long = GENERATION_OVERALL_DEADLINE_MS,
    block: suspend () -> T
): T = try {
    kotlinx.coroutines.withTimeout(timeoutMs) { block() }
} catch (e: kotlinx.coroutines.TimeoutCancellationException) {
    throw IllegalStateException(GENERATION_TIMEOUT_MESSAGE)
}

// ── H4: parse an Anthropic SSE stream back into a ClaudeResponse ──────────────────────────────────
//
// Full-program generation is sent with stream=true (FIX-A: the non-streaming time-to-first-byte ≈ the
// whole generation time was crossing OkHttp's 180 s readTimeout on heavier state; streaming turns the
// readTimeout into an inter-event stall guard instead). This pure parser reconstructs the SAME
// ClaudeResponse the non-streaming endpoint would have returned, so EVERY downstream consumer
// (extractJsonOrNull, isLikelyTruncated, .text(), .stopReason, parseProgram) is unchanged. Kept
// package-level + pure (no AiRepository / no network) so it is unit-testable in isolation, mirroring the
// F3 / S3 / B10 helper pattern.
//
// SSE shape (validated live): each event is an `event:`/`data:` line pair. We only read `data:` lines;
//   - blank / `[DONE]` payloads are ignored;
//   - {type:"content_block_delta", delta:{type:"text_delta", text:…}} → append the text;
//   - {type:"message_delta", delta:{stop_reason:…}} → capture stop_reason;
//   - {type:"error", …} → throw IOException so it flows through the existing transient/retry +
//     friendly-error handling (IOException is classified transient);
//   - message_start / content_block_start / content_block_stop / ping / message_stop → ignored.
// A `data:` line whose JSON is incomplete (a stream cut mid-event) fails to parse and is skipped, so a
// truncated stream still yields the partial accumulated text (downstream truncation detection handles it).
internal fun parseClaudeStream(sse: String): ClaudeResponse {
    val accumulated = StringBuilder()
    var stopReason: String? = null
    for (rawLine in sse.lineSequence()) {
        val line = rawLine.trim()
        if (!line.startsWith("data:")) continue
        val payload = line.substring("data:".length).trim()
        if (payload.isEmpty() || payload == "[DONE]") continue
        val obj = runCatching { JsonParser.parseString(payload).asJsonObject }.getOrNull() ?: continue
        when (obj.get("type")?.asString) {
            "content_block_delta" -> {
                val delta = obj.get("delta")?.takeIf { it.isJsonObject }?.asJsonObject
                if (delta?.get("type")?.asString == "text_delta") {
                    accumulated.append(delta.get("text")?.asString ?: "")
                }
            }
            "message_delta" -> {
                val sr = obj.get("delta")?.takeIf { it.isJsonObject }?.asJsonObject?.get("stop_reason")
                if (sr != null && !sr.isJsonNull) stopReason = sr.asString
            }
            "error" -> {
                val errObj = obj.get("error")?.takeIf { it.isJsonObject }?.asJsonObject
                val msg = errObj?.get("message")?.asString
                    ?: errObj?.get("type")?.asString
                    ?: "AI stream error"
                throw IOException("Claude streaming error: $msg")
            }
            else -> { /* message_start, content_block_start/stop, ping, message_stop → ignore */ }
        }
    }
    return ClaudeResponse(
        content = listOf(ClaudeResponse.ContentBlock(type = "text", text = accumulated.toString())),
        stopReason = stopReason
    )
}

// ── H5: consume the streaming body OFF the main thread ────────────────────────────────────────────
//
// `claudeApi.sendMessageStreaming` is a Retrofit SUSPEND call returning a `@Streaming`
// [okhttp3.ResponseBody]. A suspend call resumes on the CALLER's dispatcher, and generation is launched
// from `viewModelScope` (Dispatchers.Main), so the BLOCKING `.string()` read of the streaming body would
// otherwise run on the main thread for the whole ~60–180 s generation → ANR. This helper forces the
// blocking read onto the supplied IO dispatcher and CLOSES the body with `use {}` (so the connection is
// always released, even on a parse exception). Kept package-level (like [parseClaudeStream]) so the
// dispatcher contract is unit-testable WITHOUT an [AiRepository] instance.
internal suspend fun consumeClaudeStream(body: okhttp3.ResponseBody, io: CoroutineDispatcher): ClaudeResponse =
    withContext(io) { body.use { parseClaudeStream(it.string()) } }

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

// ── G1 / P2: direction-aware, DURATION-DERIVED per-day time-budget feedback ───────────────────────
//
// The deterministic ±10-min per-day duration gate is kept STRICT (the accepted window is unchanged).
// This pure helper makes the retry feedback DIRECTION-AWARE — a day under the floor is told to ADD
// work; a day over the ceiling is told to TRIM.
//
// The reject CONDITION is byte-for-byte the old one: a non-null message is returned in EXACTLY the
// cases the gate rejects (est < target-10, or est > target+10) and null when the day is inside the
// window. So the strict gate is untouched — only the wording the model sees on the next attempt
// changes. Package-level + pure so it is unit-testable without an AiRepository instance (matching
// the F3 / S3 / B10 helper pattern).
//
// P2 (generation-quality overhaul 2026-07): the wording is now GOAL-GENERAL and DURATION-DERIVED. The
// old feedback hard-coded a hypertrophy-only "120 s rest on EVERY set + a FULL ~19–20 working sets →
// ~46 min" hammer. That fixed set count / minute target / 120 s ceiling made a session effectively
// bounded at ~45–50 min regardless of the chosen duration, and only worked for hypertrophy. The under-
// fill levers (rest → reps → sets → accessory) are kept, but framed against THIS goal's rest band and
// THIS session's duration, with NO fixed set count, minute target, or rest ceiling. Volume/rest/count
// are sized to the actual duration; the prompt above already tells the model each goal's band.
internal fun dayDurationFeedback(
    day: Int,
    estimateMinutes: Int,
    targetMinutes: Int
): String? {
    val low = targetMinutes - 10
    val high = targetMinutes + 10
    return when {
        estimateMinutes < low ->
            "Day $day estimates ~$estimateMinutes min — that is UNDER the target window " +
                "($low–$high min, aim $targetMinutes). ADD work to this day so it CLEARS the $low-min " +
                "floor, in THIS order until it lands in-window: (1) raise inter-set REST toward the TOP " +
                "of THIS goal's rest band — rest is the #1 time lever and short rest is the main cause of " +
                "under-time days (do not exceed the goal's own rest band); (2) raise reps toward the TOP " +
                "of each exercise's role range; (3) add a set to an accessory/isolation exercise; (4) add " +
                "one more accessory exercise. Size to the SESSION DURATION, not to a fixed set count; keep " +
                "volume goal- and duration-appropriate; CLEAR the floor — do NOT pad with junk volume. " +
                "Do NOT shorten it — it is already too short."
        estimateMinutes > high ->
            "Day $day estimates ~$estimateMinutes min — that is OVER the target window " +
                "($low–$high min, aim $targetMinutes). TRIM this day so it drops to about " +
                "$targetMinutes min: remove an accessory exercise, or reduce sets or shorten rest."
        else -> null
    }
}

// ── Phase-2 SALVAGE: deterministic auto-trim of OVER-target days back into the window ──────────────
//
// The user reversed the earlier "no salvage" stance (2026-06-27): when an attempt produces a complete,
// otherwise-valid plan that fails the strict gate ONLY because some day(s) ESTIMATE OVER the ceiling,
// the app should deterministically TRIM those days back into [target-10, target+10] and SAVE, instead of
// looping to a no-save failure. This is layered STRICTLY AFTER the gate's estimate — it NEVER alters
// [WorkoutTimeEstimator], the ±10-min window, or any threshold (the gate math stays byte-for-byte). It
// only ever (1) lowers recommendedRestSeconds DOWNWARD toward a 60 s floor (never below 60, never above the
// original), (2) drops whole SETS, and (3) removes whole trailing exercises; it NEVER edits reps / weight /
// notes, so it cannot introduce a rep-range-by-role or notes violation. UNDER-time days are left untouched
// (under-fill stays the model's job — we never auto-add volume); locked (already-logged) days are skipped.
//
// For each NON-locked day estimating > high, in this LEVER ORDER, re-estimating after every step until
// est <= high. REST goes FIRST because it reclaims the most minutes with ZERO loss of exercises/sets/muscle
// coverage, and validateProgram never penalises low rest (60 s is the hypertrophy/weight-loss band minimum)
// — live-verified as the most reliable salvage (4/4 vs 3/4 for sets+removal alone):
//   (1) lower recommendedRestSeconds of the exercise with the CURRENT highest rest by 15 s (floor 60),
//       repeating until no exercise is above 60 s.
//   (2) drop ONE set from the trailing exercise (highest orderInDay) that still has sets > 2 and is NOT
//       the day's primary (lowest orderInDay); re-pick the trailing eligible exercise each pass.
//   (3) if still over and no safe set-drop remains, remove a whole trailing non-primary exercise ONLY IF
//       (i) its muscle group [MuscleClassifier.displayName] is still covered by another remaining exercise
//       that day, (ii) the day stays >= low after removal, and (iii) the day keeps >= 4 exercises.
//
// Returns the trimmed list when EVERY non-locked day lands in [low, high]; otherwise null (an over-day
// could not reach <= high without dropping under the floor / running out of safe trims, OR some day is
// UNDER the floor) — the caller treats null as a genuine no-save failure. Pure + package-level so it is
// unit-testable without an [AiRepository] instance (mirrors [dayDurationFeedback]).
private const val TRIM_REST_FLOOR_SECONDS = 60
private const val TRIM_REST_STEP_SECONDS = 15
internal fun trimOverflowToWindow(
    exercises: List<PlannedExercise>,
    targetMinutes: Int,
    lockedDays: Set<Int>
): List<PlannedExercise>? {
    val low = targetMinutes - 10
    val high = targetMinutes + 10

    val resultByDay = LinkedHashMap<Int, List<PlannedExercise>>()

    for ((day, rawDay) in exercises.groupBy { it.dayOfWeek }) {
        val ordered = rawDay.sortedBy { it.orderInDay }
        // Locked (already-logged) days are preserved verbatim and never gated/trimmed here.
        if (day in lockedDays) {
            resultByDay[day] = ordered
            continue
        }

        var working = ordered
        var est = WorkoutTimeEstimator.estimateDayMinutes(working)
        // Only OVER days are trimmed. In-window AND under-window days are returned unchanged (an under
        // day makes the whole plan un-salvageable — caught by the final window check below).
        if (est <= high) {
            resultByDay[day] = working
            continue
        }

        var removedAny = false

        // (1) REST DOWN to the 60 s floor — lower the CURRENT highest-rest exercise by 15 s each pass,
        // until no exercise is above 60 s. Reclaims the most minutes with ZERO loss of sets/exercises/
        // coverage; rest only ever DECREASES (never < 60, never above its original value). A single 15 s
        // step is far smaller than the 20-min window, so this lever never undershoots the floor.
        while (est > high) {
            val restTarget = working
                .filter { it.recommendedRestSeconds > TRIM_REST_FLOOR_SECONDS }
                .maxByOrNull { it.recommendedRestSeconds }
                ?: break
            val newRest = maxOf(TRIM_REST_FLOOR_SECONDS, restTarget.recommendedRestSeconds - TRIM_REST_STEP_SECONDS)
            working = working.map { if (it === restTarget) it.copy(recommendedRestSeconds = newRest) else it }
            est = WorkoutTimeEstimator.estimateDayMinutes(working)
        }

        // (2) Still over → drop ONE set at a time from the TRAILING eligible exercise.
        while (est > high) {
            val primaryOrder = working.minOf { it.orderInDay }
            val trimTarget = working
                .filter { it.orderInDay != primaryOrder && it.sets > 2 }
                .maxByOrNull { it.orderInDay }
                ?: break
            working = working.map { if (it === trimTarget) it.copy(sets = it.sets - 1) else it }
            est = WorkoutTimeEstimator.estimateDayMinutes(working)
        }

        // (3) Still over → remove a whole TRAILING non-primary exercise, guarded.
        while (est > high) {
            val primaryOrder = working.minOf { it.orderInDay }
            val removable = working
                .filter { it.orderInDay != primaryOrder }
                .sortedByDescending { it.orderInDay }
                .firstOrNull { cand ->
                    val remaining = working.filter { it !== cand }
                    val group = MuscleClassifier.displayName(cand.exerciseName)
                    val stillCovered = remaining.any { MuscleClassifier.displayName(it.exerciseName) == group }
                    val keepsFourPlus = remaining.size >= 4
                    val staysAtOrAboveLow = WorkoutTimeEstimator.estimateDayMinutes(remaining) >= low
                    stillCovered && keepsFourPlus && staysAtOrAboveLow
                }
                ?: break
            working = working.filter { it !== removable }
            est = WorkoutTimeEstimator.estimateDayMinutes(working)
            removedAny = true
        }

        // Removing an exercise can leave a gap in orderInDay; re-number contiguously (preserving order)
        // so the saved/serialized day stays 0-based like a freshly parsed plan. Set-drop-only days keep
        // their original (already-contiguous) numbering.
        resultByDay[day] = if (removedAny)
            working.sortedBy { it.orderInDay }.mapIndexed { idx, ex -> ex.copy(orderInDay = idx) }
        else working
    }

    // Salvage succeeds only if EVERY non-locked day now estimates within the strict window. Any day still
    // over (un-trimmable within the guards) OR under the floor (never auto-filled) ⇒ un-salvageable (null).
    val allInWindow = resultByDay.all { (day, list) ->
        day in lockedDays || WorkoutTimeEstimator.estimateDayMinutes(list) in low..high
    }
    if (!allInWindow) return null

    return resultByDay.values.flatten()
}

// ── H2: build the generation EXERCISE BLACKLIST from STRUCTURED names ─────────────────────────────
//
// The blacklist was previously rebuilt by RE-PARSING the rendered previous-plan string — splitting the
// text after each ":" on commas. That shattered exercise names containing a comma (e.g. "Dumbbell Push
// Press (Seated, Alternating)" → "Dumbbell Push Press (Seated" + "Alternating)") and the greedy
// ":\s*(.+)" also harvested the day-prefixed line after the "LAST GENERATED PROGRAM …:" header, yielding
// junk like "Mon: Barbell Bench Press". The fix is to never re-parse the formatted string: merge the
// already-clean recent-session names with the clean previous-plan exercise names (whole PlannedExercise
// .exerciseName values), so commas are preserved and no header/junk lines can leak in. Package-level +
// pure so it is unit-testable without an [AiRepository] instance.
internal fun buildBlacklistNames(
    recentExercises: Set<String>,
    previousPlanNames: Set<String>
): java.util.SortedSet<String> =
    (recentExercises + previousPlanNames)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toSortedSet()

// Onboarding question JSON model
private data class OQJson(
    val id: String = "",
    val question: String = "",
    val type: String = "text",
    val options: List<String> = emptyList()
)
private data class OQsJson(val questions: List<OQJson> = emptyList())

// Item 11: injury-text sufficiency check (setup wizard). Reuses OQJson for the follow-up questions.
private data class InjuryCheckJson(
    val sufficient: Boolean = true,
    val questions: List<OQJson> = emptyList()
)

// Top-level private classes so Gson can instantiate them without issues.
//
// P5 (generation-quality overhaul 2026-07): the model now DECLARES auditable §6 metadata alongside the
// core plan fields. Every metadata field is OPTIONAL with a neutral default, so (a) an old/lean response
// with none of them still parses byte-for-byte, and (b) the salvage re-serialization
// (buildProgramJsonForValidation) that constructs these with only the core args keeps compiling. The
// deterministic WorkoutTimeEstimator remains AUTHORITATIVE for the duration gate — the declared numbers
// are for audit / the model's own self-check, never a substitute the model could game.
private data class ExJson(
    val name: String = "",
    val sets: Int = 3,
    @SerializedName("targetReps") val targetReps: String = "8-12",
    @SerializedName("targetWeightKg") val targetWeightKg: Float = 0f,
    val notes: String = "",
    val recommendedRestSeconds: Int = 90,
    // P5 declared per-exercise metadata (auditable; not gated on)
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

private data class DayJson(
    val dayOfWeek: Int = 1,
    val name: String = "",
    val exercises: List<ExJson> = emptyList(),
    // P5 declared per-day metadata (auditable; the gate re-derives its own estimate)
    val dayEstimateMinutes: Float = 0f,
    val withinDurationWindow: Boolean = false
)

// P5 declared per-week metadata (§6). `blockState` is the single continue/new signal (shared with
// P1/§3.4). Absent on old responses ⇒ neutral defaults.
private data class WeekMetaJson(
    val weeklyVolumeSummary: Map<String, Int> = emptyMap(),
    val movementPatternSummary: List<String> = emptyList(),
    val durationSummary: String = "",
    val blockState: String = "",
    val constraintNotes: String = ""
)

// B2: `rationale` is a top-level sibling of `days` — the model's own plain-language reasoning
// for the plan it just produced. Absent on old responses ⇒ defaults to "" (neutral state).
// P5: `week` carries the declared per-week metadata; absent ⇒ null.
private data class ProgramJson(
    val days: List<DayJson> = emptyList(),
    val rationale: String = "",
    val week: WeekMetaJson? = null
)

data class GenerationResult(
    val exercises: List<PlannedExercise>,
    val attemptCount: Int,
    val rejectionReasons: List<String> = emptyList(),
    val rationale: String = ""
)

// Phase-2 SALVAGE: an attempt whose plan failed the gate ONLY because some non-locked day(s) ESTIMATE
// OVER the ceiling (none under) and is otherwise valid. We remember the best (smallest total overshoot)
// such candidate across attempts; if every attempt fails, the loop auto-trims it back into the window
// (see [trimOverflowToWindow]) and, if the trimmed plan passes peer review, saves it instead of failing.
private data class SalvageCandidate(
    val exercises: List<PlannedExercise>,
    val rationale: String,
    val totalOvershoot: Int
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
    private val rejectionLog: RejectionLog,
    // P3: posts a backgrounded "generation finished" notification at every program-generation
    // terminal outcome. Injected here so ALL entry points (weekly + single-day, and the P1/P2
    // rebalances that route through generateAdaptedProgram) are covered from one seam.
    private val generationNotifier: com.migul.treningsprogram.notify.GenerationNotifier
) {
    companion object {
        const val MAX_GENERATION_ATTEMPTS = 3
    }

    /**
     * H4 (v1.10.4): send [req] as an SSE stream and reconstruct the equivalent [ClaudeResponse].
     *
     * Forces `stream = true`, reads the WHOLE response body with [okhttp3.ResponseBody.string] (simplest
     * and correct — the read timeout still applies per network read, so a mid-stream stall still trips
     * OkHttp's readTimeout) and parses it with the pure [parseClaudeStream]. The reconstructed response is
     * shape-identical to the non-streaming endpoint's, so all callers (text(), stopReason, extraction,
     * truncation detection, parseProgram) are unchanged. There is no reactive/Flow UI — the stream is
     * consumed server-side only; streaming exists purely to make readTimeout an inter-event stall guard.
     *
     * H5 (v1.10.5): the WHOLE call runs inside `withContext(Dispatchers.IO)`. A Retrofit suspend call
     * resumes on the CALLER's dispatcher (here Dispatchers.Main via `viewModelScope`), so without this the
     * blocking `.string()` read of the `@Streaming` body would block the main thread for the full ~60–180 s
     * generation → ANR (the app dies; in-app logging on the frozen main thread captures nothing). Wrapping
     * the network suspend call AND the blocking read in `withContext(Dispatchers.IO)` keeps both off Main;
     * [consumeClaudeStream] additionally closes the body via `use {}`.
     */
    private suspend fun sendStreaming(req: ClaudeRequest): ClaudeResponse =
        withContext(Dispatchers.IO) {
            consumeClaudeStream(claudeApi.sendMessageStreaming(req.copy(stream = true)), Dispatchers.IO)
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
            sendStreaming(
                ClaudeRequest(messages = listOf(ClaudeRequest.Message(content = prompt)))
            ).text()
        }
        promptLog.add("onboarding_questions", prompt, responseText)
        val json = extractJson(responseText)
        val parsed = gson.fromJson(json, OQsJson::class.java)
        parsed.questions.map { OnboardingQuestion(it.id, it.question, it.type, it.options) }
    }

    /** Item 11: AI-judged sufficiency of the setup-wizard injury free-text + follow-up questions. */
    data class InjurySufficiency(val sufficient: Boolean, val questions: List<OnboardingQuestion>)

    /**
     * Item 11 — SETUP WIZARD ONLY. Judges whether the injury free-text is detailed enough to plan a
     * workout around (a concrete body part / movement restriction — not a fixed character count).
     * Empty text is a no-op (treated as sufficient — nothing to check), matching P4's empty-injury =
     * no-op decision. Insufficient text returns AI-generated follow-up questions targeting what's
     * missing. Reuses the same streaming + retry + JSON machinery as [getOnboardingQuestions]; the
     * caller degrades gracefully (never blocks the wizard) if this fails.
     */
    suspend fun checkInjurySufficiency(injuryText: String): Result<InjurySufficiency> = runCatching {
        val trimmed = injuryText.trim()
        if (trimmed.isEmpty()) return@runCatching InjurySufficiency(sufficient = true, questions = emptyList())

        val prompt = """
You are a strength coach reviewing a client's free-text description of an injury or movement limitation, deciding whether it is detailed enough to safely plan a workout program around.

Client's description:
"$trimmed"

"Sufficient" means you can identify a concrete body part AND a movement restriction you can actually program around (which exercises or planes of motion to avoid or modify). A vague mention alone ("bad knee", "shoulder", "back issues") is NOT sufficient.

If it IS sufficient, return: {"sufficient": true, "questions": []}
If it is NOT sufficient, return {"sufficient": false, "questions": [...]} with 2 to 4 SHORT follow-up questions that would gather exactly the missing detail — e.g. which specific movements hurt, how severe / painful, any diagnosis, what aggravates or relieves it, any medical restriction.

Return ONLY valid JSON — no prose, no markdown fences:
{"sufficient": true, "questions": [{"id": "q1", "question": "...", "type": "text"}]}
        """.trimIndent()

        val responseText = withAiRetry {
            sendStreaming(
                ClaudeRequest(messages = listOf(ClaudeRequest.Message(content = prompt)))
            ).text()
        }
        promptLog.add("injury_sufficiency", prompt, responseText)
        val parsed = gson.fromJson(extractJson(responseText), InjuryCheckJson::class.java)
        InjurySufficiency(
            sufficient = parsed.sufficient,
            questions = if (parsed.sufficient) emptyList()
                        else parsed.questions.map { OnboardingQuestion(it.id, it.question, it.type, it.options) }
        )
    }

    /**
     * Item 11 — rewrites the injury box into ONE clean, sufficient description that merges the user's
     * original text with their follow-up answers (a REWRITE of the whole box, not appended snippets),
     * preserving the original meaning while adding the elicited detail. Returns plain text.
     */
    suspend fun rewriteInjuryDescription(
        original: String,
        answers: List<Pair<String, String>>
    ): Result<String> = runCatching {
        val qa = answers.filter { it.second.isNotBlank() }
            .joinToString("\n") { (q, a) -> "Q: $q\nA: $a" }

        val prompt = """
Rewrite a client's injury / movement-limitation description into ONE clean, concise description a coach can plan a workout around. Merge the ORIGINAL text with the FOLLOW-UP answers into a single coherent description (a rewrite, NOT a list of appended snippets). Preserve the original meaning and add the new detail. Do NOT invent anything the client did not state. Output PLAIN TEXT only — no JSON, no markdown, no preamble, just the rewritten description.

ORIGINAL description:
"$original"

FOLLOW-UP answers:
$qa
        """.trimIndent()

        val responseText = withAiRetry {
            sendStreaming(
                ClaudeRequest(messages = listOf(ClaudeRequest.Message(content = prompt)))
            ).text()
        }
        promptLog.add("injury_rewrite", prompt, responseText)
        val cleaned = responseText.trim().trim('"').trim()
        cleaned.ifBlank { original }
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
        // H1: bound the WHOLE generate flow (all attempts) with one overall wall-clock deadline so it
        // ALWAYS reaches a terminal outcome — a timeout becomes a clear thrown error (→ Result.failure
        // → the existing onFailure message), never a frozen "Attempt N of 3". See GENERATION_OVERALL_
        // DEADLINE_MS for the arithmetic. Body indentation is left as-is to keep this a minimal diff.
        withGenerationDeadline {
        val lockedDays = lockedExercises.map { it.dayOfWeek }.toSet()
        val sessions = workoutRepository.getRecentSessions(12)
        val (history, recentExercises) = buildSessionHistory(sessions)
        val previousPlanCtx = buildPreviousPlanContext()
        val previousPlan = previousPlanCtx.text
        val variationTheme = variationThemes.random()
        val splitSuggestion = splitSuggestions[daysPerWeek]?.random()
            ?: splitSuggestions.entries.minByOrNull { kotlin.math.abs(it.key - daysPerWeek) }?.value?.random() ?: ""
        val rejectionReasons = mutableListOf<String>()

        // Phase-2 SALVAGE: best OVER-only duration-rejected candidate across attempts (smallest overshoot).
        var salvageCandidate: SalvageCandidate? = null

        // Terminal handler shared by both MAX-attempt rejection paths. The model's own clean plan is still
        // preferred (it returns earlier in the loop); ONLY here, after every attempt failed, do we attempt
        // deterministic auto-trim on the best OVER-only candidate. We never alter the estimate/window/
        // thresholds — the trim is layered strictly after the gate — and the trimmed plan must still pass
        // peer review (the overshoot attempt was never LLM-reviewed because the gate short-circuits it).
        suspend fun finalizeOrSalvage(rejections: List<String>, candidate: SalvageCandidate?): GenerationResult {
            if (candidate != null) {
                val trimmed = trimOverflowToWindow(candidate.exercises, sessionDurationMinutes, lockedDays)
                if (trimmed != null) {
                    onProgress("Trimming an over-target day to fit your $sessionDurationMinutes-min target…")
                    val trimmedJson = buildProgramJsonForValidation(trimmed, candidate.rationale)
                    val validation = validateProgram(
                        trimmedJson, daysPerWeek, sessionDurationMinutes, goal, experience, injuries, injurySeverity
                    )
                    if (validation.accepted) {
                        val salvageAttempts = rejections.mapIndexed { i, r ->
                            RejectionLog.Attempt(i + 1, r, finalFailure = false)
                        } + RejectionLog.Attempt(
                            rejections.size + 1,
                            "Auto-trimmed an over-target day into the ±10-min window; trimmed plan passed review and was saved.",
                            finalFailure = false
                        )
                        rejectionLog.addSession(salvageAttempts, succeeded = true)
                        promptLog.add(
                            "auto_trim_salvage", trimmedJson,
                            "accepted=true (deterministic over-day trim → peer review passed)"
                        )
                        val note = " (Note: one or more days were automatically trimmed to fit your " +
                            "$sessionDurationMinutes-min session target.)"
                        val rationale = (candidate.rationale.trim() + note).trim()
                        return GenerationResult(trimmed, MAX_GENERATION_ATTEMPTS, rejections.toList(), rationale)
                    }
                    promptLog.add("auto_trim_salvage", trimmedJson, "accepted=false: ${validation.reason}")
                }
            }
            val logAttempts = rejections.mapIndexed { i, r ->
                RejectionLog.Attempt(i + 1, r, i == rejections.lastIndex)
            }
            rejectionLog.addSession(logAttempts, succeeded = false)
            val reasons = rejections.mapIndexed { i, r -> "Attempt ${i + 1}: $r" }.joinToString("\n")
            throw IllegalStateException("Program rejected after $MAX_GENERATION_ATTEMPTS attempts.\n$reasons")
        }

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
                previousPlan, previousPlanCtx.exerciseNames, recentExercises, variationTheme, splitSuggestion, mesocycle,
                restDays, lockedExercises
            )
            // H1: generation-specific retry — a timed-out (SocketTimeout) generate call is NOT retried
            // (it would just time out again, burning a second callTimeout). Real transient blips (5xx/429,
            // connection-reset IOException) still retry. Other AI callers keep the default policy.
            // H4: the call now STREAMS (sendStreaming) so a slow-but-healthy long generation no longer
            // crosses the read timeout (see parseClaudeStream / NetworkModule).
            // FIX-C: log the attempt EVEN when the call throws (timeout/failure). Previously promptLog.add
            // ran only AFTER a response returned, so a timed-out attempt logged NOTHING and the user saw an
            // empty Prompt Log. Record the failed attempt, then rethrow so the existing error path is intact.
            val response = try {
                withAiRetry(isRetryable = ::isTransientGenerationError) {
                    sendStreaming(
                        // G2: adaptive thinking + a 32000-token budget were live A/B-tested on this call and
                        // REMOVED — they regressed hard (unbounded adaptive thinking on this large prompt
                        // starved the JSON: 0/3 saves, ~522 s/gen over the 360 s deadline, the full budget
                        // burned on thinking with NO JSON emitted, ~10× cost vs the proven path). With no
                        // thinking and the efficiency prompt fix, generation output is ~2200 tokens, so the
                        // ClaudeRequest default max_tokens=16384 is ample. NO thinking field is sent.
                        ClaudeRequest(messages = listOf(ClaudeRequest.Message(content = prompt)))
                    )
                }
            } catch (e: Throwable) {
                promptLog.add("generate_attempt_$attempt", prompt, "<request failed: ${e.message}>")
                throw e
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
                    return@withGenerationDeadline finalizeOrSalvage(rejectionReasons, salvageCandidate)
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
                    // G1/P2: direction-aware feedback — under-time days are told to ADD work, over-time
                    // days to TRIM. The reject CONDITION (the ±10 window) is unchanged; only the wording
                    // fed back to the next attempt differs, so the strict gate is untouched. The wording
                    // is now goal-general + duration-derived (no fixed set count / minute / rest ceiling).
                    dayDurationFeedback(day, est, sessionDurationMinutes)
                }
                .joinToString(" ")

            // ── Phase-2 SALVAGE candidate capture ─────────────────────────────────
            // If THIS attempt's plan fails the gate ONLY on duration AND every non-locked out-of-window
            // day is OVER the ceiling (none under) AND there is no empty-plan or rest-day violation, keep
            // it as a salvage candidate. We prefer the model's own clean plan (handled below); only if all
            // attempts fail do we auto-trim the SMALLEST-overshoot candidate (see finalizeOrSalvage). The
            // estimate/window/thresholds are never touched — trimming is layered strictly after the gate.
            run {
                val low = sessionDurationMinutes - 10
                val high = sessionDurationMinutes + 10
                val nonLockedDayMinutes = exercises
                    .groupBy { it.dayOfWeek }
                    .filterKeys { it !in lockedDays }
                    .mapValues { (_, dayEx) -> WorkoutTimeEstimator.estimateDayMinutes(dayEx) }
                val anyUnderWindow = nonLockedDayMinutes.values.any { it < low }
                val isOverOnlyDurationMiss = emptyPlanReason.isEmpty() && restDayReason.isEmpty() &&
                    durationReason.isNotEmpty() && !anyUnderWindow
                if (isOverOnlyDurationMiss) {
                    val overshoot = nonLockedDayMinutes.values.sumOf { maxOf(0, it - high) }
                    val best = salvageCandidate
                    if (best == null || overshoot < best.totalOvershoot) {
                        salvageCandidate = SalvageCandidate(exercises, parseRationale(cleanJson), overshoot)
                    }
                }
            }

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
                return@withGenerationDeadline GenerationResult(exercises, attempt, rejectionReasons.toList(), rationale)
            }
            // Prefer the named deterministic reason; fall back to the LLM validator reason.
            val rejectionReason = deterministicReason.ifEmpty { validation.reason }
            rejectionReasons.add(rejectionReason)
            onProgress("Attempt $attempt rejected: $rejectionReason")
            if (attempt == MAX_GENERATION_ATTEMPTS) {
                return@withGenerationDeadline finalizeOrSalvage(rejectionReasons, salvageCandidate)
            }
        }
        throw IllegalStateException("Unexpected state")
        } // withGenerationDeadline
    }.also {
        // P3: terminal outcome of a full program generation (success OR terminal failure after all
        // attempts/timeout) → a backgrounded-only system notification.
        generationNotifier.notifyProgramGenerationComplete(it.isSuccess)
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

        // H4: stream; FIX-C: log the attempt even on failure.
        val responseText = try {
            withAiRetry {
                sendStreaming(
                    ClaudeRequest(messages = listOf(ClaudeRequest.Message(content = prompt)))
                ).text()
            }
        } catch (e: Throwable) {
            promptLog.add("weekly_summary", prompt, "<request failed: ${e.message}>")
            throw e
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
            "10. INJURY GATING (HARD — reject only real contraindications), severity = $sev for \"$injuries\". Judge against THIS severity:\n" +
            "   • SEVERE → the aggravating category must be EXCLUDED outright. REJECT if any genuinely contraindicated movement is present, e.g. jogging / high-impact cardio or a loaded single-leg balance movement (Bulgarian / rear-foot-elevated split squat, single-leg RDL, step-up) for a bad ankle; deep loaded knee flexion or high-impact plyo for a bad knee; overhead or behind-neck pressing for a bad shoulder. Bilateral / low-impact substitutes only.\n" +
            "   • MODERATE → the worst aggravators must be SUBSTITUTED with a safer same-muscle variant AND 1–2 rehab/prehab moves for the area should be present. REJECT if a clear aggravator is left in unmodified; do NOT reject a sensible substitution.\n" +
            "   • MILD → light touch. A MILD plan is NOT required to change exercise selection or add rehab unless the injury text names a clear aggravator. Do NOT reject a MILD plan for keeping normal training, for adding at most one optional prehab slot, OR for adding no rehab. Only reject if it leaves a CLEARLY contraindicated movement given a NAMED aggravator, or prescribes an obviously reckless load on the injured area.\n" +
            "   Apply the appropriate tier PER injury if several are listed with differing severity (the stated level is the overall/worst). ALWAYS reject INCOHERENT injury handling regardless of tier: a single-leg / rear-foot-elevated / Bulgarian split squat carrying \"both feet down\" / \"bilateral contact at all times\" cues is physically impossible — the fix is to SUBSTITUTE a genuinely bilateral movement (goblet/sumo squat, leg press, hand-supported split squat), not to keep the single-leg exercise with contradictory cues. Goal: reject real contraindications, do NOT over-reject appropriate light rehab or a light-touch MILD plan."
        else
            "10. INJURY GATING: no injuries reported — add NO rehab/prehab requirement and make NO injury-driven rejection. Skip."
        val prompt = """
You are a sports science peer reviewer. Evaluate the following AI-generated weekly training program against the programming principles below.

Goal: $goal | Experience: $experience | Training days: $daysPerWeek${if (injuries.isNotBlank()) " | Injuries: $injuries (severity: $sev)" else ""}

PROGRAM JSON:
$planJson

Check ALL of the following. Reject ONLY on genuine violations — do NOT penalise reasonable coaching choices, exercise variation, or a plan that sits slightly outside a numeric guideline band. Items marked HARD are always grounds for rejection; everything else is a QUALITY judgement — reject only when the plan is clearly poor training for this goal/experience, not for a small deviation. When rejecting, name the single most critical issue only.

SOFT-BAND GUARD (read first): the rep/rest/volume numbers below are WIDE GUIDELINE bands, not hard limits. A plan being a little outside a band — a slightly higher or lower rep count, a rest value inside the goal's wider band, a set count that fits the chosen session duration — is NOT grounds for rejection. NEVER hard-reject on a §5-style numeric range. The ONLY hard numeric overrides are the loaded-hinge rep caps (#1).

1. SAFETY — LOADED HINGE REP CAPS (HARD): No barbell hinge (deadlift, RDL, sumo DL, good morning, Pendlay-style) is prescribed above 8 reps, AND no loaded dumbbell hinge (DB RDL, DB stiff-leg deadlift, DB good morning) is prescribed above 12 reps. Bodyweight/light hip-extension work (hip thrust, glute bridge, back extension, leg curl, kettlebell swing) is exempt and may run high-rep. Higher-rep posterior-chain work must be routed to that exempt list, NOT to a barbell pull.
2. DAYS (HARD): The plan contains exactly $daysPerWeek training days.
3. RECOVERY (HARD): No primary muscle group (chest, back, quads, hamstrings/glutes, shoulders) is trained on consecutive days. Heavy leg days need ≥48 h between them (a LIGHT second hinge in the week is fine — only a second HEAVY/near-max barbell hinge is a problem).
4. ROLE-BASED REP RANGES (HARD only if MONOTONE): reject a session that applies ONE identical rep range to every exercise (no primary→accessory→isolation spread). Otherwise the ranges are WIDE GUIDELINES per goal — reject only if the bias is clearly WRONG for the goal (e.g. a "strength" plan built entirely on 15-rep sets, or an "endurance" plan of heavy 3-rep singles). Do NOT reject for being inside a wider band:
   - Strength: heavy low-rep compounds (~1–6), accessories ~5–8, isolation ~8–12; LONG rests (up to ~300 s) are correct — never reject strength for rest >120 s.
   - Hypertrophy: compounds ~5–10, accessories ~8–15, isolation ~10–20(+); rest hypertrophy compounds up to ~240 s, isolation ~45–150 s. There is NO 120 s ceiling — do NOT reject for rest >120 s.
   - Endurance: higher-rep (~12–30) / shorter rest (~15–90 s) + 2–3 cardio sessions.
   - Weight Loss: ~10–20 reps, short rest (~45–90 s), density/circuits + ≥2 cardio sessions, plus enough resistance to preserve muscle.
5. ORDER & HIERARCHY: Within each session, compounds appear before isolation for the same muscle group, and the session leads with a primary compound rather than a flat list of identical-volume exercises.
6. EFFORT & PROGRESSION (HARD): Every exercise's notes must state a target effort (RIR or RPE) AND a progression rule. Reject if exercises only say "establish baseline" or carry no effort target.
7. VOLUME (scaled to duration): Every major muscle group the plan targets is trained at least once (not zero). No single muscle is absurdly over-volumed in one session. Per-session and weekly volume should FIT the chosen session duration and goal — do NOT reject for a set count that is appropriate to a long session, and do NOT reject a short/low-frequency plan for having less volume than a long one. Reject only for junk-padded volume or a genuinely unbalanced brutal session.
8. DE-DUPLICATION: No two near-identical movement patterns within one session (e.g. RDL + single-leg stiff-leg deadlift). No obvious structural errors (all-chest sessions, cardio the day before heavy legs).
9. WEEKLY BALANCE (COVERAGE SCALES WITH days × duration): a full week with enough days/time should broadly cover push/pull/squat/hinge and include direct lateral + rear-delt and a knee-dominant quad movement, and not stack only posterior-chain hinges with no quad. But a SHORT or LOW-FREQUENCY week CANNOT fit every pattern — do NOT reject it for missing a pattern it has no room for; on few/short days, multi-pattern compounds are the correct choice. Reject only a clearly lopsided full-capacity week.
$injuryCheck
11. EXERCISE COUNT (HARD upper cap): Beginner ≤5/session, Intermediate ≤7/session, Advanced ≤8/session. Fewer is fine (short sessions).
12. NOTES: Notes assert no incorrect mechanisms (e.g. "calf raises strengthen ankle stabilisers", "targets the inner chest"). Reject any use of "peak" as a NOUN for muscle shape ("bicep peak", "peak stretch", "increases bicep peak"). "Peak contraction" as a squeeze cue (hold the shortened position) is allowed.
13. TIME BUDGET — DO NOT EVALUATE THIS. Per-day session length is ALREADY enforced by an authoritative deterministic check that has PASSED before this review runs, so every training day is guaranteed to be within the allowed ±10-min window. Do NOT estimate or calculate session duration, and NEVER reject on time-budget, session-length, "too long", "too short", or any duration grounds. Disregard session length entirely when deciding accepted true/false.

Respond with ONLY valid JSON — no prose, no markdown fences:
{"accepted": true}
OR
{"accepted": false, "reason": "one or two sentences on the most critical issue only"}
        """.trimIndent()

        return runCatching {
            // H4: route the quality-review call through streaming too (same readTimeout benefit).
            val responseText = withAiRetry {
                sendStreaming(
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
                appendLine("Session ${fmt.format(Date(com.migul.treningsprogram.domain.DayBoundary.toLogicalMillis(session.dateMs)))} (${session.durationMinutes} min):")
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

    // H2: returns BOTH the rendered reference text AND the clean set of whole exercise names from the
    // structured rows. The blacklist is built from [exerciseNames] (commas preserved, no header/junk),
    // not by re-parsing [text]. Empty/no-plan ⇒ blank text + empty set.
    private data class PreviousPlanContext(val text: String, val exerciseNames: Set<String>)

    private suspend fun buildPreviousPlanContext(): PreviousPlanContext {
        val weekStart = workoutRepository.getLatestPlanWeekStart()
            ?: return PreviousPlanContext("", emptySet())
        // E2: previous-plan context must come from the ACTIVE program only, so generation varies
        // against the right program's last week (not another program's rows sharing the weekStart).
        val forWeek = workoutRepository.getActiveProgramPlanForWeek(weekStart)
        if (forWeek.isEmpty()) return PreviousPlanContext("", emptySet())
        val dayNames = mapOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun")
        val text = buildString {
            appendLine("LAST GENERATED PROGRAM (exercises used — vary these in the new plan):")
            forWeek.groupBy { it.dayOfWeek }.toSortedMap().forEach { (day, exList) ->
                val label = dayNames[day] ?: "Day $day"
                val names = exList.sortedBy { it.orderInDay }.joinToString(", ") { it.exerciseName }
                appendLine("  $label: $names")
            }
        }.trim()
        // Clean whole names straight from the structured rows — NO comma-splitting, NO header line.
        val exerciseNames = forWeek.map { it.exerciseName.trim() }.filter { it.isNotBlank() }.toSet()
        return PreviousPlanContext(text, exerciseNames)
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
        // H2: clean whole exercise names from the PREVIOUS plan's structured rows (commas preserved,
        // no header/junk) — used to build the blacklist instead of re-parsing [previousPlan]'s text.
        previousPlanExerciseNames: Set<String> = emptySet(),
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

        // P1 (generation-quality overhaul 2026-07): this was the "EXERCISE BLACKLIST — the single most
        // important constraint" that force-churned the user's MAIN LIFTS out for unrelated exercises every
        // week (and the un-anchored replacements then got GUESSED weights). It is replaced by a RECENTLY-
        // USED reference list that drives a VARIATION HIERARCHY: keep + progress most main lifts, vary
        // grip/angle/accessories freely, and cap FULL main-lift swaps at ≤2/week. The names are a "you have
        // trained these — freshen accessories/variations" cue, NOT a "must be different" mandate.
        // H2: still merged from recent logged exercises + the previous plan's clean structured names.
        val blacklist = buildBlacklistNames(recentExercises, previousPlanExerciseNames)

        val blacklistBlock = if (blacklist.isNotEmpty()) """
══════════════════════════════════════════
RECENTLY USED EXERCISES — VARY, BUT DO NOT CHURN YOUR MAIN LIFTS
══════════════════════════════════════════
These exercises appear in the user's recent sessions or last week's plan:
${blacklist.joinToString("\n") { "  • $it" }}

VARIATION HIERARCHY (progression-tracking beats novelty — apply in this order):
1. KEEP + PROGRESS most main/anchor lifts. For each major muscle, the primary compound should RECUR week to week so its load is trackable and progressively overloaded from the user's logged history. Do NOT swap an anchor out just because it appears above — repeating a main lift is REQUIRED for progressive overload, not a failure to vary.
2. VARY an anchor by IMPLEMENT / GRIP / ANGLE more freely (barbell↔dumbbell, pronated↔neutral grip, flat↔incline). This stays the same movement pattern, so estimate its weight from the CLOSELY-RELATED logged lift — never fabricate.
3. FULL main-lift SWAPS (replacing a main lift with a genuinely different exercise) are RARE: at most TWO fully-swapped main lifts this whole week. Every other main lift stays and progresses.
4. Accessories / isolation / exercise order / weekday placement / weekly theme rotate FREELY for freshness (respecting the recovery rules below).

So: no two consecutive weeks are identical copies (accessories/grip/angle/order/theme differ), but the trackable main lifts persist. If equipment genuinely leaves only one option for a muscle group, reuse it and note "only available option".
""" else ""

        val previousPlanBlock = if (previousPlan.isNotBlank()) """
──────────────────────────────────────────
For reference, last week's plan was:
$previousPlan
──────────────────────────────────────────
""" else ""

        val themeBlock = if (variationTheme.isNotBlank()) """
══════════════════════════════════════════
THIS WEEK'S VARIATION THEME (accessories / grip / angle — NOT a mandate to swap main lifts)
══════════════════════════════════════════
$variationTheme
Apply this theme to ACCESSORIES, isolation, grip/angle variations, and exercise order to keep the week fresh. It does NOT override the variation hierarchy above: keep and progress the main anchor lifts, and stay within the ≤2 full-main-lift-swaps-per-week cap. Never let the theme force a trackable anchor out for an unrelated exercise or drift a rep range.
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
MILD → light touch only. Add at most ONE optional prehab slot for the WHOLE WEEK (not a rehab slot every session) plus a brief note. Do NOT force a per-session exercise-selection change UNLESS the injury text names a clear aggravator — if it does, train around that specific aggravator. Otherwise keep the normal plan:
- Bad ankle ⇒ keep bilateral work primary; prefer low-impact cardio (bike, row, incline walk) over jogging; ONE optional light calf/ankle stability slot for the week is enough.
- Bad knee ⇒ keep knee-friendly ROM; ONE optional light controlled-leg / terminal-knee-extension slot for the week is enough.
- Bad shoulder ⇒ keep pressing in pain-free ranges; ONE optional light external-rotation / scapular slot for the week is enough. Do NOT stack rehab into every session or exclude the area's normal training."""
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

CRITICAL: Do NOT follow gym-culture day conventions (e.g., chest on Monday, back on Tuesday, arms on Friday). Assign muscle groups to days based purely on recovery logic — weekday placement may shift week to week as long as the recovery rules hold. A good coach KEEPS the main/anchor lifts stable and progressively overloads them from the logged history, and finds variety in accessories, grip/angle, order, and theme — NOT by swapping the trackable main lifts out every week.
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
GOAL PROGRAMMING — WIDE REP/REST/VOLUME BANDS (GUIDELINES, not a fixed table)
══════════════════════════════════════════
Assign every exercise a ROLE — primary compound, accessory, or isolation — and pick its rep range from the WIDE band for this goal. These are GUIDELINES you may adjust when goal, duration, injury, equipment, or exercise type justifies it — NOT a narrow fixed table. Two hard rules still apply: (a) rep range is fixed by ROLE + GOAL, so do NOT drift it as a variation lever (variation lives in exercise choice/grip/angle/accessories, per the hierarchy above), and NEVER apply one identical rep range to a whole session — each session shows a primary→accessory→isolation SPREAD; (b) the loaded-hinge rep caps override every band (hard safety rule above): barbell hinges (deadlift, RDL, sumo DL, good morning) ≤8 reps; loaded dumbbell hinges (DB RDL, DB stiff-leg, DB good morning) ≤12 reps; bodyweight/light hip-extension work (hip thrust, glute bridge, back extension, leg curl, KB swing) is exempt and may run high-rep. Each of the four goals must produce a VISIBLY DISTINCT, appropriate plan — none may be a relabeled hypertrophy block. Rest scales to the GOAL (longer for heavy strength, shorter for endurance/density) — there is NO blanket 120 s ceiling.
${when {
    goalLower.contains("strength") -> """
Goal: STRENGTH — build maximal force. Lead with heavy, low-rep compounds; intensity over volume.
- Rep ranges by role: primary compounds 1–6 / accessories 5–8 / isolation 8–12.
- Rest: LONG — 3–5 min (up to ~300 s) between heavy compound sets; 2–3 min for accessories. (Longer rest drives strength — do NOT cap at 120 s.)
- Weekly volume: LOWER (8–12 hard sets/muscle) — keep isolation minimal; too much accessory fatigue interferes with the heavy work.
- Effort: heavy compounds RPE 6–8 (1–4 RIR, generally not to failure); isolation RPE 7–9.
- Progression: add load (barbell upper +1.25–2.5 kg, barbell lower +2.5–5 kg) when all reps hit with ≥2 RIR.""".trimIndent()

    goalLower.contains("hypertrophy") -> """
Goal: HYPERTROPHY — maximise muscle growth across a broad load spectrum.
- Rep ranges by role: primary compounds 5–10 / accessories 8–15 / isolation 10–20 (isolation may run to ~30). Moderate loads.
- Rest: hypertrophy compounds 90–240 s; isolation 45–150 s. There is NO 120 s ceiling — use longer rest on the heavy compounds when it serves the set quality and the time budget.
- Weekly volume 10–20 hard sets/muscle; spread across the week (train a muscle ~2×/week WHERE the number of training days allows — do NOT force 2×/week on a low day count).
- Order: 1–2 compounds first per session, then accessory/isolation.
- Effort: compounds RPE 7–8 (~2 RIR), isolation RPE 8–10 (0–3 RIR).
- Progression: double progression — add reps toward the top of the range across all sets, then +load and reset to the bottom.""".trimIndent()

    goalLower.contains("endurance") -> """
Goal: ENDURANCE — muscular and cardiovascular conditioning. This is CONDITIONING-LED, not mislabeled hypertrophy.
- Rep ranges by role: compounds 12–20 (barbell hinges still ≤8) / isolation 15–30 or time-based holds. Light loads, 2–3 sets.
- Rest: SHORT — 15–90 s (circuit/superset pacing).
- Cardio is the PRIORITY: include 2–3 dedicated cardio sessions per week; circuits, supersets, bodyweight work welcome.
- Weekly resistance volume per muscle: LOWER (6–12 hard sets) — cardio takes the majority of the week.
- Progression: add reps/rounds/duration before load.""".trimIndent()

    goalLower.contains("weight") || goalLower.contains("loss") -> """
Goal: WEIGHT LOSS — calorie expenditure + muscle retention. NOT a relabeled hypertrophy plan.
- Structure: density work / circuits / supersets of non-competing muscle groups + cardio for calorie burn, PLUS enough straight-set resistance work to PRESERVE muscle.
- Rep ranges: 10–20 reps (barbell hinges ≤8). Rest: SHORT — 45–90 s (keep heart rate elevated).
- Prioritise compound multi-joint movements (highest burn + retention).
- Include ≥2 cardio sessions per week — mix HIIT and steady-state; prefer injury-safe low-impact options where needed.
- Effort: ~1–2 RIR. Progression: add reps/density, then load. Avoid extremely heavy low-rep work.""".trimIndent()

    else -> """
Goal: GENERAL health and fitness.
- Rep ranges by role: primary compounds 5–10 / accessories 8–12 / isolation 10–20 (barbell hinges ≤8). Rest 60–180 s.
- Balanced mix of compound and isolation work; hit all major muscle groups across the week.
- Effort: ~2–3 RIR. Progression: double progression.""".trimIndent()
}}

══════════════════════════════════════════
WEEKLY STRUCTURE
══════════════════════════════════════════
Organise $daysPerWeek days to distribute muscle group stimulus optimally for the stated goal. PRIORITY ORDER for resolving conflicts: safety → user hard boundaries → equipment → goal → experience → RECOVERY/FATIGUE → focus muscles → movement balance → variation. Recovery ranks ABOVE focus muscles and variation — when they conflict, recovery wins.
- WEEKLY VOLUME per trained muscle (guideline): maintenance ~4–8 / normal ~6–16 / focus ~8–20 hard sets. Beyond ~25 sets/week is rarely justified.
- PER-SESSION VOLUME: keep any single muscle to a goal-appropriate amount in one session (~8–10 hard sets is a lot); excess in-session sets are junk volume — spread across the week rather than stacking one brutal session.
- COVERAGE SCALES WITH days × duration: full movement-pattern coverage is a TARGET, not a hard requirement. On FEW or SHORT days, prioritise multi-pattern COMPOUNDS over exhaustive coverage — do NOT cram patterns in to hit a checklist. On MORE/LONGER days, cover more patterns.
- FREQUENCY: train a muscle ~2×/week WHERE the training-day count allows it — do NOT force 2×/week on a low day count; on few days, prioritise the biggest compounds.
- Never train the same primary muscle group on consecutive days.
- Heavy leg sessions (squat, deadlift) need at least ~48 h before the next heavy leg session. A LIGHT second hinge in the week (e.g. an RDL a few days after a deadlift) is fine — only a second HEAVY/near-max barbell hinge is limited.
- WEEKLY PATTERN BALANCE (scaled to days×duration): across the week aim to include horizontal + vertical push, horizontal + vertical pull, a knee-dominant (squat/quad) and a hip-dominant (hinge) lower movement, and direct lateral-delt + rear-delt work. Do not stack only posterior-chain hinges with NO knee-dominant quad movement.
- $cardioInstruction (Cardio matters most for weight-loss and endurance; it is largely irrelevant for a pure strength or hypertrophy block — do not add junk cardio to those.)
- Weekday placement may shift week to week as long as the recovery rules hold; space the days evenly where possible.
$splitBlock$previousPlanBlock

══════════════════════════════════════════
TIME BUDGET (applies PER training day — DERIVED FROM THE SESSION DURATION, STRICT both directions)
══════════════════════════════════════════
The session target is $sessionDurationMinutes min. EACH training day MUST estimate within ±10 min of that — aim $sessionDurationMinutes, accept ${sessionDurationMinutes - 10}–${sessionDurationMinutes + 10} min. This applies across the FULL range the user might pick (20–120 min): a SHORT session must be lean with NO junk padding, and a LONG session must actually REACH its target. Volume, rest, exercise count, and per-day time are DERIVED from goal × experience × days/week × THIS session duration — there is NO fixed set count, NO fixed minute target, and NO blanket rest ceiling driving the size. A day UNDER ${sessionDurationMinutes - 10} min is rejected just as hard as one OVER ${sessionDurationMinutes + 10} min. Self-size EVERY day using this EXACT estimate (it is the formula the app enforces — match it, do not approximate):
- Per strength exercise ≈ sets × reps × 4 s work + (sets − 1) × rest seconds + ~60 s setup. Rest counts only BETWEEN sets, so an exercise with N sets has N−1 rest periods, NOT N. (4 s/rep reflects a realistic controlled tempo.)
- Per cardio exercise ≈ its duration (the targetReps minutes/distance) + ~60 s.
- A day's estimate = the sum of its exercises.
REST IS YOUR #1 TIME LEVER. Short rest is the single biggest cause of under-time days. When a day estimates under $sessionDurationMinutes min, RAISE inter-set rest toward the TOP of THIS GOAL's rest band FIRST — strength has the most headroom here (3–5 min / up to ~300 s), hypertrophy compounds up to ~240 s, endurance/weight-loss stay in their shorter bands. Do not push rest beyond the goal's own band.
UNDER-FILL CORRECTION — when a day lands under ${sessionDurationMinutes - 10} min, apply these IN ORDER until it reaches about $sessionDurationMinutes min (do NOT stop the moment it scrapes the ${sessionDurationMinutes - 10}-min floor — aim toward the CENTRE):
  1. Raise inter-set rest toward this goal's band MAXIMUM (this alone clears most under-time days).
  2. RAISE reps toward the TOP of each exercise's role range.
  3. ADD 1 set to an accessory/isolation exercise.
  4. ADD one more accessory exercise (respecting the experience exercise-count cap and goal-appropriate per-muscle volume).
OVER-FILL: if a day lands over ${sessionDurationMinutes + 10} min, TRIM — remove an accessory, reduce sets, or shorten rest until it estimates close to $sessionDurationMinutes. For a LONG target that is hard to reach, add USEFUL rest/warm-up/mobility/carries/conditioning BEFORE junk volume; for a SHORT target, do NOT pad — keep it lean. Aim for the CENTRE ($sessionDurationMinutes), not the edge — a low-edge day tips back under the floor on rounding. Size within goal- and duration-appropriate volume; never add junk volume or duplicate a movement pattern just to hit a number.

══════════════════════════════════════════
SESSION DESIGN RULES
══════════════════════════════════════════
- Use a PRIMARY → ACCESSORY hierarchy: lead each session with a main compound (heavier, lower-rep), then accessories and isolation (lighter, higher-rep). Avoid the flat "6 exercises × identical sets × identical reps" template.
- DE-DUPLICATE movement patterns: no two near-identical patterns in one session (e.g. do not pair RDL with single-leg stiff-leg deadlift, or barbell bench with dumbbell bench as two separate slots).
- Order: compound exercises before isolation for the same muscle group.
- Start each session with the most demanding movement.
- Exercise count per session (scaled to duration; upper cap is hard): Beginner 3–5, Intermediate 4–7, Advanced 4–8. Short sessions sit at the LOW end, long sessions toward the HIGH end.
- Sets per exercise (guideline): Beginner 1–3, Intermediate 2–4, Advanced 2–5.
${when {
    experience.lowercase().contains("beginner") -> "- Stick to fundamental, easy-to-learn movements. Avoid complex or high-skill exercises."
    experience.lowercase().contains("intermediate") -> "- Include some variation and unilateral work. Technique is established."
    else -> "- Advanced techniques (drop sets, rest-pause, tempo work) are appropriate where beneficial."
}}
${if (injuries.isNotBlank()) """
- INJURY GATING (severity: $sev for "$injuries"): apply the INJURY HARD-CONSTRAINTS block above. At SEVERE, EXCLUDE the aggravating category outright; at MODERATE, REPLACE the worst aggravators with safer same-muscle variants AND include 1–2 rehab/prehab moves. At MILD, do NOT force a per-session selection change unless the text names a clear aggravator — just add at most ONE optional prehab slot for the WEEK plus a note.
  • e.g. ankle instability (MODERATE/SEVERE) → down-rank/exclude loaded single-leg balance work (Bulgarian/rear-foot-elevated split squat, single-leg RDL, step-ups). When you down-rank these, SUBSTITUTE a genuinely BILATERAL movement (goblet/sumo squat, leg press, hand-supported split squat) — do NOT keep the single-leg exercise and bolt on contradictory cues. A rear-foot-elevated / Bulgarian split squat is single-leg BY DEFINITION; NEVER append "both feet down" / "bilateral contact at all times" to it (physically incoherent). If a single-leg movement is genuinely wanted, allow FIXED external support (hand on wall/bench). For cardio prefer low-impact (bike, row, incline walk) over jogging.
- Rehab/prehab (REQUIRED only at MODERATE/SEVERE): 1–2 low-load moves for the injured area, staged as light progression — NOT counted as primary strength/hypertrophy volume.""" else ""}
${if (priorityMuscles.isNotBlank()) """
- Priority muscles ($priorityMuscles): allocate at least 2 extra sets compared to non-priority groups. Train them twice per week.""" else ""}

══════════════════════════════════════════
PROGRESSION & EFFORT (every exercise)
══════════════════════════════════════════
EVERY exercise's notes MUST state (a) a target effort as RIR or RPE, and (b) a concrete progression rule. "Establish baseline" alone is NOT acceptable.
- Default effort: ~2–3 RIR on compounds, ~1–2 RIR on isolation.
- Default progression: DOUBLE PROGRESSION — add reps to the top of the range across all sets, then add load and reset to the bottom of the range.
WEIGHTS ARE ALWAYS ANCHORED, NEVER FABRICATED. Set every targetWeightKg from the workout history above:
- KEPT lift WITH logged history: start from its most recent logged load, then apply the progression increment (below).
- VARIED anchor (same movement, different implement/grip/angle — e.g. barbell bench ↔ dumbbell bench, flat ↔ incline press): ESTIMATE the weight from the CLOSELY-RELATED logged lift (map the variation back to the anchor's logged performance — e.g. per-dumbbell ≈ a sensible fraction of the barbell load). Do NOT guess a load unrelated to what the user has done.
- GENUINELY NEW lift with NO logged or closely-related history: prescribe by RPE/RIR with a CONSERVATIVE start (~55–65% estimated 1RM) — never emit an unexplained absolute kg the user has no basis for. (Real bodyweight movements use targetWeightKg 0.)
Increment rules against the logged base:
- Progressing (load rising across sessions): add barbell-upper +1.25–2.5 kg / barbell-lower +2.5–5 kg / dumbbell +1–2 kg each (beginner may use the top of each range).
- Plateaued (3+ sessions at same weight): push reps to the top of the range, or note a technique cue, before adding load.
- Regressing: reduce weight 5–10% and note "deload — rebuild form".

══════════════════════════════════════════
CARDIO
══════════════════════════════════════════
For cardio exercises, use these name conventions so the app can identify them: Easy Jog, Outdoor Run, Tempo Run, Interval Run.
Cardio JSON fields: sets=1, targetReps = duration or distance only (e.g. "30 min", "5 km", "6×400m"), targetWeightKg=0, recommendedRestSeconds=60.

══════════════════════════════════════════
NOTES FIELD — ONE short clause per exercise
══════════════════════════════════════════
Each exercise's "notes" must be TERSE — a single short clause, NOT sentences. It MUST still carry exactly (a) a target effort as RIR or RPE AND (b) a concrete progression rule, and nothing more. Follow this shape: "RPE 8 (~2 RIR); double progression +reps then +load". Do NOT add form cues, anatomy, coaching prose, or mechanism claims. NEVER assert an incorrect mechanism (e.g. "calf raises strengthen ankle stabilisers", "targets the inner chest", "increases bicep peak") and NEVER use "peak" as a NOUN for muscle shape ("bicep peak", "peak stretch") — "peak contraction" as a squeeze cue is the only allowed use of that word.

══════════════════════════════════════════
BUILD RULES — every day must satisfy ALL of these; apply them as you build, do NOT narrate or restate them in the output
══════════════════════════════════════════
- Each training day must estimate within ${sessionDurationMinutes - 10}–${sessionDurationMinutes + 10} min by the TIME BUDGET formula above — size each day with the formula (do not eyeball it), but do NOT write the arithmetic into the output. Volume/rest/count are DERIVED from the $sessionDurationMinutes-min target, not from a fixed set count. For ANY day under ${sessionDurationMinutes - 10} min, apply the UNDER-FILL CORRECTION in order (rest toward the goal's band max FIRST, then reps, then a set, then an accessory — staying inside the goal's own rest band); TRIM any day over ${sessionDurationMinutes + 10} min. Aim for the CENTRE ($sessionDurationMinutes), not the edge — low-edge days tip back under the floor on rounding.
- No barbell hinge above 8 reps; no loaded DB hinge above 12 reps.
- Rep ranges vary by exercise role within each session (not monotone), and match the goal.
- Every exercise's notes carry an RIR/RPE target AND a progression rule; every loaded lift carries a history-anchored targetWeightKg (never a fabricated number).
- No single muscle over ~8–10 hard sets in one session; per-session and weekly volume are goal- and duration-appropriate (not a fixed number).
- Main/anchor lifts persist + progress from logged history; at most TWO full main-lift swaps this week.
- No duplicate movement pattern within a session.
- Injury changes applied to SELECTION per severity (empty injury = no change; MILD = light touch); no rear-foot-elevated / single-leg movement carries "both feet down" cues.
- Weekly plan (scaled to days×duration) includes direct lateral + rear-delt work and a knee-dominant quad movement.
- Notes assert no incorrect mechanisms and use no "peak" muscle-shape noun.

══════════════════════════════════════════
OUTPUT — valid JSON only, no prose, no markdown fences
══════════════════════════════════════════
Output ONLY the JSON object — nothing before it, nothing after it. Your VERY FIRST output character MUST be the opening "{" and the VERY LAST MUST be its closing "}". Do NOT write any visible planning preamble, step-by-step reasoning, per-day time-budget arithmetic, set-by-set or recently-used cross-checking, or self-check narration before, around, or after the JSON. Do NOT open with lines like "I'll plan silently" or "Let me compute the days" — just emit the JSON. Apply every rule above internally as you build the plan; the ONLY place any prose belongs is the short "rationale" field. Narrating your planning burns the output budget and makes the response run out of room before the JSON closes — a truncated, unclosed JSON object cannot be used.
Include a top-level "rationale" string (sibling of "days"): AT MOST 2 short sentences — plain-language note on WHAT changed vs the user's recent training / last week and WHY, referencing ACTUAL exercises (e.g. "Kept your barbell bench and squat and progressed the loads, swapped the accessory rows for a different grip, and added a hamstring isolation for your lagging posterior chain."). Speak directly to the user, no jargon, no essay. New user with no history: one short sentence on how the plan fits their goal.
ALSO declare AUDITABLE METADATA so the numbers are checkable. Keep every metadata field TERSE (short scalars / short strings / short arrays — no prose): they exist so the plan declares its own numbers, NOT so you narrate. Per exercise add: role ("primary compound"|"accessory"|"isolation"|"cardio"|"rehab"), movementPattern (e.g. "horizontal push"), primaryMuscles (array), secondaryMuscles (array), countsAsHardSets (int), workSeconds (int), restSeconds (int, = recommendedRestSeconds), setupSeconds (int, ~60), estimatedMinutes (number, = the TIME BUDGET formula for this exercise), injuryModification (short string, "" if none). Per day add: dayEstimateMinutes (number), withinDurationWindow (bool). Add a top-level "week" object: weeklyVolumeSummary (object of muscle→hard sets), movementPatternSummary (array of patterns covered), durationSummary (short string), blockState ("continue"|"new"), constraintNotes (short string). These declared numbers are auditable — the app STILL enforces duration with its own deterministic estimate, so the declared minutes must MATCH the formula, not replace it.
{
  "rationale": "Short plain-language explanation of what changed in this plan and why.",
  "days": [
    {
      "dayOfWeek": 1,
      "name": "Day name",
      "dayEstimateMinutes": 60,
      "withinDurationWindow": true,
      "exercises": [
        {
          "name": "Exercise Name",
          "sets": 4,
          "targetReps": "8-10",
          "targetWeightKg": 80.0,
          "notes": "RPE 8 (~2 RIR); double progression +reps then +2.5 kg",
          "recommendedRestSeconds": 120,
          "role": "primary compound",
          "movementPattern": "horizontal push",
          "primaryMuscles": ["chest"],
          "secondaryMuscles": ["triceps", "front delts"],
          "countsAsHardSets": 4,
          "workSeconds": 160,
          "restSeconds": 120,
          "setupSeconds": 60,
          "estimatedMinutes": 10,
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
        // P4: bound the whole single-day flow with the same overall deadline as the weekly path so it
        // always reaches a terminal outcome (never a frozen progress line).
        withGenerationDeadline {
        val weekStart = thisMonday()
        // Effective severity (only used when injuries non-blank). Legacy/unspecified ⇒ cautious Moderate.
        val sev = injurySeverity.ifBlank { "Moderate" }
        val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        val dayName = dayNames.getOrElse(dayOfWeek - 1) { "Day $dayOfWeek" }

        // P4: WEEKLY PARITY — pull real workout history so target weights are history-driven and
        // progressed. (Bug fix: the old single-day prompt sent NO history and the return-shape example
        // hard-coded targetWeightKg:0, so the model echoed 0 for every exercise → all-bodyweight.)
        val sessions = workoutRepository.getRecentSessions(12)
        val (history, recentExercises) = buildSessionHistory(sessions)

        // P4: variety WITHIN the fixed focus. (Bug fix: the old path excluded the current day and passed
        // no history/variation signal, so a fixed focus re-rolled the same canonical list every time.)
        // Blacklist the day's CURRENT exercises plus recently-trained names, and inject a variation
        // directive — the same levers the weekly generator uses.
        val currentDayExerciseNames = existingWeekPlan.filter { it.dayOfWeek == dayOfWeek }
            .map { it.exerciseName.trim() }.filter { it.isNotBlank() }.toSet()
        val singleDayBlacklist = buildBlacklistNames(recentExercises, currentDayExerciseNames)
        val variationTheme = variationThemes.random()

        val otherDays = existingWeekPlan.filter { it.dayOfWeek != dayOfWeek }
        // Peer review runs on the RESULTING full week so its weekly checks (recovery between days,
        // weekly balance, day count) are meaningful for the regenerated day in context.
        val resultingDayCount = (otherDays.map { it.dayOfWeek }.toSet() + dayOfWeek).size

        val weekContext = otherDays
            .groupBy { it.dayOfWeek }
            .entries.sortedBy { it.key }
            .joinToString("\n") { (day, exs) ->
                val dname = dayNames.getOrElse(day - 1) { "Day $day" }
                "  $dname: ${exs.joinToString(", ") { it.exerciseName }}"
            }
            .ifBlank { "  (No other days scheduled)" }

        val equipStr = if (equipment.isEmpty()) "Bodyweight only — no equipment" else equipment.joinToString(", ")

        // P3 (generation-quality overhaul 2026-07): WIDE goal bands (guidelines), goal-appropriate rest.
        val repRange = when {
            goal.lowercase().contains("strength") -> "primary compounds 1–6, accessories 5–8, isolation 8–12; LONG rest (3–5 min / up to ~300 s) on heavy compounds"
            goal.lowercase().contains("endurance") -> "compounds 12–20 (barbell hinges ≤8), isolation 15–30; SHORT rest (15–90 s)"
            goal.lowercase().contains("loss") || goal.lowercase().contains("weight") -> "10–20 reps (barbell hinges ≤8), density/circuits; SHORT rest (45–90 s)"
            else -> "primary compounds 5–10, accessories 8–15, isolation 10–20 (hypertrophy); rest compounds 90–240 s, isolation 45–150 s (NO 120 s ceiling)"
        }

        fun buildSingleDayPrompt(previousRejectionReason: String): String = buildString {
            appendLine("You are an expert personal trainer. Generate a single training day workout for $dayName.")
            appendLine()
            if (previousRejectionReason.isNotBlank()) {
                appendLine("PREVIOUS ATTEMPT REJECTED — FIX THIS FIRST: \"$previousRejectionReason\". Your new plan must directly address this or it will be rejected again.")
                appendLine()
            }
            appendLine("GOAL: $goal")
            appendLine("EXPERIENCE: $experience")
            appendLine("SESSION DURATION: $sessionDurationMinutes minutes")
            appendLine("TIME BUDGET (DERIVED from the $sessionDurationMinutes-min target): this day MUST estimate within ±10 min of $sessionDurationMinutes (aim $sessionDurationMinutes, accept ${sessionDurationMinutes - 10}–${sessionDurationMinutes + 10}) — a day UNDER ${sessionDurationMinutes - 10} is rejected just like one OVER ${sessionDurationMinutes + 10}. Volume/rest/count are sized to THIS duration — NO fixed set count, NO blanket rest ceiling. Estimate ≈ per strength exercise: sets × reps × 4 s work + (sets − 1) × rest seconds + ~60 s setup (rest counts only between sets; 4 s/rep = realistic controlled tempo); per cardio exercise: its duration + ~60 s. REST IS THE #1 TIME LEVER — if the day is short, FIRST raise inter-set rest toward THIS goal's band max (strength up to ~300 s, hypertrophy compounds up to ~240 s, endurance/weight-loss stay short), THEN raise reps toward the top of each range, THEN add a set, THEN add an accessory; if long, trim. Keep volume goal- and duration-appropriate; aim for the CENTRE ($sessionDurationMinutes), not the edge.")
            appendLine("TARGET REP RANGE: $repRange")
            appendLine("AVAILABLE EQUIPMENT: $equipStr")
            if (equipmentNotes.isNotBlank()) appendLine("EQUIPMENT NOTES: $equipmentNotes")
            appendLine()
            appendLine("WORKOUT HISTORY (most recent first — use this to set REAL starting weights and progress them):")
            appendLine(history)
            appendLine()
            appendLine("WEIGHT SELECTION (use the history above — do NOT default loaded exercises to 0/bodyweight):")
            appendLine("- Progressing (load rising across sessions): add 2.5 kg (intermediate/advanced) or 5 kg (beginner) to the last weight.")
            appendLine("- Plateaued (same weight 3+ sessions): keep the load, push reps to the top of the range first.")
            appendLine("- Regressing: reduce 5–10% and note \"deload — rebuild form\".")
            appendLine("- No history for a lift: set a sensible working weight (~55–65% est. 1RM) for this user — NOT 0.")
            appendLine("- ONLY genuine bodyweight movements (push-ups, pull-ups, dips, planks, bodyweight lunges/squats) use targetWeightKg 0; every loaded movement MUST carry a real kg target.")
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
            appendLine("VARIETY DIRECTIVE: $variationTheme")
            if (singleDayBlacklist.isNotEmpty()) {
                appendLine("Do NOT just re-use the exercises this day already had or recent sessions used — pick DIFFERENT movements that still match the muscle focus. Already used (vary away from these): ${singleDayBlacklist.joinToString(", ")}")
            }
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
            append("Return ONLY valid JSON, no prose, no markdown fences (use REAL history-based weights, not the example's number):")
            appendLine("""{"days":[{"dayOfWeek":$dayOfWeek,"name":"$dayName","exercises":[{"name":"Exercise Name","sets":4,"targetReps":"8-12","targetWeightKg":40.0,"notes":"RPE 8 (~2 RIR); double progression: +reps to top of range, then +load","recommendedRestSeconds":120}]}]}""")
        }

        // P4: WEEKLY-PARITY retry loop + strict per-day time-budget gate + validateProgram peer review.
        val rejectionReasons = mutableListOf<String>()
        // Phase-2 SALVAGE parity: best OVER-only duration-rejected candidate (smallest overshoot).
        var salvageCandidate: SalvageCandidate? = null

        // Terminal handler: after every attempt failed, attempt the same deterministic auto-trim the
        // weekly path uses on the best OVER-only candidate (strict gate untouched — trim is layered
        // strictly after it), then peer-review the trimmed day in full-week context; else throw.
        suspend fun finalizeSingleDay(): List<PlannedExercise> {
            val candidate = salvageCandidate
            if (candidate != null) {
                val trimmed = trimOverflowToWindow(candidate.exercises, sessionDurationMinutes, emptySet())
                if (trimmed != null) {
                    onProgress("Trimming $dayName to fit your $sessionDurationMinutes-min target…")
                    val fullWeekJson = buildProgramJsonForValidation(otherDays + trimmed, candidate.rationale)
                    val validation = validateProgram(
                        fullWeekJson, resultingDayCount, sessionDurationMinutes, goal, experience, injuries, injurySeverity
                    )
                    if (validation.accepted) {
                        rejectionLog.addSession(
                            rejectionReasons.mapIndexed { i, r -> RejectionLog.Attempt(i + 1, r, finalFailure = false) },
                            succeeded = true
                        )
                        promptLog.add("single_day_${dayName.lowercase()}_auto_trim", fullWeekJson,
                            "accepted=true (deterministic over-day trim → peer review passed)")
                        return trimmed.map { it.copy(weekStart = weekStart) }
                    }
                    promptLog.add("single_day_${dayName.lowercase()}_auto_trim", fullWeekJson, "accepted=false: ${validation.reason}")
                }
            }
            rejectionLog.addSession(
                rejectionReasons.mapIndexed { i, r -> RejectionLog.Attempt(i + 1, r, i == rejectionReasons.lastIndex) },
                succeeded = false
            )
            val reasons = rejectionReasons.mapIndexed { i, r -> "Attempt ${i + 1}: $r" }.joinToString("\n")
            throw IllegalStateException("Couldn't generate $dayName after $MAX_GENERATION_ATTEMPTS attempts.\n$reasons")
        }

        for (attempt in 1..MAX_GENERATION_ATTEMPTS) {
            if (attempt == 1) onProgress("Generating $dayName exercises…")
            else onProgress("Attempt $attempt of $MAX_GENERATION_ATTEMPTS: refining $dayName…")
            val prompt = buildSingleDayPrompt(rejectionReasons.lastOrNull() ?: "")

            // H1/H4: generation-specific retry (a timed-out generate is NOT re-issued) + streaming.
            // FIX-C: log the attempt even when the call throws.
            val response = try {
                withAiRetry(isRetryable = ::isTransientGenerationError) {
                    sendStreaming(ClaudeRequest(messages = listOf(ClaudeRequest.Message(content = prompt))))
                }
            } catch (e: Throwable) {
                promptLog.add("single_day_${dayName.lowercase()}", prompt, "<request failed: ${e.message}>")
                throw e
            }
            val responseText = response.text()
            promptLog.add("single_day_${dayName.lowercase()}", prompt, responseText)

            // No-JSON / truncated / unparseable → retryable rejection (matches the weekly loop).
            val cleanJson = extractJsonOrNull(responseText)
            val exercises = cleanJson?.let { runCatching { parseProgram(it) }.getOrNull() }
                ?.filter { it.dayOfWeek == dayOfWeek }
            if (cleanJson == null || exercises == null || exercises.isEmpty()) {
                val reason = when {
                    isLikelyTruncated(responseText, response.stopReason) ->
                        "The response was cut off before a complete $dayName plan was produced. Lead with the JSON; keep reasoning brief."
                    cleanJson == null ->
                        "No usable JSON plan was found for $dayName. Return only the JSON object."
                    else ->
                        "The $dayName plan could not be parsed. Return a single valid JSON object matching the required shape."
                }
                rejectionReasons.add(reason)
                onProgress("Attempt $attempt rejected: $reason")
                if (attempt == MAX_GENERATION_ATTEMPTS) return@withGenerationDeadline finalizeSingleDay()
                continue
            }

            // Strict ±10-min per-day duration gate (same formula/window as the weekly path).
            val est = WorkoutTimeEstimator.estimateDayMinutes(exercises)
            val durationReason = dayDurationFeedback(dayOfWeek, est, sessionDurationMinutes) ?: ""

            // Salvage candidate: an OVER-only duration miss (est > high) that is otherwise valid.
            if (durationReason.isNotEmpty() && est > sessionDurationMinutes + 10) {
                val overshoot = est - (sessionDurationMinutes + 10)
                val best = salvageCandidate
                if (best == null || overshoot < best.totalOvershoot) {
                    salvageCandidate = SalvageCandidate(exercises, parseRationale(cleanJson), overshoot)
                }
            }

            // Peer review in FULL-WEEK context (skip the costly LLM call when the gate already failed).
            val validation = if (durationReason.isEmpty()) {
                onProgress("Reviewing $dayName for quality…")
                val fullWeekJson = buildProgramJsonForValidation(otherDays + exercises, parseRationale(cleanJson))
                validateProgram(fullWeekJson, resultingDayCount, sessionDurationMinutes, goal, experience, injuries, injurySeverity)
            } else ValidationResult(accepted = false, reason = durationReason)

            if (durationReason.isEmpty() && validation.accepted) {
                rejectionLog.addSession(
                    rejectionReasons.mapIndexed { i, r -> RejectionLog.Attempt(i + 1, r, false) },
                    succeeded = true
                )
                // Re-stamp weekStart (parseProgram calls thisMonday() internally).
                return@withGenerationDeadline exercises.map { it.copy(weekStart = weekStart) }
            }
            val rejectionReason = durationReason.ifEmpty { validation.reason }
            rejectionReasons.add(rejectionReason)
            onProgress("Attempt $attempt rejected: $rejectionReason")
            if (attempt == MAX_GENERATION_ATTEMPTS) return@withGenerationDeadline finalizeSingleDay()
        }
        throw IllegalStateException("Unexpected state")
        } // withGenerationDeadline
    }.also {
        // P3: terminal outcome of a single-day regeneration → backgrounded-only notification.
        generationNotifier.notifyProgramGenerationComplete(it.isSuccess)
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
                    // P2/P3: the 120 s hypertrophy ceiling and the old 180 s clamp are gone — rest is now
                    // goal-derived and can legitimately reach the strength band's ~300 s. Keep only a loose
                    // sanity ceiling (600 s) so an absurd model value can't inflate the estimate, while
                    // preserving every legitimate band value (strength ≤300, hypertrophy compounds ≤240).
                    recommendedRestSeconds = ex.recommendedRestSeconds.coerceAtMost(600),
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

    // Phase-2 SALVAGE: re-serialize a (possibly auto-trimmed) PlannedExercise list back to the generation
    // program-JSON shape so [validateProgram] can peer-review it. The salvage candidate's raw response is
    // the UN-trimmed plan, so after trimming we must review the TRIMMED plan, not the original. Groups by
    // day (sorted), preserves per-day order; field names match the model's schema via ExJson @SerializedName.
    private fun buildProgramJsonForValidation(exercises: List<PlannedExercise>, rationale: String): String {
        val days = exercises
            .groupBy { it.dayOfWeek }
            .toSortedMap()
            .map { (day, dayExercises) ->
                DayJson(
                    dayOfWeek = day,
                    name = "",
                    exercises = dayExercises.sortedBy { it.orderInDay }.map { ex ->
                        ExJson(
                            name = ex.exerciseName,
                            sets = ex.sets,
                            targetReps = ex.targetReps,
                            targetWeightKg = ex.targetWeightKg,
                            notes = ex.notes,
                            recommendedRestSeconds = ex.recommendedRestSeconds
                        )
                    }
                )
            }
        return gson.toJson(ProgramJson(days = days, rationale = rationale))
    }

    /** E2 (L1 + M2): the mesocycle / deload directive injected into the generation prompt. */
    private fun buildMesocycleBlock(m: MesocycleContext): String = m.promptBlock()

    // Delegates to the hardened package-level [extractJsonOrThrow]; kept as a member so existing call
    // sites are unchanged.
    private fun extractJson(text: String): String = extractJsonOrThrow(text)
}

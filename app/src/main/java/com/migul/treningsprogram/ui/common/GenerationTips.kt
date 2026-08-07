package com.migul.treningsprogram.ui.common

import kotlin.random.Random

/**
 * P5: rotating, informative + friendly wait copy shown WHILE a plan generates, so the wait is less
 * boring. This is purely presentational — it never replaces the real per-attempt status (which every
 * wait screen keeps on its own line); it is the changing companion text.
 *
 * ## Brief 01 (body-progress batch 2026-08-04) — 150 messages, random rotation
 * The pool grew 12 → 150 and is deliberately MIXED in tone, in four roughly equal blocks below:
 * training tips (36), app facts (42), encouragement (36), humour (36).
 *
 * **App facts are load-bearing: every one is checked against the code and must stay true.** If a
 * behaviour changes, the matching line here changes with it. The facts below were each verified
 * against a specific implementation — e.g. the 12-session history window
 * (`AiRepository.buildPrompt` → `WorkoutRepository.getRecentSessions(12)`), 3 attempts
 * (`AiRepository.Companion.MAX_GENERATION_ATTEMPTS`), the 6-minute cap
 * (`GENERATION_OVERALL_DEADLINE_MS`), Epley (`domain.Epley`), the 3-session stall window
 * (`domain.StallDetector.STALL_WINDOW`), the 2-stall deload trigger (`domain.DeloadPolicy`), the
 * 48 h / 7-day recovery bands (`domain.MuscleRecovery`), the level curve
 * (`domain/strength/StrengthStandards`), and so on. Do NOT add a "fact" you have not read in code.
 *
 * ## Rotation
 * Screens no longer walk a fixed index — they take a [rotation] and call [Rotation.next] on their
 * timer. Each rotation shuffles the whole pool and hands out every message once before reshuffling
 * (assumption A3), so a wait never repeats a line and two waits show different orders. [tip] is kept
 * unchanged for any caller/test that still wants deterministic index-based access.
 *
 * Pure object (no Android) so both the pool and the sequencing are unit-testable.
 */
object GenerationTips {

    // ── Training tips (36) ────────────────────────────────────────────────────────────────────────
    private val TRAINING_TIPS: List<String> = listOf(
        "Tip: progressive overload — add a rep or a little load when a set starts to feel easy.",
        "Tip: leaving 1–2 reps in the tank on most sets lets you train hard and still recover.",
        "Tip: sleep and protein do as much for progress as the program does.",
        "Tip: consistency beats intensity — showing up most weeks is what moves the needle.",
        "Tip: compound lifts first, isolation last — that order is built into every day.",
        "Tip: control the way down; the lowering phase is where a lot of the work lives.",
        "Tip: add reps first, then add load and reset the reps. That is double progression.",
        "Tip: rest long enough that the next set is limited by the muscle, not your breathing.",
        "Tip: full range of motion under load usually beats a heavier partial.",
        "Tip: a stalled lift often needs less fatigue around it, not more effort in it.",
        "Tip: soreness is not a scoreboard — great sessions often leave you fine the next day.",
        "Tip: train each muscle at least twice a week if your schedule has room for it.",
        "Tip: log what you actually did, not what you meant to do — the plan reads the truth.",
        "Tip: if your grip gives out before your back does, straps are a tool, not a cheat.",
        "Tip: pick a load you can own for every prescribed rep, not just the first three.",
        "Tip: heavy hinges like deadlifts and RDLs are best kept well away from failure.",
        "Tip: keep your setup identical between sets — same stance, same grip, same bar path.",
        "Tip: a deload is a tool for going further, not an admission of anything.",
        "Tip: side and rear delts respond to frequency more than to one heroic session.",
        "Tip: change one variable at a time or you will never know which one worked.",
        "Tip: warm-up sets should wake a lift up, not tire it out.",
        "Tip: weigh yourself weekly rather than daily — the trend is signal, the day is noise.",
        "Tip: if a movement hurts a joint, change the angle before you change the effort.",
        "Tip: single-leg work exposes imbalances that a barbell will happily hide.",
        "Tip: on a short session, favour multi-joint moves — they buy the most per minute.",
        "Tip: spread protein across the day instead of one enormous evening portion.",
        "Tip: strength is a skill; practising the lift matters as much as pushing it.",
        "Tip: brace before the bar moves, not halfway up.",
        "The last two or three hard reps of a set are the ones that ask for adaptation.",
        "A lift that goes up every single week was probably started a bit too light.",
        "Rest days are training days for tendons and connective tissue.",
        "Two seconds down, no pause, drive up — a tempo that works nearly everywhere.",
        "Technique breaking down is a rep counter, not a badge of honour.",
        "Calves and forearms want frequency; they mostly shrug at heroic single sessions.",
        "Under-eating shows up in your last set long before it shows up on the scale.",
        "One hard set close to failure counts for more than three comfortable ones."
    )

    // ── App facts (42) — every line verified against the implementation ───────────────────────────
    private val APP_FACTS: List<String> = listOf(
        "Did you know? Your plan adapts to what you actually logged, not a fixed template.",
        "Reading your recent sessions to set real starting weights…",
        "The planner reads your last 12 completed sessions before it writes a single line.",
        "Balancing your week so the same primary muscle isn't trained two days in a row.",
        "Checking each day fits your session-length target…",
        "Running a quality review pass over the plan before it's saved…",
        "Almost there — putting the finishing touches on your week.",
        "Every plan gets a second AI pass — a sports-science review — before it can be saved.",
        "Generation gets up to three attempts, and each rejection feeds notes into the next one.",
        "The whole run is capped at six minutes, so it always reaches a real answer.",
        "The \"~Xm\" on the Program screen is the exact estimate the generation gate enforces.",
        "The time estimator was calibrated against real logged sessions, not a textbook.",
        "Estimated 1RM here is Epley: weight × (1 + reps ÷ 30).",
        "A lift counts as stalled only after three sessions with no estimated-1RM improvement.",
        "Adding reps at the same load still counts as progress — stall detection knows that.",
        "A deload fires when two lifts stall at once, and it lasts exactly one week.",
        "Recovery bands: under 48 hours recovering, up to a week ready, beyond that overdue.",
        "A muscle worked as a synergist is treated as recovering faster than a primary one.",
        "Warm-up sets are excluded from volume, personal records and strength charts everywhere.",
        "A first-ever lift sets a baseline, never a record — you can't beat what didn't exist.",
        "Home's number to beat is exactly the target the in-workout chip will show you.",
        "Your streak measures sticking to the plan: rest days neutral, missed days break it.",
        "Empty past days are auto-logged as rest or missed — today is never filled in for you.",
        "Rest and missed placeholder days never count as training anywhere in the app.",
        "Your level is strength: best qualifying set in 3 months, against your body weight and sex.",
        "A perfect week is worth 150 XP.",
        "Around 200 achievements are waiting, graded Common, Rare, Epic and Legendary.",
        "The weekly challenge is drawn from 26 templates and stays fixed for the whole week.",
        "Calorie estimates are MET maths: METs × body weight × hours, rounded to the nearest 10.",
        "The plate readout assumes a 7 kg bar and a 50 mm plate set until you set up a gym.",
        "When a weight isn't loadable, the plate readout shows \"≈\" and the nearest weight below.",
        "Exercises you exclude for a gym are filtered out deterministically, prompt or no prompt.",
        "A backup export contains everything except your API key.",
        "Importing a backup merges into your data — it never wipes what is already there.",
        "The day boundary is adjustable, so a 01:00 session can still count as the previous day.",
        "The monthly Wrapped is read-only — opening one never touches XP, streaks or stats.",
        "The rest timer keeps running in a notification, so you can leave the app between sets.",
        "The home-screen widget shows today's plan, up to four exercises at a glance.",
        "A new week's plan can generate itself on Monday, even from the background.",
        "The exercise library ships inside the app — pictures and instructions work offline.",
        "Every prescribed weight is anchored to something you actually lifted, never invented.",
        "Your Easy, Moderate and Hard effort labels are summarised and sent to the planner."
    )

    // ── Encouragement (36) ────────────────────────────────────────────────────────────────────────
    private val ENCOURAGEMENT: List<String> = listOf(
        "Every session you have logged is quietly making this plan better.",
        "The hard part was deciding to train. This is just paperwork.",
        "You have already done the thing most people skip: you showed up.",
        "Progress is rarely visible week to week. It is obvious a year later.",
        "Small, boring, repeated — that is what strength is actually made of.",
        "You don't need a perfect week. You need a week that happened.",
        "The plan is built around what you can do, not what you think you should do.",
        "Nobody has ever regretted the workout they finished.",
        "Rough sessions still count. They are the ones that keep a streak honest.",
        "Your future self is going to be quietly grateful for this week.",
        "Strength does not care how motivated you felt on the day.",
        "You are allowed to be proud of a completely ordinary training week.",
        "Doubt turns up before every heavy set. Lift anyway.",
        "The set you don't feel like doing is usually worth the most.",
        "Nothing here has to be impressive. It only has to be done.",
        "You are building a habit that will outlast any single program.",
        "Momentum is far easier to keep than to restart. Nice work keeping it.",
        "There is no wasted session, only data the plan hasn't used yet.",
        "Showing up tired still counts. Some days it counts double.",
        "You have already beaten the version of you who was going to skip today.",
        "The weights have no idea what day of the week it is.",
        "You are not behind. You are mid-progression.",
        "One good week doesn't transform anyone. Twenty of them do.",
        "Give this session your attention rather than your anxiety.",
        "Comparison is the quickest way to ruin a perfectly good training block.",
        "Trust the boring lifts — they are the ones that keep paying out.",
        "You get stronger between sessions. Training just asks for it.",
        "If today's numbers come in lower, that is information, not failure.",
        "The best program is the one you will still be doing in six months.",
        "You are doing something today that most people are only thinking about.",
        "Discipline is mostly just remembering what you wanted.",
        "The bar is patient. It will be there whatever kind of day you have had.",
        "Rest days are part of the plan, not a break from it.",
        "Turning up on the average days is what makes the good days possible.",
        "Whatever this week looks like, it is one more week of being someone who trains.",
        "Finish the session you started. That is the whole trick."
    )

    // ── Humour and fun (36) ───────────────────────────────────────────────────────────────────────
    private val HUMOUR: List<String> = listOf(
        "Consulting a large language model about your hamstrings. What a time to be alive.",
        "Doing the maths so you can get back to not doing maths.",
        "Somewhere, a barbell is getting nervous.",
        "This would all be faster if muscles came with an API.",
        "Arranging your week so no muscle group can file a formal complaint.",
        "Yes, there will be legs. There are always legs.",
        "Negotiating with physics on your behalf.",
        "Essentially rearranging dumbbells inside a spreadsheet.",
        "Deciding how much you are going to enjoy Thursday.",
        "No, it will not schedule an all-biceps week. It has been asked.",
        "Fun fact: the plural of gains is also gains.",
        "Loading… much like your bar, but with fewer plates.",
        "The AI has never done a squat and remains weirdly confident about them.",
        "Estimating your future suffering to two decimal places.",
        "Reading your logs like a detective who genuinely cares about rows.",
        "Good news: this wait is considerably shorter than the workout.",
        "Currently outsourcing your excuses.",
        "Nobody tell it that rest days are the best days.",
        "Converting optimism and coffee into sets and reps.",
        "Sadly, none of this burns calories. Not yet.",
        "Thinking very hard about your posterior chain. Someone has to.",
        "It declines to program a rest week for your thumbs. Sorry.",
        "Trying to make Wednesday sound appealing.",
        "Assembling a week that respects both your goals and your calendar.",
        "This is the calmest part of your training day. Savour it.",
        "Choosing exercises with names you will pretend you can pronounce.",
        "The mirror lies daily. The log does not.",
        "One day this will be muscle memory. Today it is still a loading screen.",
        "Deciding which muscle group gets to be the main character this week.",
        "Warning: may contain lunges.",
        "You could be scrolling right now. You are doing this instead. Respect.",
        "The bar always weighs the same. Some days it just lies about it.",
        "A whole week of programming in less time than a heavy set of squats.",
        "Statistically, this is the least tiring part of leg day.",
        "If it suggests burpees, that is strictly between you and it.",
        "Somewhere in here, an algorithm is quietly ruining your Tuesday."
    )

    /**
     * The full wait-message pool: [TRAINING_TIPS] + [APP_FACTS] + [ENCOURAGEMENT] + [HUMOUR].
     * 150 distinct, non-blank messages (locked by GenerationTipsTest).
     */
    val messages: List<String> = TRAINING_TIPS + APP_FACTS + ENCOURAGEMENT + HUMOUR

    /**
     * The wait message at rotation step [index] (wraps around the list).
     *
     * Kept from the pre-150 implementation for deterministic index-based access; the wait screens
     * themselves now use [rotation]. Safe for any [index], including negative ones, and returns ""
     * for an empty pool.
     */
    fun tip(index: Int): String =
        if (messages.isEmpty()) "" else messages[Math.floorMod(index, messages.size)]

    /**
     * A fresh sequencer over the whole [messages] pool for ONE wait. Every message is emitted once
     * before any repeats, in a shuffled order — so two waits show different sequences (brief 01,
     * assumption A3). Pass a seeded [random] to make the order reproducible in tests.
     */
    fun rotation(random: Random = Random.Default): Rotation = Rotation(messages, random)

    /**
     * [rotation] over an arbitrary [pool] — the seam that lets tests cover the degenerate pools
     * (empty / single-message) without touching the real 150-message library.
     */
    fun rotationOf(pool: List<String>, random: Random = Random.Default): Rotation =
        Rotation(pool, random)

    /**
     * Stateful shuffled sequencer ("shuffle bag"): draws from a shuffled copy of the pool and
     * reshuffles only once the bag is empty, so nothing repeats within a pass. On reshuffle the
     * first message is never the one that just played, so a cycle boundary can't look like a stutter.
     *
     * Never throws: an empty pool always yields "", and [next] may be called unboundedly.
     * Not thread-safe by design — each wait screen owns its own instance on its own coroutine.
     */
    class Rotation internal constructor(
        private val pool: List<String>,
        private val random: Random
    ) {
        private val bag: ArrayDeque<String> = ArrayDeque()
        private var lastEmitted: String? = null

        /** The next wait message, or "" when the pool is empty. */
        fun next(): String {
            if (pool.isEmpty()) return ""
            if (bag.isEmpty()) refill()
            val message = bag.removeFirst()
            lastEmitted = message
            return message
        }

        private fun refill() {
            val shuffled = pool.shuffled(random).toMutableList()
            // Avoid an immediate back-to-back repeat across a cycle boundary.
            if (shuffled.size > 1 && shuffled.first() == lastEmitted) {
                val swapWith = 1 + random.nextInt(shuffled.size - 1)
                shuffled[0] = shuffled[swapWith].also { shuffled[swapWith] = shuffled[0] }
            }
            bag.addAll(shuffled)
        }
    }
}

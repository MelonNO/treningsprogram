package com.migul.treningsprogram.domain

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.migul.treningsprogram.data.db.dao.GymPresetDao
import com.migul.treningsprogram.data.db.entity.Program
import com.migul.treningsprogram.data.preferences.PreferencesManager
import com.migul.treningsprogram.data.repository.AiRepository
import com.migul.treningsprogram.data.repository.MesocycleContext
import com.migul.treningsprogram.data.repository.WorkoutRepository
import com.migul.treningsprogram.data.repository.autoGenWeekKey
import com.migul.treningsprogram.data.repository.thisMonday
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Item 06: the weekly automatic program generation, extracted verbatim from
 * MainActivity.checkAndAutoGenerateWeeklyPlan() so it can fire from EVERY path that should start a
 * new week's plan:
 *  - activity creation (cold launch — the only trigger that existed before),
 *  - activity onStart (warm resume from recents in a new week — the case the old trigger missed),
 *  - the scheduled unattended Monday run ([com.migul.treningsprogram.notify.WeeklyAutoGenWorker]).
 *
 * Decision logic lives in [AutoGenPolicy] (unit-tested); the generation itself runs through
 * [GenerationRunner] so it survives backgrounding (item 05) and, on success or terminal failure
 * while backgrounded, the existing [com.migul.treningsprogram.notify.GenerationNotifier] fires from
 * inside [AiRepository] exactly as for every other generation path.
 *
 * Re-entry safe: a [Mutex] plus the runner's busy flag make concurrent triggers (onCreate+onStart
 * racing, or a manual generation already in flight) collapse to a single evaluation.
 */
@Singleton
class WeeklyAutoGenerator @Inject constructor(
    private val prefsManager: PreferencesManager,
    private val workoutRepository: WorkoutRepository,
    private val aiRepository: AiRepository,
    private val gymPresetDao: GymPresetDao,
    private val gson: Gson,
    private val runner: GenerationRunner,
) {

    enum class Outcome {
        GENERATED,
        FAILED_TRANSIENT,
        FAILED_CAPPED,
        SKIPPED_ALREADY_DONE,
        SKIPPED_GUARD,
        SKIPPED_BUSY,
        MARKED_DONE_EXISTING_PLAN,
    }

    private val mutex = Mutex()

    /**
     * Evaluates the weekly auto-gen guards and, when due, generates and saves this week's plan.
     * Cheap no-op on the common path (week already handled). Safe to call from any coroutine —
     * the actual generation is launched on the app-scoped [GenerationRunner] and joined, so a
     * caller cancelled mid-flight (e.g. the activity dying) does NOT cancel the generation.
     */
    suspend fun runIfDue(): Outcome {
        if (!mutex.tryLock()) return Outcome.SKIPPED_BUSY
        try {
            // A manual generation in flight (Settings / wizard / regen / rebalance) — don't stack an
            // automatic one on top; the next trigger (or the plan it saves) resolves the week.
            if (runner.isBusy) return Outcome.SKIPPED_BUSY

            val thisWeek = autoGenWeekKey()
            // Fast prefs-only pre-checks keep the common every-launch path free of DB reads.
            if (prefsManager.lastAutoGenerateWeek == thisWeek) return Outcome.SKIPPED_ALREADY_DONE
            if (prefsManager.apiKey.isBlank() || !prefsManager.hasCompletedOnboarding) {
                return Outcome.SKIPPED_GUARD
            }

            // E2 assumption N: a FROZEN program opts out of automatic weekly AI re-adaptation —
            // skip WITHOUT marking the week done so its plan stays as-is until the user acts.
            val activeProgram = workoutRepository.ensureActiveProgramId().let {
                workoutRepository.getActiveProgramOnce()
            }
            val monday = thisMonday()
            val existing = workoutRepository.getPlannedForWeek(monday).first()

            return when (
                AutoGenPolicy.evaluate(
                    weekKey = thisWeek,
                    lastAutoGenerateWeek = prefsManager.lastAutoGenerateWeek,
                    apiKeyBlank = prefsManager.apiKey.isBlank(),
                    onboardingComplete = prefsManager.hasCompletedOnboarding,
                    programFrozen = activeProgram?.isFrozen == true,
                    weekHasPlanRows = existing.isNotEmpty(),
                )
            ) {
                AutoGenPolicy.Decision.SKIP_ALREADY_DONE -> Outcome.SKIPPED_ALREADY_DONE
                AutoGenPolicy.Decision.SKIP_NO_API_KEY,
                AutoGenPolicy.Decision.SKIP_ONBOARDING_INCOMPLETE,
                AutoGenPolicy.Decision.SKIP_FROZEN -> Outcome.SKIPPED_GUARD

                AutoGenPolicy.Decision.MARK_DONE_EXISTING_PLAN -> {
                    // Plan already present (e.g. generated manually) — the week is genuinely done.
                    prefsManager.lastAutoGenerateWeek = thisWeek
                    Outcome.MARKED_DONE_EXISTING_PLAN
                }

                AutoGenPolicy.Decision.GENERATE -> {
                    var outcome = Outcome.FAILED_TRANSIENT
                    // Item 05: app scope + foreground service — survives backgrounding AND a dying
                    // caller. join() hands the outcome back when the caller is still alive.
                    runner.launch { outcome = generateAndSave(thisWeek, monday, activeProgram) }.join()
                    outcome
                }
            }
        } finally {
            mutex.unlock()
        }
    }

    /** The generation body, moved unchanged from MainActivity.checkAndAutoGenerateWeeklyPlan(). */
    private suspend fun generateAndSave(thisWeek: String, monday: Long, activeProgram: Program?): Outcome {
        val preset = gymPresetDao.getById(prefsManager.selectedGymPresetId)
        val equipment: List<String> = preset?.let {
            runCatching {
                val type = object : TypeToken<List<String>>() {}.type
                gson.fromJson<List<String>>(it.equipmentJson, type)
            }.getOrElse { emptyList() }
        } ?: prefsManager.wizardEquipment.split(",").map { it.trim() }.filter { it.isNotBlank() }
        // Item 02: the selected gym's hard exclusion list rides along with its equipment.
        val gymAvoid = GymExclusions.parse(preset?.avoidExercisesJson)

        // E2 (M2): stall/fatigue-triggered deload decision, reusing B3's StallDetector via the
        // repository. If we were already deloading last week, the recovery week is done (exit);
        // otherwise enter a deload iff enough lifts are concurrently stalled.
        val stalledLifts = workoutRepository.computeStalledLifts()
        val isDeload = DeloadPolicy.nextDeloadState(
            currentlyDeloading = activeProgram?.isDeloadActive ?: false,
            stalledCount = stalledLifts.size
        )
        // E2 (L1): mesocycle position so the model knows it is producing week N of a block.
        val mesocycle = activeProgram?.let { p ->
            MesocycleContext(
                mesocycleWeeks = p.mesocycleWeeks,
                weekInBlock = workoutRepository.weekInBlock(p, monday),
                isDeload = isDeload,
                stalledLifts = stalledLifts
            )
        } ?: MesocycleContext(isDeload = isDeload, stalledLifts = stalledLifts)

        // B08: honour pinned rest days (rest-day mode derives days/week; count mode unchanged).
        val eff = TrainingDaySelection.effective(prefsManager.restDaysCsv, prefsManager.daysPerWeek)
        val result = aiRepository.generateAdaptedProgram(
            daysPerWeek = eff.daysPerWeek,
            goal = prefsManager.fitnessGoal,
            experience = prefsManager.experienceLevel,
            sessionDurationMinutes = prefsManager.sessionDurationMinutes,
            equipment = equipment,
            equipmentNotes = preset?.notes ?: "",
            separateCardioDays = prefsManager.separateCardioDays,
            injuries = prefsManager.injuries,
            injurySeverity = prefsManager.injurySeverity,
            priorityMuscles = prefsManager.priorityMuscles,
            dislikedExercises = prefsManager.dislikedExercises,
            gymAvoidExercises = gymAvoid,
            onboardingContext = prefsManager.onboardingContext,
            mesocycle = mesocycle,
            restDays = eff.restDays
        )

        var outcome = Outcome.FAILED_TRANSIENT
        result.onSuccess { generationResult ->
            // B2: stamp the week's rationale onto every row so any row of the week carries it.
            workoutRepository.savePlan(
                monday,
                generationResult.exercises.map { it.copy(rationale = generationResult.rationale) }
            )
            prefsManager.lastGenerationAttemptCount = generationResult.attemptCount
            // E2: persist the deload flag the generated week was built for, so Home/Program show
            // (or clear) the deload indicator coherently with the plan that was just saved.
            workoutRepository.setActiveDeload(isDeload)
            prefsManager.lastAutoGenerateWeek = thisWeek
            outcome = Outcome.GENERATED
        }
        result.onFailure {
            // A transient failure does not write the whole week off — retry on a later trigger,
            // but stop after a small cap so a persistent failure doesn't keep burning API attempts.
            val update = AutoGenPolicy.onFailure(
                weekKey = thisWeek,
                prevFailWeek = prefsManager.autoGenFailWeek,
                prevFailCount = prefsManager.autoGenFailCount,
            )
            prefsManager.autoGenFailWeek = update.failWeek
            prefsManager.autoGenFailCount = update.failCount
            if (update.weekWrittenOff) prefsManager.lastAutoGenerateWeek = thisWeek
            outcome = if (update.weekWrittenOff) Outcome.FAILED_CAPPED else Outcome.FAILED_TRANSIENT
        }
        return outcome
    }
}

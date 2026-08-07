package com.migul.treningsprogram

import com.migul.treningsprogram.data.MuscleClassifier
import com.migul.treningsprogram.domain.BodyComposition
import com.migul.treningsprogram.domain.strength.RatingSet
import com.migul.treningsprogram.domain.strength.StrengthRating
import com.migul.treningsprogram.domain.strength.StrengthStandards
import com.migul.treningsprogram.domain.strength.StrengthTier
import com.migul.treningsprogram.domain.strength.UnratedReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Brief 02 (2026-08-07) — the strength rating engine.
 *
 * Every number here is hand-computed in the comment above it, against a round 80 kg male lifter,
 * so a future change to a threshold or a weighting fails loudly rather than silently re-rating
 * everyone. Epley is the app's single formula (`weight x (1 + reps/30)`) and is used as shipped.
 */
class StrengthRatingTest {

    private val NOW = 1_700_000_000_000L
    private val DAY = 24L * 60L * 60L * 1000L
    private val MALE = BodyComposition.SEX_MALE
    private val BW = 80f

    private fun set(name: String, kg: Float, reps: Int, daysAgo: Long = 1) =
        RatingSet(name, kg, reps, NOW - daysAgo * DAY)

    private fun rate(
        sets: List<RatingSet>,
        bw: Float? = BW,
        weighInDaysAgo: Long = 1,
        sex: String = MALE,
    ) = StrengthRating.rate(
        sets = sets,
        bodyWeightKg = bw,
        bodyWeightDateMs = if (bw == null) null else NOW - weighInDaysAgo * DAY,
        sex = sex,
        nowMs = NOW,
    )

    private fun group(sets: List<RatingSet>, name: String, bw: Float = BW) =
        rate(sets, bw).groups.first { it.group == name }

    // ── Tier boundaries: the product surface ─────────────────────────────────────────────────

    private val bench = StrengthStandards.LIFTS.first { it.id == "bench_press" }.male

    /** Bench male thresholds are 0.50 / 0.75 / 1.25 / 1.75 / 2.10 x body weight. */
    @Test fun aRatioExactlyOnATierBoundaryCountsAsTheHigherTier() {
        assertEquals(3.0f, StrengthRating.liftScore(1.25f, bench), 1e-4f)
        assertEquals(StrengthTier.INTERMEDIATE, StrengthRating.tierFor(StrengthRating.liftScore(1.25f, bench)))
    }

    @Test fun aHairUnderTheBoundaryIsStillTheLowerTier() {
        assertEquals(StrengthTier.NOVICE, StrengthRating.tierFor(StrengthRating.liftScore(1.2499f, bench)))
    }

    @Test fun everyThresholdLandsExactlyOnItsOwnTier() {
        listOf(
            0.50f to StrengthTier.BEGINNER, 0.75f to StrengthTier.NOVICE,
            1.25f to StrengthTier.INTERMEDIATE, 1.75f to StrengthTier.ADVANCED,
            2.10f to StrengthTier.ELITE,
        ).forEach { (ratio, tier) ->
            assertEquals("ratio $ratio", tier, StrengthRating.tierFor(StrengthRating.liftScore(ratio, bench)))
        }
    }

    @Test fun belowTheBeginnerThresholdIsUntrainedAndAboveEliteIsCapped() {
        assertEquals(StrengthTier.UNTRAINED, StrengthRating.tierFor(StrengthRating.liftScore(0.49f, bench)))
        assertEquals(5f, StrengthRating.liftScore(9f, bench), 1e-4f)
    }

    @Test fun ratioForScoreIsTheExactInverseOfLiftScore() {
        listOf(0.2f, 0.9f, 1f, 2.5f, 3f, 4.4f, 5f).forEach { s ->
            val back = StrengthRating.liftScore(StrengthRating.ratioForScore(s, bench), bench)
            assertEquals("score $s", s, back, 1e-3f)
        }
    }

    // ── A rating comes from kilos and reps, never from how often you logged ──────────────────

    /**
     * Bench 100 kg x 5. e1RM = 100 x (1 + 5/30) = 116.667; ratio = 116.667 / 80 = 1.4583.
     * That sits between 1.25 and 1.75, so score = 3 + (1.4583 - 1.25) / 0.50 = 3.4167 -> Intermediate.
     */
    @Test fun aSingleQualifyingLiftRatesItsGroup() {
        val chest = group(listOf(set("Bench Press", 100f, 5)), "Chest")
        assertEquals(StrengthTier.INTERMEDIATE, chest.tier)
        assertEquals(3.4167f, chest.score, 1e-3f)
        assertEquals("Bench Press", chest.drivingLift)
    }

    @Test fun loggingTheSameSetTenMoreTimesChangesNothing() {
        val once = rate(listOf(set("Bench Press", 100f, 5)))
        val tenTimes = rate((1..10).map { set("Bench Press", 100f, 5, daysAgo = it.toLong()) })
        assertEquals(once.totalScore, tenTimes.totalScore, 1e-6f)
        assertEquals(once.strengthScore, tenTimes.strengthScore)
    }

    /** A heavier single must lose to a better set: 100x1 = 103.3 e1RM, 90x8 = 114 e1RM. */
    @Test fun theBestSetIsTheHighestE1rmNotTheHeaviest() {
        val chest = group(listOf(set("Bench Press", 100f, 1), set("Bench Press", 90f, 8)), "Chest")
        assertEquals(90f, chest.bestWeightKg!!, 1e-4f)
        assertEquals(8, chest.bestReps)
    }

    // ── Body weight ─────────────────────────────────────────────────────────────────────────

    /**
     * The consequence the user was shown explicitly and accepted. Same 100 kg x 5 bench:
     * at 80 kg body weight ratio 1.4583 -> Intermediate; at 100 kg ratio 1.1667, which is between
     * 0.75 and 1.25, so score = 2 + (1.1667 - 0.75) / 0.50 = 2.8333 -> Novice.
     */
    @Test fun gainingBodyWeightLowersARatingWithUnchangedLifts() {
        val lifts = listOf(set("Bench Press", 100f, 5))
        assertEquals(StrengthTier.INTERMEDIATE, group(lifts, "Chest", bw = 80f).tier)
        assertEquals(StrengthTier.NOVICE, group(lifts, "Chest", bw = 100f).tier)
    }

    @Test fun losingBodyWeightRaisesIt() {
        val lifts = listOf(set("Bench Press", 100f, 5))
        assertTrue(group(lifts, "Chest", bw = 70f).score > group(lifts, "Chest", bw = 80f).score)
    }

    // ── Main dominates, accessories nudge ───────────────────────────────────────────────────

    /**
     * Bench 100x5 alone scores 3.4167. A single body-weight dip at 80 kg: e1RM = 80 x 1.0333 =
     * 82.667, ratio 1.0333, dip thresholds 0.95/1.10/1.35/1.65/2.00 -> score = 1 + (1.0333 - 0.95)
     * / 0.15 = 1.5556. Blend = 0.75 x 3.4167 + 0.25 x 1.5556 = 2.9514 -> Novice.
     */
    @Test fun aWeakAccessoryPullsTheGroupDownWithoutOverturningTheMainLift() {
        val chest = group(listOf(set("Bench Press", 100f, 5), set("Dip", 0f, 1)), "Chest")
        assertEquals(2.9514f, chest.score, 1e-3f)
        // The main lift still dominates: the group sits far closer to the bench than to the dip.
        assertTrue(chest.score > 0.5f * (3.4167f + 1.5556f))
    }

    @Test fun aStrongAccessoryNudgesTheGroupUp() {
        val alone = group(listOf(set("Bench Press", 100f, 5)), "Chest").score
        val nudged = group(listOf(set("Bench Press", 100f, 5), set("Dip", 60f, 8)), "Chest").score
        assertTrue("a strong dip should raise Chest", nudged > alone)
    }

    @Test fun theMainLiftAlwaysCarriesThreeQuartersOfTheGroup() {
        assertEquals(0.75f, StrengthRating.MAIN_WEIGHT, 1e-6f)
        assertEquals(0.25f, StrengthRating.ACCESSORY_WEIGHT, 1e-6f)
        assertEquals(1f, StrengthRating.MAIN_WEIGHT + StrengthRating.ACCESSORY_WEIGHT, 1e-6f)
    }

    /** With no main lift logged, the best accessory simply IS the rating rather than nothing. */
    @Test fun aGroupWithOnlyAccessoriesIsStillRated() {
        val back = group(listOf(set("Pull-Up", 0f, 10)), "Back")
        assertEquals("Pull-Up", back.drivingLift)
        assertNotNull(back.tier)
    }

    // ── Body-weight lifts contribute body weight ────────────────────────────────────────────

    /**
     * Pull-up at 80 kg body weight x 10: e1RM = 80 x (1 + 10/30) = 106.667, ratio 1.3333.
     * Pull-up thresholds 0.95/1.10/1.35/1.65/2.00 -> score = 2 + (1.3333 - 1.10) / 0.25 = 2.9333.
     */
    @Test fun aBodyweightPullUpRatesAgainstBodyWeight() {
        val back = group(listOf(set("Pull-Up", 0f, 10)), "Back")
        assertEquals(2.9333f, back.score, 1e-3f)
        assertEquals(StrengthTier.NOVICE, back.tier)
    }

    /** +20 kg on the belt at 80 kg body weight is a 100 kg lift, and must rate as one. */
    @Test fun addedWeightOnABodyweightLiftCountsAsBodyWeightPlusTheLoad() {
        val plain = group(listOf(set("Pull-Up", 0f, 5)), "Back").score
        val loaded = group(listOf(set("Pull-Up", 20f, 5)), "Back").score
        assertTrue("weighted pull-ups must outrank bodyweight ones", loaded > plain)
        // 100 kg total x 5 -> e1RM 116.667, ratio 1.4583 -> 3 + (1.4583-1.35)/0.30 = 3.3611
        assertEquals(3.3611f, loaded, 1e-3f)
    }

    // ── Only lifts with a real population standard count ────────────────────────────────────

    @Test fun machinesCablesAndDumbbellsNeverRate() {
        listOf(
            "Leg Press", "Cable Fly", "Lat Pulldown", "Smith Machine Bench Press",
            "Pec Deck", "Assisted Pull-Up", "Dumbbell Bench Press", "DB Shoulder Press",
            "Hack Squat", "Resistance Band Row", "Kettlebell Swing", "Goblet Squat",
            "Machine Chest Press", "Seated Cable Row",
        ).forEach { assertNull("$it must not rate", StrengthStandards.identify(it)) }
    }

    @Test fun theClassicLiftsDoRate() {
        listOf(
            "Bench Press", "Back Squat", "Deadlift", "Overhead Press", "Barbell Row",
            "Pull-Up", "Chin-Up", "Dip", "Front Squat", "Romanian Deadlift", "Barbell Curl",
        ).forEach { assertNotNull("$it must rate", StrengthStandards.identify(it)) }
    }

    @Test fun aMachineOnlyChestSessionLeavesChestUnrated() {
        val chest = group(listOf(set("Machine Chest Press", 90f, 8), set("Cable Fly", 25f, 12)), "Chest")
        assertNull(chest.tier)
        assertEquals(UnratedReason.NO_QUALIFYING_LIFT, chest.unratedReason)
    }

    /** Specific names must not be captured by a more general lift's keywords. */
    @Test fun specificVariantsBeatGeneralOnes() {
        assertEquals("incline_bench", StrengthStandards.identify("Incline Bench Press")!!.id)
        assertEquals("bench_press", StrengthStandards.identify("Barbell Bench Press")!!.id)
        assertEquals("romanian_deadlift", StrengthStandards.identify("Romanian Deadlift")!!.id)
        assertEquals("sumo_deadlift", StrengthStandards.identify("Sumo Deadlift")!!.id)
        assertEquals("deadlift", StrengthStandards.identify("Deadlift")!!.id)
        assertEquals("front_squat", StrengthStandards.identify("Front Squat")!!.id)
        assertEquals("back_squat", StrengthStandards.identify("Barbell Back Squat")!!.id)
        assertEquals("chin_up", StrengthStandards.identify("Chin-Up")!!.id)
        assertEquals("push_press", StrengthStandards.identify("Push Press")!!.id)
    }

    /** Push-ups deliberately do not rate — see the rationale in StrengthStandards. */
    @Test fun pushUpsDoNotRate() {
        assertNull(StrengthStandards.identify("Push-Up"))
        assertNull(StrengthStandards.identify("Incline Push Up"))
    }

    /** Core only rates with added load; a bodyweight rollout has no defensible fraction. */
    @Test fun coreOnlyRatesWhenLoadIsActuallyAdded() {
        assertNull(group(listOf(set("Ab Wheel Rollout", 0f, 10)), "Core").tier)
        assertNotNull(group(listOf(set("Weighted Sit-Up", 20f, 8)), "Core").tier)
    }

    // ── Warm-ups, rep range and the three-month window ──────────────────────────────────────

    /** D6: Epley degrades on very high reps, so those sets do not rate. */
    @Test fun setsAboveTheQualifyingRepRangeAreIgnored() {
        assertEquals(12, StrengthStandards.MAX_QUALIFYING_REPS)
        assertNull(group(listOf(set("Bench Press", 60f, 20)), "Chest").tier)
        assertNotNull(group(listOf(set("Bench Press", 60f, 12)), "Chest").tier)
    }

    @Test fun aLiftOutsideTheThreeMonthWindowNoLongerRates() {
        assertEquals(90L, StrengthStandards.WINDOW_DAYS)
        assertNotNull(group(listOf(set("Bench Press", 100f, 5, daysAgo = 89)), "Chest").tier)
        assertNull(group(listOf(set("Bench Press", 100f, 5, daysAgo = 91)), "Chest").tier)
    }

    @Test fun lettingThreeMonthsPassLowersTheTotal() {
        val fresh = rate(listOf(set("Bench Press", 100f, 5, daysAgo = 10)))
        val stale = rate(listOf(set("Bench Press", 100f, 5, daysAgo = 200)))
        assertEquals(StrengthTier.INTERMEDIATE, fresh.totalTier)
        assertNull(stale.totalTier)
    }

    // ── The total ───────────────────────────────────────────────────────────────────────────

    /**
     * Chest = 3.4167 (weight 2), Legs from a 120 kg x 5 squat: e1RM 140, ratio exactly 1.75, which
     * is the Advanced threshold for squat -> score exactly 3.0 (weight 3).
     * Total = (2 x 3.4167 + 3 x 3.0) / 5 = 15.8333 / 5 = 3.1667 -> Intermediate.
     *
     * If the four unrated groups counted as zeroes the denominator would be 12 and the total would
     * be 1.32 -> Beginner. Asserting Intermediate is what proves they are excluded, not zeroed.
     */
    @Test fun unratedGroupsAreLeftOutOfTheTotalRatherThanCountedAsZero() {
        val p = rate(listOf(set("Bench Press", 100f, 5), set("Back Squat", 120f, 5)))
        assertEquals(3.1667f, p.totalScore, 1e-3f)
        assertEquals(StrengthTier.INTERMEDIATE, p.totalTier)
        assertEquals(63, p.strengthScore)
        assertEquals(4, p.groups.count { !it.isRated })
    }

    /** Bigger groups weigh more — legs and back outrank arms and core. */
    @Test fun theTotalWeightsBiggerGroupsMore() {
        val w = StrengthStandards.GROUP_WEIGHTS
        assertTrue(w["Legs"]!! > w["Core"]!!)
        assertTrue(w["Back"]!! > w["Arms"]!!)
        assertTrue(w["Chest"]!! >= w["Arms"]!!)
        assertEquals(StrengthStandards.RATED_GROUPS.toSet(), w.keys)
    }

    /**
     * The same two ratings swapped between a heavy and a light group must move the total, or the
     * weighting is not doing anything.
     */
    @Test fun whichGroupIsStrongChangesTheTotal() {
        // Strong legs (weight 3) + weak arms (weight 1) vs the mirror image.
        val strongLegs = rate(listOf(set("Back Squat", 200f, 3), set("Barbell Curl", 20f, 8)))
        val strongArms = rate(listOf(set("Back Squat", 60f, 3), set("Barbell Curl", 60f, 8)))
        assertTrue(strongLegs.totalScore > strongArms.totalScore)
    }

    /**
     * The scalar the gamification layer keys on. Chest alone at 3.4167 -> 68.
     * Elite everywhere is 100, which is what keeps `level_100` the hardest achievement it always was.
     */
    @Test fun strengthScoreIsTheTotalOnAPerCentScale() {
        val p = rate(listOf(set("Bench Press", 100f, 5)))
        assertEquals(Math.round(p.totalScore * 20f), p.strengthScore)
        assertEquals(68, p.strengthScore)
        assertTrue(p.strengthScore in 0..100)
        assertEquals(0, rate(emptyList()).strengthScore)
    }

    @Test fun cardioNeverGetsALevel() {
        assertEquals(6, StrengthStandards.RATED_GROUPS.size)
        assertTrue(StrengthStandards.RATED_GROUPS.none { it == "Cardio" })
        val p = rate(listOf(set("Treadmill Run", 0f, 1), set("Bench Press", 100f, 5)))
        assertEquals(6, p.groups.size)
        assertTrue(p.groups.none { it.group == "Cardio" })
    }

    @Test fun theWeakestRatedGroupIsNamedAndIsActuallyTheWeakest() {
        // Squat at 60x5 is far weaker relative to standards than the 100x5 bench.
        val p = rate(listOf(set("Bench Press", 100f, 5), set("Back Squat", 60f, 5)))
        assertEquals("Legs", p.weakestRatedGroup)
        assertNull("an unrated group is never called the weakest",
            p.groups.firstOrNull { it.group == p.weakestRatedGroup }?.unratedReason)
    }

    // ── No guessed ratings ──────────────────────────────────────────────────────────────────

    @Test fun missingSexProducesAnUnratedStateNotAnEstimate() {
        val p = rate(listOf(set("Bench Press", 100f, 5)), sex = "")
        assertEquals(UnratedReason.NO_SEX, p.profileUnratedReason)
        assertNull(p.totalTier)
        assertTrue(p.groups.all { it.tier == null && it.unratedReason == UnratedReason.NO_SEX })
    }

    @Test fun missingBodyWeightProducesAnUnratedState() {
        val p = rate(listOf(set("Bench Press", 100f, 5)), bw = null)
        assertEquals(UnratedReason.NO_BODY_WEIGHT, p.profileUnratedReason)
        assertNull(p.totalTier)
    }

    @Test fun aWeighInOlderThanTheWindowIsRefusedRatherThanUsed() {
        val p = rate(listOf(set("Bench Press", 100f, 5)), weighInDaysAgo = 200)
        assertEquals(UnratedReason.STALE_BODY_WEIGHT, p.profileUnratedReason)
        assertNull(p.totalTier)
    }

    @Test fun femaleStandardsAreUsedForFemaleLifters() {
        val lifts = listOf(set("Bench Press", 60f, 5))
        val m = rate(lifts, sex = BodyComposition.SEX_MALE).groups.first { it.group == "Chest" }
        val f = rate(lifts, sex = BodyComposition.SEX_FEMALE).groups.first { it.group == "Chest" }
        assertTrue("the same lift must rate higher against female standards", f.score > m.score)
    }

    // ── "What would move this" ──────────────────────────────────────────────────────────────

    /**
     * Chest sits at 3.4167 off a 100 kg x 5 bench; Advanced is score 4.0, which for bench is the
     * 1.75 threshold -> needed e1RM = 1.75 x 80 = 140 kg.
     *   weight route: 140 / (1 + 5/30) = 120.0 kg, i.e. +20.0 kg at the same 5 reps.
     *   reps route:   30 x (140/100 - 1) = 12.0 reps, i.e. +7 reps at the same 100 kg.
     */
    @Test fun nextStepIsTheUsersOwnKilosAndReps() {
        val chest = group(listOf(set("Bench Press", 100f, 5)), "Chest")
        val step = chest.nextStep!!
        assertEquals(StrengthTier.ADVANCED, step.targetTier)
        assertEquals("Bench Press", step.liftName)
        assertEquals(20.0f, step.addedKg!!, 1e-2f)
        assertEquals(7, step.addedReps)
        assertEquals(100f, step.currentWeightKg, 1e-4f)
        assertEquals(5, step.currentReps)
    }

    /** Applying the advice must actually produce the tier it promised. */
    @Test fun followingTheWeightAdviceReachesThePromisedTier() {
        val step = group(listOf(set("Bench Press", 100f, 5)), "Chest").nextStep!!
        val after = group(listOf(set("Bench Press", 100f + step.addedKg!!, 5)), "Chest")
        assertEquals(step.targetTier, after.tier)
    }

    @Test fun followingTheRepsAdviceReachesThePromisedTier() {
        val step = group(listOf(set("Bench Press", 100f, 5)), "Chest").nextStep!!
        val after = group(listOf(set("Bench Press", 100f, 5 + step.addedReps!!)), "Chest")
        assertEquals(step.targetTier, after.tier)
    }

    /**
     * When the reps route would leave the qualifying range, only the weight route is offered —
     * the app must not advise a set it would then refuse to count.
     *
     * Barbell curl 30 kg x 10 at 80 kg: e1RM 40, ratio 0.50, curl thresholds
     * 0.25/0.40/0.60/0.85/1.10 -> score 2.5 (Novice). Intermediate needs ratio 0.60 = 48 kg e1RM.
     *   reps route:   30 x (48/30 - 1) = 18 reps — past the 12-rep cap, so it is withheld.
     *   weight route: 48 / (1 + 10/30) = 36.0 kg, i.e. +6.0 kg.
     */
    @Test fun theRepsRouteIsOmittedWhenItWouldLeaveTheQualifyingRange() {
        val step = group(listOf(set("Barbell Curl", 30f, 10)), "Arms").nextStep!!
        assertNull("18 reps is past the qualifying cap and must not be advised", step.addedReps)
        assertEquals(6.0f, step.addedKg!!, 1e-2f)
    }

    /** Whenever a reps route IS offered, it must land inside the qualifying range. */
    @Test fun anyAdvisedRepsRouteStaysInsideTheQualifyingRange() {
        listOf(
            set("Bench Press", 100f, 5), set("Back Squat", 120f, 3),
            set("Pull-Up", 0f, 8), set("Overhead Press", 50f, 6),
        ).forEach { s ->
            val g = rate(listOf(s)).groups.first { it.isRated }
            g.nextStep?.addedReps?.let { extra ->
                assertTrue(
                    "${s.exerciseName}: ${g.nextStep!!.currentReps} + $extra must stay within the cap",
                    g.nextStep!!.currentReps + extra <= StrengthStandards.MAX_QUALIFYING_REPS
                )
            }
        }
    }

    @Test fun anEliteGroupHasNoNextStep() {
        val chest = group(listOf(set("Bench Press", 250f, 5)), "Chest")
        assertEquals(StrengthTier.ELITE, chest.tier)
        assertNull(chest.nextStep)
    }

    @Test fun aBodyweightLiftsNextStepIsAddedLoad() {
        val back = group(listOf(set("Pull-Up", 0f, 8)), "Back")
        assertTrue(back.nextStep!!.isBodyWeightLift)
    }

    /**
     * The rating itself must declare whether its driving lift carries body weight. A display layer
     * that had to rediscover this by looking the lift up by NAME would break silently the day a
     * lift is renamed — and break as a wrong number ("Pull-Up ... 0 kg"), not a failing test.
     */
    @Test fun aGroupDeclaresWhetherItsDrivingLiftCarriesBodyWeight() {
        assertTrue(group(listOf(set("Pull-Up", 0f, 8)), "Back").isBodyWeightLift)
        assertTrue(group(listOf(set("Dip", 10f, 6)), "Chest").isBodyWeightLift)
        assertFalse(group(listOf(set("Bench Press", 100f, 5)), "Chest").isBodyWeightLift)
        assertFalse(group(listOf(set("Back Squat", 120f, 5)), "Legs").isBodyWeightLift)
        // An unrated group has no driving lift, so the flag must not claim one.
        assertFalse(group(emptyList(), "Core").isBodyWeightLift)
    }

    // ── Consistency with the rest of the app ────────────────────────────────────────────────

    /** A6: ratings must speak the app's existing muscle-group language, not a second taxonomy. */
    @Test fun everyQualifyingLiftAgreesWithTheAppsOwnMuscleClassifier() {
        StrengthStandards.LIFTS.forEach { lift ->
            assertEquals(
                "${lift.displayName} is ${lift.group} here but ${MuscleClassifier.fromName(lift.displayName)} in MuscleClassifier",
                lift.group,
                MuscleClassifier.fromName(lift.displayName)
            )
        }
    }

    @Test fun everyRatedGroupHasExactlyOneMainLiftAndAtLeastOneUnlockHint() {
        StrengthStandards.RATED_GROUPS.forEach { g ->
            val mains = StrengthStandards.LIFTS.filter {
                it.group == g && it.role == StrengthStandards.LiftRole.MAIN
            }
            assertEquals("$g must have exactly one main lift", 1, mains.size)
            assertNotNull("$g must be able to name what unlocks it", StrengthStandards.mainLiftFor(g))
        }
    }

    @Test fun everyLiftHasStrictlyAscendingThresholdsForBothSexes() {
        StrengthStandards.LIFTS.forEach { lift ->
            listOf("male" to lift.male, "female" to lift.female).forEach { (who, std) ->
                val t = std.thresholds
                (1 until t.size).forEach { i ->
                    assertTrue(
                        "${lift.id} $who thresholds must ascend: ${t[i - 1]} then ${t[i]}",
                        t[i] > t[i - 1]
                    )
                }
            }
        }
    }

    @Test fun femaleStandardsSitBelowMaleOnesForEveryLift() {
        StrengthStandards.LIFTS.forEach { lift ->
            lift.male.thresholds.zip(lift.female.thresholds).forEach { (m, f) ->
                assertTrue("${lift.id}: female $f should not exceed male $m", f <= m)
            }
        }
    }

    @Test fun everyLiftIdIsUnique() {
        val ids = StrengthStandards.LIFTS.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    /** The catalogue must be reachable: each lift's own display name must identify back to it. */
    @Test fun everyLiftIdentifiesFromItsOwnDisplayName() {
        StrengthStandards.LIFTS.forEach { lift ->
            assertEquals(
                "${lift.displayName} must identify as ${lift.id}",
                lift.id,
                StrengthStandards.identify(lift.displayName)?.id
            )
        }
    }
}

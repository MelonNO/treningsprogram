package com.migul.treningsprogram

import com.migul.treningsprogram.domain.strength.GroupRating
import com.migul.treningsprogram.domain.strength.NextStep
import com.migul.treningsprogram.domain.strength.StrengthProfile
import com.migul.treningsprogram.domain.strength.StrengthStandards
import com.migul.treningsprogram.domain.strength.StrengthTier
import com.migul.treningsprogram.domain.strength.UnratedReason
import com.migul.treningsprogram.ui.strength.StrengthCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Brief 02 (2026-08-07) — the Strength breakdown screen's copy.
 *
 * The screen IS its wording: "what would move this", the unlock hints and the unrated prompts are
 * the deliverable, and every one of them is built by the pure [StrengthCopy] object. That is what
 * is asserted here, following the house pattern of unit-testing the pure seam off-device
 * (`RestUxBatchTest` over `LogWorkoutViewModel.resolveRestStart`). No Robolectric, no UI test —
 * the on-device look is the user's check.
 */
class StrengthBreakdownCopyTest {

    // ── Helpers ───────────────────────────────────────────────────────────────────────────────

    private fun rated(
        group: String = "Chest",
        tier: StrengthTier = StrengthTier.INTERMEDIATE,
        score: Float = 3.4f,
        drivingLift: String = "Bench Press",
        bestWeightKg: Float = 100f,
        bestReps: Int = 5,
        nextStep: NextStep? = null,
    ) = GroupRating(
        group = group, tier = tier, score = score, drivingLift = drivingLift,
        bestWeightKg = bestWeightKg, bestReps = bestReps, nextStep = nextStep,
        unratedReason = null,
    )

    private fun unrated(group: String, reason: UnratedReason) = GroupRating(
        group = group, tier = null, score = 0f, drivingLift = null, bestWeightKg = null,
        bestReps = null, nextStep = null, unratedReason = reason,
    )

    private fun step(
        liftName: String = "Bench Press",
        targetTier: StrengthTier = StrengthTier.ADVANCED,
        addedKg: Float? = 5f,
        addedReps: Int? = 2,
        currentWeightKg: Float = 80f,
        currentReps: Int = 5,
        isBodyWeightLift: Boolean = false,
    ) = NextStep(
        liftName, targetTier, addedKg, addedReps, currentWeightKg, currentReps, isBodyWeightLift
    )

    private fun profile(
        groups: List<GroupRating>,
        totalTier: StrengthTier? = StrengthTier.INTERMEDIATE,
        totalScore: Float = 3.4f,
        weakest: String? = "Arms",
        profileUnratedReason: UnratedReason? = null,
    ) = StrengthProfile(
        groups = groups, totalTier = totalTier, totalScore = totalScore,
        strengthScore = (totalScore * 20f).toInt(), weakestRatedGroup = weakest,
        profileUnratedReason = profileUnratedReason, bodyWeightKg = 80f,
    )

    // ── "What would move this" — both routes ──────────────────────────────────────────────────

    @Test fun nextStep_bothRoutes_usesTheUsersOwnNumbers() {
        assertEquals(
            "Bench Press: add 5 kg, or get 2 more reps at 80 kg, to reach Advanced.",
            StrengthCopy.nextStepLine(step())
        )
    }

    @Test fun nextStep_halfKiloStepsKeepTheirDecimal() {
        assertEquals(
            "Bench Press: add 2.5 kg, or get 1 more rep at 82.5 kg, to reach Advanced.",
            StrengthCopy.nextStepLine(
                step(addedKg = 2.5f, addedReps = 1, currentWeightKg = 82.5f)
            )
        )
    }

    // ── One route missing: the other is omitted, not faked ────────────────────────────────────

    @Test fun nextStep_nullAddedReps_omitsTheRepsRoute() {
        val line = StrengthCopy.nextStepLine(step(addedReps = null))
        assertEquals("Bench Press: add 5 kg to reach Advanced.", line)
        assertFalse("no reps route when addedReps is null", line.contains("more rep"))
        // Single route: no dangling comma before "to reach".
        assertFalse(line.contains(", to reach"))
    }

    @Test fun nextStep_nullAddedKg_omitsTheWeightRoute() {
        val line = StrengthCopy.nextStepLine(step(addedKg = null))
        assertEquals("Bench Press: get 2 more reps at 80 kg to reach Advanced.", line)
        assertFalse("no weight route when addedKg is null", line.contains("add "))
    }

    @Test fun nextStep_bothNull_neverInventsARoute() {
        val line = StrengthCopy.nextStepLine(step(addedKg = null, addedReps = null))
        assertFalse(line.contains("add "))
        assertFalse(line.contains("more rep"))
        assertTrue(line.isNotBlank())
    }

    // ── Body-weight lifts: the kilos are ADDED load ───────────────────────────────────────────

    @Test fun nextStep_bodyWeightLift_wordsTheKilosAsAddedLoad() {
        val line = StrengthCopy.nextStepLine(
            step(
                liftName = "Pull-Up", targetTier = StrengthTier.ELITE, addedKg = 7.5f,
                addedReps = 3, currentWeightKg = 0f, isBodyWeightLift = true,
            )
        )
        assertEquals(
            "Pull-Up: add 7.5 kg on a belt or vest, or get 3 more reps at body weight, " +
                "to reach Elite.",
            line
        )
        // Never "add 7.5 kg" bare, which would read as loading a bar.
        assertTrue(line.contains("belt or vest"))
    }

    @Test fun nextStep_bodyWeightLift_alreadyCarryingLoad_saysSoInTheRepsRoute() {
        val line = StrengthCopy.nextStepLine(
            step(
                liftName = "Dip", addedKg = 5f, addedReps = 2, currentWeightKg = 10f,
                isBodyWeightLift = true,
            )
        )
        assertTrue(line, line.contains("get 2 more reps with 10 kg added"))
        assertFalse("body-weight reps route must not read as a bar load", line.contains("at 10 kg"))
    }

    @Test fun nextStep_barbellLift_neverMentionsBeltOrVest() {
        assertFalse(StrengthCopy.nextStepLine(step()).contains("belt"))
    }

    // ── nextStep == null: honest, and distinguishes the two reasons ───────────────────────────

    @Test fun nextStep_nullAtElite_saysTopTier() {
        val line = StrengthCopy.nextStepLine(
            rated(tier = StrengthTier.ELITE, score = 5f, nextStep = null)
        )
        assertEquals(StrengthCopy.AT_TOP_TIER, line)
        assertTrue(line.contains("top tier"))
    }

    @Test fun nextStep_nullBelowElite_blamesTheOtherLifts_notAFakeRoute() {
        // The engine returns null when the main lift alone cannot carry the blended group score.
        val line = StrengthCopy.nextStepLine(
            rated(
                group = "Back", tier = StrengthTier.ADVANCED, score = 4.6f,
                drivingLift = "Deadlift", nextStep = null,
            )
        )
        assertTrue(line, line.contains("Deadlift"))
        assertTrue(line, line.contains("Elite"))
        assertFalse("must not fabricate a weight route", line.contains("add "))
        assertFalse("must not claim the top tier below Elite", line.contains("top tier"))
    }

    // ── The best set behind the rating ────────────────────────────────────────────────────────

    @Test fun bestSet_barbellLift() {
        assertEquals("Bench Press · 5 reps × 100 kg", StrengthCopy.bestSetLine(rated()))
    }

    @Test fun bestSet_singleRepIsSingular() {
        assertEquals(
            "Bench Press · 1 rep × 140 kg",
            StrengthCopy.bestSetLine(rated(bestWeightKg = 140f, bestReps = 1))
        )
    }

    @Test fun bestSet_bodyWeightLiftWithNoAddedLoad_neverReadsAsZeroKg() {
        val line = StrengthCopy.bestSetLine(
            rated(group = "Back", drivingLift = "Pull-Up", bestWeightKg = 0f, bestReps = 8)
        )
        assertEquals("Pull-Up · 8 reps at body weight", line)
        assertFalse("0 kg is the stored value, not the truth", line!!.contains("0 kg"))
    }

    @Test fun bestSet_bodyWeightLiftWithAddedLoad_showsBoth() {
        assertEquals(
            "Chin-Up · 5 reps × body weight + 20 kg",
            StrengthCopy.bestSetLine(
                rated(group = "Back", drivingLift = "Chin-Up", bestWeightKg = 20f, bestReps = 5)
            )
        )
    }

    @Test fun bestSet_unratedGroupHasNone() {
        assertNull(StrengthCopy.bestSetLine(unrated("Core", UnratedReason.NO_QUALIFYING_LIFT)))
    }

    // ── Unrated: NO_QUALIFYING_LIFT names a specific, real lift ───────────────────────────────

    @Test fun unrated_noQualifyingLift_namesTheGroupsMainLift() {
        val line = StrengthCopy.unratedLine("Shoulders", UnratedReason.NO_QUALIFYING_LIFT)
        assertTrue(line, line.startsWith("Log an Overhead Press to get a Shoulders rating."))
        assertTrue("the other qualifying lift is offered too", line.contains("Push Press"))
    }

    @Test fun unrated_noQualifyingLift_articleFollowsTheLiftName() {
        // "a Bench Press" but "an Overhead Press" — the hint is read aloud in the user's head.
        assertTrue(
            StrengthCopy.unratedLine("Chest", UnratedReason.NO_QUALIFYING_LIFT)
                .startsWith("Log a Bench Press")
        )
        assertTrue(
            StrengthCopy.unratedLine("Shoulders", UnratedReason.NO_QUALIFYING_LIFT)
                .startsWith("Log an Overhead Press")
        )
    }

    @Test fun unrated_core_saysTheLiftsNeedAddedWeight() {
        val line = StrengthCopy.unratedLine("Core", UnratedReason.NO_QUALIFYING_LIFT)
        assertTrue(line, line.contains("Weighted Sit-Up"))
        assertTrue(line, line.contains("Hanging Leg Raise"))
        assertTrue(line, line.contains("Ab Wheel Rollout"))
        assertTrue(
            "Core must say bodyweight reps don't rate",
            line.contains("only count with weight added")
        )
        // Guard the premise: every Core lift really does require added load.
        assertTrue(StrengthStandards.liftsFor("Core").all { it.requiresAddedLoad })
    }

    @Test fun unrated_nonCoreGroupsDoNotClaimAddedWeightIsNeeded() {
        listOf("Chest", "Back", "Shoulders", "Arms", "Legs").forEach { group ->
            val line = StrengthCopy.unratedLine(group, UnratedReason.NO_QUALIFYING_LIFT)
            assertFalse("$group: $line", line.contains("with weight added"))
        }
    }

    @Test fun unrated_everyRatedGroupProducesAHintNamingARealLift() {
        StrengthStandards.RATED_GROUPS.forEach { group ->
            val line = StrengthCopy.unratedLine(group, UnratedReason.NO_QUALIFYING_LIFT)
            val main = StrengthStandards.mainLiftFor(group)
            assertNotNull("$group has no MAIN lift to name", main)
            assertTrue("$group hint must name $group", line.contains(group))
            assertTrue("$group hint must name ${main!!.displayName}", line.contains(main.displayName))
        }
    }

    // ── Unrated: the profile-wide blockers ────────────────────────────────────────────────────

    @Test fun unrated_noSex_pointsAtSettingsTraining_andRefusesToGuess() {
        val line = StrengthCopy.unratedLine("Legs", UnratedReason.NO_SEX)
        assertTrue(line, line.contains("Settings → Training"))
        assertTrue(line, line.contains("sex"))
        assertTrue("must not offer a default", line.contains("no honest default"))
    }

    @Test fun unrated_noBodyWeight_asksForAWeighIn() {
        val line = StrengthCopy.unratedLine("Legs", UnratedReason.NO_BODY_WEIGHT)
        assertTrue(line, line.contains("Log a body weight"))
        assertTrue(line, line.contains("History → Body"))
    }

    @Test fun unrated_staleBodyWeight_saysWhyTheOldOneCannotBeUsed() {
        val line = StrengthCopy.unratedLine("Legs", UnratedReason.STALE_BODY_WEIGHT)
        assertTrue(line, line.contains("${StrengthStandards.MAX_WEIGHIN_AGE_DAYS} days"))
        assertTrue(line, line.contains("Log a body weight"))
    }

    @Test fun unrated_everyReasonHasNonEmptyDistinctCopy() {
        val lines = UnratedReason.values().map { StrengthCopy.unratedLine("Legs", it) }
        lines.forEach { assertTrue("blank unrated copy", it.isNotBlank()) }
        assertEquals("each reason needs its own sentence", lines.size, lines.toSet().size)
    }

    // ── Weakest rated group — informs, never acts ─────────────────────────────────────────────

    @Test fun weakest_namesTheGroup() {
        assertEquals("Arms is your weakest rated group right now.", StrengthCopy.weakestLine("Arms"))
    }

    @Test fun weakest_nullWhenNothingIsRated() {
        assertNull(StrengthCopy.weakestLine(null))
    }

    @Test fun weakest_noteSaysNothingWasChanged() {
        assertTrue(StrengthCopy.WEAKEST_NOTE.contains("Nothing has been changed for you"))
        assertTrue(StrengthCopy.WEAKEST_NOTE.contains("yourself"))
    }

    // ── The total ─────────────────────────────────────────────────────────────────────────────

    @Test fun total_isATierNameNeverANumber() {
        val p = profile(StrengthStandards.RATED_GROUPS.map { rated(group = it) })
        assertEquals("Intermediate", StrengthCopy.totalTierName(p))
        assertFalse(StrengthCopy.totalTierName(p).any { it.isDigit() })
    }

    @Test fun total_caption_countsTheRatedGroups() {
        val groups = listOf(
            rated(group = "Chest"), rated(group = "Back"),
            unrated("Shoulders", UnratedReason.NO_QUALIFYING_LIFT),
            unrated("Arms", UnratedReason.NO_QUALIFYING_LIFT),
            unrated("Legs", UnratedReason.NO_QUALIFYING_LIFT),
            unrated("Core", UnratedReason.NO_QUALIFYING_LIFT),
        )
        assertEquals(
            "Weighted across your rated muscle groups — 2 of 6 rated.",
            StrengthCopy.totalCaption(profile(groups))
        )
    }

    @Test fun total_unratedProfile_showsTheBlockingReason() {
        val groups = StrengthStandards.RATED_GROUPS.map { unrated(it, UnratedReason.NO_SEX) }
        val p = profile(groups, totalTier = null, totalScore = 0f, weakest = null,
            profileUnratedReason = UnratedReason.NO_SEX)
        assertEquals(StrengthCopy.NOT_RATED, StrengthCopy.totalTierName(p))
        assertTrue(StrengthCopy.totalCaption(p).contains("Settings → Training"))
    }

    @Test fun total_noGroupRatedYet_saysSoWithoutBlamingTheUser() {
        val groups = StrengthStandards.RATED_GROUPS
            .map { unrated(it, UnratedReason.NO_QUALIFYING_LIFT) }
        val p = profile(groups, totalTier = null, totalScore = 0f, weakest = null)
        val caption = StrengthCopy.totalCaption(p)
        assertTrue(caption, caption.contains("No qualifying lift in the last 3 months"))
        assertTrue(caption, caption.contains("lifts that count"))
    }

    // ── Progress within a tier ────────────────────────────────────────────────────────────────

    @Test fun tierProgress_isTheFractionalPartOfTheScore() {
        assertEquals(0, StrengthCopy.tierProgressPercent(3.0f))
        assertEquals(40, StrengthCopy.tierProgressPercent(3.4f))
        assertEquals(99, StrengthCopy.tierProgressPercent(2.99f))
    }

    @Test fun tierProgress_eliteIsFull_notEmpty() {
        // floor(5.0) == 5.0, so the raw fraction is 0 — an empty bar next to "Elite" would lie.
        assertEquals(100, StrengthCopy.tierProgressPercent(5f))
    }

    @Test fun tierProgress_clampsOutOfRangeScores() {
        assertEquals(0, StrengthCopy.tierProgressPercent(0f))
        assertEquals(0, StrengthCopy.tierProgressPercent(-1f))
        assertEquals(100, StrengthCopy.tierProgressPercent(6f))
    }

    // ── The lifts that count ──────────────────────────────────────────────────────────────────

    @Test fun liftSections_followTheScreensGroupOrder() {
        assertEquals(
            StrengthStandards.RATED_GROUPS,
            StrengthCopy.liftSections().map { it.group }
        )
    }

    @Test fun liftSections_showEveryQualifyingLiftExactlyOnce() {
        val shown = StrengthCopy.liftSections().flatMap { it.lines }
        assertEquals(StrengthStandards.LIFTS.size, shown.size)
        StrengthStandards.LIFTS.forEach { lift ->
            assertEquals(
                "${lift.displayName} must appear exactly once",
                1, shown.count { it.startsWith("${lift.displayName} —") }
            )
        }
    }

    @Test fun liftSections_putTheMainLiftFirstInEachGroup() {
        StrengthCopy.liftSections().forEach { section ->
            val main = StrengthStandards.mainLiftFor(section.group)!!
            assertTrue(
                "${section.group} should lead with ${main.displayName}",
                section.lines.first().startsWith(main.displayName)
            )
            assertTrue(section.lines.first().contains("main lift"))
        }
    }

    @Test fun liftLine_marksAddedLoadAndBodyWeightLifts() {
        val situp = StrengthStandards.LIFTS.first { it.id == "weighted_situp" }
        assertTrue(StrengthCopy.liftLine(situp).contains("weight added"))

        val pullUp = StrengthStandards.LIFTS.first { it.id == "pull_up" }
        assertTrue(StrengthCopy.liftLine(pullUp).contains("body weight + any added load"))

        val bench = StrengthStandards.LIFTS.first { it.id == "bench_press" }
        assertEquals("Bench Press — main lift", StrengthCopy.liftLine(bench))
    }

    @Test fun liftsNote_isHonestAboutWhatDoesNotCount() {
        listOf("Machines", "cables", "dumbbells", "assisted", "push-ups").forEach {
            assertTrue("note should mention $it", StrengthCopy.LIFTS_NOTE.contains(it))
        }
        assertTrue(
            StrengthCopy.LIFTS_NOTE.contains("${StrengthStandards.MAX_QUALIFYING_REPS} reps")
        )
        // Excluded work still counts everywhere else — say so rather than implying it is worthless.
        assertTrue(StrengthCopy.LIFTS_NOTE.contains("still log"))
    }

    // ── The explainer ─────────────────────────────────────────────────────────────────────────

    @Test fun explainer_statesTheThreeThingsTheUserAccepted() {
        val e = StrengthCopy.EXPLAINER
        assertTrue("relative to body weight", e.contains("body weight"))
        assertTrue("relative to sex", e.contains("sex"))
        assertTrue("three-month window", e.contains("3 months"))
        assertTrue("can go down", e.contains("can go down"))
        assertTrue("gaining weight lowers it", e.contains("gaining body weight lowers it"))
    }

    @Test fun explainer_doesNotApologiseOrOfferToCorrectIt() {
        val e = StrengthCopy.EXPLAINER.lowercase()
        listOf("sorry", "unfortunately", "we'll fix", "correct this", "bug").forEach {
            assertFalse("explainer must not apologise ($it)", e.contains(it))
        }
        assertTrue(StrengthCopy.EXPLAINER.contains("working as intended"))
    }

    // ── Small shared helpers ──────────────────────────────────────────────────────────────────

    @Test fun kg_matchesTheHouseFormat() {
        assertEquals("5", StrengthCopy.kg(5f))
        assertEquals("2.5", StrengthCopy.kg(2.5f))
        assertEquals("82.5", StrengthCopy.kg(82.5f))
    }

    @Test fun joinNames_readsAsASentence() {
        assertEquals("", StrengthCopy.joinNames(emptyList()))
        assertEquals("A", StrengthCopy.joinNames(listOf("A")))
        assertEquals("A and B", StrengthCopy.joinNames(listOf("A", "B")))
        assertEquals("A, B and C", StrengthCopy.joinNames(listOf("A", "B", "C")))
    }

    @Test fun withArticle_picksAnBeforeAVowel() {
        assertEquals("a Bench Press", StrengthCopy.withArticle("Bench Press"))
        assertEquals("an Overhead Press", StrengthCopy.withArticle("Overhead Press"))
        assertEquals("an Ab Wheel Rollout", StrengthCopy.withArticle("Ab Wheel Rollout"))
    }

    // ── Cross-check against the real engine output ────────────────────────────────────────────

    @Test fun everyGroupRatingProducesNonBlankCopy_ratedOrNot() {
        val all = StrengthStandards.RATED_GROUPS.mapIndexed { i, g ->
            if (i % 2 == 0) rated(group = g, nextStep = step(liftName = "Lift $g"))
            else unrated(g, UnratedReason.values()[i % UnratedReason.values().size])
        }
        all.forEach { g ->
            assertTrue("${g.group}: blank tier name", StrengthCopy.groupTierName(g).isNotBlank())
            val line = if (g.isRated) StrengthCopy.nextStepLine(g)
            else StrengthCopy.unratedLine(g.group, g.unratedReason)
            assertTrue("${g.group}: blank hint", line.isNotBlank())
        }
    }
}

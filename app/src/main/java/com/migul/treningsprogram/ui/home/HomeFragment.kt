package com.migul.treningsprogram.ui.home

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import com.migul.treningsprogram.R
import com.migul.treningsprogram.data.db.entity.BodyMeasurement
import com.migul.treningsprogram.data.repository.GamificationRepository
import com.migul.treningsprogram.data.repository.currentDayOfWeek
import com.migul.treningsprogram.databinding.FragmentHomeBinding
import com.migul.treningsprogram.domain.MuscleRecovery
import com.migul.treningsprogram.ui.history.RecapTargetViewModel
import com.migul.treningsprogram.domain.model.DailyChallenge
import com.migul.treningsprogram.domain.model.WorkoutResult
import com.migul.treningsprogram.ui.shared.SharedWorkoutResultViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// Item 7: the LOGICAL weekday (1=Mon … 7=Sun) an active session's timestamp belongs to, respecting
// the configurable day boundary so a resumed early-hours session keeps the previous day's plan.
private fun dayOfWeekFromMs(ms: Long): Int =
    com.migul.treningsprogram.domain.DayBoundary.logicalDate(ms).dayOfWeek.value

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()
    private val sharedResultVm: SharedWorkoutResultViewModel by activityViewModels()
    private val recapTarget: RecapTargetViewModel by activityViewModels()
    private var xpAnimating = false
    // H2: once the user opens/closes the weigh-in input themselves, stop auto-expanding it
    // on the empty (zero weigh-ins) state.
    private var bwInputUserToggled = false
    private val bwDateFmt = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Show first-launch card if setup not done; hide all normal content
        if (viewModel.isFirstLaunch) {
            binding.cardFirstLaunch.visibility = View.VISIBLE
            binding.btnStartWorkout.visibility = View.GONE
            binding.btnStartSetup.setOnClickListener {
                if (findNavController().currentDestination?.id == R.id.homeFragment)
                    findNavController().navigate(R.id.action_home_to_setup_wizard)
            }
            return  // skip wiring up the rest of the home screen until setup is complete
        }

        // B7: the once-per-new-month "Wrapped is ready" card — tap opens the story, x dismisses;
        // both retire the card for that month.
        viewLifecycleOwner.lifecycleScope.launch {
            val month = viewModel.wrappedReadyMonth() ?: return@launch
            if (_binding == null) return@launch
            binding.tvWrappedReady.text = "\uD83C\uDF81 Your ${month.label} Wrapped is ready — tap to open"
            binding.cardWrappedReady.visibility = View.VISIBLE
            binding.cardWrappedReady.setOnClickListener {
                if (!isAdded) return@setOnClickListener
                viewModel.markWrappedSeen(month)
                binding.cardWrappedReady.visibility = View.GONE
                com.migul.treningsprogram.ui.history.WrappedDialogFragment
                    .newInstance(month)
                    .show(parentFragmentManager, "wrapped")
            }
            binding.btnWrappedReadyDismiss.setOnClickListener {
                viewModel.markWrappedSeen(month)
                binding.cardWrappedReady.visibility = View.GONE
            }
        }

        // U2: tap the XP bar/card to open the XP log. Guard against rapid double-tap (S8 convention).
        binding.cardHomeXp.setOnClickListener {
            if (findNavController().currentDestination?.id == R.id.homeFragment)
                findNavController().navigate(R.id.action_home_to_xp_log)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    data class TodayCard(
                        val plan: List<com.migul.treningsprogram.data.db.entity.PlannedExercise>,
                        val active: com.migul.treningsprogram.data.db.entity.WorkoutSession?,
                        val completed: Boolean,
                        val targets: Map<String, Float>
                    )
                    combine(
                        viewModel.todayPlan,
                        viewModel.activeSession,
                        viewModel.todayCompleted,
                        viewModel.todayTargets
                    ) { plan, active, completed, targets -> TodayCard(plan, active, completed, targets) }
                        .collect { (plan, active, completed, targets) ->
                            // B5: the rest-day recovery card rides the same state — rest day
                            // (no plan), nothing active/logged, feature on, not dismissed today.
                            renderRestRecovery(plan.isEmpty(), active != null, completed)
                            when {
                                active != null -> {
                                    binding.btnStartWorkout.isEnabled = true
                                    binding.btnStartWorkout.text = "Resume Workout"
                                    binding.tvTodayPlan.text = "Session in progress — tap to continue"
                                    binding.btnStartWorkout.setOnClickListener {
                                        if (!isAdded) return@setOnClickListener
                                        if (findNavController().currentDestination?.id != R.id.homeFragment) return@setOnClickListener
                                        findNavController().navigate(
                                            R.id.action_home_to_log,
                                            bundleOf("sessionId" to active.id, "dayOfWeek" to dayOfWeekFromMs(active.dateMs))
                                        )
                                    }
                                }
                                completed -> {
                                    binding.btnStartWorkout.isEnabled = true
                                    binding.btnStartWorkout.text = "View Recap"
                                    binding.tvTodayPlan.text = "Today's session is logged. Great work!"
                                    binding.btnStartWorkout.setOnClickListener {
                                        // Open the latest session's recap under the Stats tab.
                                        // Navigate by selecting the tab — avoids pushing historyFragment
                                        // onto homeFragment's back stack which desyncs NavigationUI.
                                        recapTarget.request(null)
                                        requireActivity()
                                            .findViewById<BottomNavigationView>(R.id.bottom_nav)
                                            ?.selectedItemId = R.id.historyFragment
                                    }
                                }
                                plan.isEmpty() -> {
                                    binding.btnStartWorkout.isEnabled = true
                                    binding.btnStartWorkout.text = "Log Freestyle Session"
                                    binding.tvTodayPlan.text = "Rest day — recovery is part of the plan."
                                    binding.btnStartWorkout.setOnClickListener {
                                        viewModel.startWorkout { sessionId ->
                                            if (!isAdded) return@startWorkout
                                            if (findNavController().currentDestination?.id != R.id.homeFragment) return@startWorkout
                                            findNavController().navigate(
                                                R.id.action_home_to_log,
                                                bundleOf("sessionId" to sessionId, "dayOfWeek" to -1)
                                            )
                                        }
                                    }
                                }
                                else -> {
                                    binding.btnStartWorkout.isEnabled = true
                                    binding.btnStartWorkout.text = getString(R.string.start_workout)
                                    binding.tvTodayPlan.text = renderTodayPlan(plan, targets)
                                    binding.btnStartWorkout.setOnClickListener {
                                        viewModel.startWorkout { sessionId ->
                                            if (!isAdded) return@startWorkout
                                            if (findNavController().currentDestination?.id != R.id.homeFragment) return@startWorkout
                                            findNavController().navigate(
                                                R.id.action_home_to_log,
                                                bundleOf("sessionId" to sessionId, "dayOfWeek" to currentDayOfWeek())
                                            )
                                        }
                                    }
                                }
                            }
                        }
                }
                launch {
                    viewModel.userStats.collect { stats ->
                        if (stats != null) {
                            binding.tvLevelBadge.text = "L${stats.level}"
                            if (!xpAnimating) {
                                val progress = (GamificationRepository.levelProgress(stats.totalXp) * 100).toInt()
                                binding.progressXp.progress = progress
                            }
                            val xpNext = GamificationRepository.xpForLevel(stats.level + 1) - stats.totalXp
                            val title = GamificationRepository.levelTitle(stats.level)
                            binding.tvXpLabel.text = "$title  •  ${stats.totalXp} XP  •  $xpNext to next"
                            val streakEmoji = when {
                                stats.currentStreak >= 7 -> "🔥🔥"
                                stats.currentStreak >= 3 -> "🔥"
                                else -> "📅"
                            }
                            binding.tvStreak.text = "$streakEmoji ${stats.currentStreak}"
                        } else {
                            binding.tvLevelBadge.text = "L1"
                            binding.tvXpLabel.text = "0 XP"
                            binding.tvStreak.text = "📅 0"
                        }
                    }
                }
                launch {
                    viewModel.challenges.collect { challenges ->
                        binding.layoutHomeChallenges.removeAllViews()
                        challenges.forEach { ch -> binding.layoutHomeChallenges.addView(makeChallengeItem(ch)) }
                    }
                }
                launch {
                    val fmt = SimpleDateFormat("dd MMM", Locale.getDefault())
                    viewModel.recentSessions.collect { summaries ->
                        binding.tvRecentSessions.text = if (summaries.isEmpty()) {
                            getString(R.string.no_sessions_yet)
                        } else {
                            summaries.joinToString("\n") { s ->
                                val focus = if (s.focusMuscle.isNotBlank()) "${s.focusMuscle}  •  " else ""
                                val dur = if (s.session.durationMinutes > 0) "  •  ${s.session.durationMinutes} min" else ""
                                val setWord = if (s.setCount == 1) "set" else "sets"
                                "• ${fmt.format(Date(com.migul.treningsprogram.domain.DayBoundary.toLogicalMillis(s.session.dateMs)))}  —  ${focus}${s.exerciseCount} ex  •  ${s.setCount} $setWord$dur"
                            }
                        }
                    }
                }
                launch {
                    viewModel.muscleRecovery.collect { items ->
                        // H3: the recovery card behaves like Home's other conditional cards —
                        // present only when at least one muscle is actually recovering.
                        binding.cardRecovery.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
                        renderRecovery(items)
                    }
                }
                launch {
                    viewModel.bodyMeasurements.collect { measurements ->
                        renderBodyWeightEntries(measurements.take(5))
                        // H2 + R3: compact glance line. >=2 weigh-ins show the smoothed
                        // current weight + trend; exactly one shows the current weight alone;
                        // zero shows no glance and opens the input so the first log is one field
                        // away (auto-expand only until the user has toggled it themselves).
                        val glance = com.migul.treningsprogram.domain.WeightTrend.homeLine(measurements)
                            ?: measurements.firstOrNull()?.let { "${formatWeight(it.weightKg)} kg" }
                        binding.tvHomeBwTrend.visibility = if (glance == null) View.GONE else View.VISIBLE
                        binding.tvHomeBwTrend.text = glance ?: ""
                        if (measurements.isEmpty() && !bwInputUserToggled) {
                            binding.layoutHomeBwInput.visibility = View.VISIBLE
                        }
                    }
                }
                launch {
                    // E2: surface the active program's deload state so the user can tell a
                    // stall/fatigue-triggered deload week is in effect (M2).
                    viewModel.deloadActive.collect { active ->
                        binding.cardDeload.visibility = if (active) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    // N5: the quiet goal nudge — visible only when a goal is one step away.
                    viewModel.goalNudge.collect { line ->
                        binding.cardGoalNudge.visibility = if (line == null) View.GONE else View.VISIBLE
                        binding.tvGoalNudge.text = line ?: ""
                    }
                }
                launch {
                    // B5: refresh the card's CONTENT when the recovery picture changes.
                    viewModel.restDaySuggestion.collect { s ->
                        binding.tvRestRecoveryTitle.text = s.title
                        binding.tvRestRecoveryLine.text = s.line
                    }
                }
            }
        }

        binding.btnHomeAddWeight.setOnClickListener {
            val text = binding.etHomeBodyweight.text?.toString() ?: return@setOnClickListener
            val kg = text.toFloatOrNull() ?: return@setOnClickListener
            viewModel.addBodyWeight(kg)
            binding.etHomeBodyweight.text?.clear()
        }

        // H2: the weigh-in input + recent entries reveal on demand, so the body-weight card
        // is a compact glance on days nothing is being entered.
        binding.btnHomeBwToggle.setOnClickListener {
            bwInputUserToggled = true
            val show = binding.layoutHomeBwInput.visibility != View.VISIBLE
            binding.layoutHomeBwInput.visibility = if (show) View.VISIBLE else View.GONE
        }
    }

    private fun makeChallengeItem(ch: DailyChallenge): View {
        val item = layoutInflater.inflate(R.layout.item_challenge, binding.layoutHomeChallenges, false)
        val check = item.findViewById<TextView>(R.id.tv_challenge_check)
        val name = item.findViewById<TextView>(R.id.tv_challenge_name)
        val desc = item.findViewById<TextView>(R.id.tv_challenge_desc)
        val xp = item.findViewById<TextView>(R.id.tv_challenge_xp)
        if (ch.isCompleted) {
            check.text = "✓"
            check.background = requireContext().getDrawable(R.drawable.bg_check_done)
            item.alpha = 0.6f
        } else {
            check.text = ""
            check.background = requireContext().getDrawable(R.drawable.bg_check_todo)
        }
        name.text = ch.name
        // R4: week-scoped countable challenges show their standing progress ("31/45") on Home.
        val suffix = com.migul.treningsprogram.domain.ChallengeProgress.homeSuffix(ch)
        desc.text = if (suffix != null) "${ch.description}  ·  $suffix" else ch.description
        xp.text = "+${ch.bonusXp} XP"
        return item
    }

    private fun formatWeight(w: Float): String =
        if (w == w.toInt().toFloat()) w.toInt().toString() else w.toString()

    /**
     * N1 — today's plan lines with a quiet per-exercise target suffix (A-N1a): a muted,
     * slightly smaller "Beat 60 kg" inline on the exercise's own line, so the card gains no
     * height. Exercises without history get no suffix; the number is exactly the in-workout
     * Beat chip's initial target (HomeTargets contract).
     */
    private fun renderTodayPlan(
        plan: List<com.migul.treningsprogram.data.db.entity.PlannedExercise>,
        targets: Map<String, Float>
    ): CharSequence {
        val out = android.text.SpannableStringBuilder()
        val muted = android.graphics.Color.parseColor("#7E908E")
        plan.forEachIndexed { i, ex ->
            if (i > 0) out.append("\n")
            out.append("• ${ex.exerciseName}  ${ex.sets}x${ex.targetReps}")
            if (ex.targetWeightKg > 0f) out.append(" @ ${formatWeight(ex.targetWeightKg)}kg")
            val target = targets[ex.exerciseName]
            if (target != null) {
                val start = out.length
                out.append("   Beat ${formatWeight(target)} kg")
                out.setSpan(
                    android.text.style.ForegroundColorSpan(muted),
                    start, out.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                out.setSpan(
                    android.text.style.RelativeSizeSpan(0.85f),
                    start, out.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        return out
    }

    override fun onResume() {
        super.onResume()
        val result = sharedResultVm.consumeForHome() ?: return
        // Set XP bar to pre-workout position, then animate
        val beforeXp = result.totalXp - result.xpEarned
        val startProgress = (GamificationRepository.levelProgress(beforeXp) * 100).toInt()
        binding.progressXp.progress = startProgress
        xpAnimating = true
        binding.root.post { playXpAnimation(result, startProgress) }
    }

    private fun playXpAnimation(result: WorkoutResult, startProgress: Int) {
        val endProgress = (result.levelProgress * 100).toInt()

        if (result.didLevelUp) {
            // 1. Fill bar to 100%
            ObjectAnimator.ofInt(binding.progressXp, "progress", startProgress, 100).apply {
                duration = ((100 - startProgress) * 8L).coerceAtLeast(400)
                interpolator = DecelerateInterpolator()
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (_binding == null) return
                        // 2. Show level-up overlay
                        showLevelUpOverlay(result) {
                            if (_binding == null) return@showLevelUpOverlay
                            // 3. Reset bar, animate to new level progress
                            binding.progressXp.progress = 0
                            ObjectAnimator.ofInt(binding.progressXp, "progress", 0, endProgress).apply {
                                duration = 700
                                interpolator = DecelerateInterpolator()
                                addListener(object : AnimatorListenerAdapter() {
                                    override fun onAnimationEnd(animation: Animator) {
                                        xpAnimating = false
                                        showChallengeSnackbar(result)
                                    }
                                })
                                start()
                            }
                        }
                    }
                })
                start()
            }
        } else {
            ObjectAnimator.ofInt(binding.progressXp, "progress", startProgress, endProgress).apply {
                duration = 900
                interpolator = DecelerateInterpolator()
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        xpAnimating = false
                        showChallengeSnackbar(result)
                    }
                })
                start()
            }
        }
    }

    private fun showLevelUpOverlay(result: WorkoutResult, onDismiss: () -> Unit) {
        val overlay = binding.layoutLevelUpOverlay
        val title = GamificationRepository.levelTitle(result.level)
        binding.tvLevelUpNewLevel.text = "Level ${result.level}"
        binding.tvLevelUpTitle.text = title

        // Scale in
        overlay.alpha = 0f
        overlay.visibility = View.VISIBLE
        overlay.animate().alpha(1f).setDuration(250).start()

        // Animate the card itself (child 0 of the overlay FrameLayout)
        val card = overlay.getChildAt(0)
        card.scaleX = 0.5f
        card.scaleY = 0.5f
        card.animate()
            .scaleX(1f).scaleY(1f)
            .setDuration(400)
            .setInterpolator(OvershootInterpolator(2.5f))
            .start()

        // Bounce the level badge
        binding.tvLevelBadge.animate()
            .scaleX(1.5f).scaleY(1.5f).setDuration(200)
            .withEndAction {
                binding.tvLevelBadge.animate()
                    .scaleX(1f).scaleY(1f).setDuration(300)
                    .setInterpolator(OvershootInterpolator(3f)).start()
            }.start()

        val dismiss = {
            overlay.animate().alpha(0f).setDuration(250).withEndAction {
                overlay.visibility = View.GONE
                onDismiss()
            }.start()
        }
        binding.btnLevelUpDismiss.setOnClickListener { dismiss() }
        overlay.setOnClickListener { dismiss() }

        // Auto-dismiss after 3.5s if user doesn't tap
        overlay.postDelayed({ if (_binding != null && overlay.visibility == View.VISIBLE) dismiss() }, 3500)
    }

    private fun showChallengeSnackbar(result: WorkoutResult) {
        if (!isAdded || _binding == null) return
        result.completedChallenges.forEach { ch ->
            Snackbar.make(binding.root, "Challenge complete: ${ch.name}  +${ch.bonusXp} XP", Snackbar.LENGTH_LONG).show()
        }
    }

    /**
     * B5 — visibility gate + dismiss wiring for the rest-day recovery card. Pure decision in
     * RecoverySuggestions.shouldShow; dismissing stores today's logical epoch-day so the card
     * returns on the NEXT rest day. Nothing here touches streaks/XP/challenges/history.
     */
    private fun renderRestRecovery(planEmpty: Boolean, hasActive: Boolean, completedToday: Boolean) {
        if (_binding == null) return
        val show = com.migul.treningsprogram.domain.RecoverySuggestions.shouldShow(
            planEmpty = planEmpty,
            hasActiveSession = hasActive,
            completedToday = completedToday,
            enabled = viewModel.prefs.restDayRecoveryEnabled,
            dismissedEpochDay = viewModel.prefs.recoveryCardDismissedEpochDay,
            todayEpochDay = com.migul.treningsprogram.domain.DayBoundary.todayEpochDay()
        )
        binding.cardRestRecovery.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            binding.btnRestRecoveryDismiss.setOnClickListener {
                viewModel.prefs.recoveryCardDismissedEpochDay =
                    com.migul.treningsprogram.domain.DayBoundary.todayEpochDay()
                binding.cardRestRecovery.visibility = View.GONE
            }
        }
    }

    /** Amber color for the RECOVERING state (the only state shown in the panel). */
    private val recoveringColor = android.graphics.Color.parseColor("#FF9800")

    /**
     * Render the recovering-only muscle recovery panel (U1).
     *
     * Shows only muscles currently RECOVERING. Each row:
     *   - Amber status dot
     *   - Muscle name + "trained Xh ago" subtitle
     *   - Progress bar showing how far through recovery (0=just trained, 1=almost ready)
     *   - Remaining hours text
     *
     * Empty state: shows a single row with "Nothing recovering — all muscles are ready." text.
     *
     * Tap on any row: navigates to the last session that trained that muscle (via RecapTargetViewModel
     * + bottom-nav switch to history, mirroring the "View Recap" pattern in onViewCreated).
     */
    private fun renderRecovery(items: List<HomeViewModel.MuscleRecoveryItem>) {
        if (_binding == null) return
        val container = binding.layoutHomeRecovery
        container.removeAllViews()
        val density = resources.displayMetrics.density

        if (items.isEmpty()) {
            // Empty state: sensible message, not a broken panel
            val tv = TextView(requireContext()).apply {
                text = "All muscles are rested and ready."
                textSize = 13f
                setTextColor(requireContext().getColor(com.google.android.material.R.color.material_on_surface_emphasis_medium))
            }
            container.addView(tv)
            return
        }

        items.forEach { item ->
            val outerRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                val vp = (6 * density).toInt()
                setPadding(0, vp, 0, vp)
                isClickable = true
                isFocusable = true
                // Tap: navigate to the last session that trained this muscle
                setOnClickListener {
                    if (!isAdded || _binding == null) return@setOnClickListener
                    // B06: pass the tapped fine-muscle label so Recap highlights the
                    // exercise(s) in that session that drove this muscle's fatigue.
                    recapTarget.request(item.lastSessionId, highlightMuscle = item.muscleLabel)
                    requireActivity()
                        .findViewById<BottomNavigationView>(R.id.bottom_nav)
                        ?.selectedItemId = R.id.historyFragment
                }
            }

            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            // Colored status dot (amber = recovering)
            val dotSize = (12 * density).toInt()
            val dot = View(requireContext()).apply {
                val bg = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(recoveringColor)
                }
                background = bg
                layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                    marginEnd = (12 * density).toInt()
                }
            }

            // Left column: muscle name + subtitle
            val textCol = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val tvName = TextView(requireContext()).apply {
                text = item.muscleLabel
                textSize = 14f
                setTextColor(requireContext().getColor(com.google.android.material.R.color.material_on_surface_emphasis_high_type))
            }
            val tvSub = TextView(requireContext()).apply {
                text = formatTrainedAgo(item.lastTrainedMs)
                textSize = 12f
                setTextColor(requireContext().getColor(com.google.android.material.R.color.material_on_surface_emphasis_medium))
            }
            textCol.addView(tvName)
            textCol.addView(tvSub)

            // Right column: remaining hours
            val remainHours = (item.remainingMs / (60L * 60L * 1000L)).coerceAtLeast(0L)
            val tvRemain = TextView(requireContext()).apply {
                text = if (remainHours < 1L) "<1h left" else "${remainHours}h left"
                textSize = 12f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(recoveringColor)
            }

            row.addView(dot)
            row.addView(textCol)
            row.addView(tvRemain)
            outerRow.addView(row)

            // Progress bar: fraction of the 48 h recovery window completed
            val progressBar = ProgressBar(
                requireContext(), null,
                android.R.attr.progressBarStyleHorizontal
            ).apply {
                max = 100
                progress = (item.recoveryFraction * 100).toInt()
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (4 * density).toInt()
                )
                lp.topMargin = (4 * density).toInt()
                layoutParams = lp
                progressDrawable?.setColorFilter(
                    recoveringColor, android.graphics.PorterDuff.Mode.SRC_IN
                )
            }
            outerRow.addView(progressBar)
            container.addView(outerRow)
        }
    }

    private fun formatTrainedAgo(lastTrainedMs: Long): String {
        val hours = ((System.currentTimeMillis() - lastTrainedMs).coerceAtLeast(0L)) / (60L * 60L * 1000L)
        return when {
            hours < 1L  -> "trained just now"
            hours < 24L -> "trained ${hours}h ago"
            else        -> "trained ${hours / 24L}d ago"
        }
    }

    private fun renderBodyWeightEntries(entries: List<BodyMeasurement>) {
        binding.layoutHomeBwEntries.removeAllViews()
        entries.forEach { m ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val p = (4 * resources.displayMetrics.density).toInt()
                setPadding(0, p, 0, p)
            }
            val tvDate = TextView(requireContext()).apply {
                text = bwDateFmt.format(java.util.Date(m.dateMs))
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setTextColor(requireContext().getColor(com.google.android.material.R.color.material_on_background_emphasis_medium))
            }
            val tvWeight = TextView(requireContext()).apply {
                text = "${formatWeight(m.weightKg)} kg"
                textSize = 13f
                setTextColor(android.graphics.Color.parseColor("#7FE9E1"))
            }
            row.addView(tvDate)
            row.addView(tvWeight)
            row.setOnLongClickListener {
                // H4: deleting a weigh-in is recoverable — an accidental long-press no longer
                // silently destroys data. The snackbar Undo re-inserts the same entry (id, date
                // and value preserved), so the trend line and AI context see it again unchanged.
                viewModel.deleteBodyMeasurement(m)
                if (isAdded && _binding != null) {
                    Snackbar.make(binding.root, "Weigh-in deleted", Snackbar.LENGTH_LONG)
                        .setAction("Undo") { viewModel.restoreBodyMeasurement(m) }
                        .show()
                }
                true
            }
            binding.layoutHomeBwEntries.addView(row)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

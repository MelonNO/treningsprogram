package com.migul.treningsprogram.ui.home

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
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
import com.migul.treningsprogram.data.repository.GamificationRepository
import com.migul.treningsprogram.data.repository.currentDayOfWeek
import com.migul.treningsprogram.databinding.FragmentHomeBinding
import com.migul.treningsprogram.domain.model.DailyChallenge
import com.migul.treningsprogram.domain.model.WorkoutResult
import com.migul.treningsprogram.ui.shared.SharedWorkoutResultViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private fun dayOfWeekFromMs(ms: Long): Int {
    val cal = Calendar.getInstance().apply { timeInMillis = ms }
    return when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY    -> 1
        Calendar.TUESDAY   -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY  -> 4
        Calendar.FRIDAY    -> 5
        Calendar.SATURDAY  -> 6
        Calendar.SUNDAY    -> 7
        else               -> -1
    }
}

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()
    private val sharedResultVm: SharedWorkoutResultViewModel by activityViewModels()
    private var xpAnimating = false

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
                findNavController().navigate(R.id.action_home_to_setup_wizard)
            }
            return  // skip wiring up the rest of the home screen until setup is complete
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(
                        viewModel.todayPlan,
                        viewModel.activeSession,
                        viewModel.todayCompleted
                    ) { plan, active, completed -> Triple(plan, active, completed) }
                        .collect { (plan, active, completed) ->
                            when {
                                active != null -> {
                                    binding.btnStartWorkout.isEnabled = true
                                    binding.btnStartWorkout.text = "Resume Workout"
                                    binding.tvTodayPlan.text = "Session in progress — tap to continue"
                                    binding.btnStartWorkout.setOnClickListener {
                                        if (!isAdded) return@setOnClickListener
                                        findNavController().navigate(
                                            R.id.action_home_to_log,
                                            bundleOf("sessionId" to active.id, "dayOfWeek" to dayOfWeekFromMs(active.dateMs))
                                        )
                                    }
                                }
                                completed -> {
                                    binding.btnStartWorkout.isEnabled = true
                                    binding.btnStartWorkout.text = "View Today's Session"
                                    binding.tvTodayPlan.text = "Today's session is logged. Great work!"
                                    binding.btnStartWorkout.setOnClickListener {
                                        // Navigate by selecting the tab — avoids pushing historyFragment
                                        // onto homeFragment's back stack which desyncs NavigationUI.
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
                                    binding.tvTodayPlan.text = plan.joinToString("\n") { ex ->
                                        "• ${ex.exerciseName}  ${ex.sets}x${ex.targetReps}" +
                                            if (ex.targetWeightKg > 0f) " @ ${formatWeight(ex.targetWeightKg)}kg" else ""
                                    }
                                    binding.btnStartWorkout.setOnClickListener {
                                        viewModel.startWorkout { sessionId ->
                                            if (!isAdded) return@startWorkout
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
                                "• ${fmt.format(Date(s.session.dateMs))}  —  ${focus}${s.exerciseCount} ex  •  ${s.setCount} $setWord$dur"
                            }
                        }
                    }
                }
            }
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
        desc.text = ch.description
        xp.text = "+${ch.bonusXp} XP"
        return item
    }

    private fun formatWeight(w: Float): String =
        if (w == w.toInt().toFloat()) w.toInt().toString() else w.toString()

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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

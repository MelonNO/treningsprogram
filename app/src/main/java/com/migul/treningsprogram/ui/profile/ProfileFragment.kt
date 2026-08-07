package com.migul.treningsprogram.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import android.widget.LinearLayout
import android.widget.ProgressBar
import com.migul.treningsprogram.R
import com.migul.treningsprogram.data.db.entity.Achievement
import com.migul.treningsprogram.data.db.entity.UserStats
import com.migul.treningsprogram.data.repository.GamificationRepository
import com.migul.treningsprogram.databinding.FragmentProfileBinding
import com.migul.treningsprogram.domain.AchievementCatalog
import com.migul.treningsprogram.domain.DayBoundary
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProfileViewModel by viewModels()

    // B04: always start collapsed every time the Profile screen is opened.
    // Reset in onViewCreated so a reused fragment instance never restores a prior open state.
    private var achievementsExpanded = false

    // R5: gallery state — latest data (categories populate lazily on expand) and which
    // categories are open. Categories start collapsed each screen open (B04 convention).
    private var galleryAchievements: List<Achievement> = emptyList()
    private var galleryStats: UserStats = UserStats()
    /** Brief 02: the strength achievements read this instead of the retired UserStats.level. */
    private var galleryStrengthScore: Int = 0
    private val expandedCategories = mutableSetOf<AchievementCatalog.Category>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.cardSettings.setOnClickListener {
            if (findNavController().currentDestination?.id == R.id.profileFragment)
                findNavController().navigate(R.id.action_profile_to_settings)
        }
        // Brief 02 / decision D7: the level card is now the STRENGTH card, and tapping it opens the
        // per-muscle-group breakdown. (S8 convention: guard against rapid double-tap.)
        binding.cardProfileXp.setOnClickListener {
            if (findNavController().currentDestination?.id == R.id.profileFragment)
                findNavController().navigate(R.id.strengthBreakdownFragment)
        }
        // U2's XP log keeps its entry point, moved onto the XP line itself now that the card as a
        // whole belongs to the strength breakdown.
        binding.tvProfileXpLabel.setOnClickListener {
            if (findNavController().currentDestination?.id == R.id.profileFragment)
                findNavController().navigate(R.id.action_profile_to_xp_log)
        }
        // B04: force the achievements gallery collapsed on every open (do not restore prior state).
        achievementsExpanded = false
        expandedCategories.clear()
        binding.layoutAchievements.visibility = View.GONE
        binding.tvAchievementsChevron.text = "▸"
        binding.headerAchievements.setOnClickListener { toggleAchievements() }
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    val stats = state.userStats
                    val xp = stats?.totalXp ?: 0
                    val strength = state.strength

                    // Brief 02: this card used to read "L7 / Champion / 340 XP to Level 8". The XP
                    // level and its Rookie→Apex titles are retired; the card now shows the strength
                    // tier, the bar fills through that tier, and XP is just a total.
                    val tier = strength?.totalTier
                    binding.tvProfileLevelBadge.text = tier?.displayName?.take(3)?.uppercase() ?: "—"
                    binding.tvLevelTitle.text = tier?.displayName ?: "Unrated"
                    binding.progressProfileXp.progress =
                        if (tier == null) 0
                        else ((strength!!.totalScore - kotlin.math.floor(strength.totalScore)) * 100)
                            .toInt().coerceIn(0, 100)
                    binding.tvProfileXpLabel.text = "$xp XP  •  tap for XP log"

                    // Stage-3 item 4: only PRs earned in the last 7 days — a recent-wins surface.
                    binding.tvPrs.text = if (state.recentPrs.isEmpty())
                        getString(R.string.no_prs_recent)
                    else state.recentPrs.joinToString("\n") {
                        "🏆 ${it.exerciseName}: ${formatWeight(it.weightKg)}kg · ${daysAgoLabel(it.dateMs)}"
                    }

                    // R5: the achievement GALLERY — categories with counts, rarity tiers, live
                    // progress on locked threshold achievements, and a "next up" teaser strip.
                    val unlocked = state.achievements.filter { it.isUnlocked }
                    val total = state.achievements.size
                    binding.tvAchievementsHeader.text = "Achievements (${unlocked.size}/$total)"
                    galleryAchievements = state.achievements
                    galleryStats = stats ?: UserStats()
                    galleryStrengthScore = strength?.strengthScore ?: 0
                    renderNextUp()
                    renderGallery()

                    // N5: goals overview — hidden entirely when no goals exist.
                    renderGoals(state.goals)
                }
            }
        }
    }

    /**
     * Brief 02: a strength rating is relative to body weight, so it can change with no workout
     * involved. Recompute on resume so coming back from a weigh-in shows the new rating rather
     * than the one captured when this screen was first built.
     */
    override fun onResume() {
        super.onResume()
        viewModel.refreshStrength()
    }

    private fun formatWeight(w: Float): String =
        if (w == w.toInt().toFloat()) w.toInt().toString() else w.toString()

    /** N5: active goals with a progress bar; achieved goals as quiet history rows. */
    private fun renderGoals(rows: List<GoalRow>) {
        if (_binding == null) return
        binding.cardGoals.visibility = if (rows.isEmpty()) View.GONE else View.VISIBLE
        binding.layoutGoals.removeAllViews()
        if (rows.isEmpty()) return
        val density = resources.displayMetrics.density
        val gold = requireContext().getColor(R.color.game_gold)
        rows.forEach { row ->
            val goal = row.goal
            val active = goal.status == com.migul.treningsprogram.data.db.entity.LiftGoal.STATUS_ACTIVE
            val column = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                val p = (6 * density).toInt()
                setPadding(0, p, 0, p)
            }
            val kind = if (goal.isE1rm) " est. 1RM" else ""
            val flavor = com.migul.treningsprogram.domain.GoalProgress
                .dateFlavor(goal.targetDateMs)?.let { "  ·  $it" } ?: ""
            val title = TextView(requireContext()).apply {
                textSize = 14f
                if (active) {
                    text = "\uD83C\uDFAF ${goal.exerciseName} — ${formatWeight(goal.targetWeightKg)} kg$kind$flavor  ·  ${row.progressPercent}%"
                    setTextColor(requireContext().getColor(R.color.auros_snow))
                } else {
                    text = "\u2713 ${goal.exerciseName} — ${formatWeight(goal.targetWeightKg)} kg$kind reached ${achievedLabel(goal.achievedAtMs)}"
                    setTextColor(gold)
                }
            }
            column.addView(title)
            if (active) {
                column.addView(ProgressBar(
                    requireContext(), null, android.R.attr.progressBarStyleHorizontal
                ).apply {
                    max = 100
                    progress = row.progressPercent
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, (4 * density).toInt()
                    )
                    lp.topMargin = (4 * density).toInt()
                    layoutParams = lp
                    progressDrawable?.setColorFilter(gold, android.graphics.PorterDuff.Mode.SRC_IN)
                })
            }
            binding.layoutGoals.addView(column)
        }
    }

    private fun achievedLabel(achievedAtMs: Long): String =
        if (achievedAtMs <= 0L) "" else java.text.SimpleDateFormat(
            "dd MMM yyyy", java.util.Locale.getDefault()
        ).format(java.util.Date(DayBoundary.toLogicalMillis(achievedAtMs)))

    /** How recent a PR is, in logical days ("today" / "yesterday" / "N days ago"). */
    private fun daysAgoLabel(dateMs: Long): String =
        when (val d = DayBoundary.todayEpochDay() - DayBoundary.logicalEpochDay(dateMs)) {
            0L -> "today"
            1L -> "yesterday"
            else -> "$d days ago"
        }

    // ── R5: achievement gallery ─────────────────────────────────────────────────────────────────

    private fun tierColor(tier: AchievementCatalog.Tier): Int = requireContext().getColor(
        when (tier) {
            AchievementCatalog.Tier.COMMON -> R.color.tier_common
            AchievementCatalog.Tier.RARE -> R.color.tier_rare
            AchievementCatalog.Tier.EPIC -> R.color.tier_epic
            AchievementCatalog.Tier.LEGENDARY -> R.color.tier_legendary
        }
    )

    /** The always-visible teaser: the 2-3 locked achievements closest to unlocking. */
    private fun renderNextUp() {
        if (_binding == null) return
        binding.layoutNextUp.removeAllViews()
        val next = AchievementCatalog.nextUp(galleryAchievements, galleryStats, galleryStrengthScore, 3)
        if (next.isEmpty()) return
        val caption = TextView(requireContext()).apply {
            text = "NEXT UP"
            textSize = 10f
            letterSpacing = 0.12f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(requireContext().getColor(R.color.auros_fog_dim))
            val d = resources.displayMetrics.density
            setPadding(0, (6 * d).toInt(), 0, (2 * d).toInt())
        }
        binding.layoutNextUp.addView(caption)
        next.forEach { binding.layoutNextUp.addView(makeAchievementItem(it, binding.layoutNextUp)) }
    }

    /** Category sections with unlocked/total counts; content populates lazily on expand. */
    private fun renderGallery() {
        if (_binding == null) return
        binding.layoutAchievements.removeAllViews()
        val byCategory = galleryAchievements.groupBy {
            AchievementCatalog.metaFor(it.id)?.category ?: AchievementCatalog.Category.COMBOS
        }
        AchievementCatalog.CATEGORY_ORDER.forEach { cat ->
            val inCat = byCategory[cat] ?: return@forEach
            val unlockedCount = inCat.count { it.isUnlocked }
            val density = resources.displayMetrics.density

            val content = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                visibility = if (cat in expandedCategories) View.VISIBLE else View.GONE
            }
            val chevron = TextView(requireContext()).apply {
                text = if (cat in expandedCategories) "▾" else "▸"
                textSize = 14f
                setTextColor(requireContext().getColor(R.color.auros_fog_dim))
            }
            val header = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                val p = (8 * density).toInt()
                setPadding(0, p, 0, p)
                addView(TextView(requireContext()).apply {
                    text = cat.label
                    textSize = 14f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(requireContext().getColor(R.color.auros_snow))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                addView(TextView(requireContext()).apply {
                    text = "$unlockedCount/${inCat.size}   "
                    textSize = 12f
                    setTextColor(requireContext().getColor(R.color.auros_fog))
                })
                addView(chevron)
                setOnClickListener {
                    if (cat in expandedCategories) {
                        expandedCategories.remove(cat)
                        content.visibility = View.GONE
                        chevron.text = "▸"
                    } else {
                        expandedCategories.add(cat)
                        if (content.childCount == 0) populateCategory(content, inCat)
                        content.visibility = View.VISIBLE
                        chevron.text = "▾"
                    }
                }
            }
            binding.layoutAchievements.addView(header)
            binding.layoutAchievements.addView(content)
            if (cat in expandedCategories && content.childCount == 0) populateCategory(content, inCat)
        }
    }

    private fun populateCategory(container: LinearLayout, achievements: List<Achievement>) {
        // Unlocked first (proud wall), then locked by nearness-to-threshold order as defined.
        achievements.sortedByDescending { it.isUnlocked }
            .forEach { container.addView(makeAchievementItem(it, container)) }
    }

    private fun makeAchievementItem(a: Achievement, parent: ViewGroup): View {
        val item = layoutInflater.inflate(R.layout.item_achievement, parent, false)
        val emoji = item.findViewById<TextView>(R.id.tv_achievement_emoji)
        val name = item.findViewById<TextView>(R.id.tv_achievement_name)
        val desc = item.findViewById<TextView>(R.id.tv_achievement_desc)
        val tierChip = item.findViewById<TextView>(R.id.tv_achievement_tier)
        val progressRow = item.findViewById<LinearLayout>(R.id.row_achievement_progress)
        val progressBar = item.findViewById<ProgressBar>(R.id.progress_achievement)
        val progressText = item.findViewById<TextView>(R.id.tv_achievement_progress)

        val meta = AchievementCatalog.metaFor(a.id)
        name.text = a.name
        desc.text = a.description
        desc.visibility = View.VISIBLE

        if (meta != null) {
            tierChip.visibility = View.VISIBLE
            tierChip.text = meta.tier.label.uppercase()
            tierChip.setTextColor(tierColor(meta.tier))
        } else {
            tierChip.visibility = View.GONE
        }

        if (a.isUnlocked) {
            emoji.text = a.emoji
            item.alpha = 1f
            progressRow.visibility = View.GONE
        } else {
            emoji.text = a.emoji
            emoji.alpha = 0.5f
            item.alpha = 0.55f
            // Live progress for lifetime-threshold achievements; condition text (the description)
            // stands in for session-scoped families and combos.
            val progress = AchievementCatalog.progressFor(a.id, galleryStats, galleryStrengthScore)
            if (progress != null && progress.first > 0) {
                progressRow.visibility = View.VISIBLE
                progressBar.progress = (progress.first * 100 / progress.second).coerceIn(0, 100)
                if (meta != null) {
                    progressBar.progressDrawable?.setColorFilter(
                        tierColor(meta.tier), android.graphics.PorterDuff.Mode.SRC_IN
                    )
                }
                progressText.text = "${progress.first}/${progress.second}"
            } else {
                progressRow.visibility = View.GONE
            }
        }
        return item
    }

    private fun toggleAchievements() {
        achievementsExpanded = !achievementsExpanded
        binding.layoutAchievements.visibility = if (achievementsExpanded) View.VISIBLE else View.GONE
        binding.tvAchievementsChevron.text = if (achievementsExpanded) "▾" else "▸"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

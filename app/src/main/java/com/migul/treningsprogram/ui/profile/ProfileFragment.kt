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
        // U2: tap the XP bar/card to open the XP log. Guard against rapid double-tap (S8 convention).
        binding.cardProfileXp.setOnClickListener {
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
                    val level = stats?.level ?: 1
                    val xp = stats?.totalXp ?: 0

                    binding.tvProfileLevelBadge.text = "L$level"
                    binding.tvLevelTitle.text = state.levelTitle
                    binding.progressProfileXp.progress = (GamificationRepository.levelProgress(xp) * 100).toInt()
                    val xpToNext = GamificationRepository.xpForLevel(level + 1) - xp
                    binding.tvProfileXpLabel.text = "$xp XP  •  $xpToNext to Level ${level + 1}"

                    binding.tvStatWorkouts.text = "${stats?.totalWorkouts ?: 0}"
                    binding.tvStatSets.text = "${state.totalSets}"
                    val kg = state.totalVolumeKg
                    binding.tvStatVolume.text = if (kg >= 1000f) "%.1ft".format(kg / 1000f) else "${kg.toInt()}kg"
                    binding.tvStatStreak.text = "${stats?.currentStreak ?: 0}"

                    // PRs
                    binding.tvPrs.text = if (state.topPrs.isEmpty()) "No records yet. Complete a workout!"
                    else state.topPrs.joinToString("\n") { "🏆 ${it.exerciseName}: ${formatWeight(it.maxWeight)}kg" }

                    // R5: the achievement GALLERY — categories with counts, rarity tiers, live
                    // progress on locked threshold achievements, and a "next up" teaser strip.
                    val unlocked = state.achievements.filter { it.isUnlocked }
                    val total = state.achievements.size
                    binding.tvAchievementsHeader.text = "Achievements (${unlocked.size}/$total)"
                    galleryAchievements = state.achievements
                    galleryStats = stats ?: UserStats()
                    renderNextUp()
                    renderGallery()
                }
            }
        }
    }

    private fun formatWeight(w: Float): String =
        if (w == w.toInt().toFloat()) w.toInt().toString() else w.toString()

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
        val next = AchievementCatalog.nextUp(galleryAchievements, galleryStats, 3)
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
            val progress = AchievementCatalog.progressFor(a.id, galleryStats)
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

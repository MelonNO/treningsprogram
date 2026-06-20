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
import com.migul.treningsprogram.R
import com.migul.treningsprogram.data.db.entity.Achievement
import com.migul.treningsprogram.data.repository.GamificationRepository
import com.migul.treningsprogram.databinding.FragmentProfileBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProfileViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.cardSettings.setOnClickListener {
            findNavController().navigate(R.id.action_profile_to_settings)
        }
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

                    // Achievements
                    val unlocked = state.achievements.count { it.isUnlocked }
                    val total = state.achievements.size
                    binding.tvAchievementsHeader.text = "Achievements ($unlocked/$total)"
                    binding.layoutAchievements.removeAllViews()
                    state.achievements.forEach { a -> binding.layoutAchievements.addView(makeAchievementItem(a)) }
                }
            }
        }
    }

    private fun formatWeight(w: Float): String =
        if (w == w.toInt().toFloat()) w.toInt().toString() else w.toString()

    private fun makeAchievementItem(a: Achievement): View {
        val item = layoutInflater.inflate(R.layout.item_achievement, binding.layoutAchievements, false)
        val emoji = item.findViewById<TextView>(R.id.tv_achievement_emoji)
        val name = item.findViewById<TextView>(R.id.tv_achievement_name)
        val desc = item.findViewById<TextView>(R.id.tv_achievement_desc)
        emoji.text = if (a.isUnlocked) a.emoji else "🔒"
        name.text = a.name
        desc.text = a.description
        item.alpha = if (a.isUnlocked) 1f else 0.35f
        return item
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

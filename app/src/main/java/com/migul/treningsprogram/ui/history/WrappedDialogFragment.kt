package com.migul.treningsprogram.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.migul.treningsprogram.R
import com.migul.treningsprogram.data.db.dao.AchievementDao
import com.migul.treningsprogram.data.db.dao.BodyMeasurementDao
import com.migul.treningsprogram.data.db.dao.WorkoutSessionDao
import com.migul.treningsprogram.data.db.dao.WorkoutSetDao
import com.migul.treningsprogram.databinding.DialogWrappedBinding
import com.migul.treningsprogram.domain.AchievementCatalog
import com.migul.treningsprogram.domain.MonthlyWrapped
import com.migul.treningsprogram.domain.RelativeStrength
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * B7 — the full-screen monthly Wrapped story. READ-ONLY: loads existing rows, derives the
 * month via the pure [MonthlyWrapped], renders; nothing is ever written from here.
 */
@AndroidEntryPoint
class WrappedDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_YEAR = "year"
        private const val ARG_MONTH = "month"

        fun newInstance(month: MonthlyWrapped.MonthKey) = WrappedDialogFragment().apply {
            arguments = Bundle().also {
                it.putInt(ARG_YEAR, month.year)
                it.putInt(ARG_MONTH, month.month)
            }
        }
    }

    @Inject lateinit var sessionDao: WorkoutSessionDao
    @Inject lateinit var setDao: WorkoutSetDao
    @Inject lateinit var achievementDao: AchievementDao
    @Inject lateinit var bodyMeasurementDao: BodyMeasurementDao

    private var _binding: DialogWrappedBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = DialogWrappedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnWrappedClose.setOnClickListener { dismiss() }
        // W1: the persistent top close dismisses exactly like the bottom Close button.
        binding.btnWrappedCloseTop.setOnClickListener { dismiss() }

        val month = MonthlyWrapped.MonthKey(
            arguments?.getInt(ARG_YEAR) ?: 0,
            (arguments?.getInt(ARG_MONTH) ?: 1).coerceIn(1, 12)
        )

        viewLifecycleOwner.lifecycleScope.launch {
            val wrapped = MonthlyWrapped.build(
                month = month,
                sessions = sessionDao.getAllOnce(),
                allSets = setDao.getAllOnce(),
                achievements = achievementDao.getAllOnce(),
                weighIns = bodyMeasurementDao.getAllOnce()
                    .map { RelativeStrength.WeighIn(it.dateMs, it.weightKg) }
            )
            if (_binding == null) return@launch
            if (wrapped == null) {
                // Only reachable defensively — entry points offer months with data.
                dismissAllowingStateLoss()
                return@launch
            }
            render(wrapped)
        }
    }

    private fun render(w: MonthlyWrapped.Wrapped) {
        binding.tvWrappedMonth.text = w.month.label
        val sessionWord = if (w.sessions == 1) "session" else "sessions"
        binding.tvWrappedHeadline.text =
            "${w.sessions} $sessionWord across ${w.activeDays} training day" +
                (if (w.activeDays == 1) "" else "s") + ". Every one of them counted."

        binding.tvWrappedVolume.text =
            if (w.totalVolumeKg >= 1000f) "%.1f t".format(w.totalVolumeKg / 1000f)
            else "${w.totalVolumeKg.toInt()} kg"
        binding.tvWrappedSets.text = w.totalSets.toString()
        binding.tvWrappedDays.text = w.activeDays.toString()
        binding.tvWrappedMinutes.text = w.totalMinutes.toString()

        w.biggestPr?.let { pr ->
            binding.cardWrappedPr.isVisible = true
            binding.tvWrappedPr.text =
                "${pr.exerciseName} — ${fmt(pr.newKg)} kg, up from ${fmt(pr.previousKg)} kg"
        }
        w.mostImproved?.let { imp ->
            binding.cardWrappedImproved.isVisible = true
            binding.tvWrappedImproved.text =
                "${imp.exerciseName} — est. 1RM ${imp.fromE1rm.toInt()} → ${imp.toE1rm.toInt()} kg this month"
        }
        w.favorite?.let { fav ->
            binding.cardWrappedFavorite.isVisible = true
            val times = if (fav.sessions == 1) "once" else "${fav.sessions} sessions"
            binding.tvWrappedFavorite.text = "${fav.exerciseName} — showed up in $times"
        }

        if (w.achievementsUnlocked.isNotEmpty()) {
            binding.cardWrappedAchievements.isVisible = true
            binding.tvWrappedAchievementsHeader.text =
                "🏆 ACHIEVEMENTS · ${w.achievementsUnlocked.size} UNLOCKED"
            binding.layoutWrappedAchievements.removeAllViews()
            // Rarest first, capped to keep the story tight.
            val tierOrder = mapOf(
                AchievementCatalog.Tier.LEGENDARY to 0, AchievementCatalog.Tier.EPIC to 1,
                AchievementCatalog.Tier.RARE to 2, AchievementCatalog.Tier.COMMON to 3
            )
            w.achievementsUnlocked
                .sortedBy { tierOrder[AchievementCatalog.metaFor(it.id)?.tier] ?: 4 }
                .take(4)
                .forEach { a ->
                    val meta = AchievementCatalog.metaFor(a.id)
                    binding.layoutWrappedAchievements.addView(TextView(requireContext()).apply {
                        text = "${a.emoji} ${a.name}" +
                            (meta?.let { "  ·  ${it.tier.label}" } ?: "")
                        textSize = 14f
                        setPadding(0, 4, 0, 4)
                        setTextColor(
                            when (meta?.tier) {
                                AchievementCatalog.Tier.LEGENDARY -> requireContext().getColor(R.color.tier_legendary)
                                AchievementCatalog.Tier.EPIC -> requireContext().getColor(R.color.tier_epic)
                                AchievementCatalog.Tier.RARE -> requireContext().getColor(R.color.tier_rare)
                                else -> requireContext().getColor(R.color.auros_snow)
                            }
                        )
                    })
                }
        }

        w.bodyWeight?.let { bw ->
            binding.cardWrappedBw.isVisible = true
            val delta = bw.endKg - bw.startKg
            val arrow = if (delta < 0f) "↓" else if (delta > 0f) "↑" else "→"
            binding.tvWrappedBw.text =
                "${fmt(bw.startKg)} → ${fmt(bw.endKg)} kg  ($arrow ${fmt(kotlin.math.abs(delta))} kg)"
        }

        // Gentle, honest adherence — never shaming (rest days are part of the plan).
        val parts = buildList {
            if (w.restDays > 0) add("${w.restDays} rest day" + (if (w.restDays == 1) "" else "s") + " honored")
            if (w.missedDays > 0) add("${w.missedDays} day" + (if (w.missedDays == 1) "" else "s") + " that got away")
        }
        if (parts.isNotEmpty()) {
            binding.tvWrappedAdherence.isVisible = true
            binding.tvWrappedAdherence.text = parts.joinToString("  ·  ")
        }
    }

    private fun fmt(w: Float): String =
        if (w == w.toInt().toFloat()) w.toInt().toString() else "%.1f".format(w)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

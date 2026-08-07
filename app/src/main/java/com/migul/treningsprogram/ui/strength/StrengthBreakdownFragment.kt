package com.migul.treningsprogram.ui.strength

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.migul.treningsprogram.R
import com.migul.treningsprogram.databinding.FragmentStrengthBreakdownBinding
import com.migul.treningsprogram.databinding.ItemStrengthLiftGroupBinding
import com.migul.treningsprogram.domain.strength.StrengthProfile
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Brief 02 (2026-08-07) — the Strength breakdown, reached by tapping the level card on Profile.
 *
 * What it shows, top to bottom:
 *  1. the **total** as a tier NAME (never a number), or the unrated state and why;
 *  2. **all six** rated groups, always, in `StrengthStandards.RATED_GROUPS` order — each either
 *     rated (tier name, driving lift + best set, and "what would move this" in the user's own
 *     kilos and reps) or explicitly unrated with the sentence that would unlock it;
 *  3. the **weakest rated group**, next to a route to the priority-muscles setting. That route
 *     navigates and nothing more — this screen never writes `PreferencesManager.priorityMuscles`;
 *  4. the **lifts that count**, collapsed by default — the list decides what moves a rating, so
 *     the user has to be able to read it;
 *  5. the honest explainer: relative to body weight and sex, last three months, and it can go down.
 *
 * All wording comes from [StrengthCopy], which is pure and unit-tested. Nothing is computed here.
 */
@AndroidEntryPoint
class StrengthBreakdownFragment : Fragment() {

    private var _binding: FragmentStrengthBreakdownBinding? = null
    private val binding get() = _binding!!
    private val viewModel: StrengthBreakdownViewModel by viewModels()

    private val groupAdapter = StrengthGroupAdapter()
    private var liftsExpanded = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStrengthBreakdownBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvStrengthGroups.layoutManager = LinearLayoutManager(requireContext())
        binding.rvStrengthGroups.adapter = groupAdapter

        binding.tvStrengthLiftsHeader.text = StrengthCopy.LIFTS_HEADER
        binding.tvStrengthLiftsNote.text = StrengthCopy.LIFTS_NOTE
        binding.tvStrengthExplainerHeader.text = StrengthCopy.EXPLAINER_HEADER
        binding.tvStrengthExplainer.text = StrengthCopy.EXPLAINER
        binding.tvStrengthWeakestNote.text = StrengthCopy.WEAKEST_NOTE
        binding.btnStrengthPriority.text = StrengthCopy.WEAKEST_CTA

        renderLiftCatalogue()
        binding.headerStrengthLifts.setOnClickListener {
            liftsExpanded = !liftsExpanded
            applyLiftsExpansion()
        }
        applyLiftsExpansion()

        // Informs only. Navigating to the setting is the whole action — the priority muscles are
        // never edited on the user's behalf.
        binding.btnStrengthPriority.setOnClickListener {
            findNavController().navigate(R.id.action_strength_to_settings_training)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Ratings are derived, never stored. Setting your sex or logging a weigh-in both happen on
        // other screens, so recompute whenever the user comes back to this one.
        viewModel.refresh()
    }

    private fun render(state: StrengthBreakdownViewModel.UiState) {
        val profile = state.profile
        if (profile == null) {
            binding.tvStrengthTotalTier.text =
                if (state.loading) "…" else StrengthCopy.NOT_RATED
            binding.tvStrengthTotalCaption.text = when {
                state.loading -> "Reading your logged history…"
                state.failed -> "Couldn't read your history just now. Pull back and try again."
                else -> ""
            }
            binding.progressStrengthTotal.visibility = View.GONE
            binding.cardStrengthWeakest.visibility = View.GONE
            groupAdapter.submitList(emptyList())
            return
        }
        renderProfile(profile)
    }

    private fun renderProfile(profile: StrengthProfile) {
        binding.tvStrengthTotalTier.text = StrengthCopy.totalTierName(profile)
        binding.tvStrengthTotalCaption.text = StrengthCopy.totalCaption(profile)

        if (profile.isRated) {
            binding.progressStrengthTotal.visibility = View.VISIBLE
            binding.progressStrengthTotal.setProgressCompat(
                StrengthCopy.tierProgressPercent(profile.totalScore), false
            )
        } else {
            binding.progressStrengthTotal.visibility = View.GONE
        }

        // Always all six, rated or not, in the engine's display order.
        groupAdapter.submitList(profile.groups)

        val weakest = StrengthCopy.weakestLine(profile.weakestRatedGroup)
        binding.cardStrengthWeakest.visibility = if (weakest == null) View.GONE else View.VISIBLE
        binding.tvStrengthWeakest.text = weakest.orEmpty()
    }

    /** The qualifying-lift catalogue, grouped exactly like the rows above it. */
    private fun renderLiftCatalogue() {
        val container = binding.layoutStrengthLifts
        container.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        StrengthCopy.liftSections().forEach { section ->
            val row = ItemStrengthLiftGroupBinding.inflate(inflater, container, false)
            row.tvLiftGroup.text = section.group
            row.tvLiftLines.text = section.lines.joinToString("\n")
            container.addView(row.root)
        }
    }

    private fun applyLiftsExpansion() {
        val visible = if (liftsExpanded) View.VISIBLE else View.GONE
        binding.layoutStrengthLifts.visibility = visible
        binding.tvStrengthLiftsNote.visibility = visible
        binding.tvStrengthLiftsChevron.text = if (liftsExpanded) "▾" else "▸"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvStrengthGroups.adapter = null
        _binding = null
    }
}

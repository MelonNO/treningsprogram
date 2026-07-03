package com.migul.treningsprogram.ui.settings

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.snackbar.Snackbar
import com.migul.treningsprogram.databinding.FragmentSettingsAppBinding
import com.migul.treningsprogram.domain.DayBoundary
import com.migul.treningsprogram.notify.ReminderScheduler
import dagger.hilt.android.AndroidEntryPoint

/**
 * Item 4: the "App Settings" screen — the day-boundary (day-reset) hour picker, the
 * auto-rebalance-week toggle, and (R2) the notification center: every notification type the app
 * can send — workout-day reminder, streak warning, weigh-in reminder, program-ready — each with
 * its own switch so any combination is possible.
 */
@AndroidEntryPoint
class SettingsAppFragment : Fragment() {

    private var _binding: FragmentSettingsAppBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by activityViewModels()

    // Supported day-boundary hours (0..6).
    private val dayBoundaryHours = (DayBoundary.MIN_CUTOFF_HOUR..DayBoundary.MAX_CUTOFF_HOUR).toList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsAppBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDayBoundarySpinner()
        setupAutoRebalanceSwitch()
        setupRestRecoverySwitch()
        setupWorkoutReminder()
        setupStreakWarning()
        setupWeighInReminder()
        setupProgramReady()
    }

    // Item 1: 24-hour labels (no AM/PM anywhere). Range is 00:00..06:00; annotate midnight + default.
    private fun setupDayBoundarySpinner() {
        val labels = dayBoundaryHours.map { hour ->
            val base = when (hour) {
                0 -> "00:00 (midnight)"
                else -> "%02d:00".format(hour)
            }
            if (hour == DayBoundary.DEFAULT_CUTOFF_HOUR) "$base — default" else base
        }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerDayBoundary.adapter = adapter

        val saved = viewModel.prefs.dayBoundaryHour
        binding.spinnerDayBoundary.setSelection(dayBoundaryHours.indexOf(saved).coerceAtLeast(0))

        binding.spinnerDayBoundary.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val hour = dayBoundaryHours.getOrElse(position) { DayBoundary.DEFAULT_CUTOFF_HOUR }
                if (hour != viewModel.prefs.dayBoundaryHour) {
                    viewModel.prefs.dayBoundaryHour = hour  // persists + updates the process-wide holder
                    Snackbar.make(binding.root, "Day boundary saved", Snackbar.LENGTH_SHORT).show()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    // Item 2: default ON for anyone who has not explicitly chosen (handled in PreferencesManager).
    // Set the initial checked state BEFORE attaching the listener so simply displaying the screen
    // never writes the preference — only a real user toggle persists a value.
    private fun setupAutoRebalanceSwitch() {
        binding.switchAutoRebalance.isChecked = viewModel.prefs.autoRebalanceEnabled
        binding.switchAutoRebalance.setOnCheckedChangeListener { _, isChecked ->
            viewModel.prefs.autoRebalanceEnabled = isChecked
            Snackbar.make(
                binding.root,
                if (isChecked) "Auto-rebalance is ON" else "Auto-rebalance is OFF",
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    // B5: the permanent off-switch for the rest-day recovery card (A-A3). Default ON; initial
    // state set before the listener so displaying the screen never writes the preference.
    private fun setupRestRecoverySwitch() {
        binding.switchRestRecovery.isChecked = viewModel.prefs.restDayRecoveryEnabled
        binding.switchRestRecovery.setOnCheckedChangeListener { _, isChecked ->
            viewModel.prefs.restDayRecoveryEnabled = isChecked
            Snackbar.make(
                binding.root,
                if (isChecked) "Rest-day suggestions ON" else "Rest-day suggestions OFF",
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    // F3: opt-in workout-day reminder — toggle + 24h time picker; the alarm is (re)scheduled or
    // cancelled immediately on every change. Initial state set before the listener (same pattern
    // as auto-rebalance) so showing the screen never writes prefs.
    private fun setupWorkoutReminder() {
        val prefs = viewModel.prefs
        binding.switchWorkoutReminder.isChecked = prefs.workoutRemindersEnabled
        binding.tvReminderTime.text = "%02d:%02d".format(prefs.reminderHour, prefs.reminderMinute)
        binding.rowReminderTime.isVisible = prefs.workoutRemindersEnabled

        binding.switchWorkoutReminder.setOnCheckedChangeListener { _, isChecked ->
            prefs.workoutRemindersEnabled = isChecked
            binding.rowReminderTime.isVisible = isChecked
            ReminderScheduler.sync(requireContext(), prefs)
            Snackbar.make(
                binding.root,
                if (isChecked) "Reminders ON — %02d:%02d on training days"
                    .format(prefs.reminderHour, prefs.reminderMinute)
                else "Reminders OFF",
                Snackbar.LENGTH_SHORT
            ).show()
        }

        binding.rowReminderTime.setOnClickListener {
            TimePickerDialog(
                requireContext(),
                { _, hour, minute ->
                    prefs.reminderHour = hour
                    prefs.reminderMinute = minute
                    binding.tvReminderTime.text = "%02d:%02d".format(hour, minute)
                    ReminderScheduler.sync(requireContext(), prefs)
                },
                prefs.reminderHour, prefs.reminderMinute, true, // 24h — house style, no AM/PM
            ).show()
        }
    }

    // R2: streak warning — toggle + its own evening time; alarm resynced immediately on every
    // change so disabling stops it at once (same pattern as the workout reminder above).
    private fun setupStreakWarning() {
        val prefs = viewModel.prefs
        binding.switchStreakWarning.isChecked = prefs.streakWarningEnabled
        binding.tvStreakWarningTime.text = "%02d:%02d".format(prefs.streakWarningHour, prefs.streakWarningMinute)
        binding.rowStreakWarningTime.isVisible = prefs.streakWarningEnabled

        binding.switchStreakWarning.setOnCheckedChangeListener { _, isChecked ->
            prefs.streakWarningEnabled = isChecked
            binding.rowStreakWarningTime.isVisible = isChecked
            ReminderScheduler.sync(requireContext(), prefs)
            Snackbar.make(
                binding.root,
                if (isChecked) "Streak warning ON — %02d:%02d on training days"
                    .format(prefs.streakWarningHour, prefs.streakWarningMinute)
                else "Streak warning OFF",
                Snackbar.LENGTH_SHORT
            ).show()
        }

        binding.rowStreakWarningTime.setOnClickListener {
            TimePickerDialog(
                requireContext(),
                { _, hour, minute ->
                    prefs.streakWarningHour = hour
                    prefs.streakWarningMinute = minute
                    binding.tvStreakWarningTime.text = "%02d:%02d".format(hour, minute)
                    ReminderScheduler.sync(requireContext(), prefs)
                },
                prefs.streakWarningHour, prefs.streakWarningMinute, true,
            ).show()
        }
    }

    // R2: weekly weigh-in reminder — toggle + weekday + time (1 = Monday … 7 = Sunday, app-wide).
    private fun setupWeighInReminder() {
        val prefs = viewModel.prefs
        binding.switchWeighIn.isChecked = prefs.weighInReminderEnabled
        binding.tvWeighInDay.text = weekdayName(prefs.weighInDayOfWeek)
        binding.tvWeighInTime.text = "%02d:%02d".format(prefs.weighInHour, prefs.weighInMinute)
        binding.rowWeighInDay.isVisible = prefs.weighInReminderEnabled
        binding.rowWeighInTime.isVisible = prefs.weighInReminderEnabled

        binding.switchWeighIn.setOnCheckedChangeListener { _, isChecked ->
            prefs.weighInReminderEnabled = isChecked
            binding.rowWeighInDay.isVisible = isChecked
            binding.rowWeighInTime.isVisible = isChecked
            ReminderScheduler.sync(requireContext(), prefs)
            Snackbar.make(
                binding.root,
                if (isChecked) "Weigh-in reminder ON — ${weekdayName(prefs.weighInDayOfWeek)} %02d:%02d"
                    .format(prefs.weighInHour, prefs.weighInMinute)
                else "Weigh-in reminder OFF",
                Snackbar.LENGTH_SHORT
            ).show()
        }

        binding.rowWeighInDay.setOnClickListener {
            val days = (1..7).map { weekdayName(it) }.toTypedArray()
            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Weigh-in day")
                .setSingleChoiceItems(days, prefs.weighInDayOfWeek - 1) { dialog, which ->
                    prefs.weighInDayOfWeek = which + 1
                    binding.tvWeighInDay.text = weekdayName(which + 1)
                    ReminderScheduler.sync(requireContext(), prefs)
                    dialog.dismiss()
                }
                .show()
        }

        binding.rowWeighInTime.setOnClickListener {
            TimePickerDialog(
                requireContext(),
                { _, hour, minute ->
                    prefs.weighInHour = hour
                    prefs.weighInMinute = minute
                    binding.tvWeighInTime.text = "%02d:%02d".format(hour, minute)
                    ReminderScheduler.sync(requireContext(), prefs)
                },
                prefs.weighInHour, prefs.weighInMinute, true,
            ).show()
        }
    }

    // R2: program-ready toggle — event-driven (GenerationNotifier checks it), no alarm to sync.
    private fun setupProgramReady() {
        val prefs = viewModel.prefs
        binding.switchProgramReady.isChecked = prefs.programReadyEnabled
        binding.switchProgramReady.setOnCheckedChangeListener { _, isChecked ->
            prefs.programReadyEnabled = isChecked
            Snackbar.make(
                binding.root,
                if (isChecked) "Program-ready notification ON" else "Program-ready notification OFF",
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    private fun weekdayName(day: Int): String = when (day) {
        1 -> "Monday"; 2 -> "Tuesday"; 3 -> "Wednesday"; 4 -> "Thursday"
        5 -> "Friday"; 6 -> "Saturday"; else -> "Sunday"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

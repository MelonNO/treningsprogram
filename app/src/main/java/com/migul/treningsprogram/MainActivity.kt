package com.migul.treningsprogram

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.migul.treningsprogram.data.db.dao.GymPresetDao
import com.migul.treningsprogram.data.preferences.PreferencesManager
import com.migul.treningsprogram.data.repository.AiRepository
import com.migul.treningsprogram.data.repository.GamificationRepository
import com.migul.treningsprogram.data.repository.WorkoutRepository
import com.migul.treningsprogram.data.repository.thisMonday
import com.migul.treningsprogram.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or denied — system handles display */ }

    @Inject lateinit var prefsManager: PreferencesManager
    @Inject lateinit var workoutRepository: WorkoutRepository
    @Inject lateinit var aiRepository: AiRepository
    @Inject lateinit var gamificationRepository: GamificationRepository
    @Inject lateinit var gymPresetDao: GymPresetDao
    @Inject lateinit var gson: Gson

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        binding.bottomNav.setupWithNavController(navController)

        // Map every non-tab destination to the tab that owns it,
        // so the correct bottom nav item stays highlighted when navigating deeper.
        val destToTab = mapOf(
            R.id.logWorkoutFragment          to R.id.homeFragment,
            R.id.setupWizardFragment         to R.id.homeFragment,
            R.id.settingsFragment            to R.id.profileFragment,
            R.id.settingsTrainingFragment    to R.id.profileFragment,
            R.id.settingsAiFragment          to R.id.profileFragment,
            R.id.settingsDebugFragment       to R.id.profileFragment,
            R.id.settingsBackupFragment      to R.id.profileFragment,
            R.id.settingsPromptLogFragment   to R.id.profileFragment,
            R.id.settingsRejectionLogFragment to R.id.profileFragment,
            R.id.settingsCrashLogFragment    to R.id.profileFragment,
            R.id.settingsUnrecognizedFragment to R.id.profileFragment,
            R.id.gymPresetsFragment          to R.id.profileFragment,
        )

        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.bottomNav.visibility =
                if (destination.id == R.id.setupWizardFragment) android.view.View.GONE
                else android.view.View.VISIBLE
            // Update visual selection without triggering navigation.
            // selectedItemId fires the item-selected listener → causes a nav loop; isChecked does not.
            destToTab[destination.id]?.let { tabId ->
                binding.bottomNav.menu.findItem(tabId)?.isChecked = true
            }
        }

        // Android 13+ requires POST_NOTIFICATIONS to be requested at runtime.
        // Without it, foreground service notifications are silently suppressed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        lifecycleScope.launch {
            gamificationRepository.ensureAchievementsSeeded()
            checkAndAutoGenerateWeeklyPlan()
        }
    }

    private suspend fun checkAndAutoGenerateWeeklyPlan() {
        val thisWeek = java.text.SimpleDateFormat("yyyy-'W'ww", java.util.Locale.getDefault()).format(java.util.Date())
        if (prefsManager.lastAutoGenerateWeek == thisWeek) return
        if (prefsManager.apiKey.isBlank()) return
        if (!prefsManager.hasCompletedOnboarding) return  // wait until user completes onboarding
        val monday = thisMonday()
        val existing = workoutRepository.getPlannedForWeek(monday).first()
        if (existing.isEmpty()) {
            val preset = gymPresetDao.getById(prefsManager.selectedGymPresetId)
            val equipment: List<String> = preset?.let {
                runCatching {
                    val type = object : TypeToken<List<String>>() {}.type
                    gson.fromJson<List<String>>(it.equipmentJson, type)
                }.getOrElse { emptyList() }
            } ?: prefsManager.wizardEquipment.split(",").map { it.trim() }.filter { it.isNotBlank() }
            val result = aiRepository.generateAdaptedProgram(
                daysPerWeek = prefsManager.daysPerWeek,
                goal = prefsManager.fitnessGoal,
                experience = prefsManager.experienceLevel,
                sessionDurationMinutes = prefsManager.sessionDurationMinutes,
                equipment = equipment,
                equipmentNotes = preset?.notes ?: "",
                separateCardioDays = prefsManager.separateCardioDays,
                injuries = prefsManager.injuries,
                priorityMuscles = prefsManager.priorityMuscles,
                dislikedExercises = prefsManager.dislikedExercises,
                onboardingContext = prefsManager.onboardingContext
            )
            result.onSuccess { generationResult ->
                workoutRepository.savePlan(monday, generationResult.exercises)
                prefsManager.lastGenerationAttemptCount = generationResult.attemptCount
            }
        }
        prefsManager.lastAutoGenerateWeek = thisWeek
    }

}

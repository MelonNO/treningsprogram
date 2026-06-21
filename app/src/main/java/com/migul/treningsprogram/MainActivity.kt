package com.migul.treningsprogram

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.migul.treningsprogram.data.db.dao.GymPresetDao
import com.migul.treningsprogram.data.preferences.PreferencesManager
import com.migul.treningsprogram.data.repository.AiRepository
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

    @Inject lateinit var prefsManager: PreferencesManager
    @Inject lateinit var workoutRepository: WorkoutRepository
    @Inject lateinit var aiRepository: AiRepository
    @Inject lateinit var gymPresetDao: GymPresetDao
    @Inject lateinit var gson: Gson

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        val topLevelDests = setOf(
            R.id.homeFragment, R.id.logWorkoutFragment,
            R.id.historyFragment, R.id.programFragment, R.id.profileFragment
        )
        setupActionBarWithNavController(navController, AppBarConfiguration(topLevelDests))
        binding.bottomNav.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val fullScreen = destination.id == R.id.setupWizardFragment
            binding.bottomNav.visibility = if (fullScreen) android.view.View.GONE else android.view.View.VISIBLE
            supportActionBar?.let { if (fullScreen) it.hide() else it.show() }
        }

        lifecycleScope.launch { checkAndAutoGenerateWeeklyPlan() }
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
            } ?: emptyList()
            val result = aiRepository.generateAdaptedProgram(
                daysPerWeek = prefsManager.daysPerWeek,
                goal = prefsManager.fitnessGoal,
                experience = prefsManager.experienceLevel,
                sessionDurationMinutes = prefsManager.sessionDurationMinutes,
                equipment = equipment,
                equipmentNotes = preset?.notes ?: "",
                separateCardioDays = prefsManager.separateCardioDays,
                onboardingContext = prefsManager.onboardingContext
            )
            result.onSuccess { generationResult ->
                workoutRepository.savePlan(monday, generationResult.exercises)
                prefsManager.lastGenerationAttemptCount = generationResult.attemptCount
            }
        }
        prefsManager.lastAutoGenerateWeek = thisWeek
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(
            AppBarConfiguration(setOf(R.id.homeFragment, R.id.logWorkoutFragment, R.id.historyFragment, R.id.programFragment, R.id.profileFragment))
        ) || super.onSupportNavigateUp()
    }
}

package com.migul.treningsprogram

import android.Manifest
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.migul.treningsprogram.data.db.dao.GymPresetDao
import com.migul.treningsprogram.data.db.dao.WeeklySummaryDao
import com.migul.treningsprogram.data.db.entity.WeeklySummary
import com.migul.treningsprogram.data.preferences.PreferencesManager
import com.migul.treningsprogram.data.preferences.isoWeekKey
import com.migul.treningsprogram.domain.DeloadPolicy
import com.migul.treningsprogram.domain.WeeklySummaryTrigger
import com.migul.treningsprogram.data.repository.AiRepository
import com.migul.treningsprogram.data.repository.GamificationRepository
import com.migul.treningsprogram.data.repository.MesocycleContext
import com.migul.treningsprogram.data.repository.WorkoutRepository
import com.migul.treningsprogram.data.repository.autoGenWeekKey
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

    private var updateDownloadId: Long = -1L
    private var downloadReceiver: BroadcastReceiver? = null

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or denied — system handles display */ }

    @Inject lateinit var prefsManager: PreferencesManager
    @Inject lateinit var workoutRepository: WorkoutRepository
    @Inject lateinit var aiRepository: AiRepository
    @Inject lateinit var gamificationRepository: GamificationRepository
    @Inject lateinit var gymPresetDao: GymPresetDao
    @Inject lateinit var weeklySummaryDao: WeeklySummaryDao
    @Inject lateinit var gson: Gson

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        binding.bottomNav.setupWithNavController(navController)
        // Item 3: a single tap on the Profile bottom-nav button, from ANY Profile sub-screen (the
        // Settings list, App Settings, AI & Program, Backup & Data, Debug, Coach Summary, etc.), returns
        // to the Profile tab's ROOT in one tap. Handle both the reselect case (the tab is already the
        // selected one — which it is whenever a profile-owned sub-screen is showing) and the select
        // case, so the guarantee holds regardless of which the framework reports. Other tabs keep the
        // default multi-back-stack behaviour via NavigationUI.
        binding.bottomNav.setOnItemSelectedListener { item ->
            if (item.itemId == R.id.profileFragment) {
                if (navController.currentDestination?.id != R.id.profileFragment &&
                    !navController.popBackStack(R.id.profileFragment, false)
                ) {
                    NavigationUI.onNavDestinationSelected(item, navController)
                }
                true
            } else {
                NavigationUI.onNavDestinationSelected(item, navController)
            }
        }
        binding.bottomNav.setOnItemReselectedListener { item ->
            if (item.itemId == R.id.profileFragment &&
                navController.currentDestination?.id != R.id.profileFragment
            ) {
                navController.popBackStack(R.id.profileFragment, false)
            }
        }
        binding.btnHeaderBack.setOnClickListener { navController.navigateUp() }

        // P3: a tapped "plan generation finished" notification deep-links to the Program tab.
        handleGenerationDeepLink(intent)

        val topLevelIds = setOf(R.id.homeFragment, R.id.historyFragment, R.id.programFragment, R.id.profileFragment)
        // Fragments that manage their own header/toolbar internally
        val selfHeaderIds = setOf(R.id.gymPresetsFragment, R.id.logWorkoutFragment)

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
            R.id.settingsAppFragment         to R.id.profileFragment,
            R.id.settingsPromptLogFragment   to R.id.profileFragment,
            R.id.settingsRejectionLogFragment to R.id.profileFragment,
            R.id.settingsCrashLogFragment    to R.id.profileFragment,
            R.id.settingsUnrecognizedFragment to R.id.profileFragment,
            R.id.settingsAboutFragment       to R.id.profileFragment,
            R.id.gymPresetsFragment          to R.id.profileFragment,
            R.id.weeklySummaryFragment       to R.id.profileFragment,
            // S8 fix: exerciseLibraryFragment and exerciseDetailFragment were missing from this
            // map; the Profile tab lost its highlight when navigating into the library.
            R.id.exerciseLibraryFragment     to R.id.profileFragment,
            R.id.exerciseDetailFragment      to R.id.profileFragment,
            R.id.recapTrendsFragment         to R.id.historyFragment,
        )

        navController.addOnDestinationChangedListener { controller, destination, _ ->
            val isFullScreen = destination.id == R.id.setupWizardFragment
            val isTopLevel = destination.id in topLevelIds
            val hasSelfHeader = destination.id in selfHeaderIds
            val hideNav = isFullScreen || destination.id == R.id.logWorkoutFragment
            binding.bottomNav.visibility = if (hideNav) android.view.View.GONE else android.view.View.VISIBLE
            // Show in-screen header with back button for non-tab destinations
            if (isTopLevel || isFullScreen || hasSelfHeader) {
                binding.screenHeader.visibility = android.view.View.GONE
            } else {
                binding.screenHeader.visibility = android.view.View.VISIBLE
                binding.tvHeaderTitle.text = destination.label ?: ""
            }
            // Update visual selection without triggering navigation.
            // selectedItemId fires the item-selected listener → causes a nav loop; isChecked does not.
            if (destination.id == R.id.logWorkoutFragment) {
                // Highlight the tab that launched the workout (Home or Program)
                val prevId = controller.previousBackStackEntry?.destination?.id
                val tabId = if (prevId == R.id.programFragment) R.id.programFragment else R.id.homeFragment
                binding.bottomNav.menu.findItem(tabId)?.isChecked = true
            } else {
                destToTab[destination.id]?.let { tabId ->
                    binding.bottomNav.menu.findItem(tabId)?.isChecked = true
                }
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
            // B1: weekly coach summary — independent of and after plan gen, in the background so
            // it never blocks UI. Its own guards (API key / onboarding / once-per-week / data) apply.
            checkAndGenerateWeeklySummary()
        }

        // B05: clean up leftover downloaded update APKs on launch. Android doesn't reliably signal
        // install-finished, so we sweep at startup and delete only the app's own
        // treningsprogram-*.apk files whose version is already installed (or older). An APK carrying
        // a NEWER version (failed/partial install) is preserved so the user can retry.
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val currentVersion = try {
                packageManager.getPackageInfo(packageName, 0).versionName ?: "0"
            } catch (_: Exception) { "0" }
            ApkCleanup.cleanup(currentVersion)
        }

        lifecycleScope.launch {
            val release = UpdateChecker.fetchLatest()
            val currentVersion = try {
                packageManager.getPackageInfo(packageName, 0).versionName ?: "0"
            } catch (_: Exception) { "0" }
            if (release != null &&
                UpdateChecker.isNewer(release.tag, currentVersion) &&
                release.tag != prefsManager.skippedUpdateVersion
            ) {
                showUpdateDialog(release, currentVersion)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Auto-log a REST/MISSED placeholder for every empty past day. onStart fires on cold launch
        // AND on every return-from-background, so gaps are caught up whenever the app is foregrounded.
        // The call is idempotent + mutex-guarded and runs off the main thread, so re-entry is safe.
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { workoutRepository.autoLogRestDays() }
        }
    }

    private fun showUpdateDialog(release: UpdateChecker.ReleaseInfo, currentVersion: String) {
        val view = layoutInflater.inflate(R.layout.dialog_update_prompt, null)

        view.findViewById<TextView>(R.id.tv_update_dialog_version).text =
            "v$currentVersion  →  ${release.tag}"

        if (release.notes.isNotBlank()) {
            view.findViewById<View>(R.id.card_update_notes).visibility = View.VISIBLE
            view.findViewById<TextView>(R.id.tv_update_dialog_notes).text = release.notes.trim()
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(view)
            .setCancelable(false)
            .create()

        view.findViewById<View>(R.id.btn_update_download).setOnClickListener {
            dialog.dismiss()
            downloadAndInstall(release)
        }
        view.findViewById<View>(R.id.btn_update_later).setOnClickListener {
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.btn_update_skip).setOnClickListener {
            prefsManager.skippedUpdateVersion = release.tag
            dialog.dismiss()
        }

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    private fun downloadAndInstall(release: UpdateChecker.ReleaseInfo) {
        val fileName = "treningsprogram-${release.tag}.apk"
        val request = DownloadManager.Request(Uri.parse(release.apkUrl))
            .setTitle("Treningsprogram ${release.tag}")
            .setDescription("Downloading update…")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)

        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        updateDownloadId = dm.enqueue(request)
        Toast.makeText(this, "Downloading ${release.tag}…", Toast.LENGTH_SHORT).show()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id != updateDownloadId) return
                val apkUri = dm.getUriForDownloadedFile(id) ?: return
                startActivity(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                })
                unregisterReceiver(this)
                downloadReceiver = null
            }
        }
        downloadReceiver = receiver
        @Suppress("UnspecifiedRegisterReceiverFlag")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleGenerationDeepLink(intent)
    }

    /** P3: open the Program tab when launched from a generation-complete notification. */
    private fun handleGenerationDeepLink(intent: Intent?) {
        if (intent?.getBooleanExtra(
                com.migul.treningsprogram.notify.GenerationNotifier.EXTRA_OPEN_PROGRAM, false
            ) == true
        ) {
            intent.removeExtra(com.migul.treningsprogram.notify.GenerationNotifier.EXTRA_OPEN_PROGRAM)
            binding.bottomNav.post { binding.bottomNav.selectedItemId = R.id.programFragment }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        downloadReceiver?.let { unregisterReceiver(it) }
    }

    private suspend fun checkAndAutoGenerateWeeklyPlan() {
        val thisWeek = autoGenWeekKey()
        if (prefsManager.lastAutoGenerateWeek == thisWeek) return
        if (prefsManager.apiKey.isBlank()) return
        if (!prefsManager.hasCompletedOnboarding) return  // wait until user completes onboarding
        // E2: assumption N — a FROZEN program opts out of automatic weekly AI re-adaptation. Skip
        // generation (and do not mark the week done) so its plan stays as-is until the user acts.
        val activeProgram = workoutRepository.ensureActiveProgramId().let {
            workoutRepository.getActiveProgramOnce()
        }
        if (activeProgram?.isFrozen == true) return
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

            // E2 (M2): stall/fatigue-triggered deload decision, reusing B3's StallDetector via the
            // repository. If we were already deloading last week, the recovery week is done (exit);
            // otherwise enter a deload iff enough lifts are concurrently stalled.
            val stalledLifts = workoutRepository.computeStalledLifts()
            val isDeload = DeloadPolicy.nextDeloadState(
                currentlyDeloading = activeProgram?.isDeloadActive ?: false,
                stalledCount = stalledLifts.size
            )
            // E2 (L1): mesocycle position so the model knows it is producing week N of a block.
            val mesocycle = activeProgram?.let { p ->
                MesocycleContext(
                    mesocycleWeeks = p.mesocycleWeeks,
                    weekInBlock = workoutRepository.weekInBlock(p, monday),
                    isDeload = isDeload,
                    stalledLifts = stalledLifts
                )
            } ?: MesocycleContext(isDeload = isDeload, stalledLifts = stalledLifts)

            // B08: honour pinned rest days (rest-day mode derives days/week; count mode unchanged).
            // This is the automatic start-of-week generation — B09 leaves its preserve behaviour
            // untouched; only the day placement now respects the user's chosen rest days.
            val eff = com.migul.treningsprogram.domain.TrainingDaySelection.effective(
                prefsManager.restDaysCsv, prefsManager.daysPerWeek
            )
            val result = aiRepository.generateAdaptedProgram(
                daysPerWeek = eff.daysPerWeek,
                goal = prefsManager.fitnessGoal,
                experience = prefsManager.experienceLevel,
                sessionDurationMinutes = prefsManager.sessionDurationMinutes,
                equipment = equipment,
                equipmentNotes = preset?.notes ?: "",
                separateCardioDays = prefsManager.separateCardioDays,
                injuries = prefsManager.injuries,
                injurySeverity = prefsManager.injurySeverity,
                priorityMuscles = prefsManager.priorityMuscles,
                dislikedExercises = prefsManager.dislikedExercises,
                onboardingContext = prefsManager.onboardingContext,
                mesocycle = mesocycle,
                restDays = eff.restDays
            )
            result.onSuccess { generationResult ->
                // B2: stamp the week's rationale onto every row so any row of the week carries it.
                workoutRepository.savePlan(
                    monday,
                    generationResult.exercises.map { it.copy(rationale = generationResult.rationale) }
                )
                prefsManager.lastGenerationAttemptCount = generationResult.attemptCount
                // E2: persist the deload flag the generated week was built for, so Home/Program show
                // (or clear) the deload indicator coherently with the plan that was just saved.
                workoutRepository.setActiveDeload(isDeload)
            }
        }
        prefsManager.lastAutoGenerateWeek = thisWeek
    }

    /**
     * B1: automatic, non-blocking weekly coach summary. Mirrors [checkAndAutoGenerateWeeklyPlan]'s
     * once-per-week guard but keyed by [isoWeekKey] and persisted as its own [WeeklySummary] row.
     * Guards (skip → no-op, no broken row written): API key blank, onboarding incomplete, already
     * generated this ISO week, or too little data (no completed sessions in the lookback).
     */
    private suspend fun checkAndGenerateWeeklySummary() {
        val thisWeek = isoWeekKey()
        // Belt-and-suspenders: the prefs guard AND the table count both pin the once-per-week boundary.
        if (prefsManager.lastWeeklySummaryWeek == thisWeek) return
        if (weeklySummaryDao.countForWeek(thisWeek) > 0) {
            prefsManager.lastWeeklySummaryWeek = thisWeek
            return
        }
        val completedSessions = workoutRepository.getRecentSessions(12)
        if (!WeeklySummaryTrigger.shouldGenerate(
                lastSummaryWeek = prefsManager.lastWeeklySummaryWeek,
                currentWeekKey = thisWeek,
                hasApiKey = prefsManager.apiKey.isNotBlank(),
                onboardingComplete = prefsManager.hasCompletedOnboarding,
                completedSessionCount = completedSessions.size
            )
        ) return

        aiRepository.generateWeeklySummary(
            goal = prefsManager.fitnessGoal,
            experience = prefsManager.experienceLevel,
            daysPerWeek = prefsManager.daysPerWeek
        ).onSuccess { summary ->
            weeklySummaryDao.insert(
                WeeklySummary(
                    weekKey = thisWeek,
                    createdAtMs = System.currentTimeMillis(),
                    summaryText = summary
                )
            )
            // Only mark the week done on success, so a transient API failure retries next launch.
            prefsManager.lastWeeklySummaryWeek = thisWeek
        }
        // On failure: leave the guard unset → it retries on a later launch this week. No broken row.
    }

}

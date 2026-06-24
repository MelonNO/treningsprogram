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
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.migul.treningsprogram.data.db.dao.GymPresetDao
import com.migul.treningsprogram.data.preferences.PreferencesManager
import com.migul.treningsprogram.data.repository.AiRepository
import com.migul.treningsprogram.data.repository.GamificationRepository
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
    @Inject lateinit var gson: Gson

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        binding.bottomNav.setupWithNavController(navController)
        binding.btnHeaderBack.setOnClickListener { navController.navigateUp() }

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
            R.id.settingsPromptLogFragment   to R.id.profileFragment,
            R.id.settingsRejectionLogFragment to R.id.profileFragment,
            R.id.settingsCrashLogFragment    to R.id.profileFragment,
            R.id.settingsUnrecognizedFragment to R.id.profileFragment,
            R.id.settingsAboutFragment       to R.id.profileFragment,
            R.id.gymPresetsFragment          to R.id.profileFragment,
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

    override fun onDestroy() {
        super.onDestroy()
        downloadReceiver?.let { unregisterReceiver(it) }
    }

    private suspend fun checkAndAutoGenerateWeeklyPlan() {
        val thisWeek = autoGenWeekKey()
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
                injurySeverity = prefsManager.injurySeverity,
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

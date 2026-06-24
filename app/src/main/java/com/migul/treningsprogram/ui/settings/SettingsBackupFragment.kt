package com.migul.treningsprogram.ui.settings

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.google.android.material.snackbar.Snackbar
import com.migul.treningsprogram.databinding.FragmentSettingsBackupBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class SettingsBackupFragment : Fragment() {

    private var _binding: FragmentSettingsBackupBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by activityViewModels()

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        AlertDialog.Builder(requireContext())
            .setTitle("Import Backup?")
            .setMessage("This merges the backup file into your current data. Your existing workouts, achievements, and measurements are kept — nothing is deleted; backup entries are added in. Stats are recomputed and settings are reconciled. An older backup is upgraded to the current format automatically.")
            .setPositiveButton("Import") { _, _ ->
                try {
                    val json = requireContext().contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                        ?: throw IllegalArgumentException("Could not read file")
                    viewModel.importBackup(json)
                } catch (e: Exception) {
                    Snackbar.make(binding.root, "Could not read file: ${e.message}", Snackbar.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        // Both success and "cancelled" arrive here; GoogleSignIn parses the resulting intent.
        try {
            GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java)
            viewModel.onSignedIn()
        } catch (e: ApiException) {
            viewModel.onSignInFailed("code ${e.statusCode}")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBackupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnExportBackup.setOnClickListener {
            viewModel.consumeExportJson { json ->
                try {
                    val datePart = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    val file = File(requireContext().cacheDir, "treningsprogram-backup-$datePart.json")
                    file.writeText(json)
                    val uri = FileProvider.getUriForFile(
                        requireContext(),
                        "${requireContext().packageName}.fileprovider",
                        file
                    )
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, "Treningsprogram Backup")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(shareIntent, "Save backup via…"))
                } catch (e: Exception) {
                    Snackbar.make(binding.root, "Export failed: ${e.message}", Snackbar.LENGTH_LONG).show()
                }
            }
        }

        binding.btnImportBackup.setOnClickListener {
            importLauncher.launch(arrayOf("application/json", "*/*"))
        }

        // ---- Cloud backup ----
        binding.btnCloudConnect.setOnClickListener {
            if (!viewModel.cloudConfigured) {
                Snackbar.make(
                    binding.root,
                    "Cloud backup is not configured yet. A Google Web client ID must be added to the app.",
                    Snackbar.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }
            signInLauncher.launch(viewModel.googleDriveAuth.signInIntent())
        }

        binding.btnCloudBackupNow.setOnClickListener {
            viewModel.backupToCloud()
        }

        binding.btnCloudRestore.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Restore from cloud?")
                .setMessage("This merges the cloud backup into your current data. Existing workouts are kept; nothing is deleted.")
                .setPositiveButton("Restore") { _, _ -> viewModel.restoreFromCloud() }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnCloudDisconnect.setOnClickListener {
            viewModel.signOutCloud()
        }

        binding.btnResetWorkouts.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Reset All Workouts?")
                .setMessage("This will permanently delete all workout sessions and sets. This cannot be undone.")
                .setPositiveButton("Delete All") { _, _ -> viewModel.resetAllWorkouts() }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnFactoryReset.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Reset All User Data?")
                .setMessage("This will erase everything: all workouts, stats, your API key, and all settings. The app will restart as if freshly installed. This cannot be undone.")
                .setPositiveButton("Erase Everything") { _, _ -> viewModel.factoryReset() }
                .setNegativeButton("Cancel", null)
                .show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.resetDone.collect { done ->
                        if (done) Snackbar.make(binding.root, "All workout history deleted.", Snackbar.LENGTH_SHORT).show()
                    }
                }
                launch {
                    viewModel.factoryResetDone.collect { requireActivity().recreate() }
                }
                launch {
                    viewModel.importResult.collect { msg ->
                        if (msg != null) Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
                    }
                }
                launch {
                    viewModel.cloudAccount.collect { renderCloudState() }
                }
                launch {
                    viewModel.cloudLastBackup.collect { renderCloudState() }
                }
                launch {
                    viewModel.cloudBusy.collect { busy ->
                        binding.progressCloud.visibility = if (busy) View.VISIBLE else View.GONE
                        renderCloudState()
                    }
                }
                launch {
                    viewModel.cloudMessage.collect { msg ->
                        if (msg != null) Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }

        viewModel.refreshCloudStatus()
        renderCloudState()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshCloudStatus()
    }

    /** Reflect sign-in / configuration / busy state onto the cloud card. */
    private fun renderCloudState() {
        if (_binding == null) return
        val configured = viewModel.cloudConfigured
        val account = viewModel.cloudAccount.value
        val signedIn = account != null
        val busy = viewModel.cloudBusy.value

        binding.tvCloudStatus.text = when {
            !configured -> "Cloud backup not configured"
            signedIn -> "Connected as $account"
            else -> "Not connected"
        }

        val lastBackup = viewModel.cloudLastBackup.value
        if (signedIn && lastBackup != null) {
            binding.tvCloudLastBackup.visibility = View.VISIBLE
            binding.tvCloudLastBackup.text = "Last cloud backup: $lastBackup"
        } else {
            binding.tvCloudLastBackup.visibility = View.GONE
        }

        // Connect button is always tappable while not signed in (shows config message if needed).
        binding.btnCloudConnect.isEnabled = !busy && !signedIn
        binding.btnCloudConnect.visibility = if (signedIn) View.GONE else View.VISIBLE
        binding.btnCloudBackupNow.isEnabled = configured && signedIn && !busy
        binding.btnCloudRestore.isEnabled = configured && signedIn && !busy
        binding.btnCloudDisconnect.visibility = if (signedIn) View.VISIBLE else View.GONE
        binding.btnCloudDisconnect.isEnabled = !busy
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

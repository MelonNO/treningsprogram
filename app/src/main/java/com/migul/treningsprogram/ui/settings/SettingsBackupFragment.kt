package com.migul.treningsprogram.ui.settings

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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
            .setMessage("This will replace all current workout data, stats, and settings with the backup. This cannot be undone.")
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
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

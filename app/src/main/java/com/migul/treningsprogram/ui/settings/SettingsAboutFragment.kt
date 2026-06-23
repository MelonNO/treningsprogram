package com.migul.treningsprogram.ui.settings

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.migul.treningsprogram.UpdateChecker
import com.migul.treningsprogram.databinding.FragmentSettingsAboutBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsAboutFragment : Fragment() {

    private var _binding: FragmentSettingsAboutBinding? = null
    private val binding get() = _binding!!

    private var pendingRelease: UpdateChecker.ReleaseInfo? = null
    private var downloadId: Long = -1L
    private var downloadReceiver: BroadcastReceiver? = null

    private val currentVersion: String get() = try {
        requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName ?: "—"
    } catch (_: Exception) { "—" }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvVersion.text = "v$currentVersion"

        binding.btnCheckUpdate.setOnClickListener { checkForUpdates() }
        binding.btnDownloadInstall.setOnClickListener {
            pendingRelease?.let { startDownload(it) }
        }
    }

    private fun checkForUpdates() {
        setStateChecking()
        viewLifecycleOwner.lifecycleScope.launch {
            val release = UpdateChecker.fetchLatest()
            if (_binding == null) return@launch
            when {
                release == null -> setStateError()
                UpdateChecker.isNewer(release.tag, currentVersion) -> setStateAvailable(release)
                else -> setStateUpToDate()
            }
        }
    }

    private fun setStateChecking() {
        binding.btnCheckUpdate.isEnabled = false
        binding.btnCheckUpdate.text = "Checking…"
        binding.layoutUpdateStatus.visibility = View.GONE
        binding.cardReleaseNotes.visibility = View.GONE
        binding.btnDownloadInstall.visibility = View.GONE
        binding.progressDownload.visibility = View.GONE
    }

    private fun setStateUpToDate() {
        binding.btnCheckUpdate.isEnabled = true
        binding.btnCheckUpdate.text = "Check for Updates"
        binding.layoutUpdateStatus.visibility = View.VISIBLE
        binding.tvUpdateIcon.text = "✓"
        binding.tvUpdateTitle.text = "You're up to date"
        binding.tvUpdateSubtitle.text = "v$currentVersion is the latest version"
        binding.tvUpdateSubtitle.visibility = View.VISIBLE
        binding.tvUpdateTitle.setTextColor(resources.getColor(android.R.color.holo_green_light, null))
        binding.cardReleaseNotes.visibility = View.GONE
        binding.btnDownloadInstall.visibility = View.GONE
        binding.progressDownload.visibility = View.GONE
    }

    private fun setStateAvailable(release: UpdateChecker.ReleaseInfo) {
        pendingRelease = release
        binding.btnCheckUpdate.isEnabled = true
        binding.btnCheckUpdate.text = "Check for Updates"
        binding.layoutUpdateStatus.visibility = View.VISIBLE
        binding.tvUpdateIcon.text = "⬆"
        binding.tvUpdateTitle.text = "Update available — ${release.tag}"
        binding.tvUpdateTitle.setTextColor(requireContext().getColor(android.R.color.white))
        binding.tvUpdateSubtitle.text = "Current: v$currentVersion"
        binding.tvUpdateSubtitle.visibility = View.VISIBLE
        if (release.notes.isNotBlank()) {
            binding.cardReleaseNotes.visibility = View.VISIBLE
            binding.tvReleaseNotes.text = release.notes.trim()
        }
        binding.btnDownloadInstall.visibility = View.VISIBLE
        binding.progressDownload.visibility = View.GONE
    }

    private fun setStateError() {
        binding.btnCheckUpdate.isEnabled = true
        binding.btnCheckUpdate.text = "Check for Updates"
        binding.layoutUpdateStatus.visibility = View.VISIBLE
        binding.tvUpdateIcon.text = "✕"
        binding.tvUpdateTitle.text = "Could not check for updates"
        binding.tvUpdateTitle.setTextColor(requireContext().getColor(android.R.color.holo_red_light))
        binding.tvUpdateSubtitle.text = "Check your internet connection"
        binding.tvUpdateSubtitle.visibility = View.VISIBLE
        binding.cardReleaseNotes.visibility = View.GONE
        binding.btnDownloadInstall.visibility = View.GONE
    }

    private fun setStateDownloading() {
        binding.btnDownloadInstall.isEnabled = false
        binding.btnDownloadInstall.text = "Downloading…"
        binding.progressDownload.visibility = View.VISIBLE
        binding.progressDownload.isIndeterminate = true
        binding.tvUpdateTitle.text = "Downloading update…"
    }

    private fun startDownload(release: UpdateChecker.ReleaseInfo) {
        setStateDownloading()
        val fileName = "treningsprogram-${release.tag}.apk"
        val request = DownloadManager.Request(Uri.parse(release.apkUrl))
            .setTitle("Treningsprogram ${release.tag}")
            .setDescription("Downloading update…")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)

        val dm = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadId = dm.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id != downloadId) return
                val apkUri = dm.getUriForDownloadedFile(id) ?: return
                startActivity(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                })
                ctx.unregisterReceiver(this)
                downloadReceiver = null
            }
        }
        downloadReceiver = receiver
        @Suppress("UnspecifiedRegisterReceiverFlag")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_EXPORTED
            )
        } else {
            requireContext().registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        downloadReceiver?.let {
            try { requireContext().unregisterReceiver(it) } catch (_: Exception) {}
        }
        downloadReceiver = null
        _binding = null
    }
}

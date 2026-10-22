package io.github.darkboyjyoti.morpheupdater.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.darkboyjyoti.morpheupdater.R
import io.github.darkboyjyoti.morpheupdater.data.DownloadFiles
import io.github.darkboyjyoti.morpheupdater.data.DownloadPhase
import io.github.darkboyjyoti.morpheupdater.data.DownloadStore
import io.github.darkboyjyoti.morpheupdater.data.Prefs
import io.github.darkboyjyoti.morpheupdater.data.Release
import io.github.darkboyjyoti.morpheupdater.data.UpdateRepository
import io.github.darkboyjyoti.morpheupdater.data.UpdateRow
import io.github.darkboyjyoti.morpheupdater.databinding.ActivityMainBinding
import io.github.darkboyjyoti.morpheupdater.install.InstallResultReceiver
import io.github.darkboyjyoti.morpheupdater.install.Installer
import io.github.darkboyjyoti.morpheupdater.service.DownloadService
import io.github.darkboyjyoti.morpheupdater.service.NetworkPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_INSTALL_PACKAGE = "auto_install_package"
        private const val EXTRA_INSTALL_PATH = "auto_install_path"
        private const val EXTRA_INSTALL_TAG = "auto_install_tag"

        fun intentForInstall(
            context: Context,
            packageName: String,
            filePath: String,
            tag: String?,
        ): Intent = Intent(context, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
            putExtra(EXTRA_INSTALL_PACKAGE, packageName)
            putExtra(EXTRA_INSTALL_PATH, filePath)
            putExtra(EXTRA_INSTALL_TAG, tag)
        }
    }

    private lateinit var binding: ActivityMainBinding
    private val adapter = UpdateAdapter(::onRowAction)

    private var rows: List<UpdateRow> = emptyList()
    private var currentRelease: Release? = null
    private var loading = false
    private var lastLoadAt = 0L

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    private val installReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            rebuildRows()
            refreshDownloadedFiles()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UiUtils.applySystemBarInsets(binding.root)

        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_refresh -> {
                    load(force = true)
                    true
                }
                R.id.action_installer -> {
                    startActivity(Intent(this, InstallerActivity::class.java))
                    true
                }
                R.id.action_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        binding.swipeRefresh.setOnRefreshListener { load(force = true) }

        DownloadStore.states.observe(this) { states ->
            adapter.setStates(states)
            if (states.values.any { it.phase == DownloadPhase.DOWNLOADED }) {
                refreshDownloadedFiles()
            }
        }

        ContextCompat.registerReceiver(
            this,
            installReceiver,
            IntentFilter(InstallResultReceiver.ACTION_RESULT),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        maybeRequestNotificationPermission()
        load(force = false)
        handleInstallExtras(intent)
    }

    override fun onResume() {
        super.onResume()
        if (lastLoadAt > 0L && System.currentTimeMillis() - lastLoadAt > 60_000L) {
            load(force = false)
        }
        refreshDownloadedFiles()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleInstallExtras(intent)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(installReceiver) }
        super.onDestroy()
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            Prefs(this).notifications &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun load(force: Boolean) {
        if (loading) return
        loading = true
        binding.statusProgress.isVisible = true
        if (force) binding.swipeRefresh.isRefreshing = true

        lifecycleScope.launch {
            try {
                val release = withContext(Dispatchers.IO) {
                    UpdateRepository.loadRelease(this@MainActivity, force)
                }
                val builtRows = withContext(Dispatchers.IO) {
                    UpdateRepository.buildRows(this@MainActivity, release)
                }
                currentRelease = release
                rows = builtRows
                lastLoadAt = System.currentTimeMillis()
                Prefs(this@MainActivity).lastCheckMillis = lastLoadAt
                render(release)
            } catch (error: Exception) {
                binding.statusTitle.setText(R.string.status_offline)
                if (rows.isEmpty()) {
                    binding.emptyText.text = UiUtils.friendlyError(this@MainActivity, error)
                } else {
                    toast(UiUtils.friendlyError(this@MainActivity, error))
                }
            } finally {
                loading = false
                binding.statusProgress.isVisible = false
                binding.swipeRefresh.isRefreshing = false
                updateEmptyState()
            }
        }
    }

    private fun render(release: Release) {
        val prefs = Prefs(this)
        binding.repoLabel.text = prefs.repo
        binding.toolbar.subtitle = getString(R.string.status_release, release.tag)

        val lastChecked = prefs.lastCheckMillis
        binding.statusDetail.text = if (lastChecked > 0L) {
            getString(R.string.status_last_checked, UiUtils.formatDate(lastChecked))
        } else {
            getString(R.string.status_never)
        }

        val pending = rows.count { !it.upToDate && it.canInstall }
        binding.statusTitle.text = if (pending > 0) {
            getString(R.string.status_updates_available, pending)
        } else {
            getString(R.string.status_ready)
        }

        adapter.submit(rows, DownloadStore.states.value.orEmpty())
        updateEmptyState()
    }

    private fun updateEmptyState() {
        val empty = rows.isEmpty() && !loading
        binding.emptyState.isVisible = empty
        if (empty) {
            binding.emptyText.setText(R.string.empty_no_builds)
        }
    }

    private fun rebuildRows() {
        val release = currentRelease ?: return
        lifecycleScope.launch {
            val builtRows = withContext(Dispatchers.IO) {
                UpdateRepository.buildRows(this@MainActivity, release)
            }
            rows = builtRows
            render(release)
        }
    }

    private fun refreshDownloadedFiles() {
        if (rows.isEmpty()) return
        val updated = rows.map { row ->
            if (row.downloadedFile?.isFile == true) {
                row
            } else {
                row.copy(downloadedFile = DownloadFiles.find(this, row.packageName))
            }
        }
        if (updated != rows) {
            rows = updated
            adapter.submit(rows, DownloadStore.states.value.orEmpty())
        }
    }

    private fun onRowAction(row: UpdateRow) {
        if (!row.canInstall) {
            toast(UiUtils.supportMessage(this, row.support) ?: getString(R.string.support_unsupported))
            return
        }

        if (row.upToDate) return

        val file = row.downloadedFile?.takeIf { it.isFile }
            ?: DownloadFiles.find(this, row.packageName)

        if (file != null) {
            requestInstall(file, row.packageName, row.releaseTag)
            return
        }

        val prefs = Prefs(this)
        if (!NetworkPolicy.canDownload(this, prefs)) {
            toast(getString(R.string.waiting_for_wifi))
            return
        }

        try {
            DownloadService.start(this, row)
            toast(getString(R.string.toast_started))
        } catch (_: Exception) {
            toast(getString(R.string.toast_queued))
        }
    }

    private fun requestInstall(file: File, packageName: String, tag: String?) {
        when (val result = Installer.start(this, file, packageName, tag)) {
            Installer.StartResult.Started -> toast(getString(R.string.toast_installed))
            Installer.StartResult.NeedPermission -> UiUtils.showInstallPermissionDialog(this)
            is Installer.StartResult.Failed -> toast(
                getString(R.string.error_install_failed, result.message),
            )
        }
    }

    private fun handleInstallExtras(intent: Intent?) {
        if (intent == null) return
        val path = intent.getStringExtra(EXTRA_INSTALL_PATH) ?: return
        val packageName = intent.getStringExtra(EXTRA_INSTALL_PACKAGE) ?: return
        val tag = intent.getStringExtra(EXTRA_INSTALL_TAG)
        intent.removeExtra(EXTRA_INSTALL_PATH)
        intent.removeExtra(EXTRA_INSTALL_PACKAGE)
        intent.removeExtra(EXTRA_INSTALL_TAG)

        val file = File(path)
        if (!file.isFile) {
            toast(getString(R.string.error_file_missing))
            return
        }
        binding.root.post { requestInstall(file, packageName, tag) }
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }
}

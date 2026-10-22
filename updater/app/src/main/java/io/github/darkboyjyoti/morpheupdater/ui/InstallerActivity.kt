package io.github.darkboyjyoti.morpheupdater.ui

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import io.github.darkboyjyoti.morpheupdater.R
import io.github.darkboyjyoti.morpheupdater.data.ApkInspector
import io.github.darkboyjyoti.morpheupdater.data.Compatibility
import io.github.darkboyjyoti.morpheupdater.data.DownloadFiles
import io.github.darkboyjyoti.morpheupdater.data.InstalledApps
import io.github.darkboyjyoti.morpheupdater.data.SupportResult
import io.github.darkboyjyoti.morpheupdater.databinding.ActivityInstallerBinding
import io.github.darkboyjyoti.morpheupdater.databinding.ItemDownloadedBinding
import io.github.darkboyjyoti.morpheupdater.install.InstallTracker
import io.github.darkboyjyoti.morpheupdater.install.Installer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class InstallerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInstallerBinding
    private var pickedFile: File? = null
    private var pickedDetails: ApkInspector.ApkDetails? = null

    private val pickLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) handlePicked(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInstallerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UiUtils.applySystemBarInsets(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.pickApkButton.setOnClickListener { pickLauncher.launch(arrayOf("*/*")) }
        binding.installApkButton.setOnClickListener { installPicked() }
    }

    override fun onResume() {
        super.onResume()
        InstallTracker.clear()
        pickedDetails?.let { details -> pickedFile?.let { renderPicked(it, details) } }
        refreshDownloadedList()
    }

    private fun handlePicked(uri: Uri) {
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) { copyToCache(uri) }
            if (file == null) {
                toast(getString(R.string.error_read_file))
                return@launch
            }
            val details = withContext(Dispatchers.IO) {
                ApkInspector.inspect(this@InstallerActivity, file)
            }
            if (details == null) {
                file.delete()
                toast(getString(R.string.error_invalid_apk))
                return@launch
            }
            pickedFile = file
            pickedDetails = details
            renderPicked(file, details)
        }
    }

    private fun copyToCache(uri: Uri): File? {
        return runCatching {
            val directory = File(cacheDir, "picked").apply { mkdirs() }
            val target = File(directory, "picked-${System.currentTimeMillis()}.apk")
            val input = contentResolver.openInputStream(uri) ?: return null
            input.use { source ->
                target.outputStream().use { destination -> source.copyTo(destination) }
            }
            target
        }.getOrNull()
    }

    private fun renderPicked(file: File, details: ApkInspector.ApkDetails) {
        binding.apkCard.isVisible = true
        binding.apkLabel.text = details.label

        val unknown = getString(R.string.installer_unknown)
        val universal = getString(R.string.installer_universal)
        val minimum = if (details.minSdk > 0) {
            "Android ${details.minSdk}"
        } else {
            unknown
        }
        val abis = if (details.abis.isEmpty()) {
            universal
        } else {
            UiUtils.formatAbis(details.abis)
        }
        val certificate = details.signerSha256?.take(24) ?: unknown

        binding.apkDetails.text = buildString {
            appendLine("${getString(R.string.installer_package)}: ${details.packageName}")
            appendLine(
                "${getString(R.string.installer_version)}: " +
                    "${details.versionName} (${details.versionCode})",
            )
            appendLine("${getString(R.string.installer_size)}: ${DownloadFiles.formatBytes(details.size)}")
            appendLine("${getString(R.string.installer_arch)}: $abis")
            appendLine("${getString(R.string.installer_min_sdk)}: $minimum")
            append("${getString(R.string.installer_signer)}: $certificate…")
        }

        val installed = InstalledApps.get(this, details.packageName)
        val support = Compatibility.forApk(details, installed)
        val message = UiUtils.supportMessage(this, support)
        binding.compatText.isVisible = message != null
        binding.compatText.text = message

        val canInstall = support is SupportResult.Supported
        binding.installApkButton.isEnabled = canInstall
        binding.installApkButton.alpha = if (canInstall) 1f else 0.45f
        binding.installApkButton.setText(
            if (installed != null) R.string.action_update else R.string.action_install,
        )
        binding.installApkButton.setOnClickListener { installPicked() }
    }

    private fun installPicked() {
        val file = pickedFile ?: return
        val details = pickedDetails ?: return
        requestInstall(file, details.packageName, null)
    }

    private fun requestInstall(file: File, packageName: String, tag: String?) {
        when (val result = Installer.start(this, file, packageName, tag)) {
            Installer.StartResult.Started -> {
                InstallTracker.begin(packageName)
                binding.installApkButton.setText(R.string.action_installing)
                binding.installApkButton.isEnabled = false
                toast(getString(R.string.toast_installed))
            }
            Installer.StartResult.NeedPermission -> UiUtils.showInstallPermissionDialog(this)
            is Installer.StartResult.Failed -> toast(
                getString(R.string.error_install_failed, result.message),
            )
        }
    }

    private fun refreshDownloadedList() {
        binding.downloadedList.removeAllViews()
        val files = DownloadFiles.list(this)
        binding.noDownloadsText.isVisible = files.isEmpty()

        files.forEach { file ->
            val item = ItemDownloadedBinding.inflate(
                layoutInflater,
                binding.downloadedList,
                false,
            )
            val packageName = file.name.substringBeforeLast('-', file.name)
            item.downloadName.text = file.name
            item.downloadMeta.text = buildString {
                append(DownloadFiles.formatBytes(file.length()))
                if (packageName.contains('.')) {
                    append(" · ")
                    append(packageName)
                }
            }
            item.installButton.setOnClickListener {
                requestInstall(file, packageName, null)
            }
            item.deleteButton.setOnClickListener {
                DownloadFiles.delete(file)
                refreshDownloadedList()
            }
            binding.downloadedList.addView(item.root)
        }
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }
}

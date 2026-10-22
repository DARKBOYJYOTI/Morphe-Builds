package io.github.darkboyjyoti.morpheupdater.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.github.darkboyjyoti.morpheupdater.BuildConfig
import io.github.darkboyjyoti.morpheupdater.R
import io.github.darkboyjyoti.morpheupdater.data.Prefs
import io.github.darkboyjyoti.morpheupdater.databinding.ActivitySettingsBinding
import io.github.darkboyjyoti.morpheupdater.service.Scheduler

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val intervalValues = listOf(0, 6, 12, 24)
    private var selectedInterval = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiUtils.applyEdgeToEdge(this)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UiUtils.applySystemBarInsets(binding.root, binding.toolbar)

        binding.toolbar.setNavigationOnClickListener { finish() }

        val prefs = Prefs(this)
        binding.repoInput.setText(prefs.repo)

        val intervalLabels = listOf(
            getString(R.string.interval_off),
            getString(R.string.interval_6h),
            getString(R.string.interval_12h),
            getString(R.string.interval_24h),
        )
        val selectedIndex = intervalValues.indexOf(prefs.intervalHours).takeIf { it >= 0 } ?: 2
        selectedInterval = selectedIndex
        binding.intervalDropdown.setAdapter(
            ArrayAdapter(this, R.layout.item_dropdown, intervalLabels),
        )
        binding.intervalDropdown.setText(intervalLabels[selectedIndex], false)
        binding.intervalDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedInterval = position
        }

        binding.wifiOnlySwitch.isChecked = prefs.wifiOnly
        binding.notificationsSwitch.isChecked = prefs.notifications

        binding.versionText.text = getString(
            R.string.settings_version,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE,
        )

        binding.saveButton.setOnClickListener { save() }
    }

    private fun save() {
        val repo = binding.repoInput.text?.toString()?.trim().orEmpty()
        if (!REPO_PATTERN.matches(repo)) {
            binding.repoLayout.error = getString(R.string.error_repo_invalid)
            return
        }
        binding.repoLayout.error = null

        val prefs = Prefs(this)
        val previousRepo = prefs.repo
        prefs.repo = repo
        prefs.intervalHours = intervalValues[selectedInterval.coerceIn(0, intervalValues.lastIndex)]
        prefs.wifiOnly = binding.wifiOnlySwitch.isChecked
        prefs.notifications = binding.notificationsSwitch.isChecked

        if (previousRepo != repo) {
            prefs.clearReleaseCache()
            prefs.lastCheckMillis = 0L
        }

        Scheduler.schedule(this)
        Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    companion object {
        private val REPO_PATTERN = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
    }
}

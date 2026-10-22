package io.github.darkboyjyoti.morpheupdater.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import io.github.darkboyjyoti.morpheupdater.R
import io.github.darkboyjyoti.morpheupdater.data.AppRegistry
import io.github.darkboyjyoti.morpheupdater.data.DownloadPhase
import io.github.darkboyjyoti.morpheupdater.data.DownloadState
import io.github.darkboyjyoti.morpheupdater.data.SupportResult
import io.github.darkboyjyoti.morpheupdater.data.UpdateRow
import io.github.darkboyjyoti.morpheupdater.databinding.ItemUpdateBinding
import io.github.darkboyjyoti.morpheupdater.util.AppIcons

class UpdateAdapter(
    private val onAction: (UpdateRow) -> Unit,
) : RecyclerView.Adapter<UpdateAdapter.Holder>() {

    private var rows: List<UpdateRow> = emptyList()
    private var states: Map<String, DownloadState> = emptyMap()
    private var installing: Set<String> = emptySet()

    fun submit(
        newRows: List<UpdateRow>,
        newStates: Map<String, DownloadState>,
        newInstalling: Set<String>,
    ) {
        rows = newRows
        states = newStates
        installing = newInstalling
        notifyDataSetChanged()
    }

    fun setStates(newStates: Map<String, DownloadState>) {
        states = newStates
        notifyDataSetChanged()
    }

    fun setInstalling(newInstalling: Set<String>) {
        installing = newInstalling
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemUpdateBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return Holder(binding)
    }

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(rows[position], states[rows[position].packageName] ?: DownloadState())
    }

    inner class Holder(
        private val binding: ItemUpdateBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(row: UpdateRow, state: DownloadState) {
            val context = binding.root.context

            binding.appName.text = row.appName
            binding.installedText.text = if (row.installedVersionCode != null) {
                context.getString(
                    R.string.installed_version_code,
                    row.installedVersionName ?: "?",
                    row.installedVersionCode,
                )
            } else {
                context.getString(R.string.not_installed)
            }
            binding.availableText.text = context.getString(
                R.string.available_version,
                row.asset.versionName.ifBlank { row.releaseTag },
            )

            val iconKey = "${row.packageName}|${row.asset.iconUrl.orEmpty()}"
            binding.appIcon.tag = iconKey
            binding.appIcon.setImageResource(R.drawable.ic_android)
            binding.appIcon.imageTintList = ColorStateList.valueOf(
                AppRegistry.colorFor(row.packageName),
            )
            AppIcons.load(
                context,
                row.packageName,
                row.asset.iconUrl,
                row.downloadedFile,
            ) { icon ->
                if (binding.appIcon.tag == iconKey) {
                    binding.appIcon.setImageDrawable(icon)
                    binding.appIcon.imageTintList = null
                }
            }

            val supportMessage = UiUtils.supportMessage(context, row.support)
            val failureMessage = state.message?.takeIf {
                state.phase == DownloadPhase.FAILED && it.isNotBlank()
            }
            val message = failureMessage ?: supportMessage
            binding.reasonText.isVisible = message != null
            binding.reasonText.text = message

            val downloading = state.phase == DownloadPhase.DOWNLOADING
            val verifying = state.phase == DownloadPhase.VERIFYING
            val failed = state.phase == DownloadPhase.FAILED
            val installingNow = row.packageName in installing
            val busy = downloading || verifying || installingNow

            var clickable = false
            var grayed = false

            when {
                row.signatureConflict -> {
                    binding.actionButton.setText(R.string.support_unsupported)
                    grayed = true
                }
                row.support !is SupportResult.Supported -> {
                    binding.actionButton.setText(R.string.support_unsupported)
                    grayed = true
                }
                installingNow -> {
                    binding.actionButton.setText(R.string.action_installing)
                }
                downloading -> {
                    binding.actionButton.text = context.getString(
                        R.string.action_downloading,
                        state.progress,
                    )
                }
                verifying -> {
                    binding.actionButton.setText(R.string.action_verifying)
                }
                row.upToDate -> {
                    binding.actionButton.setText(R.string.action_up_to_date)
                    grayed = true
                }
                row.downloadedFile?.isFile == true || state.phase == DownloadPhase.DOWNLOADED -> {
                    binding.actionButton.setText(R.string.action_install_now)
                    clickable = true
                }
                failed -> {
                    binding.actionButton.setText(R.string.action_retry)
                    clickable = true
                }
                row.installedVersionCode != null -> {
                    binding.actionButton.setText(R.string.action_update)
                    clickable = true
                }
                else -> {
                    binding.actionButton.setText(R.string.action_install)
                    clickable = true
                }
            }

            binding.actionButton.isEnabled = true
            binding.actionButton.isClickable = clickable
            binding.actionButton.isFocusable = clickable
            binding.actionButton.alpha = if (grayed) 0.45f else 1f
            binding.root.alpha = if (row.support is SupportResult.Supported) 1f else 0.65f

            binding.progressBar.isVisible = busy
            binding.progressBar.isIndeterminate = verifying || installingNow
            if (!binding.progressBar.isIndeterminate) {
                binding.progressBar.progress = state.progress
            }

            binding.actionButton.setOnClickListener { if (clickable) onAction(row) }
        }
    }
}

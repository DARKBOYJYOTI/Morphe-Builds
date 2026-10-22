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

class UpdateAdapter(
    private val onAction: (UpdateRow) -> Unit,
) : RecyclerView.Adapter<UpdateAdapter.Holder>() {

    private var rows: List<UpdateRow> = emptyList()
    private var states: Map<String, DownloadState> = emptyMap()

    fun submit(newRows: List<UpdateRow>, newStates: Map<String, DownloadState>) {
        rows = newRows
        states = newStates
        notifyDataSetChanged()
    }

    fun setStates(newStates: Map<String, DownloadState>) {
        states = newStates
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

            val icon = if (row.installedVersionCode != null) {
                runCatching {
                    context.packageManager.getApplicationIcon(row.packageName)
                }.getOrNull()
            } else {
                null
            }
            if (icon != null) {
                binding.appIcon.setImageDrawable(icon)
                binding.appIcon.imageTintList = null
            } else {
                binding.appIcon.setImageResource(R.drawable.ic_android)
                binding.appIcon.imageTintList = ColorStateList.valueOf(
                    AppRegistry.colorFor(row.packageName),
                )
            }

            val supportMessage = UiUtils.supportMessage(context, row.support)
            binding.reasonText.isVisible = supportMessage != null
            binding.reasonText.text = supportMessage

            val downloading = state.phase == DownloadPhase.DOWNLOADING
            val verifying = state.phase == DownloadPhase.VERIFYING

            when {
                row.signatureConflict -> {
                    binding.actionButton.setText(R.string.support_unsupported)
                    binding.actionButton.isEnabled = false
                }
                row.support !is SupportResult.Supported -> {
                    binding.actionButton.setText(R.string.support_unsupported)
                    binding.actionButton.isEnabled = false
                }
                downloading -> {
                    binding.actionButton.text = context.getString(
                        R.string.action_downloading,
                        state.progress,
                    )
                    binding.actionButton.isEnabled = false
                }
                verifying -> {
                    binding.actionButton.setText(R.string.action_verifying)
                    binding.actionButton.isEnabled = false
                }
                row.upToDate -> {
                    binding.actionButton.setText(R.string.action_up_to_date)
                    binding.actionButton.isEnabled = false
                }
                row.downloadedFile?.isFile == true || state.phase == DownloadPhase.DOWNLOADED -> {
                    binding.actionButton.setText(R.string.action_install_now)
                    binding.actionButton.isEnabled = true
                }
                row.installedVersionCode != null -> {
                    binding.actionButton.setText(R.string.action_update)
                    binding.actionButton.isEnabled = true
                }
                else -> {
                    binding.actionButton.setText(R.string.action_install)
                    binding.actionButton.isEnabled = true
                }
            }
            binding.actionButton.alpha = if (binding.actionButton.isEnabled) 1f else 0.45f
            binding.root.alpha = if (row.support is SupportResult.Supported) 1f else 0.65f

            binding.progressBar.isVisible = downloading || verifying
            binding.progressBar.isIndeterminate = verifying
            if (!verifying) {
                binding.progressBar.progress = state.progress
            }

            binding.actionButton.setOnClickListener { onAction(row) }
        }
    }
}

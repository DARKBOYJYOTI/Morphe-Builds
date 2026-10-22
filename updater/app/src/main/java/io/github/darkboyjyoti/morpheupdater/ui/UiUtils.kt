package io.github.darkboyjyoti.morpheupdater.ui

import android.content.Context
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.darkboyjyoti.morpheupdater.R
import io.github.darkboyjyoti.morpheupdater.install.Installer
import io.github.darkboyjyoti.morpheupdater.data.SupportResult
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object UiUtils {

    fun applySystemBarInsets(root: View) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    fun supportMessage(context: Context, support: SupportResult): String? = when (support) {
        is SupportResult.Supported -> null
        is SupportResult.UnsupportedAbi -> context.getString(
            R.string.reason_unsupported_abi,
            support.deviceAbis.joinToString(", "),
            support.assetAbis.joinToString(", "),
        )
        is SupportResult.RequiresSdk -> context.getString(
            R.string.reason_unsupported_sdk,
            support.minSdk,
        )
        SupportResult.SignatureMismatch -> context.getString(R.string.reason_signature)
    }

    fun formatDate(millis: Long): String {
        if (millis <= 0L) return ""
        val format = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())
        return format.format(Date(millis))
    }

    fun friendlyError(context: Context, error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            message == "rate_limited" -> context.getString(R.string.error_rate_limited)
            message == "release_not_found" -> context.getString(R.string.error_release_not_found)
            error is UnknownHostException -> context.getString(R.string.error_no_network)
            message == "http_403" -> context.getString(R.string.error_rate_limited)
            message.startsWith("http_") -> context.getString(R.string.error_release_not_found)
            else -> message.ifBlank { context.getString(R.string.error_no_network) }
        }
    }

    fun showInstallPermissionDialog(context: Context) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.unknown_sources_title)
            .setMessage(R.string.unknown_sources_text)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                runCatching { context.startActivity(Installer.unknownSourcesIntent(context)) }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun formatAbis(abis: Collection<String>): String {
        if (abis.isEmpty()) return ""
        return abis.map { io.github.darkboyjyoti.morpheupdater.data.ApkInspector.normalizeAbi(it) }
            .distinct()
            .joinToString(", ")
    }
}

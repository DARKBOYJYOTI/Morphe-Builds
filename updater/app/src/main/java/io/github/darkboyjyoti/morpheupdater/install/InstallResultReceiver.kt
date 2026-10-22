package io.github.darkboyjyoti.morpheupdater.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast
import io.github.darkboyjyoti.morpheupdater.data.Prefs

class InstallResultReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_PACKAGE = "install_package"
        const val EXTRA_TAG = "install_tag"
        const val EXTRA_VERSION_CODE = "install_version_code"
        const val ACTION_RESULT = "io.github.darkboyjyoti.morpheupdater.INSTALL_RESULT"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE,
        )
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val packageName = intent.getStringExtra(EXTRA_PACKAGE)
        val tag = intent.getStringExtra(EXTRA_TAG)
        val versionCode = intent.getLongExtra(EXTRA_VERSION_CODE, 0L)

        when (status) {
            PackageInstaller.STATUS_SUCCESS -> {
                if (packageName != null && !tag.isNullOrBlank()) {
                    Prefs(context.applicationContext)
                        .recordInstalled(packageName, tag, versionCode)
                }
                toast(context, "Update installed.")
            }

            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirmation = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirmation != null) {
                    confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(confirmation) }
                }
            }

            else -> {
                val reason = message?.takeIf { it.isNotBlank() } ?: "status $status"
                toast(context, "Install failed: $reason")
            }
        }

        context.sendBroadcast(
            Intent(ACTION_RESULT)
                .setPackage(context.packageName)
                .putExtra(EXTRA_PACKAGE, packageName)
                .putExtra(PackageInstaller.EXTRA_STATUS, status),
        )
    }

    private fun toast(context: Context, text: String) {
        runCatching { Toast.makeText(context.applicationContext, text, Toast.LENGTH_LONG).show() }
    }
}

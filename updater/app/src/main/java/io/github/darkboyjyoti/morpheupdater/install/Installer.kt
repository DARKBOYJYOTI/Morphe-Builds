package io.github.darkboyjyoti.morpheupdater.install

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import io.github.darkboyjyoti.morpheupdater.R
import io.github.darkboyjyoti.morpheupdater.data.AppRegistry
import io.github.darkboyjyoti.morpheupdater.data.ApkInspector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object Installer {

    sealed class StartResult {
        object Started : StartResult()
        object NeedPermission : StartResult()
        data class Failed(val message: String) : StartResult()
    }

    fun hasInstallPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            )
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }

    suspend fun start(
        context: Context,
        file: File,
        packageName: String,
        releaseTag: String?,
    ): StartResult = withContext(Dispatchers.IO) {
        if (!file.isFile) {
            return@withContext StartResult.Failed(context.getString(R.string.error_file_missing))
        }
        if (!hasInstallPermission(context)) return@withContext StartResult.NeedPermission

        val details = runCatching { ApkInspector.inspect(context, file) }
            .getOrNull() ?: return@withContext StartResult.Failed(context.getString(R.string.error_invalid_apk))
        val knownApp = AppRegistry.byPackage(packageName)
        val matchesExpected = details.packageName == packageName ||
            knownApp?.let {
                details.packageName == it.packageName || details.packageName in it.aliases
            } == true
        if (!matchesExpected) {
            return@withContext StartResult.Failed(context.getString(R.string.error_install_package_mismatch))
        }

        var sessionId = -1
        try {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL,
            ).apply {
                setAppPackageName(details.packageName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                }
            }

            sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                val output = session.openWrite("base.apk", 0L, file.length())
                try {
                    file.inputStream().use { input -> input.copyTo(output) }
                    session.fsync(output)
                } finally {
                    output.close()
                }

                val callback = Intent(context, InstallResultReceiver::class.java).apply {
                    putExtra(
                        InstallResultReceiver.EXTRA_PACKAGE,
                        AppRegistry.canonicalPackageName(packageName),
                    )
                    putExtra(InstallResultReceiver.EXTRA_TAG, releaseTag)
                    putExtra(InstallResultReceiver.EXTRA_VERSION_CODE, details.versionCode)
                    putExtra(InstallResultReceiver.EXTRA_FILE_PATH, file.absolutePath)
                }
                val pending = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    callback,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(pending.intentSender)
            }
            StartResult.Started
        } catch (error: Throwable) {
            if (sessionId >= 0) {
                runCatching { context.packageManager.packageInstaller.abandonSession(sessionId) }
            }
            if (error is CancellationException) throw error
            StartResult.Failed(error.message ?: "install_session_failed")
        }
    }
}

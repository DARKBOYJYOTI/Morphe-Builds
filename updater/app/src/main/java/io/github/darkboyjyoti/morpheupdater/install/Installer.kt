package io.github.darkboyjyoti.morpheupdater.install

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import io.github.darkboyjyoti.morpheupdater.data.ApkInspector
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

    fun start(
        context: Context,
        file: File,
        packageName: String,
        releaseTag: String?,
    ): StartResult {
        if (!file.isFile) return StartResult.Failed("file_missing")
        if (!hasInstallPermission(context)) return StartResult.NeedPermission

        val versionCode = runCatching { ApkInspector.inspect(context, file)?.versionCode }
            .getOrNull() ?: 0L

        return try {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL,
            ).apply {
                setAppPackageName(packageName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                }
            }

            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                val output = session.openWrite("base.apk", 0L, file.length())
                try {
                    file.inputStream().use { input -> input.copyTo(output) }
                    session.fsync(output)
                } finally {
                    output.close()
                }

                val callback = Intent(context, InstallResultReceiver::class.java).apply {
                    putExtra(InstallResultReceiver.EXTRA_PACKAGE, packageName)
                    putExtra(InstallResultReceiver.EXTRA_TAG, releaseTag)
                    putExtra(InstallResultReceiver.EXTRA_VERSION_CODE, versionCode)
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
            runCatching { openWithSystemInstaller(context, file) }
            StartResult.Failed(error.message ?: "install_session_failed")
        }
    }

    fun openWithSystemInstaller(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.files",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}

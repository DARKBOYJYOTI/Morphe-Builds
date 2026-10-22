package io.github.darkboyjyoti.morpheupdater.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.darkboyjyoti.morpheupdater.R
import io.github.darkboyjyoti.morpheupdater.ui.MainActivity

object Notifications {

    const val CHANNEL_UPDATES = "updates"
    const val CHANNEL_DOWNLOADS = "downloads"

    const val SUMMARY_ID = 100
    const val DOWNLOAD_BASE_ID = 1_000

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val updates = NotificationChannel(
            CHANNEL_UPDATES,
            context.getString(R.string.notif_channel_updates),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notif_channel_updates_desc)
        }
        val downloads = NotificationChannel(
            CHANNEL_DOWNLOADS,
            context.getString(R.string.notif_channel_downloads),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notif_channel_downloads_desc)
            setShowBadge(false)
        }

        manager.createNotificationChannels(listOf(updates, downloads))
    }

    fun downloadId(packageName: String): Int =
        DOWNLOAD_BASE_ID + (packageName.hashCode() and 0x0FFF)

    fun summary(context: Context, count: Int, tag: String) {
        if (!canPost(context)) return
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(GOOGLE_BLUE)
            .setContentTitle(context.getString(R.string.notif_updates_title))
            .setContentText(context.getString(R.string.notif_updates_text, count, tag))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        notify(context, SUMMARY_ID, notification)
    }

    fun progress(
        context: Context,
        packageName: String,
        label: String,
        progress: Int,
        bytes: Long,
        total: Long,
        indeterminate: Boolean = false,
    ): Notification {
        val text = if (indeterminate) {
            context.getString(R.string.notif_download_waiting)
        } else {
            context.getString(
                R.string.notif_download_text,
                progress.coerceIn(0, 100),
                formatBytes(bytes),
                formatBytes(total),
            )
        }

        return NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(GOOGLE_BLUE)
            .setContentTitle(context.getString(R.string.notif_download_title, label))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress.coerceIn(0, 100), indeterminate)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun ready(context: Context, packageName: String, label: String, filePath: String, tag: String?) {
        if (!canPost(context)) return
        val intent = MainActivity.intentForInstall(context, packageName, filePath, tag)
        val pending = PendingIntent.getActivity(
            context,
            packageName.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(GOOGLE_GREEN)
            .setContentTitle(context.getString(R.string.notif_ready_title, label))
            .setContentText(context.getString(R.string.notif_ready_text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        notify(context, downloadId(packageName) + 20_000, notification)
    }

    fun failed(context: Context, packageName: String, label: String, message: String) {
        if (!canPost(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(GOOGLE_RED)
            .setContentTitle(context.getString(R.string.notif_failed_title, label))
            .setContentText(message)
            .setAutoCancel(true)
            .build()
        notify(context, downloadId(packageName) + 40_000, notification)
    }

    private fun notify(context: Context, id: Int, notification: Notification) {
        runCatching {
            NotificationManagerCompat.from(context).notify(id, notification)
        }
    }

    private fun canPost(context: Context): Boolean {
        val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (!enabled) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return true
    }

    fun formatBytes(bytes: Long): String = io.github.darkboyjyoti.morpheupdater.data.DownloadFiles.formatBytes(bytes)

    private const val GOOGLE_BLUE = 0xFF4285F4.toInt()
    private const val GOOGLE_GREEN = 0xFF34A853.toInt()
    private const val GOOGLE_RED = 0xFFEA4335.toInt()
}

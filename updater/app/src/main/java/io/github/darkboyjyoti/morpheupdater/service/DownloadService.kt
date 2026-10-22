package io.github.darkboyjyoti.morpheupdater.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.darkboyjyoti.morpheupdater.data.DownloadFiles
import io.github.darkboyjyoti.morpheupdater.data.DownloadPhase
import io.github.darkboyjyoti.morpheupdater.data.DownloadState
import io.github.darkboyjyoti.morpheupdater.data.DownloadStore
import io.github.darkboyjyoti.morpheupdater.data.UpdateRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class DownloadService : Service() {

    companion object {
        private const val ACTION_START = "io.github.darkboyjyoti.morpheupdater.action.DOWNLOAD"
        private const val EXTRA_PACKAGE = "package"
        private const val EXTRA_URL = "url"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_SHA256 = "sha256"
        private const val EXTRA_VERSION_CODE = "version_code"
        private const val EXTRA_TAG = "release_tag"

        fun start(context: Context, row: UpdateRow): Intent {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PACKAGE, row.packageName)
                putExtra(EXTRA_URL, row.asset.url)
                putExtra(EXTRA_LABEL, row.appName)
                putExtra(EXTRA_SHA256, row.asset.sha256)
                putExtra(EXTRA_VERSION_CODE, row.asset.versionCode)
                putExtra(EXTRA_TAG, row.releaseTag)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
            return intent
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null
    private var packageName: String? = null

    @Volatile
    private var cancelled = false

    private var lastNotificationAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START) {
            stopSelf()
            return START_NOT_STICKY
        }

        val packageName = intent.getStringExtra(EXTRA_PACKAGE)
        val url = intent.getStringExtra(EXTRA_URL)
        if (packageName.isNullOrBlank() || url.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        val label = intent.getStringExtra(EXTRA_LABEL) ?: packageName
        val sha256 = intent.getStringExtra(EXTRA_SHA256)
        val versionCode = intent.getLongExtra(EXTRA_VERSION_CODE, 0L)
        val releaseTag = intent.getStringExtra(EXTRA_TAG)

        this.packageName = packageName
        cancelled = false

        val target = DownloadFiles.fileFor(this, packageName, versionCode)
        startForegroundCompat(
            packageName,
            Notifications.progress(this, packageName, label, 0, 0L, 0L, indeterminate = true),
        )

        if (target.isFile) {
            DownloadStore.update(
                packageName,
                DownloadState(DownloadPhase.DOWNLOADED, 100, target.length(), target.length()),
            )
            Notifications.ready(this, packageName, label, target.absolutePath, releaseTag)
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        DownloadStore.update(packageName, DownloadState(DownloadPhase.DOWNLOADING))

        downloadJob?.cancel()
        downloadJob = scope.launch {
            val result = ResumableDownloader(
                url = url,
                target = target,
                expectedSha256 = sha256,
                onProgress = { downloaded, total ->
                    val percent = if (total > 0L) ((downloaded * 100L) / total).toInt() else 0
                    DownloadStore.update(
                        packageName,
                        DownloadState(
                            phase = DownloadPhase.DOWNLOADING,
                            progress = percent.coerceIn(0, 100),
                            bytes = downloaded,
                            total = total,
                        ),
                    )
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastNotificationAt > 700L) {
                        lastNotificationAt = now
                        updateProgressNotification(packageName, label, percent, downloaded, total)
                    }
                },
                isCancelled = { cancelled },
            ).run()

            when (result) {
                is ResumableDownloader.Result.Success -> {
                    DownloadStore.update(
                        packageName,
                        DownloadState(
                            phase = DownloadPhase.DOWNLOADED,
                            progress = 100,
                            bytes = result.bytes,
                            total = result.bytes,
                        ),
                    )
                    Notifications.ready(this@DownloadService, packageName, label, result.file.absolutePath, releaseTag)
                }

                is ResumableDownloader.Result.Failure -> {
                    DownloadStore.update(
                        packageName,
                        DownloadState(
                            phase = DownloadPhase.FAILED,
                            message = result.message,
                        ),
                    )
                    Notifications.failed(this@DownloadService, packageName, label, result.message)
                }

                ResumableDownloader.Result.Cancelled -> {
                    DownloadStore.update(packageName, DownloadState(DownloadPhase.IDLE))
                }
            }

            ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }

        return START_NOT_STICKY
    }

    private fun updateProgressNotification(
        packageName: String,
        label: String,
        percent: Int,
        downloaded: Long,
        total: Long,
    ) {
        val notification = Notifications.progress(this, packageName, label, percent, downloaded, total)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    Notifications.downloadId(packageName),
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            } else {
                ServiceCompat.startForeground(
                    this,
                    Notifications.downloadId(packageName),
                    notification,
                    0,
                )
            }
        }
    }

    private fun startForegroundCompat(packageName: String, notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                Notifications.downloadId(packageName),
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ServiceCompat.startForeground(
                this,
                Notifications.downloadId(packageName),
                notification,
                0,
            )
        }
    }

    override fun onDestroy() {
        cancelled = true
        downloadJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}

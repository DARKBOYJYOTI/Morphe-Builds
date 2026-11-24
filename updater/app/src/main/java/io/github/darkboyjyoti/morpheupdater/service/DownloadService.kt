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
import io.github.darkboyjyoti.morpheupdater.R
import io.github.darkboyjyoti.morpheupdater.data.ApkInspector
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
import kotlinx.coroutines.withContext
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class DownloadService : Service() {

    companion object {
        private const val ACTION_START = "io.github.darkboyjyoti.morpheupdater.action.DOWNLOAD"
        private const val EXTRA_PACKAGE = "package"
        private const val EXTRA_URL = "url"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_SHA256 = "sha256"
        private const val EXTRA_VERSION_CODE = "version_code"
        private const val EXTRA_TAG = "release_tag"
        private const val EXTRA_SIZE = "size"

        fun start(context: Context, row: UpdateRow): Intent {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PACKAGE, row.packageName)
                putExtra(EXTRA_URL, row.asset.url)
                putExtra(EXTRA_LABEL, row.appName)
                putExtra(EXTRA_SHA256, row.asset.sha256)
                putExtra(EXTRA_VERSION_CODE, row.asset.versionCode)
                putExtra(EXTRA_TAG, row.releaseTag)
                putExtra(EXTRA_SIZE, row.asset.size)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
            return intent
        }
    }

    private data class DownloadRequest(
        val packageName: String,
        val url: String,
        val label: String,
        val sha256: String?,
        val versionCode: Long,
        val releaseTag: String?,
        val expectedSize: Long,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingDownloads = ArrayDeque<DownloadRequest>()
    private val cancelled = AtomicBoolean(false)
    private var activePackage: String? = null
    private var downloadJob: Job? = null
    private var latestStartId = 0

    private var lastNotificationAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        if (intent?.action != ACTION_START) {
            if (activePackage == null && pendingDownloads.isEmpty()) stopSelfResult(startId)
            return START_NOT_STICKY
        }

        val packageName = intent.getStringExtra(EXTRA_PACKAGE)
        val url = intent.getStringExtra(EXTRA_URL)
        if (packageName.isNullOrBlank() || url.isNullOrBlank()) {
            if (activePackage == null && pendingDownloads.isEmpty()) stopSelfResult(startId)
            return START_NOT_STICKY
        }

        if (activePackage == packageName ||
            pendingDownloads.any { it.packageName == packageName }
        ) {
            return START_NOT_STICKY
        }

        val request = DownloadRequest(
            packageName = packageName,
            url = url,
            label = intent.getStringExtra(EXTRA_LABEL) ?: packageName,
            sha256 = intent.getStringExtra(EXTRA_SHA256),
            versionCode = intent.getLongExtra(EXTRA_VERSION_CODE, 0L),
            releaseTag = intent.getStringExtra(EXTRA_TAG),
            expectedSize = intent.getLongExtra(EXTRA_SIZE, 0L),
        )
        pendingDownloads.addLast(request)
        DownloadStore.update(packageName, DownloadState(DownloadPhase.QUEUED))
        if (activePackage == null) {
            processNext()
        }
        return START_NOT_STICKY
    }

    private fun processNext() {
        val request = pendingDownloads.pollFirst()
        if (request == null) {
            activePackage = null
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelfResult(latestStartId)
            return
        }

        activePackage = request.packageName
        DownloadStore.update(request.packageName, DownloadState(DownloadPhase.DOWNLOADING))
        lastNotificationAt = 0L
        startForegroundCompat(
            request.packageName,
            Notifications.progress(
                this,
                request.packageName,
                request.label,
                0,
                0L,
                request.expectedSize,
                indeterminate = request.expectedSize <= 0L,
            ),
        )

        downloadJob = scope.launch {
            try {
                download(request)
            } catch (error: Exception) {
                if (!cancelled.get()) {
                    val message = friendlyFailure(
                        ResumableDownloader.Result.Failure(
                            error.message ?: "download_failed",
                            error,
                        ),
                    )
                    DownloadStore.update(
                        request.packageName,
                        DownloadState(DownloadPhase.FAILED, message = message),
                    )
                    Notifications.failed(this@DownloadService, request.packageName, request.label, message)
                }
            } finally {
                withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                    if (activePackage == request.packageName) activePackage = null
                    downloadJob = null
                    processNext()
                }
            }
        }
    }

    private fun download(request: DownloadRequest) {
        val target = DownloadFiles.fileFor(
            this,
            request.packageName,
            request.versionCode,
            request.releaseTag,
        )
        val existing = target.takeIf { it.isFile }
        if (existing != null) {
            val valid = runCatching {
                val sizeOk = request.expectedSize <= 0L || existing.length() == request.expectedSize
                sizeOk && (request.sha256.isNullOrBlank() ||
                    ApkInspector.sha256(existing).equals(request.sha256, ignoreCase = true))
            }.getOrDefault(false)

            if (valid) {
                DownloadFiles.saveReleaseTag(existing, request.releaseTag)
                DownloadStore.update(
                    request.packageName,
                    DownloadState(DownloadPhase.DOWNLOADED, 100, existing.length(), existing.length()),
                )
                Notifications.ready(
                    this,
                    request.packageName,
                    request.label,
                    existing.absolutePath,
                    request.releaseTag,
                )
                return
            }
            existing.delete()
        }

        val result = ResumableDownloader(
            url = request.url,
            target = target,
            expectedSha256 = request.sha256,
            onProgress = { downloaded, total ->
                val percent = if (total > 0L) ((downloaded * 100L) / total).toInt() else 0
                DownloadStore.update(
                    request.packageName,
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
                    updateProgressNotification(request.packageName, request.label, percent, downloaded, total)
                }
            },
            isCancelled = { cancelled.get() },
        ).run()

        when (result) {
            is ResumableDownloader.Result.Success -> {
                if (request.expectedSize > 0L && result.bytes != request.expectedSize) {
                    DownloadFiles.delete(result.file)
                    val message = getString(R.string.error_corrupted_download)
                    DownloadStore.update(
                        request.packageName,
                        DownloadState(DownloadPhase.FAILED, message = message),
                    )
                    Notifications.failed(this, request.packageName, request.label, message)
                    return
                }
                DownloadFiles.saveReleaseTag(result.file, request.releaseTag)
                DownloadStore.update(
                    request.packageName,
                    DownloadState(DownloadPhase.DOWNLOADED, 100, result.bytes, result.bytes),
                )
                Notifications.ready(
                    this,
                    request.packageName,
                    request.label,
                    result.file.absolutePath,
                    request.releaseTag,
                )
            }

            is ResumableDownloader.Result.Failure -> {
                val message = friendlyFailure(result)
                DownloadStore.update(
                    request.packageName,
                    DownloadState(DownloadPhase.FAILED, message = message),
                )
                Notifications.failed(this, request.packageName, request.label, message)
            }

            ResumableDownloader.Result.Cancelled ->
                DownloadStore.update(request.packageName, DownloadState(DownloadPhase.IDLE))
        }
    }

    private fun friendlyFailure(
        result: ResumableDownloader.Result.Failure,
    ): String {
        if (result.message == "checksum_mismatch") {
            return getString(R.string.error_corrupted_download)
        }
        val cause = result.cause
        if (cause is UnknownHostException ||
            cause is SocketTimeoutException ||
            cause is ConnectException ||
            cause is IOException
        ) {
            return getString(R.string.error_no_network)
        }
        if (result.message.startsWith("HTTP ")) {
            return getString(R.string.error_download_generic)
        }
        return result.message.ifBlank { getString(R.string.error_download_generic) }
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
        cancelled.set(true)
        downloadJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        cancelled.set(true)
        downloadJob?.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
}

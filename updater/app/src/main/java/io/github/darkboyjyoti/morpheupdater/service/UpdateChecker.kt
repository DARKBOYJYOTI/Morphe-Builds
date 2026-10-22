package io.github.darkboyjyoti.morpheupdater.service

import android.content.Context
import io.github.darkboyjyoti.morpheupdater.data.DownloadFiles
import io.github.darkboyjyoti.morpheupdater.data.Prefs
import io.github.darkboyjyoti.morpheupdater.data.SupportResult
import io.github.darkboyjyoti.morpheupdater.data.UpdateRepository

object UpdateChecker {

    suspend fun check(context: Context): Int {
        val prefs = Prefs(context.applicationContext)
        val release = UpdateRepository.loadRelease(context.applicationContext, force = true)
        prefs.lastCheckMillis = System.currentTimeMillis()

        val rows = UpdateRepository.buildRows(context.applicationContext, release)
        val pending = rows.filter { row ->
            !row.upToDate &&
                row.support is SupportResult.Supported &&
                !row.signatureConflict &&
                (row.downloadedFile == null || !row.downloadedFile.isFile)
        }

        if (pending.isEmpty()) return 0

        if (prefs.notifications) {
            Notifications.summary(context.applicationContext, pending.size, release.tag)
        }

        return pending.size
    }

    fun downloadedCount(context: Context): Int =
        DownloadFiles.list(context).size
}

package io.github.darkboyjyoti.morpheupdater

import android.app.Application
import io.github.darkboyjyoti.morpheupdater.data.DownloadFiles
import io.github.darkboyjyoti.morpheupdater.service.Notifications
import io.github.darkboyjyoti.morpheupdater.service.Scheduler

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        DownloadFiles.cleanupStaleParts(this)
        Scheduler.schedule(this)
    }
}

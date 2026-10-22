package io.github.darkboyjyoti.morpheupdater.service

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import io.github.darkboyjyoti.morpheupdater.data.Prefs
import java.util.concurrent.TimeUnit

object Scheduler {

    private const val JOB_ID = 4201

    fun schedule(context: Context) {
        val appContext = context.applicationContext
        val prefs = Prefs(appContext)
        val scheduler = appContext.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
            ?: return

        scheduler.cancel(JOB_ID)

        val hours = prefs.intervalHours
        if (hours <= 0) return

        val component = ComponentName(appContext, UpdateCheckJobService::class.java)
        val builder = JobInfo.Builder(JOB_ID, component)
            .setPersisted(true)
            .setRequiredNetworkType(
                if (prefs.wifiOnly) JobInfo.NETWORK_TYPE_UNMETERED else JobInfo.NETWORK_TYPE_ANY,
            )

        builder.setPeriodic(TimeUnit.HOURS.toMillis(hours.toLong()))

        runCatching { scheduler.schedule(builder.build()) }
    }
}

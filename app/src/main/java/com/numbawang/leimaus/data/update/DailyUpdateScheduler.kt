package com.numbawang.leimaus.data.update

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import java.util.concurrent.TimeUnit

/** A persisted daily check, independent of punch reminders and exact-alarm permission. */
object DailyUpdateScheduler {
    const val JOB_ID = 2001
    val INTERVAL_MS = TimeUnit.DAYS.toMillis(1)

    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        // App launches and reminder rescheduling must not restart the daily interval.
        if (scheduler.getPendingJob(JOB_ID) != null) return
        scheduler.schedule(JobInfo.Builder(JOB_ID, ComponentName(context, DailyUpdateJobService::class.java))
            .setPeriodic(INTERVAL_MS)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .build())
    }
}

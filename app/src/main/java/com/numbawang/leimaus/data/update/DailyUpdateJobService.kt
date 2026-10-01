package com.numbawang.leimaus.data.update

import android.app.job.JobParameters
import android.app.job.JobService
import com.numbawang.leimaus.BuildConfig
import com.numbawang.leimaus.alarm.NotificationHelper
import com.numbawang.leimaus.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DailyUpdateJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val running = mutableMapOf<Int, Job>()

    override fun onStartJob(params: JobParameters): Boolean {
        running[params.jobId] = scope.launch {
            try {
                val preferences = UserPreferencesRepository(this@DailyUpdateJobService)
                val notifications = NotificationHelper(this@DailyUpdateJobService)
                val check = CheckForUpdateUseCase(HttpUpdateService(), BuildConfig.VERSION_NAME)
                BackgroundUpdateCheck(check::execute, preferences::getLastNotifiedUpdateVersion,
                    { notifications.showUpdateAvailableNotification(it.versionName, it.sizeMb) },
                    preferences::updateLastNotifiedUpdateVersion).execute()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // A failed daily read has no actionable notification; the next daily job checks again.
            } finally {
                if (isActive) {
                    running.remove(params.jobId)
                    jobFinished(params, false)
                }
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running.remove(params.jobId)?.cancel()
        return false
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

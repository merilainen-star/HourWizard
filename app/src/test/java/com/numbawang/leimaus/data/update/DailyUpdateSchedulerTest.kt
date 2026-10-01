package com.numbawang.leimaus.data.update

import android.app.Notification
import android.app.NotificationManager
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.numbawang.leimaus.alarm.AlarmScheduler
import com.numbawang.leimaus.alarm.BootReceiver
import com.numbawang.leimaus.alarm.NotificationHelper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DailyUpdateSchedulerTest {
    @Test fun `reminder rescheduling retains one independent persisted daily network job`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scheduler = context.getSystemService(JobScheduler::class.java)
        scheduler.cancelAll()
        AlarmScheduler(context).scheduleAlarms("invalid", "invalid")
        val first = scheduler.getPendingJob(DailyUpdateScheduler.JOB_ID)!!
        assertTrue(first.isPeriodic)
        assertEquals(86_400_000L, first.intervalMillis)
        assertEquals(JobInfo.NETWORK_TYPE_ANY, first.networkType)
        assertTrue(first.isPersisted)
        assertEquals(DailyUpdateJobService::class.java.name, first.service.className)
        AlarmScheduler(context).scheduleAlarms("07:30", "16:00")
        assertSame(first, scheduler.getPendingJob(DailyUpdateScheduler.JOB_ID))
        assertEquals(1, scheduler.allPendingJobs.size)
    }

    @Test fun `boot and package update restore missing daily job`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scheduler = context.getSystemService(JobScheduler::class.java)
        for (action in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) {
            scheduler.cancelAll()
            BootReceiver { com.numbawang.leimaus.data.preferences.AppSettings() }.onReceive(context, Intent(action))
            assertNotNull(scheduler.getPendingJob(DailyUpdateScheduler.JOB_ID))
            assertEquals(1, scheduler.allPendingJobs.size)
        }
    }

    @Test fun `update notification opens updater and disabled notifications are not reported as sent`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(NotificationManager::class.java)
        val shadow = shadowOf(manager)
        shadow.setNotificationsEnabled(true)
        val helper = NotificationHelper(context)
        assertTrue(helper.showUpdateAvailableNotification("1.0-new", 22))
        val notification = shadow.getNotification(NotificationHelper.NOTIFICATION_UPDATE_ID)
        assertEquals("Numbawang-päivitys saatavilla", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(NotificationHelper.UPDATE_CHANNEL_ID, notification.channelId)
        val intent = shadowOf(notification.contentIntent).savedIntent
        assertTrue(intent.getBooleanExtra(NotificationHelper.EXTRA_OPEN_UPDATE, false))
        assertFalse(intent.hasExtra(NotificationHelper.EXTRA_PUNCH_ACTION))
        assertTrue(notification.actions.isNullOrEmpty())
        manager.cancel(NotificationHelper.NOTIFICATION_UPDATE_ID)
        shadow.setNotificationsEnabled(false)
        assertFalse(helper.showUpdateAvailableNotification("1.0-next", 22))
        assertNull(shadow.getNotification(NotificationHelper.NOTIFICATION_UPDATE_ID))
    }
}

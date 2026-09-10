package com.numbawang.leimaus.approval

import android.app.Notification
import android.app.NotificationManager
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.numbawang.leimaus.alarm.AlarmScheduler
import com.numbawang.leimaus.alarm.ApprovalReminderReceiver
import com.numbawang.leimaus.alarm.NotificationHelper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ApprovalNotificationTest {
    @Test fun `delivery has a separate notification and opens review without punch or approval action`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(NotificationManager::class.java)
        val notifications = shadowOf(manager)
        NotificationHelper(context).showMorningNotification()
        ApprovalReminderReceiver().onReceive(context,
            Intent().putExtra(NotificationHelper.EXTRA_APPROVAL_MONTH, "2026-08"))
        assertNotNull(notifications.getNotification(NotificationHelper.NOTIFICATION_ID))
        val notification = notifications.getNotification(NotificationHelper.NOTIFICATION_APPROVAL_ID)
        assertEquals("Hyväksy tunnit", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(NotificationHelper.APPROVAL_CHANNEL_ID, notification.channelId)
        assertTrue(notification.actions.isNullOrEmpty())
        val intent = shadowOf(notification.contentIntent).savedIntent
        assertEquals("2026-08", intent.getStringExtra(NotificationHelper.EXTRA_APPROVAL_MONTH))
        assertFalse(intent.hasExtra(NotificationHelper.EXTRA_PUNCH_ACTION))
        assertNotNull(shadowOf(context.getSystemService(AlarmManager::class.java)).nextScheduledAlarm)
    }

    @Test fun `scheduling retains independent morning evening and monthly pending intents`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AlarmScheduler(context).scheduleAlarms("07:30", "16:00")
        val alarms = shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms
        assertEquals(3, alarms.size)
        assertEquals(3, alarms.map { shadowOf(it.operation).requestCode }.toSet().size)
    }

    @Test fun `rescheduling keeps overdue reminder and its original month`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val now = System.currentTimeMillis()
        context.getSharedPreferences("approval_reminders", Context.MODE_PRIVATE).edit()
            .putLong("pending_due", now - 10_000).putString("pending_month", "2026-08").commit()
        AlarmScheduler(context).scheduleApprovalReminder()
        val alarm = shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.single()
        assertEquals("2026-08", shadowOf(alarm.operation).savedIntent.getStringExtra(NotificationHelper.EXTRA_APPROVAL_MONTH))
        assertTrue(alarm.triggerAtTime < now + 60_000)
    }
}

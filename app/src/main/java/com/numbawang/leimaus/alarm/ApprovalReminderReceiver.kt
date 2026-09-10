package com.numbawang.leimaus.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Deliberately has no repository or mutation path; delivery only posts a notification. */
class ApprovalReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            intent.getStringExtra(NotificationHelper.EXTRA_APPROVAL_MONTH)?.let {
                val saved = context.getSharedPreferences("approval_reminders", Context.MODE_PRIVATE)
                if (saved.getString("last_posted_month", null) != it) {
                    NotificationHelper(context).showApprovalNotification(it)
                    saved.edit().putString("last_posted_month", it).apply()
                }
            }
        } finally {
            AlarmScheduler(context).scheduleApprovalReminder()
        }
    }
}

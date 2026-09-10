package com.numbawang.leimaus.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.numbawang.leimaus.data.preferences.UserPreferencesRepository

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
                android.app.AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)) {
            val prefsRepository = UserPreferencesRepository(context)
            val settings = prefsRepository.loadSettings()
            val alarmScheduler = AlarmScheduler(context)

            alarmScheduler.scheduleAlarms(
                settings.morningReminderTime,
                settings.eveningReminderTime
            )
        }
    }
}

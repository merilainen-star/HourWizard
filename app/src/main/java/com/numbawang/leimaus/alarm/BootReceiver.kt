package com.numbawang.leimaus.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.numbawang.leimaus.data.preferences.UserPreferencesRepository

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
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

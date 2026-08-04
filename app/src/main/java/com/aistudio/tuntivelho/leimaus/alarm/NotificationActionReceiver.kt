package com.aistudio.tuntivelho.leimaus.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.aistudio.tuntivelho.leimaus.data.db.AppDatabase
import com.aistudio.tuntivelho.leimaus.data.network.StampResult
import com.aistudio.tuntivelho.leimaus.data.network.TuntivelhoRepository
import com.aistudio.tuntivelho.leimaus.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pendingResult = goAsync()

        val prefsRepository = UserPreferencesRepository(context)
        val database = AppDatabase.getInstance(context)
        val tuntivelhoRepository = TuntivelhoRepository(prefsRepository, database.stampDao())
        val notificationHelper = NotificationHelper(context)
        val alarmScheduler = AlarmScheduler(context)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_CLOCK_IN -> {
                        val result = tuntivelhoRepository.clockIn()
                        withContext(Dispatchers.Main) {
                            when (result) {
                                is StampResult.Success -> {
                                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                    // 2. Remove morning notification
                                    notificationHelper.cancelNotification()
                                    // 3. Ensure alarms scheduled for evening
                                    val settings = prefsRepository.loadSettings()
                                    alarmScheduler.scheduleAlarms(
                                        settings.morningReminderTime,
                                        settings.eveningReminderTime
                                    )
                                }
                                is StampResult.Error -> {
                                    Toast.makeText(context, "Virhe: ${result.errorMessage}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }

                    ACTION_CLOCK_OUT -> {
                        val result = tuntivelhoRepository.clockOut()
                        withContext(Dispatchers.Main) {
                            when (result) {
                                is StampResult.Success -> {
                                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                    // Remove notification and cancel ticker
                                    notificationHelper.cancelNotification()
                                    alarmScheduler.cancelEveningTicker()
                                }
                                is StampResult.Error -> {
                                    Toast.makeText(context, "Virhe: ${result.errorMessage}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_CLOCK_IN = "com.aistudio.tuntivelho.ACTION_CLOCK_IN"
        const val ACTION_CLOCK_OUT = "com.aistudio.tuntivelho.ACTION_CLOCK_OUT"
    }
}

package com.example.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleAlarms(morningTimeStr: String, eveningTimeStr: String) {
        scheduleSingleAlarm(morningTimeStr, EXTRA_TYPE_MORNING, REQ_CODE_MORNING)
        scheduleSingleAlarm(eveningTimeStr, EXTRA_TYPE_EVENING, REQ_CODE_EVENING)
    }

    private fun scheduleSingleAlarm(timeStr: String, type: String, requestCode: Int) {
        val parts = timeStr.split(":", ".")
        if (parts.size < 2) return

        val hour = parts[0].trim().toIntOrNull() ?: return
        val minute = parts[1].trim().toIntOrNull() ?: return

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // If time is in the past for today, schedule for tomorrow
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_ALARM_TYPE, type)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
    }

    fun scheduleEveningTicker() {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_ALARM_TYPE, EXTRA_TYPE_TICKER)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQ_CODE_TICKER,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAt = System.currentTimeMillis() + 60_000L // every 1 minute
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun cancelEveningTicker() {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_ALARM_TYPE, EXTRA_TYPE_TICKER)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQ_CODE_TICKER,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }

    companion object {
        const val EXTRA_ALARM_TYPE = "extra_alarm_type"
        const val EXTRA_TYPE_MORNING = "type_morning"
        const val EXTRA_TYPE_EVENING = "type_evening"
        const val EXTRA_TYPE_TICKER = "type_ticker"

        const val REQ_CODE_MORNING = 1001
        const val REQ_CODE_EVENING = 1002
        const val REQ_CODE_TICKER = 1003
    }
}

package com.numbawang.leimaus.alarm

import com.numbawang.leimaus.data.preferences.AppSettings
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

object NotificationFilterUtils {

    /**
     * Checks if notifications are allowed today based on:
     * 1. Day of week (1=Mon, ..., 7=Sun mapped from Calendar.DAY_OF_WEEK)
     * 2. Vacation period check (if enabled and current date is between vacationStart and vacationEnd)
     */
    fun isNotificationAllowedForToday(
        settings: AppSettings,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val helsinkiTz = TimeZone.getTimeZone("Europe/Helsinki")
        val cal = Calendar.getInstance(helsinkiTz).apply {
            timeInMillis = currentTimeMillis
        }

        val dayOfWeekIso = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }

        val enabledSet = settings.enabledDaysString.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .toSet()

        if (!enabledSet.contains(dayOfWeekIso)) {
            return false
        }

        if (settings.isVacationEnabled) {
            val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).apply {
                timeZone = helsinkiTz
            }

            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val todayMidnight = cal.timeInMillis

            val startDate = parseDateToMidnight(settings.vacationStart, dateFormat)
            val endDate = parseDateToMidnight(settings.vacationEnd, dateFormat)

            if (startDate != null && endDate != null) {
                val endOfDay = endDate + 86_399_999L
                if (todayMidnight in startDate..endOfDay) {
                    return false
                }
            } else if (startDate != null) {
                if (todayMidnight >= startDate) {
                    return false
                }
            } else if (endDate != null) {
                val endOfDay = endDate + 86_399_999L
                if (todayMidnight <= endOfDay) {
                    return false
                }
            }
        }

        return true
    }

    /**
     * True when the Helsinki wall clock at [currentTimeMillis] has reached [timeOfDay]
     * ("HH:mm" or "HH.mm"). Returns false for an unparseable time so callers stay quiet
     * rather than firing at the wrong moment.
     */
    fun isAtOrAfterTimeOfDay(currentTimeMillis: Long, timeOfDay: String): Boolean {
        val parts = timeOfDay.split(":", ".")
        if (parts.size < 2) return false
        val hour = parts[0].trim().toIntOrNull() ?: return false
        val minute = parts[1].trim().toIntOrNull() ?: return false

        val cal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Helsinki")).apply {
            timeInMillis = currentTimeMillis
        }
        val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return nowMinutes >= (hour * 60) + minute
    }

    private fun parseDateToMidnight(dateStr: String, dateFormat: SimpleDateFormat): Long? {
        if (dateStr.isBlank()) return null
        return try {
            val parsed = dateFormat.parse(dateStr.trim()) ?: return null
            val cal = Calendar.getInstance(dateFormat.timeZone).apply {
                time = parsed
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            cal.timeInMillis
        } catch (e: Exception) {
            null
        }
    }
}

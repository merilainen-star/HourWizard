package com.example.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.network.TuntivelhoRepository
import com.example.data.preferences.UserPreferencesRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefsRepository = UserPreferencesRepository(context)
        val settings = prefsRepository.loadSettings()
        val notificationHelper = NotificationHelper(context)
        val alarmScheduler = AlarmScheduler(context)

        val alarmType = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_TYPE)
        val isAllowed = NotificationFilterUtils.isNotificationAllowedForToday(settings)

        // Evaluate geofence location rules if enabled
        if (settings.isGeofenceEnabled) {
            try {
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
                if (locationManager != null) {
                    var loc: android.location.Location? = null
                    if (locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
                        loc = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                    }
                    if (loc == null && locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)) {
                        loc = locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                    }
                    if (loc != null) {
                        val result = LocationFilterUtils.evaluateGeofence(loc.latitude, loc.longitude, settings)
                        val helsinkiTz = java.util.TimeZone.getTimeZone("Europe/Helsinki")
                        val todayDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply { timeZone = helsinkiTz }.format(Date())

                        when (result) {
                            is GeofenceResult.TriggerArrival -> {
                                notificationHelper.showLocationArrivalNotification()
                                prefsRepository.updateGeofenceNotificationDates(arrivalDate = todayDateStr)
                            }
                            is GeofenceResult.TriggerDeparture -> {
                                notificationHelper.showLocationDepartureNotification()
                                prefsRepository.updateGeofenceNotificationDates(departureDate = todayDateStr)
                            }
                            is GeofenceResult.NoAction -> {
                                // Respect rules; no false alarm sent
                            }
                        }
                    }
                }
            } catch (_: SecurityException) {
                // Location permission not granted
            } catch (_: Exception) {
            }
        }

        when (alarmType) {
            AlarmScheduler.EXTRA_TYPE_MORNING -> {
                if (isAllowed) {
                    notificationHelper.showMorningNotification()
                }
                // Reschedule for tomorrow morning
                alarmScheduler.scheduleAlarms(settings.morningReminderTime, settings.eveningReminderTime)
            }

            AlarmScheduler.EXTRA_TYPE_EVENING, AlarmScheduler.EXTRA_TYPE_TICKER -> {
                if (!isAllowed) {
                    if (alarmType == AlarmScheduler.EXTRA_TYPE_TICKER) {
                        notificationHelper.cancelNotification()
                        alarmScheduler.cancelEveningTicker()
                    }
                    if (alarmType == AlarmScheduler.EXTRA_TYPE_EVENING) {
                        alarmScheduler.scheduleAlarms(settings.morningReminderTime, settings.eveningReminderTime)
                    }
                    return
                }

                val isClockedIn = settings.isClockedIn && settings.clockInTimestamp > 0L

                if (isClockedIn) {
                    val helsinkiTz = java.util.TimeZone.getTimeZone("Europe/Helsinki")
                    val clockInTimeStr = SimpleDateFormat("HH.mm", Locale.getDefault())
                        .apply { timeZone = helsinkiTz }
                        .format(Date(settings.clockInTimestamp))

                    val balanceStr = TuntivelhoRepository.calculateBalance(
                        clockInTs = settings.clockInTimestamp,
                        currentTs = System.currentTimeMillis(),
                        targetMinutesNeeded = settings.totalWorkdayMinutesNeeded
                    )

                    notificationHelper.showEveningNotification(
                        clockInTimeStr = clockInTimeStr,
                        balanceStr = balanceStr
                    )

                    // Keep ticker going every minute while clocked in
                    alarmScheduler.scheduleEveningTicker()
                } else if (alarmType == AlarmScheduler.EXTRA_TYPE_EVENING) {
                    // Evening alarm fired while not clocked in: show evening notification to remind user
                    notificationHelper.showEveningNotification(
                        clockInTimeStr = "",
                        balanceStr = ""
                    )
                    alarmScheduler.cancelEveningTicker()
                } else {
                    // Ticker when not clocked in
                    notificationHelper.cancelNotification()
                    alarmScheduler.cancelEveningTicker()
                }

                if (alarmType == AlarmScheduler.EXTRA_TYPE_EVENING) {
                    // Reschedule for tomorrow evening
                    alarmScheduler.scheduleAlarms(settings.morningReminderTime, settings.eveningReminderTime)
                }
            }
        }
    }
}

package com.aistudio.tuntivelho.leimaus.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.LocationServices
import com.aistudio.tuntivelho.leimaus.data.network.TuntivelhoRepository
import com.aistudio.tuntivelho.leimaus.data.preferences.UserPreferencesRepository
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

        // Evaluate geofence location rules if enabled using FusedLocationProviderClient with LocationManager fallback.
        // lastLocation resolves asynchronously, so hold the broadcast open with goAsync() —
        // without it onReceive returns immediately and the process may die before the callback runs.
        if (settings.isGeofenceEnabled) {
            val pendingResult = goAsync()
            var finished = false
            val finishOnce = {
                if (!finished) {
                    finished = true
                    pendingResult.finish()
                }
            }
            try {
                val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                fusedClient.lastLocation.addOnSuccessListener { loc ->
                    try {
                        if (loc != null) {
                            evaluateAndTriggerGeofence(loc, settings, prefsRepository, notificationHelper)
                        } else {
                            fallbackLocationCheck(context, settings, prefsRepository, notificationHelper)
                        }
                    } finally {
                        finishOnce()
                    }
                }.addOnFailureListener {
                    try {
                        fallbackLocationCheck(context, settings, prefsRepository, notificationHelper)
                    } finally {
                        finishOnce()
                    }
                }
            } catch (_: SecurityException) {
                // Location permission not granted
                finishOnce()
            } catch (_: Exception) {
                try {
                    fallbackLocationCheck(context, settings, prefsRepository, notificationHelper)
                } finally {
                    finishOnce()
                }
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

                    val nowMs = System.currentTimeMillis()
                    val balanceStr = TuntivelhoRepository.calculateBalance(
                        clockInTs = settings.clockInTimestamp,
                        currentTs = nowMs,
                        targetMinutesNeeded = settings.requiredElapsedMinutes(nowMs)
                    )

                    // The ticker now runs from clock-in so the target alert is not gated on
                    // the evening alarm. Hold back the "Kotiin?" notification until the
                    // evening time, or it would nag every minute of the workday.
                    if (alarmType == AlarmScheduler.EXTRA_TYPE_EVENING ||
                        NotificationFilterUtils.isAtOrAfterTimeOfDay(nowMs, settings.eveningReminderTime)
                    ) {
                        notificationHelper.showEveningNotification(
                            clockInTimeStr = clockInTimeStr,
                            balanceStr = balanceStr
                        )
                    }

                    // Target goal alert check in background ticker
                    val workedMinutes = settings.workedMinutesSinceClockIn(nowMs)
                    val targetMins = settings.currentTargetMinutesNeeded
                    if (targetMins > 0 && workedMinutes >= targetMins) {
                        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply { timeZone = helsinkiTz }.format(Date())
                        if (settings.lastTargetAlertDate != todayStr && (settings.targetSoundAlertEnabled || settings.targetVibrationAlertEnabled)) {
                            val modeText = if (settings.targetMode == "WEEKLY") "Työviikon" else "Työpäivän"
                            notificationHelper.showTargetReachedNotification(
                                title = "🎉 $modeText tavoite täynnä!",
                                message = "$modeText tavoite saavutettu. Saldo siirtyy plussalle (+ ylityöt)!",
                                playSound = settings.targetSoundAlertEnabled,
                                vibrate = settings.targetVibrationAlertEnabled
                            )
                            prefsRepository.updateLastTargetAlertDate(todayStr)
                        }
                    }

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

    private fun evaluateAndTriggerGeofence(
        loc: android.location.Location,
        settings: com.aistudio.tuntivelho.leimaus.data.preferences.AppSettings,
        prefsRepository: UserPreferencesRepository,
        notificationHelper: NotificationHelper
    ) {
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

    private fun fallbackLocationCheck(
        context: Context,
        settings: com.aistudio.tuntivelho.leimaus.data.preferences.AppSettings,
        prefsRepository: UserPreferencesRepository,
        notificationHelper: NotificationHelper
    ) {
        // Reached from async callbacks, where an uncaught SecurityException would
        // crash the process rather than surface as a failed broadcast
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
                ?: return
            var loc: android.location.Location? = null
            if (locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
                loc = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
            }
            if (loc == null && locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)) {
                loc = locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
            }
            if (loc != null) {
                evaluateAndTriggerGeofence(loc, settings, prefsRepository, notificationHelper)
            }
        } catch (_: SecurityException) {
            // Location permission not granted
        } catch (_: Exception) {
        }
    }
}

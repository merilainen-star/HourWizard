package com.numbawang.leimaus.alarm

import android.location.Location
import com.numbawang.leimaus.data.preferences.AppSettings
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

sealed class GeofenceResult {
    object TriggerArrival : GeofenceResult()
    object TriggerDeparture : GeofenceResult()
    data class NoAction(val reason: String) : GeofenceResult()
}

object LocationFilterUtils {

    fun calculateDistanceMeters(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double
    ): Float {
        val results = FloatArray(1)
        Location.distanceBetween(startLat, startLng, endLat, endLng, results)
        return results[0]
    }

    fun isTimeInWindow(currentTimeStr: String, windowStart: String, windowEnd: String): Boolean {
        try {
            val format = SimpleDateFormat("HH:mm", Locale.getDefault())
            val current = format.parse(currentTimeStr) ?: return false
            val start = format.parse(windowStart) ?: return false
            val end = format.parse(windowEnd) ?: return false

            if (start.before(end) || start == end) {
                return !current.before(start) && !current.after(end)
            } else {
                // Window crosses midnight
                return !current.before(start) || !current.after(end)
            }
        } catch (_: Exception) {
            return false
        }
    }

    fun evaluateGeofence(
        currentLat: Double,
        currentLng: Double,
        settings: AppSettings
    ): GeofenceResult {
        if (!settings.isGeofenceEnabled) {
            return GeofenceResult.NoAction("Sijaintimuistutukset eivät ole käytössä")
        }

        if (!NotificationFilterUtils.isNotificationAllowedForToday(settings)) {
            return GeofenceResult.NoAction("Tämä päivä ei ole aktiivinen työpäivä tai loma on käynnissä")
        }

        val helsinkiTz = TimeZone.getTimeZone("Europe/Helsinki")
        val now = Calendar.getInstance(helsinkiTz)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply { timeZone = helsinkiTz }
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault()).apply { timeZone = helsinkiTz }

        val todayDateStr = dateFormat.format(now.time)
        val currentTimeStr = timeFormat.format(now.time)

        val distance = calculateDistanceMeters(
            currentLat,
            currentLng,
            settings.workplaceLat,
            settings.workplaceLng
        )

        val isInsideRadius = distance <= settings.geofenceRadiusMeters

        // 1. Saapumistarkistus (Saapumisikkuna)
        val inArrivalWindow = isTimeInWindow(currentTimeStr, settings.arrivalWindowStart, settings.arrivalWindowEnd)
        if (inArrivalWindow && isInsideRadius) {
            if (settings.isClockedIn) {
                return GeofenceResult.NoAction("Olet jo kirjautunut sisään tänään")
            }
            if (settings.lastArrivalNotifiedDate == todayDateStr) {
                return GeofenceResult.NoAction("Saapumisilmoitus on jo annettu tänään")
            }
            return GeofenceResult.TriggerArrival
        }

        // 2. Poistumistarkistus (Poistumisikkuna)
        val inDepartureWindow = isTimeInWindow(currentTimeStr, settings.departureWindowStart, settings.departureWindowEnd)
        if (inDepartureWindow && !isInsideRadius) {
            if (!settings.isClockedIn) {
                return GeofenceResult.NoAction("Et ole kirjautuneena sisään")
            }
            if (settings.lastDepartureNotifiedDate == todayDateStr) {
                return GeofenceResult.NoAction("Poistumisilmoitus on jo annettu tänään")
            }
            return GeofenceResult.TriggerDeparture
        }

        if (!inArrivalWindow && !inDepartureWindow) {
            return GeofenceResult.NoAction("Keskipäivän aika (Lounas/Asiakaskäynnit): Ilmoituksia ei lähetetä")
        }

        return GeofenceResult.NoAction("Et ole kriteerien piirissä (Etäisyys: ${distance.toInt()}m)")
    }
}

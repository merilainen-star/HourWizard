package com.numbawang.leimaus.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.numbawang.leimaus.data.security.CryptoManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val DEFAULT_URL = "https://app.tuntivelho.com/mobiili/backend/public/graphql"
const val FALLBACK_GRAPHQL_URL = "https://app.tuntivelho.com/tvv-mobile/backend/public/graphql"

data class AppSettings(
    val username: String = "",
    val morningReminderTime: String = "07:00",
    val eveningReminderTime: String = "16:00",
    val workdayHours: Int = 7,
    val workdayMinutes: Int = 30,
    val lunchBreakMinutes: Int = 30,
    val serverUrl: String = DEFAULT_URL,
    val isDemoMode: Boolean = false,
    val isClockedIn: Boolean = false,
    val clockInTimestamp: Long = 0L,
    val isOnBreak: Boolean = false,
    /** When the currently running break started; 0 when not on a break. */
    val breakStartTimestamp: Long = 0L,
    /** Milliseconds of completed breaks during the current clock-in session. */
    val completedBreakMillis: Long = 0L,
    val lastServerBalance: String = "",
    val enabledDaysString: String = "1,2,3,4,5",
    val isVacationEnabled: Boolean = false,
    val vacationStart: String = "",
    val vacationEnd: String = "",
    val appTheme: String = "system",
    val isGeofenceEnabled: Boolean = false,
    val workplaceLat: Double = 60.1699,
    val workplaceLng: Double = 24.9384,
    val geofenceRadiusMeters: Int = 150,
    val arrivalWindowStart: String = "06:30",
    val arrivalWindowEnd: String = "10:30",
    val departureWindowStart: String = "14:30",
    val departureWindowEnd: String = "18:30",
    val lastArrivalNotifiedDate: String = "",
    val lastDepartureNotifiedDate: String = "",
    val targetMode: String = "DAILY", // "DAILY" or "WEEKLY"
    val targetHoursDaily: Double = 7.5,
    val targetHoursWeekly: Double = 37.5,
    val targetSoundAlertEnabled: Boolean = true,
    val targetVibrationAlertEnabled: Boolean = true,
    val lastTargetAlertDate: String = ""
) {
    val totalWorkdayMinutesNeeded: Int
        get() = (workdayHours * 60) + workdayMinutes + lunchBreakMinutes

    /**
     * Tuntivelho always charges at least [lunchBreakMinutes] for a break. Stamping a
     * shorter break still costs the full minimum; a longer one costs its real length
     * and pushes the end of the workday out by the excess. Never stamping a break is
     * just the zero-minute case — the minimum is deducted automatically.
     */
    fun breakDeductionMinutes(now: Long): Int {
        val runningMillis = if (isOnBreak && breakStartTimestamp > 0L) {
            (now - breakStartTimestamp).coerceAtLeast(0L)
        } else 0L
        val actualMinutes = ((completedBreakMillis + runningMillis) / 60_000L).toInt()
        return maxOf(lunchBreakMinutes, actualMinutes)
    }

    /** Wall-clock minutes that must elapse since clock-in to complete the workday. */
    fun requiredElapsedMinutes(now: Long): Int =
        (workdayHours * 60) + workdayMinutes + breakDeductionMinutes(now)

    /** Minutes actually worked since clock-in, with the break deduction applied. */
    fun workedMinutesSinceClockIn(now: Long): Int {
        if (!isClockedIn || clockInTimestamp <= 0L) return 0
        val elapsed = ((now - clockInTimestamp) / 60_000L).toInt()
        return (elapsed - breakDeductionMinutes(now)).coerceAtLeast(0)
    }

    val currentTargetMinutesNeeded: Int
        get() = if (targetMode == "WEEKLY") (targetHoursWeekly * 60).toInt() else (targetHoursDaily * 60).toInt()
}

class UserPreferencesRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val cryptoManager = CryptoManager()

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun loadSettings(): AppSettings {
        val username = prefs.getString(KEY_USERNAME, "") ?: ""
        val morning = prefs.getString(KEY_MORNING_TIME, "07:00") ?: "07:00"
        val evening = prefs.getString(KEY_EVENING_TIME, "16:00") ?: "16:00"
        val hours = prefs.getInt(KEY_WORKDAY_HOURS, 7)
        val minutes = prefs.getInt(KEY_WORKDAY_MINUTES, 30)
        val lunch = prefs.getInt(KEY_LUNCH_MINUTES, 30)
        var url = prefs.getString(KEY_SERVER_URL, DEFAULT_URL) ?: DEFAULT_URL
        if (url.isBlank() || url == "https://www.tuntivelho.fi" || url == "https://app.tuntivelho.com/") {
            url = DEFAULT_URL
        }
        val isDemo = prefs.getBoolean(KEY_IS_DEMO, username.isBlank())
        val isClockedIn = prefs.getBoolean(KEY_IS_CLOCKED_IN, false)
        val clockInTs = prefs.getLong(KEY_CLOCK_IN_TS, 0L)
        val isOnBreak = prefs.getBoolean(KEY_IS_ON_BREAK, false)
        val breakStartTs = prefs.getLong(KEY_BREAK_START_TS, 0L)
        val completedBreakMs = prefs.getLong(KEY_COMPLETED_BREAK_MS, 0L)
        val lastBalance = prefs.getString(KEY_SERVER_BALANCE, "") ?: ""
        val enabledDays = prefs.getString(KEY_ENABLED_DAYS, "1,2,3,4,5") ?: "1,2,3,4,5"
        val isVacation = prefs.getBoolean(KEY_IS_VACATION, false)
        val vStart = prefs.getString(KEY_VACATION_START, "") ?: ""
        val vEnd = prefs.getString(KEY_VACATION_END, "") ?: ""
        val theme = prefs.getString(KEY_APP_THEME, "system") ?: "system"

        val isGeofence = prefs.getBoolean(KEY_IS_GEOFENCE_ENABLED, false)
        val wLat = prefs.getFloat(KEY_WORKPLACE_LAT, 60.1699f).toDouble()
        val wLng = prefs.getFloat(KEY_WORKPLACE_LNG, 24.9384f).toDouble()
        val radius = prefs.getInt(KEY_GEOFENCE_RADIUS, 150)
        val arrStart = prefs.getString(KEY_ARRIVAL_START, "06:30") ?: "06:30"
        val arrEnd = prefs.getString(KEY_ARRIVAL_END, "10:30") ?: "10:30"
        val depStart = prefs.getString(KEY_DEPARTURE_START, "14:30") ?: "14:30"
        val depEnd = prefs.getString(KEY_DEPARTURE_END, "18:30") ?: "18:30"
        val lastArrNotif = prefs.getString(KEY_LAST_ARR_NOTIF, "") ?: ""
        val lastDepNotif = prefs.getString(KEY_LAST_DEP_NOTIF, "") ?: ""

        val targetMode = prefs.getString(KEY_TARGET_MODE, "DAILY") ?: "DAILY"
        val targetDaily = prefs.getFloat(KEY_TARGET_DAILY, 7.5f).toDouble()
        val targetWeekly = prefs.getFloat(KEY_TARGET_WEEKLY, 37.5f).toDouble()
        val targetSound = prefs.getBoolean(KEY_TARGET_SOUND, true)
        val targetVib = prefs.getBoolean(KEY_TARGET_VIB, true)
        val lastTargetAlert = prefs.getString(KEY_LAST_TARGET_ALERT, "") ?: ""

        return AppSettings(
            username = username,
            morningReminderTime = morning,
            eveningReminderTime = evening,
            workdayHours = hours,
            workdayMinutes = minutes,
            lunchBreakMinutes = lunch,
            serverUrl = url,
            isDemoMode = isDemo,
            isClockedIn = isClockedIn,
            clockInTimestamp = clockInTs,
            isOnBreak = isOnBreak,
            breakStartTimestamp = breakStartTs,
            completedBreakMillis = completedBreakMs,
            lastServerBalance = lastBalance,
            enabledDaysString = enabledDays,
            isVacationEnabled = isVacation,
            vacationStart = vStart,
            vacationEnd = vEnd,
            appTheme = theme,
            isGeofenceEnabled = isGeofence,
            workplaceLat = wLat,
            workplaceLng = wLng,
            geofenceRadiusMeters = radius,
            arrivalWindowStart = arrStart,
            arrivalWindowEnd = arrEnd,
            departureWindowStart = depStart,
            departureWindowEnd = depEnd,
            lastArrivalNotifiedDate = lastArrNotif,
            lastDepartureNotifiedDate = lastDepNotif,
            targetMode = targetMode,
            targetHoursDaily = targetDaily,
            targetHoursWeekly = targetWeekly,
            targetSoundAlertEnabled = targetSound,
            targetVibrationAlertEnabled = targetVib,
            lastTargetAlertDate = lastTargetAlert
        )
    }

    fun getPassword(): String {
        val encrypted = prefs.getString(KEY_ENCRYPTED_PASSWORD, "") ?: ""
        return if (encrypted.isNotBlank()) {
            cryptoManager.decryptString(encrypted)
        } else {
            ""
        }
    }

    fun getAuthToken(): String {
        val encrypted = prefs.getString(KEY_AUTH_TOKEN, "") ?: ""
        return if (encrypted.isNotBlank()) {
            try {
                cryptoManager.decryptString(encrypted)
            } catch (e: Exception) {
                ""
            }
        } else {
            ""
        }
    }

    fun saveAuthToken(token: String) {
        val encrypted = if (token.isNotBlank()) {
            cryptoManager.encryptString(token)
        } else {
            ""
        }
        prefs.edit()
            .putString(KEY_AUTH_TOKEN, encrypted)
            .putLong(KEY_TOKEN_EXPIRY, System.currentTimeMillis() + 86400000L)
            .apply()
    }

    fun clearAuthToken() {
        prefs.edit()
            .remove(KEY_AUTH_TOKEN)
            .remove(KEY_TOKEN_EXPIRY)
            .apply()
    }

    fun isTokenExpired(): Boolean {
        val expiry = prefs.getLong(KEY_TOKEN_EXPIRY, 0L)
        return expiry <= System.currentTimeMillis()
    }

    fun saveCredentials(username: String, passwordText: String) {
        val encrypted = if (passwordText.isNotBlank()) {
            cryptoManager.encryptString(passwordText)
        } else {
            ""
        }
        prefs.edit()
            .putString(KEY_USERNAME, username)
            .putString(KEY_ENCRYPTED_PASSWORD, encrypted)
            .putBoolean(KEY_IS_DEMO, username.isBlank() || passwordText.isBlank())
            .apply()

        _settings.value = loadSettings()
    }

    fun saveReminderSettings(
        morning: String,
        evening: String,
        hours: Int,
        minutes: Int,
        lunch: Int,
        url: String,
        isDemo: Boolean,
        enabledDays: String = "1,2,3,4,5",
        isVacation: Boolean = false,
        vacationStart: String = "",
        vacationEnd: String = "",
        appTheme: String = "system"
    ) {
        prefs.edit()
            .putString(KEY_MORNING_TIME, morning)
            .putString(KEY_EVENING_TIME, evening)
            .putInt(KEY_WORKDAY_HOURS, hours)
            .putInt(KEY_WORKDAY_MINUTES, minutes)
            .putInt(KEY_LUNCH_MINUTES, lunch)
            .putString(KEY_SERVER_URL, url)
            .putBoolean(KEY_IS_DEMO, isDemo)
            .putString(KEY_ENABLED_DAYS, enabledDays)
            .putBoolean(KEY_IS_VACATION, isVacation)
            .putString(KEY_VACATION_START, vacationStart)
            .putString(KEY_VACATION_END, vacationEnd)
            .putString(KEY_APP_THEME, appTheme)
            .apply()

        _settings.value = loadSettings()
    }

    fun saveGeofenceSettings(
        isGeofenceEnabled: Boolean,
        lat: Double,
        lng: Double,
        radiusMeters: Int,
        arrStart: String,
        arrEnd: String,
        depStart: String,
        depEnd: String
    ) {
        prefs.edit()
            .putBoolean(KEY_IS_GEOFENCE_ENABLED, isGeofenceEnabled)
            .putFloat(KEY_WORKPLACE_LAT, lat.toFloat())
            .putFloat(KEY_WORKPLACE_LNG, lng.toFloat())
            .putInt(KEY_GEOFENCE_RADIUS, radiusMeters)
            .putString(KEY_ARRIVAL_START, arrStart)
            .putString(KEY_ARRIVAL_END, arrEnd)
            .putString(KEY_DEPARTURE_START, depStart)
            .putString(KEY_DEPARTURE_END, depEnd)
            .apply()

        _settings.value = loadSettings()
    }

    fun updateGeofenceNotificationDates(arrivalDate: String? = null, departureDate: String? = null) {
        val editor = prefs.edit()
        arrivalDate?.let { editor.putString(KEY_LAST_ARR_NOTIF, it) }
        departureDate?.let { editor.putString(KEY_LAST_DEP_NOTIF, it) }
        editor.apply()

        _settings.value = loadSettings()
    }

    fun updateClockInStatus(isClockedIn: Boolean, clockInTs: Long) {
        prefs.edit()
            .putBoolean(KEY_IS_CLOCKED_IN, isClockedIn)
            .putLong(KEY_CLOCK_IN_TS, clockInTs)
            // A fresh in/out stamp always ends any running break and starts a new
            // session, so accumulated break time resets with it
            .putBoolean(KEY_IS_ON_BREAK, false)
            .putLong(KEY_BREAK_START_TS, 0L)
            .putLong(KEY_COMPLETED_BREAK_MS, 0L)
            .apply()

        _settings.value = loadSettings()
    }

    fun startBreak(startTs: Long) {
        prefs.edit()
            .putBoolean(KEY_IS_ON_BREAK, true)
            .putLong(KEY_BREAK_START_TS, startTs)
            .apply()

        _settings.value = loadSettings()
    }

    fun endBreak(endTs: Long) {
        val startTs = prefs.getLong(KEY_BREAK_START_TS, 0L)
        val elapsed = if (startTs > 0L) (endTs - startTs).coerceAtLeast(0L) else 0L
        val accumulated = prefs.getLong(KEY_COMPLETED_BREAK_MS, 0L) + elapsed

        prefs.edit()
            .putBoolean(KEY_IS_ON_BREAK, false)
            .putLong(KEY_BREAK_START_TS, 0L)
            .putLong(KEY_COMPLETED_BREAK_MS, accumulated)
            .apply()

        _settings.value = loadSettings()
    }

    fun saveSelectionDefaults(talaatuid: Int, tyopisteid: Int) {
        prefs.edit()
            .putInt(KEY_TALAATUID, talaatuid)
            .putInt(KEY_TYOPISTEID, tyopisteid)
            .apply()
    }

    fun saveServerBalance(balanceStr: String) {
        if (balanceStr.isNotBlank()) {
            prefs.edit()
                .putString(KEY_SERVER_BALANCE, balanceStr)
                .apply()
            _settings.value = loadSettings()
        }
    }

    fun saveTargetSettings(
        targetMode: String,
        targetHoursDaily: Double,
        targetHoursWeekly: Double,
        targetSoundAlertEnabled: Boolean,
        targetVibrationAlertEnabled: Boolean
    ) {
        prefs.edit()
            .putString(KEY_TARGET_MODE, targetMode)
            .putFloat(KEY_TARGET_DAILY, targetHoursDaily.toFloat())
            .putFloat(KEY_TARGET_WEEKLY, targetHoursWeekly.toFloat())
            .putBoolean(KEY_TARGET_SOUND, targetSoundAlertEnabled)
            .putBoolean(KEY_TARGET_VIB, targetVibrationAlertEnabled)
            .apply()

        _settings.value = loadSettings()
    }

    fun updateLastTargetAlertDate(dateStr: String) {
        prefs.edit()
            .putString(KEY_LAST_TARGET_ALERT, dateStr)
            .apply()

        _settings.value = loadSettings()
    }

    /**
     * The published version the user has already been notified about. Not part of [AppSettings]:
     * nothing in the UI renders it, and putting it there would rebuild the settings flow from a
     * background broadcast for no observer.
     */
    fun getLastNotifiedUpdateVersion(): String = prefs.getString(KEY_LAST_NOTIFIED_UPDATE, "") ?: ""

    fun updateLastNotifiedUpdateVersion(versionName: String) {
        prefs.edit()
            .putString(KEY_LAST_NOTIFIED_UPDATE, versionName)
            .apply()
    }

    fun getTalaatuid(): Int = prefs.getInt(KEY_TALAATUID, 1)
    fun getTyopisteid(): Int = prefs.getInt(KEY_TYOPISTEID, 5)

    companion object {
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_TOKEN_EXPIRY = "token_expiry"
        private const val PREFS_NAME = "tuntivelho_prefs" // Legacy PREFS_NAME retained to preserve saved credentials
        private const val KEY_USERNAME = "username"
        private const val KEY_ENCRYPTED_PASSWORD = "encrypted_password"
        private const val KEY_MORNING_TIME = "morning_time"
        private const val KEY_EVENING_TIME = "evening_time"
        private const val KEY_WORKDAY_HOURS = "workday_hours"
        private const val KEY_WORKDAY_MINUTES = "workday_minutes"
        private const val KEY_LUNCH_MINUTES = "lunch_minutes"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_IS_DEMO = "is_demo"
        private const val KEY_IS_CLOCKED_IN = "is_clocked_in"
        private const val KEY_CLOCK_IN_TS = "clock_in_ts"
        private const val KEY_IS_ON_BREAK = "is_on_break"
        private const val KEY_BREAK_START_TS = "break_start_ts"
        private const val KEY_COMPLETED_BREAK_MS = "completed_break_ms"
        private const val KEY_SERVER_BALANCE = "server_balance"
        private const val KEY_TALAATUID = "selection_talaatuid"
        private const val KEY_TYOPISTEID = "selection_tyopisteid"
        private const val KEY_ENABLED_DAYS = "enabled_days"
        private const val KEY_IS_VACATION = "is_vacation"
        private const val KEY_VACATION_START = "vacation_start"
        private const val KEY_VACATION_END = "vacation_end"
        private const val KEY_APP_THEME = "app_theme"
        private const val KEY_IS_GEOFENCE_ENABLED = "is_geofence_enabled"
        private const val KEY_WORKPLACE_LAT = "workplace_lat"
        private const val KEY_WORKPLACE_LNG = "workplace_lng"
        private const val KEY_GEOFENCE_RADIUS = "geofence_radius"
        private const val KEY_ARRIVAL_START = "arrival_start"
        private const val KEY_ARRIVAL_END = "arrival_end"
        private const val KEY_DEPARTURE_START = "departure_start"
        private const val KEY_DEPARTURE_END = "departure_end"
        private const val KEY_LAST_ARR_NOTIF = "last_arr_notif"
        private const val KEY_LAST_DEP_NOTIF = "last_dep_notif"
        private const val KEY_TARGET_MODE = "target_mode"
        private const val KEY_TARGET_DAILY = "target_daily"
        private const val KEY_TARGET_WEEKLY = "target_weekly"
        private const val KEY_TARGET_SOUND = "target_sound"
        private const val KEY_TARGET_VIB = "target_vib"
        private const val KEY_LAST_TARGET_ALERT = "last_target_alert"
        private const val KEY_LAST_NOTIFIED_UPDATE = "last_notified_update_version"
    }
}

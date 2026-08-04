package com.aistudio.tuntivelho.leimaus.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.tuntivelho.leimaus.alarm.AlarmScheduler
import com.aistudio.tuntivelho.leimaus.alarm.NotificationHelper
import com.aistudio.tuntivelho.leimaus.data.db.AppDatabase
import com.aistudio.tuntivelho.leimaus.data.db.StampEntity
import com.aistudio.tuntivelho.leimaus.data.network.StampResult
import com.aistudio.tuntivelho.leimaus.data.network.TuntivelhoRepository
import com.aistudio.tuntivelho.leimaus.data.preferences.AppSettings
import com.aistudio.tuntivelho.leimaus.data.preferences.UserPreferencesRepository
import com.aistudio.tuntivelho.leimaus.util.BackupUtils
import com.aistudio.tuntivelho.leimaus.util.LocationUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class UiMessage(val text: String, val isError: Boolean)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepository = UserPreferencesRepository(application)
    private val db = AppDatabase.getInstance(application)
    private val repository = TuntivelhoRepository(prefsRepository, db.stampDao())
    private val alarmScheduler = AlarmScheduler(application)
    private val notificationHelper = NotificationHelper(application)

    val settings: StateFlow<AppSettings> = prefsRepository.settings

    val logs: StateFlow<List<StampEntity>> = db.stampDao().getAllLogs()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _uiMessage = MutableStateFlow<UiMessage?>(null)
    val uiMessage: StateFlow<UiMessage?> = _uiMessage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _currentBalanceStr = MutableStateFlow("0:00")
    val currentBalanceStr: StateFlow<String> = _currentBalanceStr.asStateFlow()

    val rawApiResponse: StateFlow<String> = repository.rawApiResponse.asStateFlow()

    init {
        // Schedule alarms on launch
        val currentSettings = prefsRepository.loadSettings()
        alarmScheduler.scheduleAlarms(
            currentSettings.morningReminderTime,
            currentSettings.eveningReminderTime
        )

        // Fetch latest server balance on launch
        refreshServerBalance()

        // Live balance ticker loop when clocked in
        viewModelScope.launch {
            while (true) {
                val s = prefsRepository.settings.value
                if (s.isClockedIn && s.clockInTimestamp > 0L) {
                    val now = System.currentTimeMillis()
                    _currentBalanceStr.value = TuntivelhoRepository.calculateBalance(
                        clockInTs = s.clockInTimestamp,
                        currentTs = now,
                        targetMinutesNeeded = s.requiredElapsedMinutes(now)
                    )

                    // Target goal alert check has been moved to AlarmReceiver background ticker
                } else {
                    _currentBalanceStr.value = "0:00"
                }
                delay(1000L)
            }
        }
    }

    fun handleGlobalError(errorMsg: String) {
        val userFriendlyMessage = when {
            errorMsg.contains("404") -> " Virhe HTTP 404: Palvelimen rajapintaa ei löytynyt. Tarkista palvelimen osoite asetuksista."
            errorMsg.contains("500") -> " Virhe HTTP 500: Palvelinvirhe. Tuntivelho-palvelimella tapahtui sisäinen virhe."
            errorMsg.contains("401") || errorMsg.contains("403") -> " Virhe HTTP ${if (errorMsg.contains("401")) "401" else "403"}: Autentikointivirhe. Tarkista käyttäjätunnus ja salasana."
            errorMsg.contains("Yhteysvirhe") || errorMsg.contains("Unable to resolve host") || errorMsg.contains("Failed to connect") -> " Verkkovirhe: Tarkista internetyhteys ja palvelimen osoite."
            else -> " Virhe: $errorMsg"
        }
        _uiMessage.value = UiMessage(userFriendlyMessage, isError = true)
    }

    fun refreshServerBalance() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.fetchServerBalanceResult()
            _isLoading.value = false
            val s = prefsRepository.settings.value
            when (result) {
                is StampResult.Success -> {
                    val balanceStr = result.message
                    val statusText = if (s.isClockedIn) "Sisäänleimattu" else "Ei aktiivista leimausta"
                    val msgText = if (balanceStr.isNotBlank()) "Tuntitase ($balanceStr) ja leimaustila ($statusText) päivitetty." else "Leimaustila ($statusText) päivitetty."
                    _uiMessage.value = UiMessage(msgText, isError = false)
                }
                is StampResult.Error -> {
                    handleGlobalError(result.errorMessage)
                }
            }
        }
    }

    fun getStoredPassword(): String {
        return prefsRepository.getPassword()
    }

    fun testLogin(usernameInput: String, passwordInput: String, serverUrlInput: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            val current = prefsRepository.settings.value
            val targetUrl = serverUrlInput ?: current.serverUrl
            prefsRepository.saveCredentials(usernameInput, passwordInput)
            prefsRepository.saveReminderSettings(
                morning = current.morningReminderTime,
                evening = current.eveningReminderTime,
                hours = current.workdayHours,
                minutes = current.workdayMinutes,
                lunch = current.lunchBreakMinutes,
                url = targetUrl,
                isDemo = false
            )

            val result = repository.testLogin(targetUrl)
            _isLoading.value = false

            when (result) {
                is StampResult.Success -> {
                    _uiMessage.value = UiMessage(result.message, isError = false)
                }
                is StampResult.Error -> {
                    handleGlobalError(result.errorMessage)
                }
            }
        }
    }

    fun saveFullSettings(
        username: String,
        passwordText: String,
        morning: String,
        evening: String,
        hours: Int,
        minutes: Int,
        lunch: Int,
        serverUrl: String,
        isDemo: Boolean,
        enabledDays: String = "1,2,3,4,5",
        isVacation: Boolean = false,
        vacationStart: String = "",
        vacationEnd: String = "",
        appTheme: String = "system"
    ) {
        prefsRepository.saveCredentials(username, passwordText)
        prefsRepository.saveReminderSettings(
            morning = morning,
            evening = evening,
            hours = hours,
            minutes = minutes,
            lunch = lunch,
            url = serverUrl,
            isDemo = isDemo,
            enabledDays = enabledDays,
            isVacation = isVacation,
            vacationStart = vacationStart,
            vacationEnd = vacationEnd,
            appTheme = appTheme
        )

        // Update alarms with new times
        alarmScheduler.scheduleAlarms(morning, evening)
        _uiMessage.value = UiMessage("Asetukset tallennettu ja muistutukset päivitetty!", isError = false)
    }

    fun clockIn() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.clockIn()
            _isLoading.value = false

            when (result) {
                is StampResult.Success -> {
                    notificationHelper.cancelNotification()
                    val s = settings.value
                    alarmScheduler.scheduleAlarms(s.morningReminderTime, s.eveningReminderTime)
                    _uiMessage.value = UiMessage(result.message, isError = false)
                }
                is StampResult.Error -> {
                    handleGlobalError(result.errorMessage)
                }
            }
        }
    }

    fun clockOut() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.clockOut()
            _isLoading.value = false

            when (result) {
                is StampResult.Success -> {
                    notificationHelper.cancelNotification()
                    alarmScheduler.cancelEveningTicker()
                    _uiMessage.value = UiMessage(result.message, isError = false)
                }
                is StampResult.Error -> {
                    handleGlobalError(result.errorMessage)
                }
            }
        }
    }

    fun startBreak() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.breakStart()
            _isLoading.value = false

            when (result) {
                is StampResult.Success -> {
                    _uiMessage.value = UiMessage(result.message, isError = false)
                }
                is StampResult.Error -> {
                    handleGlobalError(result.errorMessage)
                }
            }
        }
    }

    fun endBreak() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.breakEnd()
            _isLoading.value = false

            when (result) {
                is StampResult.Success -> {
                    _uiMessage.value = UiMessage(result.message, isError = false)
                }
                is StampResult.Error -> {
                    handleGlobalError(result.errorMessage)
                }
            }
        }
    }

    fun triggerTestMorningNotification() {
        notificationHelper.showMorningNotification()
        _uiMessage.value = UiMessage("Aamumuistutusilmoitus lähetetty laitteelle!", isError = false)
    }

    fun triggerTestEveningNotification() {
        val s = settings.value
        val clockInTimeStr = if (s.clockInTimestamp > 0L) {
            val helsinkiTz = java.util.TimeZone.getTimeZone("Europe/Helsinki")
            SimpleDateFormat("HH.mm", Locale.getDefault()).apply { timeZone = helsinkiTz }.format(Date(s.clockInTimestamp))
        } else {
            "07.44"
        }
        val bal = if (s.clockInTimestamp > 0L) {
            currentBalanceStr.value
        } else {
            "−0:27"
        }

        notificationHelper.showEveningNotification(clockInTimeStr, bal)
        _uiMessage.value = UiMessage("Iltaleimausilmoitus lähetetty laitteelle!", isError = false)
    }

    fun saveGeofenceSettings(
        isEnabled: Boolean,
        lat: Double,
        lng: Double,
        radius: Int,
        arrStart: String,
        arrEnd: String,
        depStart: String,
        depEnd: String
    ) {
        prefsRepository.saveGeofenceSettings(
            isGeofenceEnabled = isEnabled,
            lat = lat,
            lng = lng,
            radiusMeters = radius,
            arrStart = arrStart,
            arrEnd = arrEnd,
            depStart = depStart,
            depEnd = depEnd
        )
        _uiMessage.value = UiMessage("Työpaikan sijainti- ja aikaraja-asetukset tallennettu!", isError = false)
    }

    fun fetchCurrentLocation(context: Context, onLocationResult: (Double, Double) -> Unit) {
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
                    onLocationResult(loc.latitude, loc.longitude)
                    _uiMessage.value = UiMessage("Sijainti haettu: ${String.format(Locale.US, "%.4f, %.4f", loc.latitude, loc.longitude)}", isError = false)
                } else {
                    _uiMessage.value = UiMessage("Sijaintia ei voitu hakea. Varmista että GPS on päällä.", isError = true)
                }
            }
        } catch (e: SecurityException) {
            _uiMessage.value = UiMessage("Sijaintilupaa ei ole myönnetty.", isError = true)
        } catch (e: Exception) {
            _uiMessage.value = UiMessage("Virhe sijaintia haettaessa.", isError = true)
        }
    }

    fun searchAddressAndSetWorkplace(
        context: Context,
        addressQuery: String,
        onResult: (Double, Double, String) -> Unit
    ) {
        viewModelScope.launch {
            _uiMessage.value = UiMessage("Etsitään osoitetta...", isError = false)
            val result = LocationUtils.searchAddress(context, addressQuery)
            if (result != null) {
                onResult(result.latitude, result.longitude, result.formattedAddress)
                val curr = settings.value
                prefsRepository.saveGeofenceSettings(
                    isGeofenceEnabled = true,
                    lat = result.latitude,
                    lng = result.longitude,
                    radiusMeters = curr.geofenceRadiusMeters,
                    arrStart = curr.arrivalWindowStart,
                    arrEnd = curr.arrivalWindowEnd,
                    depStart = curr.departureWindowStart,
                    depEnd = curr.departureWindowEnd
                )
                _uiMessage.value = UiMessage("📍 Sijainti asetettu osoitteesta: ${result.formattedAddress}", isError = false)
            } else {
                _uiMessage.value = UiMessage("Osoitetta ei löytynyt. Tarkista kirjoitusasu tai syötä koordinaatit.", isError = true)
            }
        }
    }

    fun processSharedLocationText(context: Context, sharedText: String) {
        viewModelScope.launch {
            _uiMessage.value = UiMessage("Käsitellään Google Maps -jakoa...", isError = false)
            val result = LocationUtils.searchAddress(context, sharedText)
            if (result != null) {
                val curr = settings.value
                prefsRepository.saveGeofenceSettings(
                    isGeofenceEnabled = true,
                    lat = result.latitude,
                    lng = result.longitude,
                    radiusMeters = curr.geofenceRadiusMeters,
                    arrStart = curr.arrivalWindowStart,
                    arrEnd = curr.arrivalWindowEnd,
                    depStart = curr.departureWindowStart,
                    depEnd = curr.departureWindowEnd
                )
                _uiMessage.value = UiMessage("📍 Työpaikan sijainti asetettu Google Mapsista!\n${result.formattedAddress}", isError = false)
            } else {
                _uiMessage.value = UiMessage("Jaettua sijaintia ei pystytty tunnistamaan.", isError = true)
            }
        }
    }

    fun triggerTestLocationArrivalNotification() {
        notificationHelper.showLocationArrivalNotification()
        _uiMessage.value = UiMessage("Sijainti-ilmoitus: Saapuminen lähetetty!", isError = false)
    }

    fun triggerTestLocationDepartureNotification() {
        notificationHelper.showLocationDepartureNotification()
        _uiMessage.value = UiMessage("Sijainti-ilmoitus: Poistuminen lähetetty!", isError = false)
    }

    fun clearUiMessage() {
        _uiMessage.value = null
    }

    fun clearHistory() {
        viewModelScope.launch {
            db.stampDao().clearAllLogs()
            _uiMessage.value = UiMessage("Leimaushistoria tyhjennetty.", isError = false)
        }
    }

    fun exportBackupToUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val logs = db.stampDao().getAllLogsList()
                val jsonString = BackupUtils.exportBackupJson(logs, settings.value)
                val success = BackupUtils.writeToUri(context, uri, jsonString)
                if (success) {
                    _uiMessage.value = UiMessage("💾 Varmuuskopio tallennettu onnistuneesti! (${logs.size} merkintää)", isError = false)
                } else {
                    _uiMessage.value = UiMessage("Varmuuskopion tallennus epäonnistui.", isError = true)
                }
            } catch (e: Exception) {
                _uiMessage.value = UiMessage("Virhe varmuuskopiota luotaessa: ${e.message}", isError = true)
            }
        }
    }

    fun importBackupFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val jsonString = BackupUtils.readFromUri(context, uri)
                if (jsonString.isNullOrBlank()) {
                    _uiMessage.value = UiMessage("Tiedoston lukeminen epäonnistui.", isError = true)
                    return@launch
                }

                val backupData = BackupUtils.importBackupJson(jsonString)
                if (backupData.logs.isNotEmpty()) {
                    db.stampDao().insertAll(backupData.logs)
                }

                backupData.settings?.let { s ->
                    if (s.username.isNotBlank()) {
                        prefsRepository.saveCredentials(s.username, "")
                    }
                    prefsRepository.saveReminderSettings(
                        morning = s.morningReminderTime,
                        evening = s.eveningReminderTime,
                        hours = s.workdayHours,
                        minutes = s.workdayMinutes,
                        lunch = s.lunchBreakMinutes,
                        url = s.serverUrl,
                        isDemo = s.isDemoMode,
                        enabledDays = s.enabledDaysString,
                        isVacation = s.isVacationEnabled,
                        vacationStart = s.vacationStart,
                        vacationEnd = s.vacationEnd,
                        appTheme = s.appTheme
                    )
                    prefsRepository.saveGeofenceSettings(
                        isGeofenceEnabled = s.isGeofenceEnabled,
                        lat = s.workplaceLat,
                        lng = s.workplaceLng,
                        radiusMeters = s.geofenceRadiusMeters,
                        arrStart = s.arrivalWindowStart,
                        arrEnd = s.arrivalWindowEnd,
                        depStart = s.departureWindowStart,
                        depEnd = s.departureWindowEnd
                    )
                }

                _uiMessage.value = UiMessage(
                    "✅ Varmuuskopio palautettu! Palautettiin ${backupData.logs.size} leimausmerkintää ja asetukset.",
                    isError = false
                )
            } catch (e: Exception) {
                _uiMessage.value = UiMessage("Virhe varmuuskopiota palautettaessa. Varmista että tiedosto on vaaditussa formaatissa.", isError = true)
            }
        }
    }

    fun saveTargetSettings(
        targetMode: String,
        targetHoursDaily: Double,
        targetHoursWeekly: Double,
        targetSoundAlertEnabled: Boolean,
        targetVibrationAlertEnabled: Boolean
    ) {
        prefsRepository.saveTargetSettings(
            targetMode = targetMode,
            targetHoursDaily = targetHoursDaily,
            targetHoursWeekly = targetHoursWeekly,
            targetSoundAlertEnabled = targetSoundAlertEnabled,
            targetVibrationAlertEnabled = targetVibrationAlertEnabled
        )
        _uiMessage.value = UiMessage("Tavoitetunnit ja hälytykset tallennettu.", isError = false)
    }
}

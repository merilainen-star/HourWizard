package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ui.MainViewModel
import com.example.ui.components.TimePickerDialog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var username by remember { mutableStateOf(settings.username) }
    var passwordText by remember { mutableStateOf(viewModel.getStoredPassword()) }
    var passwordVisible by remember { mutableStateOf(false) }

    var morningTime by remember { mutableStateOf(settings.morningReminderTime) }
    var eveningTime by remember { mutableStateOf(settings.eveningReminderTime) }

    var workdayHours by remember { mutableStateOf(settings.workdayHours.toString()) }
    var workdayMinutes by remember { mutableStateOf(settings.workdayMinutes.toString()) }
    var lunchMinutes by remember { mutableStateOf(settings.lunchBreakMinutes.toString()) }

    var serverUrl by remember { mutableStateOf(settings.serverUrl) }
    var isDemoMode by remember { mutableStateOf(settings.isDemoMode) }

    var enabledDaysString by remember { mutableStateOf(settings.enabledDaysString) }
    var isVacationEnabled by remember { mutableStateOf(settings.isVacationEnabled) }
    var vacationStart by remember { mutableStateOf(settings.vacationStart) }
    var vacationEnd by remember { mutableStateOf(settings.vacationEnd) }
    var appTheme by remember { mutableStateOf(settings.appTheme) }

    var isGeofenceEnabled by remember { mutableStateOf(settings.isGeofenceEnabled) }
    var workplaceLat by remember { mutableStateOf(settings.workplaceLat.toString()) }
    var workplaceLng by remember { mutableStateOf(settings.workplaceLng.toString()) }
    var geofenceRadius by remember { mutableStateOf(settings.geofenceRadiusMeters.toString()) }
    var arrivalWindowStart by remember { mutableStateOf(settings.arrivalWindowStart) }
    var arrivalWindowEnd by remember { mutableStateOf(settings.arrivalWindowEnd) }
    var departureWindowStart by remember { mutableStateOf(settings.departureWindowStart) }
    var departureWindowEnd by remember { mutableStateOf(settings.departureWindowEnd) }

    var targetMode by remember { mutableStateOf(settings.targetMode) }
    var targetDailyStr by remember { mutableStateOf(settings.targetHoursDaily.toString()) }
    var targetWeeklyStr by remember { mutableStateOf(settings.targetHoursWeekly.toString()) }
    var targetSoundEnabled by remember { mutableStateOf(settings.targetSoundAlertEnabled) }
    var targetVibEnabled by remember { mutableStateOf(settings.targetVibrationAlertEnabled) }

    var addressSearchQuery by remember { mutableStateOf("") }

    var showVacationAlert by remember { mutableStateOf(false) }

    var showMorningPicker by remember { mutableStateOf(false) }
    var showEveningPicker by remember { mutableStateOf(false) }

    var showArrPickerStart by remember { mutableStateOf(false) }
    var showArrPickerEnd by remember { mutableStateOf(false) }
    var showDepPickerStart by remember { mutableStateOf(false) }
    var showDepPickerEnd by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackupToUri(context, uri)
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importBackupFromUri(context, uri)
        }
    }

    fun openDatePicker(currentValue: String, onDateSelected: (String) -> Unit) {
        val helsinkiTz = TimeZone.getTimeZone("Europe/Helsinki")
        val cal = Calendar.getInstance(helsinkiTz)
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).apply { timeZone = helsinkiTz }
        if (currentValue.isNotBlank()) {
            try {
                val d = dateFormat.parse(currentValue.trim())
                if (d != null) cal.time = d
            } catch (_: Exception) {}
        }

        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                cal.set(year, month, dayOfMonth)
                onDateSelected(dateFormat.format(cal.time))
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    // Update local state when ViewModel settings change
    LaunchedEffect(settings) {
        username = settings.username
        passwordText = viewModel.getStoredPassword()
        morningTime = settings.morningReminderTime
        eveningTime = settings.eveningReminderTime
        workdayHours = settings.workdayHours.toString()
        workdayMinutes = settings.workdayMinutes.toString()
        lunchMinutes = settings.lunchBreakMinutes.toString()
        serverUrl = settings.serverUrl
        isDemoMode = settings.isDemoMode
        enabledDaysString = settings.enabledDaysString
        isVacationEnabled = settings.isVacationEnabled
        vacationStart = settings.vacationStart
        vacationEnd = settings.vacationEnd
        appTheme = settings.appTheme
        isGeofenceEnabled = settings.isGeofenceEnabled
        workplaceLat = settings.workplaceLat.toString()
        workplaceLng = settings.workplaceLng.toString()
        geofenceRadius = settings.geofenceRadiusMeters.toString()
        arrivalWindowStart = settings.arrivalWindowStart
        arrivalWindowEnd = settings.arrivalWindowEnd
        departureWindowStart = settings.departureWindowStart
        departureWindowEnd = settings.departureWindowEnd
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Credentials Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Kirjautumistiedot",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Salasana tallennetaan turvallisesti laitteen Android Keystore -salauksella.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Tuntivelhon käyttäjätunnus") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("username_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = passwordText,
                    onValueChange = { passwordText = it },
                    label = { Text("Salasana") },
                    leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) "Piilota salasana" else "Näytä salasana"
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("password_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Button(
                    onClick = {
                        viewModel.testLogin(username, passwordText, serverUrl)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("test_login_button"),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onSecondary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Testaa kirjautuminen")
                }
            }
        }

        // Theme Selection Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Teema & Ulkoasu",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Valitse sovelluksen teema (Vaalea, Tumma tai Järjestelmä) parantamaan luettavuutta vuorokaudenajan mukaan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val themeOptions = listOf(
                    Triple("system", "Järjestelmä", Icons.Default.PhoneAndroid),
                    Triple("light", "Vaalea", Icons.Default.LightMode),
                    Triple("dark", "Tumma", Icons.Default.DarkMode)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    themeOptions.forEach { (modeKey, label, icon) ->
                        val isSelected = appTheme == modeKey
                        Surface(
                            onClick = {
                                appTheme = modeKey
                                val h = workdayHours.toIntOrNull() ?: 7
                                val m = workdayMinutes.toIntOrNull() ?: 30
                                val l = lunchMinutes.toIntOrNull() ?: 30
                                viewModel.saveFullSettings(
                                    username = username,
                                    passwordText = passwordText,
                                    morning = morningTime,
                                    evening = eveningTime,
                                    hours = h,
                                    minutes = m,
                                    lunch = l,
                                    serverUrl = serverUrl,
                                    isDemo = isDemoMode,
                                    enabledDays = enabledDaysString,
                                    isVacation = isVacationEnabled,
                                    vacationStart = vacationStart,
                                    vacationEnd = vacationEnd,
                                    appTheme = modeKey
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .testTag("theme_option_$modeKey")
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(4.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }

        // Reminders & Target Times Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Muistutukset & Työaika",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { showMorningPicker = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Aamumuistutus", style = MaterialTheme.typography.labelSmall)
                            Text(
                                morningTime,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { showEveningPicker = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Iltamuistutus", style = MaterialTheme.typography.labelSmall)
                            Text(
                                eveningTime,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = workdayHours,
                        onValueChange = { workdayHours = it },
                        label = { Text("Työpäivä (h)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = workdayMinutes,
                        onValueChange = { workdayMinutes = it },
                        label = { Text("Minuutit (min)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                OutlinedTextField(
                    value = lunchMinutes,
                    onValueChange = { lunchMinutes = it },
                    label = { Text("Ruokatauko (min)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        // Target Goals & Overtime Alert Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Flag,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tavoitetunnit & Hälytykset",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Aseta päivittäinen tai viikoittainen tavoitetuntimäärä ja valitse saatko äänimerkin ja/tai värinähälytyksen kun tavoite täyttyy ja saldo alkaa kertyä plussalle.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Tavoitteen tyyppi",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilterChip(
                        selected = targetMode == "DAILY",
                        onClick = { targetMode = "DAILY" },
                        label = { Text("Päivittäinen tavoite") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = targetMode == "WEEKLY",
                        onClick = { targetMode = "WEEKLY" },
                        label = { Text("Viikoittainen tavoite") },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (targetMode == "DAILY") {
                    OutlinedTextField(
                        value = targetDailyStr,
                        onValueChange = { targetDailyStr = it },
                        label = { Text("Päivittäinen tavoite (h, esim. 7.5)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = { targetDailyStr = "7.5" }) { Text("7.5 h") }
                        OutlinedButton(onClick = { targetDailyStr = "8.0" }) { Text("8.0 h") }
                        OutlinedButton(onClick = { targetDailyStr = "7.0" }) { Text("7.0 h") }
                    }
                } else {
                    OutlinedTextField(
                        value = targetWeeklyStr,
                        onValueChange = { targetWeeklyStr = it },
                        label = { Text("Viikoittainen tavoite (h, esim. 37.5)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = { targetWeeklyStr = "37.5" }) { Text("37.5 h") }
                        OutlinedButton(onClick = { targetWeeklyStr = "40.0" }) { Text("40.0 h") }
                        OutlinedButton(onClick = { targetWeeklyStr = "35.0" }) { Text("35.0 h") }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Text(
                    text = "Hälytykset kun tavoite saavutettu (+ saldo)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Äänimerkki", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("Soita merkkiääni kun tavoite täyttyy", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(
                        checked = targetSoundEnabled,
                        onCheckedChange = { targetSoundEnabled = it }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Vibration,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Värinähälytys", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("Värise kun tavoite täyttyy", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(
                        checked = targetVibEnabled,
                        onCheckedChange = { targetVibEnabled = it }
                    )
                }
            }
        }

        // Weekdays & Vacation Settings Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Ilmoituspäivät & Loma-aika",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 1. Weekday selection
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Aktiiviset ilmoituspäivät",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Valitse viikonpäivät, jolloin leimausmuistutukset ovat käytössä.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val currentEnabledSet = remember(enabledDaysString) {
                        enabledDaysString.split(",")
                            .mapNotNull { it.trim().toIntOrNull() }
                            .toSet()
                    }

                    val weekdays = listOf(
                        1 to "Ma",
                        2 to "Ti",
                        3 to "Ke",
                        4 to "To",
                        5 to "Pe",
                        6 to "La",
                        7 to "Su"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        weekdays.forEach { (dayInt, label) ->
                            val isSelected = currentEnabledSet.contains(dayInt)
                            Surface(
                                onClick = {
                                    val newSet = if (isSelected) {
                                        currentEnabledSet - dayInt
                                    } else {
                                        currentEnabledSet + dayInt
                                    }
                                    enabledDaysString = newSet.sorted().joinToString(",")
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 2.dp)
                                    .height(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { enabledDaysString = "1,2,3,4,5" }) {
                            Text("Ma–Pe (Oletus)", style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = { enabledDaysString = "1,2,3,4,5,6,7" }) {
                            Text("Kaikki", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // 2. Vacation Period Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.BeachAccess,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Loma-aika",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Estä muistutukset automaattisesti loman ajaksi.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = isVacationEnabled,
                        onCheckedChange = { checked ->
                            isVacationEnabled = checked
                            if (checked) {
                                showVacationAlert = true
                            }
                        }
                    )
                }

                if (isVacationEnabled) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = vacationStart,
                                onValueChange = { vacationStart = it },
                                label = { Text("Loman alku") },
                                placeholder = { Text("pv.kk.vvvv") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                trailingIcon = {
                                    IconButton(onClick = {
                                        openDatePicker(vacationStart) { newDate ->
                                            vacationStart = newDate
                                        }
                                    }) {
                                        Icon(Icons.Default.CalendarToday, contentDescription = "Valitse alku-päivä")
                                    }
                                }
                            )

                            OutlinedTextField(
                                value = vacationEnd,
                                onValueChange = { vacationEnd = it },
                                label = { Text("Loman loppu") },
                                placeholder = { Text("pv.kk.vvvv") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                trailingIcon = {
                                    IconButton(onClick = {
                                        openDatePicker(vacationEnd) { newDate ->
                                            vacationEnd = newDate
                                        }
                                    }) {
                                        Icon(Icons.Default.CalendarToday, contentDescription = "Valitse loppu-päivä")
                                    }
                                }
                            )
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Muista laittaa loma myös Tuntivelhossa päälle! Lomat tulee kirjata manuaalisesti Tuntivelhon web-portaalissa.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }

        // Advanced & Server Settings Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Palvelin & Demotila",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("Tuntivelho Palvelin-URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Demotila (Testausta varten)",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Simuloi Tuntivelho-leimauksia ilman ulkoista verkko-yhteyttä.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = isDemoMode,
                        onCheckedChange = { isDemoMode = it }
                    )
                }
            }
        }

        // Geofence / Location Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Työpaikan sijainti & Geofence",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Saapumis- ja poistumismuistutukset",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isGeofenceEnabled,
                        onCheckedChange = { checked ->
                            isGeofenceEnabled = checked
                        }
                    )
                }

                if (isGeofenceEnabled) {
                    HorizontalDivider()

                    // Protection Info Shield Box
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Miten vääriä ilmoituksia vältetään?",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Lounas- ja asiakaskäyntejä varten muistutukset lähetetään VAIN valitsemiesi aikaikkunoiden (Saapumisikkuna & Poistumisikkuna) puitteissa. Keskellä päivää tapahtuvista poistumisista ei tule ilmoitusta.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Address Search Box
                    Text(
                        text = "1. Haku osoitteella",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = addressSearchQuery,
                        onValueChange = { addressSearchQuery = it },
                        label = { Text("Syötä työpaikan osoite (esim. Aleksanterinkatu 10, Helsinki)") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            if (addressSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { addressSearchQuery = "" }) {
                                    Icon(Icons.Default.VisibilityOff, contentDescription = "Tyhjennä")
                                }
                            }
                        }
                    )

                    Button(
                        onClick = {
                            if (addressSearchQuery.isNotBlank()) {
                                viewModel.searchAddressAndSetWorkplace(context, addressSearchQuery) { lat, lng, formatted ->
                                    workplaceLat = String.format(Locale.US, "%.5f", lat)
                                    workplaceLng = String.format(Locale.US, "%.5f", lng)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Etsi ja aseta osoite")
                    }

                    // Google Maps Sharing Tip Box
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "2. Jaa suoraan Google Mapsista",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Etsi työpaikka Google Mapsista -> Paina 'Jaa' (Share) -> Valitse sovelluslistalta 'Tuntivelho'. Sijainti asetetaan automaattisesti!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Text(
                        text = "3. Koordinaatit ja säde (Manuaalinen)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = workplaceLat,
                            onValueChange = { workplaceLat = it },
                            label = { Text("Leveysaste (Lat)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = workplaceLng,
                            onValueChange = { workplaceLng = it },
                            label = { Text("Pituusaste (Lng)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.fetchCurrentLocation(context) { lat, lng ->
                                workplaceLat = String.format(Locale.US, "%.5f", lat)
                                workplaceLng = String.format(Locale.US, "%.5f", lng)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Hae nykyinen sijainti GPS:llä")
                    }

                    // Presets
                    Text(
                        text = "Pikavalinnat:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "Helsinki" to (60.1699 to 24.9384),
                            "Tampere" to (61.4978 to 23.7610),
                            "Oulu" to (65.0121 to 25.4651),
                            "Turku" to (60.4518 to 22.2666)
                        ).forEach { (city, coords) ->
                            OutlinedButton(
                                onClick = {
                                    workplaceLat = coords.first.toString()
                                    workplaceLng = coords.second.toString()
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(city, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    // Geofence Radius
                    OutlinedTextField(
                        value = geofenceRadius,
                        onValueChange = { geofenceRadius = it.filter { c -> c.isDigit() } },
                        label = { Text("Geofence-säde (metreinä, esim. 150m)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    HorizontalDivider()

                    // Time Windows
                    Text(
                        text = "Saapumisikkuna (Aamu)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Saapumisilmoitus leimata SISÄÄN lähetetään vain jos saavut alueelle tällä aikavälillä eikä leimausta ole vielä tehty.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showArrPickerStart = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Alku: $arrivalWindowStart")
                        }
                        OutlinedButton(
                            onClick = { showArrPickerEnd = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Loppu: $arrivalWindowEnd")
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "Poistumisikkuna (Ilta)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Poistumisilmoitus leimata ULOS lähetetään vain jos poistut alueelta tällä aikavälillä ja olet kirjautuneena.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showDepPickerStart = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Alku: $departureWindowStart")
                        }
                        OutlinedButton(
                            onClick = { showDepPickerEnd = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Loppu: $departureWindowEnd")
                        }
                    }

                    // Test Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.triggerTestLocationArrivalNotification() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Testaa saapumista", style = MaterialTheme.typography.labelSmall)
                        }
                        Button(
                            onClick = { viewModel.triggerTestLocationDepartureNotification() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Testaa poistumista", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // Backup & Restore Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Backup,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Varmuuskopiointi & Palautus",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tallenna leimaustiedot ja asetukset uudelleenasennusta varten",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider()

                Text(
                    text = "Voit tallentaa kaikki leimausmerkinnät ja sovelluksen asetukset JSON-varmuuskopiotiedostoon laitteellesi tai pilvipalveluun. Voit palauttaa tiedot milloin tahansa uudelleenasennuksen jälkeen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val helsinkiTz = TimeZone.getTimeZone("Europe/Helsinki")
                            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply { timeZone = helsinkiTz }.format(Date())
                            createDocumentLauncher.launch("tuntivelho_varmuuskopio_$dateStr.json")
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Backup,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Luo varmuuskopio", style = MaterialTheme.typography.labelSmall)
                    }

                    OutlinedButton(
                        onClick = {
                            openDocumentLauncher.launch(arrayOf("application/json", "*/*"))
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Restore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Palauta tiedostosta", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Test Notifications Debug Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Testaa ilmoituksia (Debug)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Lähetä testi-ilmoitus laitteen ilmoituspalkkiin nähdäksesi miltä muistutukset näyttävät:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.triggerTestMorningNotification() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Aamumuistutus")
                    }

                    OutlinedButton(
                        onClick = { viewModel.triggerTestEveningNotification() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Iltamuistutus")
                    }
                }
            }
        }

        // Save Button
        Button(
            onClick = {
                val h = workdayHours.toIntOrNull() ?: 7
                val m = workdayMinutes.toIntOrNull() ?: 30
                val l = lunchMinutes.toIntOrNull() ?: 30

                viewModel.saveFullSettings(
                    username = username,
                    passwordText = passwordText,
                    morning = morningTime,
                    evening = eveningTime,
                    hours = h,
                    minutes = m,
                    lunch = l,
                    serverUrl = serverUrl,
                    isDemo = isDemoMode,
                    enabledDays = enabledDaysString,
                    isVacation = isVacationEnabled,
                    vacationStart = vacationStart,
                    vacationEnd = vacationEnd,
                    appTheme = appTheme
                )

                viewModel.saveGeofenceSettings(
                    isEnabled = isGeofenceEnabled,
                    lat = workplaceLat.toDoubleOrNull() ?: 60.1699,
                    lng = workplaceLng.toDoubleOrNull() ?: 24.9384,
                    radius = geofenceRadius.toIntOrNull() ?: 150,
                    arrStart = arrivalWindowStart,
                    arrEnd = arrivalWindowEnd,
                    depStart = departureWindowStart,
                    depEnd = departureWindowEnd
                )

                viewModel.saveTargetSettings(
                    targetMode = targetMode,
                    targetHoursDaily = targetDailyStr.toDoubleOrNull() ?: 7.5,
                    targetHoursWeekly = targetWeeklyStr.toDoubleOrNull() ?: 37.5,
                    targetSoundAlertEnabled = targetSoundEnabled,
                    targetVibrationAlertEnabled = targetVibEnabled
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("save_settings_button"),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Save, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("TALLENNA ASETUKSET", fontWeight = FontWeight.Bold)
        }
    }

    if (showVacationAlert) {
        AlertDialog(
            onDismissRequest = { showVacationAlert = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.BeachAccess,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text("Loma-aika aktivoitu")
            },
            text = {
                Text("Muista laittaa loma myös Tuntivelhossa päälle! Lomat ja poissaolot tulee muistaa kirjata manuaalisesti Tuntivelhon web-portaalissa.")
            },
            confirmButton = {
                Button(onClick = { showVacationAlert = false }) {
                    Text("Selvä!")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showMorningPicker) {
        TimePickerDialog(
            title = "Valitse aamumuistutuksen aika",
            initialTimeStr = morningTime,
            onDismiss = { showMorningPicker = false },
            onTimeSelected = {
                morningTime = it
                showMorningPicker = false
            }
        )
    }

    if (showEveningPicker) {
        TimePickerDialog(
            title = "Valitse iltamuistutuksen aika",
            initialTimeStr = eveningTime,
            onDismiss = { showEveningPicker = false },
            onTimeSelected = {
                eveningTime = it
                showEveningPicker = false
            }
        )
    }

    if (showArrPickerStart) {
        TimePickerDialog(
            title = "Saapumisikkunan ALKU (esim. 06:30)",
            initialTimeStr = arrivalWindowStart,
            onDismiss = { showArrPickerStart = false },
            onTimeSelected = {
                arrivalWindowStart = it
                showArrPickerStart = false
            }
        )
    }

    if (showArrPickerEnd) {
        TimePickerDialog(
            title = "Saapumisikkunan LOPPU (esim. 10:30)",
            initialTimeStr = arrivalWindowEnd,
            onDismiss = { showArrPickerEnd = false },
            onTimeSelected = {
                arrivalWindowEnd = it
                showArrPickerEnd = false
            }
        )
    }

    if (showDepPickerStart) {
        TimePickerDialog(
            title = "Poistumisikkunan ALKU (esim. 14:30)",
            initialTimeStr = departureWindowStart,
            onDismiss = { showDepPickerStart = false },
            onTimeSelected = {
                departureWindowStart = it
                showDepPickerStart = false
            }
        )
    }

    if (showDepPickerEnd) {
        TimePickerDialog(
            title = "Poistumisikkunan LOPPU (esim. 18:30)",
            initialTimeStr = departureWindowEnd,
            onDismiss = { showDepPickerEnd = false },
            onTimeSelected = {
                departureWindowEnd = it
                showDepPickerEnd = false
            }
        )
    }
}

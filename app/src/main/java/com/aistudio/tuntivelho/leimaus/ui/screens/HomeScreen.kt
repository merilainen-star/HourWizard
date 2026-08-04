package com.aistudio.tuntivelho.leimaus.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FreeBreakfast
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.tuntivelho.leimaus.data.db.StampEntity
import com.aistudio.tuntivelho.leimaus.data.preferences.AppSettings
import com.aistudio.tuntivelho.leimaus.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

fun calculateWeeklyWorkedMinutes(
    logs: List<StampEntity>,
    currentSessionMinutes: Int,
    lunchBreakMinutes: Int = 30
): Int {
    val calendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Helsinki")).apply {
        firstDayOfWeek = Calendar.MONDAY
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val mondayStartTs = calendar.timeInMillis

    val weekLogs = logs.filter { it.timestamp >= mondayStartTs && it.isSuccess }.sortedBy { it.timestamp }

    var totalLogMinutes = 0
    var lastInTs: Long? = null
    var breakStartTs: Long? = null
    var sessionBreakMs = 0L

    for (log in weekLogs) {
        when {
            log.actionType.contains("SISÄÄN", ignoreCase = true) -> {
                lastInTs = log.timestamp
                breakStartTs = null
                sessionBreakMs = 0L
            }

            log.actionType.contains("TAUOLLE", ignoreCase = true) -> {
                breakStartTs = log.timestamp
            }

            log.actionType.contains("TAUOLTA", ignoreCase = true) -> {
                breakStartTs?.let { start ->
                    val breakMs = log.timestamp - start
                    if (breakMs > 0) sessionBreakMs += breakMs
                }
                breakStartTs = null
            }

            log.actionType.contains("ULOS", ignoreCase = true) && lastInTs != null -> {
                val diffMs = log.timestamp - lastInTs
                if (diffMs > 0) {
                    // A break still running at clock-out counts up to that moment
                    breakStartTs?.let { start ->
                        val breakMs = log.timestamp - start
                        if (breakMs > 0) sessionBreakMs += breakMs
                    }
                    val rawMins = (diffMs / (60 * 1000L)).toInt()
                    val breakMins = (sessionBreakMs / (60 * 1000L)).toInt()
                    // Tuntivelho always charges at least the minimum break
                    val deduction = maxOf(lunchBreakMinutes, breakMins)
                    totalLogMinutes += (rawMins - deduction).coerceAtLeast(0)
                }
                lastInTs = null
                breakStartTs = null
                sessionBreakMs = 0L
            }
        }
    }

    return totalLogMinutes + currentSessionMinutes
}

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val balanceStr by viewModel.currentBalanceStr.collectAsState()
    val logs by viewModel.logs.collectAsState()

    val currentSessionMinutes = settings.workedMinutesSinceClockIn(System.currentTimeMillis())

    val isWeekly = settings.targetMode == "WEEKLY"
    val targetGoalHours = if (isWeekly) settings.targetHoursWeekly else settings.targetHoursDaily
    val targetMinsTotal = (targetGoalHours * 60).toInt()

    val workedMinsTotal = if (isWeekly) {
        calculateWeeklyWorkedMinutes(logs, currentSessionMinutes, settings.lunchBreakMinutes)
    } else {
        currentSessionMinutes
    }

    val progressFraction = if (targetMinsTotal > 0) (workedMinsTotal.toFloat() / targetMinsTotal.toFloat()).coerceIn(0f, 1f) else 0f
    val percentInt = (progressFraction * 100).toInt()
    val isTargetReached = targetMinsTotal > 0 && workedMinsTotal >= targetMinsTotal

    val clockInTimeText = if (settings.clockInTimestamp > 0L) {
        val helsinkiTz = java.util.TimeZone.getTimeZone("Europe/Helsinki")
        SimpleDateFormat("HH.mm", Locale.getDefault()).apply { timeZone = helsinkiTz }.format(Date(settings.clockInTimestamp))
    } else ""

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcome & Status Banner Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("status_banner_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = if (settings.isClockedIn) {
                                listOf(
                                    Color(0xFF0F766E),
                                    Color(0xFF134E4A)
                                )
                            } else {
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.primaryContainer
                                )
                            }
                        )
                    )
                    .padding(24.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = when {
                                settings.isOnBreak -> Color(0xFF38BDF8)
                                settings.isClockedIn -> Color(0xFF34D399)
                                else -> Color(0xFFFFB74D)
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = when {
                                        settings.isOnBreak -> "TAUOLLA"
                                        settings.isClockedIn -> "SISÄÄNLEIMATTU"
                                        else -> "EI AKTIIVISTA LEIMAUSTA"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            }
                        }

                        if (settings.isDemoMode) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0x33FFFFFF)
                            ) {
                                Text(
                                    text = "Demotila",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (settings.isClockedIn) "Sisään klo $clockInTimeText" else "Tuntivelho-leimaus",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Overall Tuntitase / Kokonaissaldo Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "KOKONAISSALDO (TUNTITASE)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            val displayBalance = if (settings.lastServerBalance.isNotBlank()) settings.lastServerBalance else "−"
                            Text(
                                text = displayBalance,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }

                        IconButton(
                            onClick = { viewModel.refreshServerBalance() },
                            modifier = Modifier.testTag("refresh_balance_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Päivitä tuntitase",
                                tint = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (settings.isClockedIn) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Tämän päivän saldo:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (balanceStr.startsWith("+")) Color(0xFF059669) else Color(0xFFD97706)
                            ) {
                                Text(
                                    text = balanceStr,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Muistutukset: Aamu ${settings.morningReminderTime} • Ilta ${settings.eveningReminderTime}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        // Action Buttons Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Pikaleimaukset",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                if (isLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Käsitellään leimauspyyntöä...", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { viewModel.clockIn() },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .testTag("clock_in_button"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF059669)
                            )
                        ) {
                            Icon(Icons.Default.Login, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SISÄÄN",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }

                        Button(
                            onClick = { viewModel.clockOut() },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .testTag("clock_out_button"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFEA580C)
                            )
                        ) {
                            Icon(Icons.Default.Logout, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ULOS",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }

                    // Break stamp — only meaningful while a session is running
                    Button(
                        onClick = {
                            if (settings.isOnBreak) viewModel.endBreak() else viewModel.startBreak()
                        },
                        enabled = settings.isClockedIn,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("break_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (settings.isOnBreak) Color(0xFF0284C7) else Color(0xFF7C3AED)
                        )
                    ) {
                        Icon(
                            imageVector = if (settings.isOnBreak) Icons.Default.PlayArrow else Icons.Default.FreeBreakfast,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (settings.isOnBreak) "TAUOLTA TAKAISIN" else "TAUOLLE",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    if (settings.isClockedIn) {
                        val nowMs = System.currentTimeMillis()
                        val deduction = settings.breakDeductionMinutes(nowMs)
                        val actualBreak = deduction - settings.lunchBreakMinutes
                        Text(
                            text = if (deduction > settings.lunchBreakMinutes) {
                                "Tauko $deduction min — työpäivä venyy $actualBreak min minimitaukoa pidemmäksi."
                            } else {
                                "Taukovähennys ${settings.lunchBreakMinutes} min (minimi peritään, vaikka tauko jäisi lyhyemmäksi)."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Target Hours & Progress Bar Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("target_progress_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (isTargetReached) Color(0xFFD1FAE5) else MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Flag,
                                contentDescription = null,
                                tint = if (isTargetReached) Color(0xFF059669) else MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isWeekly) "Viikkotavoitteen kertymä" else "Päivätavoitteen kertymä",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            val workedHrs = workedMinsTotal / 60
                            val workedMins = workedMinsTotal % 60
                            val targetHrs = targetMinsTotal / 60
                            val targetMins = targetMinsTotal % 60
                            Text(
                                text = "${workedHrs}h ${workedMins}min / ${targetHrs}h ${targetMins}min",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isTargetReached) Color(0xFF059669) else MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "$percentInt %",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isTargetReached) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                // Smooth Progress Bar
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    color = if (isTargetReached) Color(0xFF059669) else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (isTargetReached) {
                        val overMins = workedMinsTotal - targetMinsTotal
                        val overHrs = overMins / 60
                        val overRestMins = overMins % 60
                        Text(
                            text = "🎉 Tavoite täynnä! Plussalla: +${overHrs}h ${overRestMins}min",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF059669)
                        )
                    } else {
                        val remMins = targetMinsTotal - workedMinsTotal
                        val remHrs = remMins / 60
                        val remRestMins = remMins % 60
                        Text(
                            text = "Aikaa tavoitteeseen: ${remHrs}h ${remRestMins}min",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (settings.targetSoundAlertEnabled) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Äänimerkki päällä",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        if (settings.targetVibrationAlertEnabled) {
                            Icon(
                                imageVector = Icons.Default.Vibration,
                                contentDescription = "Värinähälytys päällä",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }



        if (settings.username.isBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Käyttäjätunnusta ei ole asetettu",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Aseta Tuntivelho-tunnuksesi Asetukset-sivulta reaalileimauksia varten.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Button(
                        onClick = onNavigateToSettings,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Asetukset")
                    }
                }
            }
        }
    }
}

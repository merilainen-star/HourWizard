package com.aistudio.tuntivelho.leimaus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aistudio.tuntivelho.leimaus.data.db.StampEntity
import com.aistudio.tuntivelho.leimaus.data.preferences.AppSettings
import java.util.*

fun calculateDailyWorkedMinutes(
    logs: List<StampEntity>,
    currentSessionMinutes: Int,
    lunchBreakMinutes: Int
): Map<Int, Int> {
    val helsinkiTz = TimeZone.getTimeZone("Europe/Helsinki")
    val nowMs = System.currentTimeMillis()
    
    val currentCal = Calendar.getInstance(helsinkiTz).apply { timeInMillis = nowMs }
    val currentDayIso = when (currentCal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        Calendar.SATURDAY -> 6
        Calendar.SUNDAY -> 7
        else -> 1
    }

    val weekStartCal = Calendar.getInstance(helsinkiTz).apply {
        timeInMillis = nowMs
        firstDayOfWeek = Calendar.MONDAY
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val mondayStartTs = weekStartCal.timeInMillis
    
    val weekLogs = logs.filter { it.timestamp >= mondayStartTs && it.isSuccess }.sortedBy { it.timestamp }

    // Map of ISO day (1..7) -> minutes
    val dailyMinutes = mutableMapOf<Int, Int>()
    
    var lastInTs: Long? = null
    var breakStartTs: Long? = null
    var sessionBreakMs = 0L

    for (log in weekLogs) {
        val logCal = Calendar.getInstance(helsinkiTz).apply { timeInMillis = log.timestamp }
        val dayIso = when (logCal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }
        
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
                val diffMs = log.timestamp - lastInTs!!
                if (diffMs > 0) {
                    breakStartTs?.let { start ->
                        val breakMs = log.timestamp - start
                        if (breakMs > 0) sessionBreakMs += breakMs
                    }
                    val rawMins = (diffMs / (60 * 1000L)).toInt()
                    val breakMins = (sessionBreakMs / (60 * 1000L)).toInt()
                    val deduction = maxOf(lunchBreakMinutes, breakMins)
                    val sessionMinutes = (rawMins - deduction).coerceAtLeast(0)
                    dailyMinutes[dayIso] = (dailyMinutes[dayIso] ?: 0) + sessionMinutes
                }
                lastInTs = null
                breakStartTs = null
                sessionBreakMs = 0L
            }
        }
    }
    
    // Add current session
    if (currentSessionMinutes > 0) {
        dailyMinutes[currentDayIso] = (dailyMinutes[currentDayIso] ?: 0) + currentSessionMinutes
    }
    
    return dailyMinutes
}

@Composable
fun WeeklySummaryCard(
    settings: AppSettings,
    logs: List<StampEntity>,
    modifier: Modifier = Modifier
) {
    val enabledDaysSet = settings.enabledDaysString.split(",")
        .mapNotNull { it.trim().toIntOrNull() }
        .toSet()

    // Always show Mon-Fri, and add Sat/Sun if enabled
    val daysToShow = (1..5).toMutableList()
    if (enabledDaysSet.contains(6)) daysToShow.add(6)
    if (enabledDaysSet.contains(7)) daysToShow.add(7)

    val currentSessionMinutes = settings.workedMinutesSinceClockIn(System.currentTimeMillis())
    val dailyMinutes = calculateDailyWorkedMinutes(logs, currentSessionMinutes, settings.lunchBreakMinutes)
    
    val targetDailyMinutes = (settings.workdayHours * 60) + settings.workdayMinutes

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Viikkonäkymä",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                // Let's find the max value to scale the bars
                val maxMinutes = daysToShow.maxOfOrNull { dailyMinutes[it] ?: 0 }?.coerceAtLeast(targetDailyMinutes) ?: targetDailyMinutes
                val scaleMax = maxMinutes * 1.2f // Add 20% headroom

                daysToShow.forEach { dayIso ->
                    val workedMins = dailyMinutes[dayIso] ?: 0
                    val dayLabel = when (dayIso) {
                        1 -> "Ma"
                        2 -> "Ti"
                        3 -> "Ke"
                        4 -> "To"
                        5 -> "Pe"
                        6 -> "La"
                        7 -> "Su"
                        else -> ""
                    }
                    
                    BarChartItem(
                        workedMinutes = workedMins,
                        targetMinutes = targetDailyMinutes,
                        maxScaleMinutes = scaleMax,
                        dayLabel = dayLabel
                    )
                }
            }
            
            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF059669)))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Tavoite", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF38BDF8)))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Ylityö", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFEA580C)))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Vajaavajaus", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun BarChartItem(
    workedMinutes: Int,
    targetMinutes: Int,
    maxScaleMinutes: Float,
    dayLabel: String
) {
    val totalHeight = 120.dp
    
    val isOvertime = workedMinutes > targetMinutes
    val isUnder = workedMinutes in 1..<targetMinutes
    
    val baseFraction = if (workedMinutes > 0) minOf(workedMinutes, targetMinutes) / maxScaleMinutes else 0f
    val overFraction = if (isOvertime) (workedMinutes - targetMinutes) / maxScaleMinutes else 0f
    val missingFraction = if (isUnder) (targetMinutes - workedMinutes) / maxScaleMinutes else 0f
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
        modifier = Modifier.height(150.dp)
    ) {
        Box(
            modifier = Modifier
                .width(24.dp)
                .height(totalHeight),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                if (isOvertime) {
                    Box(
                        modifier = Modifier
                            .width(24.dp)
                            .height(totalHeight * overFraction)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(Color(0xFF38BDF8)) // Light blue for overtime
                    )
                } else if (isUnder) {
                    Box(
                        modifier = Modifier
                            .width(24.dp)
                            .height(totalHeight * missingFraction)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(Color(0xFFEA580C).copy(alpha = 0.3f)) // Orange for missing
                    )
                }
                
                if (workedMinutes > 0) {
                    Box(
                        modifier = Modifier
                            .width(24.dp)
                            .height(totalHeight * baseFraction)
                            .clip(
                                RoundedCornerShape(
                                    bottomStart = 4.dp, 
                                    bottomEnd = 4.dp,
                                    topStart = if (isOvertime || isUnder) 0.dp else 4.dp,
                                    topEnd = if (isOvertime || isUnder) 0.dp else 4.dp
                                )
                            )
                            .background(Color(0xFF059669)) // Green for base
                    )
                } else {
                    // Empty bar placeholder
                    Box(
                        modifier = Modifier
                            .width(24.dp)
                            .height(totalHeight * (targetMinutes / maxScaleMinutes))
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = dayLabel,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (workedMinutes > 0) FontWeight.Bold else FontWeight.Normal,
            color = if (workedMinutes > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

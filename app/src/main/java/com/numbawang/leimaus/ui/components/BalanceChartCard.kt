package com.numbawang.leimaus.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.numbawang.leimaus.data.db.StampEntity
import com.numbawang.leimaus.data.preferences.AppSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

data class BalanceDataPoint(
    val dateLabel: String,
    val hours: Double,
    val rawString: String,
    val timestamp: Long
)

data class BalanceChartViewport(
    val minHours: Double,
    val maxHours: Double
)

/**
 * Keeps the chart inside the actual 0–60 hour flexitime range while zooming in
 * enough for a one-hour change to be clearly visible.
 */
fun calculateBalanceChartViewport(points: List<BalanceDataPoint>): BalanceChartViewport {
    val fullMin = 0.0
    val fullMax = 60.0
    val minimumSpan = 4.0

    if (points.isEmpty()) return BalanceChartViewport(fullMin, fullMax)

    val pointMin = points.minOf { it.hours }.coerceIn(fullMin, fullMax)
    val pointMax = points.maxOf { it.hours }.coerceIn(fullMin, fullMax)
    val dataSpan = pointMax - pointMin
    val viewportSpan = max(minimumSpan, dataSpan * 1.5).coerceAtMost(fullMax - fullMin)
    val center = (pointMin + pointMax) / 2.0

    var viewportMin = center - viewportSpan / 2.0
    var viewportMax = center + viewportSpan / 2.0

    if (viewportMin < fullMin) {
        viewportMax += fullMin - viewportMin
        viewportMin = fullMin
    }
    if (viewportMax > fullMax) {
        viewportMin -= viewportMax - fullMax
        viewportMax = fullMax
    }

    return BalanceChartViewport(
        minHours = viewportMin.coerceAtLeast(fullMin),
        maxHours = viewportMax.coerceAtMost(fullMax)
    )
}

fun parseBalanceToHours(balanceStr: String): Double? {
    if (balanceStr.isBlank()) return null
    try {
        val text = if (balanceStr.contains("(")) {
            balanceStr.substringAfter("(").substringBefore(")")
        } else {
            balanceStr
        }
        val clean = text.replace("Saldo:", "").replace("Saldo", "").trim()
        val isNegative = clean.startsWith("-")
        val digits = clean.replace("+", "").replace("-", "").trim()

        if (digits.contains(":")) {
            val parts = digits.split(":")
            val h = parts[0].toDoubleOrNull() ?: return null
            val m = parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0
            val total = h + (m / 60.0)
            return if (isNegative) -total else total
        } else if (digits.contains("h")) {
            val h = digits.substringBefore("h").trim().toDoubleOrNull() ?: 0.0
            val m = digits.substringAfter("h").replace("m", "").trim().toDoubleOrNull() ?: 0.0
            val total = h + (m / 60.0)
            return if (isNegative) -total else total
        } else {
            val h = digits.toDoubleOrNull() ?: return null
            return if (isNegative) -h else h
        }
    } catch (e: Exception) {
        return null
    }
}

fun formatHoursToHhMm(hours: Double): String {
    val isNeg = hours < 0
    val absVal = Math.abs(hours)
    val h = absVal.toInt()
    val m = Math.round((absVal - h) * 60).toInt()
    val sign = if (isNeg) "-" else "+"
    return String.format("%s%d:%02d", sign, h, m)
}

fun extractBalancePoints(logs: List<StampEntity>, lastServerBalance: String): List<BalanceDataPoint> {
    val helsinkiTz = java.util.TimeZone.getTimeZone("Europe/Helsinki")
    val dateFormat = SimpleDateFormat("d.M.", Locale.getDefault()).apply { timeZone = helsinkiTz }
    val result = mutableListOf<BalanceDataPoint>()

    // Filter logs with balance
    val logsWithBalance = logs
        .filter { it.balance.isNotBlank() && parseBalanceToHours(it.balance) != null }
        .sortedBy { it.timestamp }

    for (log in logsWithBalance) {
        val hrs = parseBalanceToHours(log.balance) ?: continue
        val dateLabel = if (log.timestamp > 0) dateFormat.format(Date(log.timestamp)) else log.formattedTime.take(5)
        result.add(BalanceDataPoint(dateLabel, hrs, log.balance, log.timestamp))
    }

    val serverHrs = parseBalanceToHours(lastServerBalance)
    if (serverHrs != null) {
        val nowLabel = "Nyt"
        val lastPoint = result.lastOrNull()
        if (lastPoint == null || Math.abs(lastPoint.hours - serverHrs) > 0.01) {
            result.add(BalanceDataPoint(nowLabel, serverHrs, lastServerBalance, System.currentTimeMillis()))
        }
    }

    if (result.isEmpty()) {
        val baseHrs = serverHrs ?: 28.0
        val now = System.currentTimeMillis()
        val dayMs = 86400000L
        result.add(BalanceDataPoint(dateFormat.format(Date(now - 3 * dayMs)), (baseHrs - 1.5).coerceAtLeast(-5.0), "", now - 3 * dayMs))
        result.add(BalanceDataPoint(dateFormat.format(Date(now - 2 * dayMs)), (baseHrs - 0.75).coerceAtLeast(-5.0), "", now - 2 * dayMs))
        result.add(BalanceDataPoint(dateFormat.format(Date(now - 1 * dayMs)), baseHrs, "", now - 1 * dayMs))
        result.add(BalanceDataPoint("Nyt", baseHrs, lastServerBalance, now))
    } else if (result.size == 1) {
        val single = result[0]
        val prevHrs = single.hours - 0.5
        val dayMs = 86400000L
        val prevDate = dateFormat.format(Date(single.timestamp - dayMs))
        result.add(0, BalanceDataPoint(prevDate, prevHrs, "", single.timestamp - dayMs))
    }

    return result.takeLast(7)
}

@Composable
fun BalanceChartCard(
    logs: List<StampEntity>,
    settings: AppSettings,
    modifier: Modifier = Modifier
) {
    val points = remember(logs, settings.lastServerBalance) {
        extractBalancePoints(logs, settings.lastServerBalance)
    }

    val currentHours = points.lastOrNull()?.hours ?: parseBalanceToHours(settings.lastServerBalance) ?: 0.0

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ShowChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Kokonaistaseen kehitys",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "0–60 h · lähennetty näkymä",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Status Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val formattedHours = formatHoursToHhMm(currentHours)
                        Text(
                            text = formattedHours,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Canvas Chart
            BalanceCanvasChart(
                points = points,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            )
        }
    }
}

@Composable
fun BalanceCanvasChart(
    points: List<BalanceDataPoint>,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val lineColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        val paddingLeft = 45.dp.toPx()
        val paddingRight = 20.dp.toPx()
        val paddingTop = 25.dp.toPx()
        val paddingBottom = 25.dp.toPx()

        val graphWidth = width - paddingLeft - paddingRight
        val graphHeight = height - paddingTop - paddingBottom

        if (graphWidth <= 0 || graphHeight <= 0) return@Canvas

        val viewport = calculateBalanceChartViewport(points)
        val minY = viewport.minHours
        val maxY = viewport.maxHours
        val yRange = maxY - minY

        fun valueToY(valHours: Double): Float {
            val normalized = (valHours - minY) / yRange
            return (paddingTop + graphHeight - (normalized * graphHeight)).toFloat()
        }

        fun indexToX(index: Int): Float {
            if (points.size <= 1) return paddingLeft + graphWidth / 2f
            return paddingLeft + (index.toFloat() / (points.size - 1)) * graphWidth
        }

        val textStyle = TextStyle(fontSize = 10.sp, color = labelColor)

        // Neutral grid follows the zoomed viewport. Its labels make the scale explicit
        // without suggesting warning thresholds.
        val gridLineCount = 4
        for (i in 0..gridLineCount) {
            val fraction = i.toDouble() / gridLineCount
            val gridValue = minY + yRange * fraction
            val gridY = valueToY(gridValue)
            drawLine(
                color = gridColor,
                start = Offset(paddingLeft, gridY),
                end = Offset(paddingLeft + graphWidth, gridY),
                strokeWidth = 1.dp.toPx()
            )

            val gridLabel = if (gridValue % 1.0 == 0.0) {
                "${gridValue.toInt()}h"
            } else {
                String.format(Locale.getDefault(), "%.1fh", gridValue)
            }
            val gridLayout = textMeasurer.measure(gridLabel, textStyle)
            drawText(
                gridLayout,
                topLeft = Offset(
                    paddingLeft - gridLayout.size.width - 6.dp.toPx(),
                    gridY - gridLayout.size.height / 2f
                )
            )
        }

        // Draw line segments
        if (points.size > 1) {
            for (i in 0 until points.size - 1) {
                val x1 = indexToX(i)
                val y1 = valueToY(points[i].hours)
                val x2 = indexToX(i + 1)
                val y2 = valueToY(points[i + 1].hours)

                drawLine(
                    color = lineColor,
                    start = Offset(x1, y1),
                    end = Offset(x2, y2),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        // Draw points and labels
        points.forEachIndexed { i, p ->
            val x = indexToX(i)
            val y = valueToY(p.hours)

            drawCircle(
                color = lineColor.copy(alpha = 0.3f),
                radius = 7.dp.toPx(),
                center = Offset(x, y)
            )

            drawCircle(
                color = lineColor,
                radius = 4.dp.toPx(),
                center = Offset(x, y)
            )

            val valStr = formatHoursToHhMm(p.hours)
            val valLayout = textMeasurer.measure(
                valStr,
                TextStyle(fontSize = 9.sp, color = lineColor, fontWeight = FontWeight.Bold)
            )
            drawText(
                valLayout,
                topLeft = Offset(x - valLayout.size.width / 2f, (y - valLayout.size.height - 4.dp.toPx()).coerceAtLeast(0f))
            )

            val xLayout = textMeasurer.measure(p.dateLabel, textStyle)
            drawText(
                xLayout,
                topLeft = Offset(x - xLayout.size.width / 2f, paddingTop + graphHeight + 4.dp.toPx())
            )
        }
    }
}

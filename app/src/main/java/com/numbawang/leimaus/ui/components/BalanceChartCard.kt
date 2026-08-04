package com.numbawang.leimaus.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
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

data class BalanceDataPoint(
    val dateLabel: String,
    val hours: Double,
    val rawString: String,
    val timestamp: Long
)

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
    val isDanger = currentHours > 40.0 || currentHours <= 0.0

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
                        tint = if (isDanger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
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
                            text = "Sallittu tasealue 0h – +40h",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Status Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDanger) Color(0xFFFEE2E2) else Color(0xFFD1FAE5)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isDanger) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        val formattedHours = formatHoursToHhMm(currentHours)
                        Text(
                            text = formattedHours,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isDanger) Color(0xFFDC2626) else Color(0xFF059669)
                        )
                    }
                }
            }

            if (isDanger) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFEF2F2),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (currentHours > 40.0)
                            "⚠️ Vaara: Tuntitase ylittää +40 tunnin enimmäisrajan!"
                        else
                            "⚠️ Vaara: Tuntitase on nollassa tai miinuksella!",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF991B1B),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
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

            Spacer(modifier = Modifier.height(12.dp))

            // Legend / Help
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color(0xFF10B981), RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Normaali (0–40h)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color(0xFFEF4444), RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Kriittinen (>40h / ≤0h)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
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
    val dangerColor = Color(0xFFEF4444)
    val safeColor = Color(0xFF10B981)

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

        val maxPointVal = points.maxOfOrNull { it.hours } ?: 0.0
        val minPointVal = points.minOfOrNull { it.hours } ?: 0.0

        val maxY = maxOf(maxPointVal, 45.0)
        val minY = minOf(minPointVal, -5.0)
        val yRange = if (maxY - minY == 0.0) 1.0 else (maxY - minY)

        fun valueToY(valHours: Double): Float {
            val normalized = (valHours - minY) / yRange
            return (paddingTop + graphHeight - (normalized * graphHeight)).toFloat()
        }

        fun indexToX(index: Int): Float {
            if (points.size <= 1) return paddingLeft + graphWidth / 2f
            return paddingLeft + (index.toFloat() / (points.size - 1)) * graphWidth
        }

        val y40 = valueToY(40.0)
        val y0 = valueToY(0.0)

        // Draw background zones
        if (y40 >= paddingTop) {
            drawRect(
                color = dangerColor.copy(alpha = 0.08f),
                topLeft = Offset(paddingLeft, paddingTop),
                size = Size(graphWidth, (y40 - paddingTop).coerceAtLeast(0f))
            )
        }

        val safeTop = y40.coerceIn(paddingTop, paddingTop + graphHeight)
        val safeBottom = y0.coerceIn(paddingTop, paddingTop + graphHeight)
        if (safeBottom > safeTop) {
            drawRect(
                color = safeColor.copy(alpha = 0.08f),
                topLeft = Offset(paddingLeft, safeTop),
                size = Size(graphWidth, safeBottom - safeTop)
            )
        }

        if (y0 <= paddingTop + graphHeight) {
            drawRect(
                color = dangerColor.copy(alpha = 0.08f),
                topLeft = Offset(paddingLeft, y0),
                size = Size(graphWidth, (paddingTop + graphHeight - y0).coerceAtLeast(0f))
            )
        }

        // Draw horizontal threshold lines (+40h and 0h)
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)

        drawLine(
            color = dangerColor,
            start = Offset(paddingLeft, y40),
            end = Offset(paddingLeft + graphWidth, y40),
            strokeWidth = 2.dp.toPx(),
            pathEffect = dashEffect
        )

        drawLine(
            color = dangerColor,
            start = Offset(paddingLeft, y0),
            end = Offset(paddingLeft + graphWidth, y0),
            strokeWidth = 2.dp.toPx(),
            pathEffect = dashEffect
        )

        val dangerTextStyle = TextStyle(fontSize = 10.sp, color = dangerColor, fontWeight = FontWeight.Bold)
        val textStyle = TextStyle(fontSize = 10.sp, color = labelColor)

        val text40 = textMeasurer.measure("+40h", dangerTextStyle)
        drawText(text40, topLeft = Offset(paddingLeft - text40.size.width - 6.dp.toPx(), y40 - text40.size.height / 2f))

        val text0 = textMeasurer.measure("0h", dangerTextStyle)
        drawText(text0, topLeft = Offset(paddingLeft - text0.size.width - 6.dp.toPx(), y0 - text0.size.height / 2f))

        // Draw line segments
        if (points.size > 1) {
            for (i in 0 until points.size - 1) {
                val x1 = indexToX(i)
                val y1 = valueToY(points[i].hours)
                val x2 = indexToX(i + 1)
                val y2 = valueToY(points[i + 1].hours)

                val inDanger = points[i].hours > 40.0 || points[i].hours <= 0.0 ||
                               points[i + 1].hours > 40.0 || points[i + 1].hours <= 0.0

                val strokeColor = if (inDanger) dangerColor else safeColor

                drawLine(
                    color = strokeColor,
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
            val isPtDanger = p.hours > 40.0 || p.hours <= 0.0
            val dotColor = if (isPtDanger) dangerColor else safeColor

            drawCircle(
                color = dotColor.copy(alpha = 0.3f),
                radius = 7.dp.toPx(),
                center = Offset(x, y)
            )

            drawCircle(
                color = dotColor,
                radius = 4.dp.toPx(),
                center = Offset(x, y)
            )

            val valStr = formatHoursToHhMm(p.hours)
            val valLayout = textMeasurer.measure(
                valStr,
                TextStyle(fontSize = 9.sp, color = dotColor, fontWeight = FontWeight.Bold)
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

package com.numbawang.leimaus.achievements

import com.numbawang.leimaus.data.db.StampEntity
import com.numbawang.leimaus.data.preferences.AppSettings
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

private val HELSINKI_TZ: TimeZone = TimeZone.getTimeZone("Europe/Helsinki")

private const val STREAK_TARGET_DAYS = 5
private const val EARLY_BIRD_MINUTE_OF_DAY = 7 * 60 + 30 // 07:30
private const val CLOCKWORK_TOLERANCE_MINUTES = 2
private const val NIGHT_OWL_MINUTE_OF_DAY = 22 * 60 // 22:00
private const val RIGHT_ON_THE_DOT_TOLERANCE_MINUTES = 5
private const val OVERTIME_HERO_MINUTES = 10 * 60
private const val CENTURY_CLUB_COUNT = 100

private val BALANCE_REGEX = Regex("^([+−-])?(\\d+):(\\d{2})$")

private data class Session(
    val clockInTs: Long,
    val clockOutTs: Long,
    val workedMinutes: Int,
    val dateKey: String
)

/**
 * Pure, stateless evaluation of which achievements the given punch history already qualifies
 * for. Callers diff the result against what's already persisted to find newly-earned ones.
 */
object AchievementEvaluator {

    fun evaluate(logs: List<StampEntity>, settings: AppSettings): Set<String> {
        val successfulLogs = logs.filter { it.isSuccess }.sortedBy { it.timestamp }
        if (successfulLogs.isEmpty()) return emptySet()

        val unlocked = mutableSetOf<String>()
        unlocked += "first_stamp"
        if (successfulLogs.size >= CENTURY_CLUB_COUNT) unlocked += "century_club"

        val sessions = buildSessions(successfulLogs, settings.lunchBreakMinutes)
        val sessionsByDate = sessions.groupBy { it.dateKey }
        val enabledDays = settings.enabledDaysString.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .toSet()
            .ifEmpty { setOf(1, 2, 3, 4, 5) }

        val dailyTargetMinutes = settings.workdayHours * 60 + settings.workdayMinutes
        if (sessionsByDate.values.any { daySessions ->
                abs(daySessions.sumOf { it.workedMinutes } - dailyTargetMinutes) <= RIGHT_ON_THE_DOT_TOLERANCE_MINUTES
            }
        ) {
            unlocked += "right_on_the_dot"
        }

        if (sessions.any { minuteOfDay(it.clockOutTs) >= NIGHT_OWL_MINUTE_OF_DAY }) {
            unlocked += "night_owl"
        }

        val balanceReadings = successfulLogs.mapNotNull { parseBalanceMinutes(it.balance) } +
            listOfNotNull(parseBalanceMinutes(settings.lastServerBalance))
        if (balanceReadings.any { it >= OVERTIME_HERO_MINUTES }) unlocked += "overtime_hero"
        if (balanceReadings.any { it == 0 }) unlocked += "dead_even"
        if (balanceReadings.zipWithNext().any { (prev, curr) -> prev < 0 && curr > 0 }) unlocked += "comeback"

        if (hasWorkdayStreak(sessionsByDate, enabledDays) { it.isNotEmpty() }) {
            unlocked += "on_a_roll"
        }
        if (hasWorkdayStreak(sessionsByDate, enabledDays) { daySessions ->
                daySessions.isNotEmpty() && minuteOfDay(daySessions.minOf { it.clockInTs }) < EARLY_BIRD_MINUTE_OF_DAY
            }
        ) {
            unlocked += "early_bird"
        }
        if (hasClockworkStreak(sessionsByDate, enabledDays)) unlocked += "clockwork"

        return unlocked
    }

    /** Mirrors the SISÄÄN/TAUOLLE/TAUOLTA/ULOS state machine used for weekly totals elsewhere,
     * but collects one [Session] per completed clock-in/out pair instead of summing minutes. */
    private fun buildSessions(successfulLogsAsc: List<StampEntity>, lunchBreakMinutes: Int): List<Session> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = HELSINKI_TZ }
        val sessions = mutableListOf<Session>()
        var lastInTs: Long? = null
        var breakStartTs: Long? = null
        var sessionBreakMs = 0L

        for (log in successfulLogsAsc) {
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
                    val inTs = lastInTs!!
                    val diffMs = log.timestamp - inTs
                    if (diffMs > 0) {
                        breakStartTs?.let { start ->
                            val breakMs = log.timestamp - start
                            if (breakMs > 0) sessionBreakMs += breakMs
                        }
                        val rawMins = (diffMs / (60 * 1000L)).toInt()
                        val breakMins = (sessionBreakMs / (60 * 1000L)).toInt()
                        val deduction = maxOf(lunchBreakMinutes, breakMins)
                        val workedMinutes = (rawMins - deduction).coerceAtLeast(0)
                        sessions.add(Session(inTs, log.timestamp, workedMinutes, dateFormat.format(Date(inTs))))
                    }
                    lastInTs = null
                    breakStartTs = null
                    sessionBreakMs = 0L
                }
            }
        }
        return sessions
    }

    /** Walks every enabled workday between the first and last session date, tracking the
     * longest run of consecutive days where [qualifies] holds for that day's sessions
     * (empty list when the day has none). */
    private fun hasWorkdayStreak(
        sessionsByDate: Map<String, List<Session>>,
        enabledDays: Set<Int>,
        qualifies: (List<Session>) -> Boolean
    ): Boolean {
        var streak = 0
        for (dateKey in workdayDateKeysInRange(sessionsByDate.keys, enabledDays)) {
            val daySessions = sessionsByDate[dateKey] ?: emptyList()
            streak = if (qualifies(daySessions)) streak + 1 else 0
            if (streak >= STREAK_TARGET_DAYS) return true
        }
        return false
    }

    /** Same walk as [hasWorkdayStreak], but a day only extends the run if its earliest clock-in
     * falls within [CLOCKWORK_TOLERANCE_MINUTES] of the run's first day - a missing day, or one
     * outside the window, starts a fresh run instead of just breaking the old one. */
    private fun hasClockworkStreak(sessionsByDate: Map<String, List<Session>>, enabledDays: Set<Int>): Boolean {
        var streak = 0
        var anchorMinute = -1
        for (dateKey in workdayDateKeysInRange(sessionsByDate.keys, enabledDays)) {
            val clockInMinute = sessionsByDate[dateKey]?.minOfOrNull { minuteOfDay(it.clockInTs) }
            if (clockInMinute == null) {
                streak = 0
                anchorMinute = -1
                continue
            }
            streak = if (streak == 0 || abs(clockInMinute - anchorMinute) > CLOCKWORK_TOLERANCE_MINUTES) {
                anchorMinute = clockInMinute
                1
            } else {
                streak + 1
            }
            if (streak >= STREAK_TARGET_DAYS) return true
        }
        return false
    }

    private fun workdayDateKeysInRange(sessionDates: Set<String>, enabledDays: Set<Int>): List<String> {
        if (sessionDates.isEmpty()) return emptyList()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = HELSINKI_TZ }
        val minTs = sessionDates.minOf { dateFormat.parse(it)!!.time }
        val maxTs = sessionDates.maxOf { dateFormat.parse(it)!!.time }

        val result = mutableListOf<String>()
        val cal = Calendar.getInstance(HELSINKI_TZ).apply { timeInMillis = minTs }
        while (cal.timeInMillis <= maxTs) {
            if (enabledDays.contains(isoDayOfWeek(cal))) {
                result.add(dateFormat.format(cal.time))
            }
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return result
    }

    private fun isoDayOfWeek(cal: Calendar): Int = when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        Calendar.SATURDAY -> 6
        else -> 7
    }

    private fun minuteOfDay(ts: Long): Int {
        val cal = Calendar.getInstance(HELSINKI_TZ).apply { timeInMillis = ts }
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    /** Parses balance strings like "+10:00" or "−0:27" (Tuntivelho uses a real minus sign,
     * not a hyphen) into signed minutes. */
    private fun parseBalanceMinutes(balance: String): Int? {
        if (balance.isBlank()) return null
        val match = BALANCE_REGEX.matchEntire(balance.trim()) ?: return null
        val (sign, hours, minutes) = match.destructured
        val totalMinutes = hours.toInt() * 60 + minutes.toInt()
        return if (sign == "-" || sign == "−") -totalMinutes else totalMinutes
    }
}

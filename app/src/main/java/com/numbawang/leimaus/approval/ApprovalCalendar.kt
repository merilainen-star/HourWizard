package com.numbawang.leimaus.approval

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Device-local reminder dates; API dates are wall-clock dates encoded in UTC. */
object ApprovalCalendar {
    fun previousMonth(now: Long = System.currentTimeMillis()): String =
        Calendar.getInstance().apply { timeInMillis = now; add(Calendar.MONTH, -1) }.let {
            SimpleDateFormat("yyyy-MM", Locale.ROOT).format(it.time)
        }

    fun monthRange(month: String): LongRange {
        require(Regex("\\d{4}-(0[1-9]|1[0-2])").matches(month)) { "Virheellinen kuukausi" }
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear(); set(month.substring(0, 4).toInt(), month.substring(5).toInt() - 1, 1)
        }
        val start = cal.timeInMillis / 1000
        cal.add(Calendar.MONTH, 1)
        return start until cal.timeInMillis / 1000
    }

    fun nextReminder(now: Long, zone: TimeZone = TimeZone.getDefault()): Long {
        val cal = Calendar.getInstance(zone).apply { timeInMillis = now }
        repeat(24) {
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 14); cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            while (!isWorkingDay(cal)) cal.add(Calendar.DAY_OF_MONTH, 1)
            if (cal.timeInMillis > now) return cal.timeInMillis
            cal.add(Calendar.MONTH, 1)
        }
        error("Muistutuspäivää ei löytynyt")
    }

    /** Monday–Friday excluding Finnish public holidays and customary Christmas/Midsummer eves. */
    fun isWorkingDay(cal: Calendar): Boolean {
        if (cal.get(Calendar.DAY_OF_WEEK) in setOf(Calendar.SATURDAY, Calendar.SUNDAY)) return false
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        if (month to day in setOf(1 to 1, 1 to 6, 5 to 1, 12 to 6, 12 to 24, 12 to 25, 12 to 26)) return false
        if (month == 6 && day in 19..25 && cal.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY) return false
        val y = cal.get(Calendar.YEAR)
        val a = y % 19; val b = y / 100; val c = y % 100
        val d = b / 4; val e = b % 4; val f = (b + 8) / 25; val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4; val k = c % 4; val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val easter = (cal.clone() as Calendar).apply {
            set(Calendar.MONTH, (h + l - 7 * m + 114) / 31 - 1)
            set(Calendar.DAY_OF_MONTH, (h + l - 7 * m + 114) % 31 + 1)
        }
        return listOf(-2, 1, 39).none { offset ->
            val holiday = (easter.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
            holiday.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)
        }
    }

    fun dateLabel(seconds: Long): String = SimpleDateFormat("dd.MM.yyyy", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(seconds * 1000))
}

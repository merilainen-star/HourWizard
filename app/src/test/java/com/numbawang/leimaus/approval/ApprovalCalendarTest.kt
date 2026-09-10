package com.numbawang.leimaus.approval

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.*
import org.junit.Test

class ApprovalCalendarTest {
    private fun date(value: String, zone: String): Long = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone(zone)
    }.parse(value)!!.time

    @Test fun `first workday skips weekend New Year May Day Easter and changes UTC offset`() {
        val cases = mapOf(
            "2026-09-10 09:00" to "2026-10-01 14:00",
            "2026-10-01 14:00" to "2026-11-02 14:00",
            "2026-12-10 09:00" to "2027-01-04 14:00",
            "2026-04-10 09:00" to "2026-05-04 14:00",
            "2024-03-10 09:00" to "2024-04-02 14:00",
            "2026-09-30 23:59" to "2026-10-01 14:00")
        for (zone in listOf("Europe/Helsinki", "America/New_York")) {
            cases.forEach { (input, expected) ->
                assertEquals("$input in $zone", date(expected, zone),
                    ApprovalCalendar.nextReminder(date(input, zone), TimeZone.getTimeZone(zone)))
            }
        }
    }

    @Test fun `month boundaries include leap day and exclude current month`() {
        val range = ApprovalCalendar.monthRange("2024-02")
        assertEquals(29 * 86400L, range.last - range.first + 1)
        assertEquals("29.02.2024", ApprovalCalendar.dateLabel(range.last))
        assertEquals("01.03.2024", ApprovalCalendar.dateLabel(range.last + 1))
    }
}

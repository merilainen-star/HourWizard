package com.numbawang.leimaus.data.network

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Form times are Finnish wall times; the workshift API encodes those as UTC seconds. */
data class ManualShift(
    val startMillis: Long,
    val endMillis: Long,
    val startSeconds: Long,
    val endSeconds: Long,
    val breakMinutes: Int,
    val description: String
) {
    companion object {
        fun parse(date: String, start: String, end: String, breakText: String,
                  nextDay: Boolean, now: Long = System.currentTimeMillis()): ManualShift {
            require(Regex("\\d{2}\\.\\d{2}\\.\\d{4}").matches(date) &&
                Regex("\\d{2}:\\d{2}").matches(start) && Regex("\\d{2}:\\d{2}").matches(end)) {
                "Anna päivä muodossa pp.kk.vvvv ja kellonajat muodossa tt:mm."
            }
            fun parseTime(value: String, zone: String): Long {
                val parser = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.ROOT).apply {
                    isLenient = false
                    timeZone = TimeZone.getTimeZone(zone)
                }
                val position = ParsePosition(0)
                val parsed = parser.parse(value, position)
                require(parsed != null && position.index == value.length) { "Tarkista päivä ja kellonajat." }
                return parsed.time
            }
            val startWall = parseTime("$date $start", "UTC")
            val endWall = parseTime("$date $end", "UTC") + if (nextDay) 86_400_000L else 0L
            val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.ROOT).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val startReal = parseTime(formatter.format(java.util.Date(startWall)), "Europe/Helsinki")
            val endReal = parseTime(formatter.format(java.util.Date(endWall)), "Europe/Helsinki")
            require(endWall > startWall && endWall - startWall <= 86_400_000L) {
                "Loppuajan pitää olla alkuajan jälkeen. Vuoro voi kestää enintään 24 tuntia."
            }
            require(endReal <= now) { "Jälkikirjattavan työvuoron pitää olla päättynyt." }
            val minutes = breakText.toIntOrNull()
            require(minutes != null && minutes >= 0 && minutes * 60L < (endWall - startWall) / 1000) {
                "Tauon pitää olla vähintään 0 minuuttia ja työvuoroa lyhyempi."
            }
            return ManualShift(startReal, endReal, startWall / 1000, endWall / 1000, minutes,
                "$date $start–${if (nextDay) "seuraava päivä " else ""}$end, tauko $minutes min")
        }
    }
}

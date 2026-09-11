package com.numbawang.leimaus.data.network

import org.junit.Assert.*
import org.junit.Test
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class ManualShiftTest {
    private fun shift(date: String = "10.09.2026", start: String = "07:30", end: String = "16:00",
                      pause: String = "30", overnight: Boolean = false) =
        ManualShift.parse(date, start, end, pause, overnight, now = 1_800_000_000_000)

    @Test fun finnishWallTimeIsEncodedOnceInSummerAndWinter() {
        val summer = shift()
        val winter = shift(date = "10.01.2026")
        assertEquals(3 * 3600L, summer.startSeconds - summer.startMillis / 1000)
        assertEquals(2 * 3600L, winter.startSeconds - winter.startMillis / 1000)
        assertEquals(8 * 3600L, summer.endSeconds - summer.startSeconds - summer.breakMinutes * 60)
    }

    @Test fun overnightRequiresExplicitNextDayAndSupportsYearBoundary() {
        assertThrows(IllegalArgumentException::class.java) { shift(start = "22:00", end = "06:00") }
        val overnight = shift(date = "31.12.2025", start = "22:00", end = "06:00", overnight = true)
        assertEquals(8 * 3600L, overnight.endSeconds - overnight.startSeconds)
    }

    @Test fun rejectsInvalidDatesFutureTimesAndInvalidBreaks() {
        for (date in listOf("31.02.2026", "1.9.2026")) {
            assertThrows(IllegalArgumentException::class.java) { shift(date = date) }
        }
        for (pause in listOf("-1", "510", "no", "2147483647")) {
            assertThrows(IllegalArgumentException::class.java) { shift(pause = pause) }
        }
        assertThrows(IllegalArgumentException::class.java) { shift(start = "24:00") }
        assertThrows(IllegalArgumentException::class.java) { shift(end = "07:30") }
        assertThrows(IllegalArgumentException::class.java) {
            ManualShift.parse("10.09.2026", "07:30", "16:00", "30", false, now = 0)
        }
        // This local time does not exist when Finnish clocks jump forward.
        assertThrows(IllegalArgumentException::class.java) { shift(date = "29.03.2026", start = "03:30") }
    }

    @Test fun parsesActualWorkshiftConfirmationAndPermissionErrors() {
        val adapter = Moshi.Builder().add(KotlinJsonAdapterFactory()).build().adapter(GraphQLResponse::class.java)
        val ok = adapter.fromJson("""{"data":{"tyovuoroAdd":{"tyovuoro":{"id":123,"alku":100,"loppu":200},"errors":[]}}}""")!!
        assertEquals("123", ok.data!!.tyovuoroAdd!!.tyovuoro!!.id)
        val denied = adapter.fromJson("""{"data":{"tyovuoroAdd":{"tyovuoro":null,"errors":[{"message":"Ei oikeuksia"}]}}}""")!!
        assertEquals("Ei oikeuksia", denied.data!!.tyovuoroAdd!!.errors!!.single().message)
        assertNull(adapter.fromJson("{}")!!.data)
    }
}

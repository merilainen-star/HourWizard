package com.numbawang.leimaus.data.network

import org.junit.Assert.*
import org.junit.Test
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class ManualShiftRequestTest {
    private val context = GraphQLDataPayload(
        userProfile = ManualShiftEmployee(123),
        kellokortti = KellokorttiPayload(selectiondefaults = SelectionDefaults(talaatuid = 42, tyopisteid = 77)),
        talaadut = listOf(ManualShiftWorkType(42, 3)), tvmerkinnat = listOf(ManualShiftMarker(3))
    )
    private val shift = ManualShift.parse("10.09.2026", "07:00", "15:30", "30", false, 1_800_000_000_000)

    @Test fun sendsAuthenticatedEmployeeAndMatchingMarkerWithNativeFormDefaults() {
        val variables = manualShiftVariables(shift, context)
        assertEquals(123, variables["henkiloid"])
        assertEquals(42, variables["talaatuid"])
        assertEquals(77, variables["tyopisteid"])
        assertEquals(3, variables["tvmerkintaid"])
        assertEquals(1800, variables["taukokesto"])
        assertEquals(shift.startSeconds - 7 * 3600, variables["taukoalku"])
        assertEquals(1, variables["tyontekijalukumaara"])
    }

    @Test fun refusesToGuessMissingEmployeeOrMarker() {
        for (invalid in listOf(context.copy(userProfile = null), context.copy(talaadut = emptyList()),
            context.copy(tvmerkinnat = emptyList()))) {
            assertThrows(IllegalArgumentException::class.java) { manualShiftVariables(shift, invalid) }
        }
    }

    @Test fun userReportedSentryResponseIsUncertainAndPreservesTechnicalDetail() {
        val json = """{"errors":[{"message":"Call to undefined method Sentry\\EventType::clientReport()","errorCode":0,"inputKey":null,"data":""}]}"""
        val response = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            .adapter(GraphQLResponse::class.java).fromJson(json)!!
        val message = manualShiftError(response.errors!!.map { it.message })
        assertTrue(message.contains("ennen uutta yritystä"))
        assertTrue(message.contains("Sentry\\EventType::clientReport()"))
        assertEquals("Ei oikeuksia", manualShiftError(listOf("Ei oikeuksia")))
    }
}

package com.numbawang.leimaus.data.network

import org.junit.Assert.*
import org.junit.Test
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class ManualShiftRequestTest {
    @Test fun sendsUnselectedPriorityAsExplicitJsonNull() {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val payload = manualShiftPayload(shift, context, moshi)
        val request = (moshi.adapter(Any::class.java).fromJson(payload) as List<*>).single() as Map<*, *>
        val variables = request["variables"] as Map<*, *>
        assertTrue(variables.containsKey("toiveprioriteetti"))
        assertNull(variables["toiveprioriteetti"])
        assertTrue((request["query"] as String).contains("toiveprioriteetti: ${'$'}toiveprioriteetti"))
    }

    @Test fun returnedIdWithErrorsRequiresReviewInsteadOfOrdinaryRetryOrSuccess() {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val response = moshi.adapter(GraphQLResponse::class.java).fromJson("""
            {"data":{"tyovuoroAdd":{"tyovuoro":{"id":123,"alku":1789023600,"loppu":1789054200},
            "errors":[{"message":"Undefined array key \"toiveprioriteetti\""}]}},"errors":[]}
        """)!!
        val warning = manualShiftReviewMessage(response)!!
        assertTrue(warning.contains("123"))
        assertTrue(warning.contains("on voinut tallentua"))
        assertTrue(warning.contains("toiveprioriteetti"))
        assertNull(manualShiftReviewMessage(response.copy(data = response.data!!.copy(
            tyovuoroAdd = response.data!!.tyovuoroAdd!!.copy(errors = emptyList())))))
        assertNull(manualShiftReviewMessage(response.copy(data = response.data!!.copy(
            tyovuoroAdd = response.data!!.tyovuoroAdd!!.copy(tyovuoro = null)))))
        assertNull(manualShiftReviewMessage(null))
    }
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

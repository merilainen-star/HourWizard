package com.numbawang.leimaus.approval

import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ApprovalDiagnosticsTest {
    @Test fun `October first at noon selects September without waiting for reminder`() {
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).parse("2026-10-01 12:01")!!.time
        assertEquals("2026-09", ApprovalCalendar.previousMonth(now))
    }

    @Test fun `incomplete totals allow review entry but never enable approval`() = runTest {
        val period = samplePeriod().copy(totals = emptyMap())
        val gateway = object : ApprovalGateway {
            override suspend fun load(month: String) = listOf(period)
            override suspend fun refresh(id: Int) = period
            override suspend fun approve(period: ApprovalPeriod, month: String) = error("Must not write")
        }
        val entry = ApprovalEntryController(gateway, this)
        entry.refresh("2026-08"); runCurrent()
        assertEquals(ApprovalEntryState.Pending, entry.state.value)
        assertTrue(entry.diagnostic.value.report.contains("incomplete_totals=1"))
        assertFalse(period.canApprove)
        val review = ApprovalController(gateway, this)
        review.open("2026-08"); runCurrent()
        review.approve(); runCurrent()
        assertFalse(review.state.value.selected!!.approved)
    }

    @Test fun `report explains filtering without exposing identities or amounts`() = runTest {
        val privatePeriod = periodJson(id = 987654) + mapOf("jakso" to "user@example.com", "henkiloid" to 876543)
        val repo = ApprovalRepository({ _, mutation ->
            assertFalse(mutation)
            envelope("tyovuorot", mapOf("jaksot" to listOf(privatePeriod, periodJson(type = 1), periodJson(month = "2026-09"))))
        }, { "2026-08" })
        val entry = ApprovalEntryController(repo, this)
        entry.refresh("2026-08"); runCurrent()
        val report = entry.diagnostic.value.report
        assertTrue(report.contains("received=3"))
        assertTrue(report.contains("matched=1"))
        assertTrue(report.contains("excluded_type=1"))
        assertTrue(report.contains("excluded_dates=1"))
        listOf("user@example.com", "987654", "876543", "152:30", "151:45", "-0:45").forEach { assertFalse(report.contains(it)) }
    }

    @Test fun `empty list and malformed responses have visible diagnostic reasons`() = runTest {
        for (body in listOf(envelope("tyovuorot", mapOf("jaksot" to emptyList<Any>())), "not json")) {
            val repo = ApprovalRepository({ _, _ -> body }, { "2026-08" })
            val entry = ApprovalEntryController(repo, this)
            entry.refresh("2026-08"); runCurrent()
            assertTrue(entry.diagnostic.value.needsAttention)
            assertNotEquals(ApprovalEntryState.Pending, entry.state.value)
            assertFalse(entry.diagnostic.value.message.contains("on hyväksytty"))
            assertTrue(entry.diagnostic.value.report.isNotBlank())
        }
    }

    @Test fun `GraphQL errors produce safe useful codes instead of raw server text`() = runTest {
        val body = """[{"errors":[{"message":"Cannot query field \"jaksoEnabled\" on type Test. user@example.com Bearer dummy_secret"}]}]"""
        val repo = ApprovalRepository({ _, _ -> body }, { "2026-08" })
        val entry = ApprovalEntryController(repo, this)
        entry.refresh("2026-08"); runCurrent()
        assertTrue(entry.diagnostic.value.report.contains("GRAPHQL_FIELD_jaksoEnabled"))
        assertFalse(entry.diagnostic.value.report.contains("dummy_secret"))
        assertFalse(entry.diagnostic.value.message.contains("user@example.com"))
        assertEquals("HTTP_401", approvalReadFailureCode(IllegalStateException("HTTP 401 dummy_secret")))
        assertEquals("READ_FAILED", approvalReadFailureCode(IllegalStateException("dummy_secret")))
    }
}

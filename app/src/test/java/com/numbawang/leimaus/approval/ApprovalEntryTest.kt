package com.numbawang.leimaus.approval

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ApprovalEntryTest {
    private class Gateway : ApprovalGateway {
        var periods = listOf(samplePeriod())
        var fail = false
        var reads = 0
        var writes = 0
        override suspend fun load(month: String): List<ApprovalPeriod> {
            reads++
            check(!fail)
            assertEquals("2026-08", month)
            return periods
        }
        override suspend fun refresh(id: Int) = periods.first { it.id == id }
        override suspend fun approve(period: ApprovalPeriod, month: String): ApprovalPeriod {
            writes++
            return period.copy(approved = true).also { approved ->
                periods = periods.map { if (it.id == approved.id) approved else it }
            }
        }
    }

    @Test fun `only a known pending period makes the entry visible`() = runTest {
        val gateway = Gateway()
        val entry = ApprovalEntryController(gateway, this)
        assertEquals(ApprovalEntryState.Unknown, entry.state.value)
        val cases = listOf(
            listOf(samplePeriod()) to ApprovalEntryState.Pending,
            listOf(samplePeriod().copy(approved = true)) to ApprovalEntryState.NoPending,
            listOf(samplePeriod().copy(approved = true), samplePeriod().copy(id = 102)) to ApprovalEntryState.Pending,
            emptyList<ApprovalPeriod>() to ApprovalEntryState.NoPending,
            listOf(samplePeriod().copy(stateKnown = false)) to ApprovalEntryState.Unavailable,
            listOf(samplePeriod().copy(locked = true)) to ApprovalEntryState.NoPending)
        for ((periods, expected) in cases) {
            gateway.periods = periods
            entry.refresh("2026-08")
            assertEquals(ApprovalEntryState.Loading, entry.state.value)
            runCurrent()
            assertEquals(expected, entry.state.value)
        }
        assertEquals(0, gateway.writes)
    }

    @Test fun `return refresh observes remote approval and failed reads never retain a pending entry`() = runTest {
        val gateway = Gateway()
        val entry = ApprovalEntryController(gateway, this)
        entry.refresh("2026-08"); runCurrent()
        assertEquals(ApprovalEntryState.Pending, entry.state.value)
        entry.invalidate()
        assertEquals(ApprovalEntryState.Unknown, entry.state.value)
        gateway.periods = listOf(samplePeriod().copy(approved = true))
        entry.refresh("2026-08"); runCurrent()
        assertEquals(ApprovalEntryState.NoPending, entry.state.value)
        gateway.fail = true
        entry.refresh("2026-08"); runCurrent()
        assertEquals(ApprovalEntryState.Unavailable, entry.state.value)
    }

    @Test fun `late response from previous account cannot restore the button`() = runTest {
        val firstRead = CompletableDeferred<List<ApprovalPeriod>>()
        var calls = 0
        val gateway = object : ApprovalGateway {
            override suspend fun load(month: String): List<ApprovalPeriod> =
                if (++calls == 1) withContext(NonCancellable) { firstRead.await() } else emptyList()
            override suspend fun refresh(id: Int) = error("Not expected")
            override suspend fun approve(period: ApprovalPeriod, month: String) = error("No write allowed")
        }
        val entry = ApprovalEntryController(gateway, this)
        entry.refresh("2026-08"); runCurrent()
        entry.invalidate()
        entry.refresh("2026-08"); runCurrent()
        firstRead.complete(listOf(samplePeriod())); runCurrent()
        assertEquals(ApprovalEntryState.NoPending, entry.state.value)
    }

    @Test fun `successful approval refreshes all periods and hides entry only after the last one`() = runTest {
        val gateway = Gateway().apply { periods = listOf(samplePeriod(), samplePeriod().copy(id = 102)) }
        val entry = ApprovalEntryController(gateway, this)
        val approval = ApprovalController(gateway, this) { entry.refresh("2026-08") }
        approval.open("2026-08"); runCurrent()
        approval.approve(); runCurrent()
        assertEquals(ApprovalEntryState.Pending, entry.state.value)
        approval.select(102); runCurrent()
        approval.approve(); runCurrent()
        assertEquals(ApprovalEntryState.NoPending, entry.state.value)
        assertEquals(2, gateway.writes)
    }
}

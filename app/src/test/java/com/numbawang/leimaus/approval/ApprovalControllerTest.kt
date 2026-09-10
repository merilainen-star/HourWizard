package com.numbawang.leimaus.approval

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ApprovalControllerTest {
    private class Gateway : ApprovalGateway {
        var current = samplePeriod()
        var writes = 0
        var fail = false
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun load(month: String) = listOf(current)
        override suspend fun refresh(id: Int) = current
        override suspend fun approve(period: ApprovalPeriod, month: String): ApprovalPeriod {
            writes++
            gate?.await()
            check(!fail) { "HTTP 401" }
            return current.copy(approved = true).also { current = it }
        }
    }

    @Test fun `open select refresh never approve and double tap sends once`() = runTest {
        val gateway = Gateway()
        val controller = ApprovalController(gateway, this)
        controller.open("2026-08"); runCurrent()
        controller.select(101); runCurrent()
        controller.refresh(); runCurrent()
        assertEquals(0, gateway.writes)
        gateway.gate = CompletableDeferred()
        controller.approve(); controller.approve(); runCurrent()
        assertTrue(controller.state.value.busy)
        assertFalse(controller.state.value.selected!!.approved)
        assertEquals(1, gateway.writes)
        gateway.gate!!.complete(Unit); runCurrent()
        assertTrue(controller.state.value.selected!!.approved)
        controller.approve(); runCurrent()
        assertEquals(1, gateway.writes)
    }

    @Test fun `failure retains approval action but requires refresh before retry`() = runTest {
        val gateway = Gateway().apply { fail = true }
        val controller = ApprovalController(gateway, this)
        controller.open("2026-08"); runCurrent()
        controller.approve(); runCurrent()
        assertFalse(controller.state.value.selected!!.approved)
        assertTrue(controller.state.value.requiresRefresh)
        assertNotNull(controller.state.value.error)
        controller.approve(); runCurrent()
        assertEquals(1, gateway.writes)
        controller.refresh(); runCurrent()
        gateway.fail = false
        controller.approve(); runCurrent()
        assertTrue(controller.state.value.selected!!.approved)
    }
}

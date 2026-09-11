package com.numbawang.leimaus.approval

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ApprovalUiState(
    val month: String = "", val periods: List<ApprovalPeriod> = emptyList(),
    val selected: ApprovalPeriod? = null, val busy: Boolean = false,
    val error: String? = null, val requiresRefresh: Boolean = false,
)

/** Main-thread controller. Busy is set before launching so rapid taps cannot queue writes. */
class ApprovalController(private val gateway: ApprovalGateway, private val scope: CoroutineScope,
    private val onApproved: () -> Unit = {}) {
    private val mutable = MutableStateFlow(ApprovalUiState())
    val state = mutable.asStateFlow()

    fun open(month: String = ApprovalCalendar.previousMonth()) {
        if (mutable.value.busy) return
        mutable.value = ApprovalUiState(month = month, busy = true)
        scope.launch {
            try {
                val periods = gateway.load(month)
                val period = periods.firstOrNull { !it.approved } ?: periods.firstOrNull()
                val fresh = period?.let { gateway.refresh(it.id) }
                mutable.value = ApprovalUiState(month, periods, fresh)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                mutable.value = mutable.value.copy(busy = false, error = e.message ?: "Tietojen haku epäonnistui.")
            }
        }
    }

    fun select(id: Int) {
        val old = mutable.value
        if (old.busy || old.periods.none { it.id == id }) return
        mutable.value = old.copy(busy = true, error = null, requiresRefresh = true)
        scope.launch {
            try {
                val fresh = gateway.refresh(id)
                mutable.value = mutable.value.copy(selected = fresh, busy = false, requiresRefresh = false)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                mutable.value = mutable.value.copy(busy = false, error = e.message ?: "Tietojen haku epäonnistui.")
            }
        }
    }

    fun refresh() {
        val s = mutable.value
        if (s.selected != null) select(s.selected.id) else open(s.month)
    }

    fun approve() {
        val old = mutable.value
        val period = old.selected ?: return
        if (old.busy || old.requiresRefresh || !period.canApprove) return
        mutable.value = old.copy(busy = true, error = null)
        scope.launch {
            try {
                val confirmed = gateway.approve(period, old.month)
                check(confirmed.approved && confirmed.id == period.id) { "Hyväksyntää ei vahvistettu." }
                mutable.value = old.copy(selected = confirmed,
                    periods = old.periods.map { if (it.id == confirmed.id) confirmed else it })
                onApproved()
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                mutable.value = old.copy(error = e.message ?: "Hyväksyntä epäonnistui.", requiresRefresh = true)
            }
        }
    }
}

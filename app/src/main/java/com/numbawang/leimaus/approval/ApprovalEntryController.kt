package com.numbawang.leimaus.approval

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ApprovalEntryState { Unknown, Loading, Pending, NoPending, Unavailable }

/** No cached positive indication survives navigation, account changes or a failed read. */
class ApprovalEntryController(private val gateway: ApprovalGateway, private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(ApprovalEntryState.Unknown)
    val state = mutable.asStateFlow()
    private var generation = 0
    private var job: Job? = null

    fun invalidate() {
        generation++
        job?.cancel()
        mutable.value = ApprovalEntryState.Unknown
    }

    fun refresh(month: String = ApprovalCalendar.previousMonth()) {
        invalidate()
        val request = generation
        mutable.value = ApprovalEntryState.Loading
        job = scope.launch {
            try {
                val periods = gateway.load(month)
                if (request == generation) {
                    mutable.value = when {
                        periods.any { it.canApprove } -> ApprovalEntryState.Pending
                        periods.any { !it.stateKnown } -> ApprovalEntryState.Unavailable
                        else -> ApprovalEntryState.NoPending
                    }
                }
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) {
                if (request == generation) mutable.value = ApprovalEntryState.Unavailable
            }
        }
    }
}

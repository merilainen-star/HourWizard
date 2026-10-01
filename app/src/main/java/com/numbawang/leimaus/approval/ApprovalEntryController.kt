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
    private val mutableDiagnostic = MutableStateFlow(ApprovalDiagnostic())
    val diagnostic = mutableDiagnostic.asStateFlow()
    private var generation = 0
    private var job: Job? = null

    fun invalidate() {
        generation++
        job?.cancel()
        mutable.value = ApprovalEntryState.Unknown
        mutableDiagnostic.value = ApprovalDiagnostic()
    }

    fun refresh(month: String = ApprovalCalendar.previousMonth()) {
        invalidate()
        val request = generation
        mutable.value = ApprovalEntryState.Loading
        mutableDiagnostic.value = ApprovalDiagnostic("Tarkistetaan kuukauden $month hyväksyntää…")
        job = scope.launch {
            try {
                val periods = gateway.load(month)
                if (request == generation) {
                    mutable.value = when {
                        periods.any { it.awaitingApproval } -> ApprovalEntryState.Pending
                        periods.any { !it.stateKnown } -> ApprovalEntryState.Unavailable
                        else -> ApprovalEntryState.NoPending
                    }
                    val message = when {
                        periods.any { it.awaitingApproval } -> "Kuukaudelta $month on hyväksyntää odottava jakso."
                        periods.isEmpty() -> "Kuukaudelle $month ei löytynyt päättynyttä toteumajaksoa."
                        periods.any { !it.stateKnown } -> "Palvelu ei palauttanut kaikkia hyväksyntätilan tietoja."
                        periods.all { it.approved } -> "Kuukauden $month jaksot on hyväksytty."
                        else -> "Jakso on lukittu tai siirtynyt jatkokäsittelyyn."
                    }
                    val counts = "approved=${periods.count { it.approved }}\nlocked=${periods.count { it.locked }}" +
                        "\nunknown_state=${periods.count { !it.stateKnown }}" +
                        "\nmissing_employee=${periods.count { it.employeeId == null || it.employeeId <= 0 }}" +
                        "\nincomplete_totals=${periods.count { it.totals.size != 4 }}"
                    mutableDiagnostic.value = ApprovalDiagnostic(message,
                        "month=$month\nstate=${mutable.value}\n${gateway.diagnosticSummary()}\n$counts",
                        periods.isEmpty() || periods.any { !it.stateKnown })
                    if (periods.any { it.awaitingApproval && !it.canApprove }) {
                        mutableDiagnostic.value = mutableDiagnostic.value.copy(message =
                            "$message Yhteenvedon tiedot ovat puutteelliset; avaa näkymä tarkistamista varten.")
                    }
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                if (request == generation) {
                    mutable.value = ApprovalEntryState.Unavailable
                    val code = approvalReadFailureCode(e)
                    mutableDiagnostic.value = ApprovalDiagnostic(
                        "Tuntien hyväksyntätilaa ei voitu hakea ($code). Tämä ei tarkoita, että tunnit olisi hyväksytty.",
                        "month=$month\nstate=Unavailable\nerror=$code\n${gateway.diagnosticSummary()}", true)
                }
            }
        }
    }
}

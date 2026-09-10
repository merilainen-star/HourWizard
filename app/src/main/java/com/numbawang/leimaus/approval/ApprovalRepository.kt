package com.numbawang.leimaus.approval

import com.squareup.moshi.Moshi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ApprovalPeriod(
    val id: Int, val start: Long, val end: Long, val type: Int,
    val name: String, val totals: Map<String, String>, val approved: Boolean,
    val locked: Boolean, val stateKnown: Boolean, val employeeId: Int? = null,
) {
    val label: String get() = "${ApprovalCalendar.dateLabel(start)}–${ApprovalCalendar.dateLabel(end)}"
    val canApprove: Boolean get() = stateKnown && employeeId != null && employeeId > 0 && !approved && !locked && totals.size == 4
}

interface ApprovalGateway {
    suspend fun load(month: String): List<ApprovalPeriod>
    suspend fun refresh(id: Int): ApprovalPeriod
    suspend fun approve(period: ApprovalPeriod, month: String): ApprovalPeriod
}

/** Transport is injectable: tests never need credentials or a production connection. */
class ApprovalRepository(
    private val transport: suspend (payload: String, mutation: Boolean) -> String,
    private val currentMonth: () -> String = { ApprovalCalendar.previousMonth() },
) : ApprovalGateway {
    private val json = Moshi.Builder().build().adapter(Any::class.java)
    private val approvalMutex = Mutex()

    override suspend fun load(month: String): List<ApprovalPeriod> {
        check(month <= currentMonth()) { "Keskeneräisen kuukauden tunteja ei voi hyväksyä." }
        val range = ApprovalCalendar.monthRange(month)
        val data = request(LIST, mapOf("from" to range.first, "to" to range.last))
        val shifts = data["tyovuorot"].obj()
        checkErrors(shifts)
        val periods = (shifts["jaksot"] as? List<*>) ?: error("Jaksojen tiedot puuttuvat.")
        return periods.map { parse(it.obj()) }.filter { eligible(it, month) }
            .distinctBy { it.id }.sortedByDescending { it.end }
    }

    override suspend fun refresh(id: Int): ApprovalPeriod =
        parse(request(READ, mapOf("jaksoid" to id))["jaksoById"].obj()).also {
            check(it.id == id) { "Palvelin palautti eri jakson." }
        }

    override suspend fun approve(period: ApprovalPeriod, month: String): ApprovalPeriod = approvalMutex.withLock {
        check(eligible(period, month)) { "Jakso ei kuulu päättyneeseen kuukauteen." }
        val fresh = refresh(period.id)
        check(eligible(fresh, month)) { "Jakson päivämäärät ovat muuttuneet." }
        if (fresh.approved) return@withLock fresh
        check(fresh.canApprove) { "Jaksoa ei voi hyväksyä. Päivitä tiedot." }
        check(fresh == period) { "Jakson tiedot muuttuivat. Päivitä ja tarkista yhteenveto ennen hyväksyntää." }
        val result = request(APPROVE, mapOf("jaksoid" to period.id, "value" to true), true)["hyvaksyJakso"].obj()
        checkErrors(result)
        val confirmed = parse(result["jakso"].obj())
        check(confirmed.id == fresh.id && confirmed.employeeId == fresh.employeeId && confirmed.start == fresh.start && confirmed.end == fresh.end && confirmed.approved) {
            "Palvelin ei vahvistanut hyväksyntää. Päivitä tiedot ennen uutta yritystä."
        }
        confirmed
    }

    private fun eligible(p: ApprovalPeriod, month: String): Boolean = month <= currentMonth() &&
        p.type == 2 && p.start <= p.end && p.end in ApprovalCalendar.monthRange(month)

    private suspend fun request(query: String, variables: Map<String, Any>, mutation: Boolean = false): Map<String, Any?> {
        val body = json.toJson(listOf(mapOf("query" to query, "variables" to variables)))
        val envelope = (json.fromJson(transport(body, mutation)) as? List<*>)?.singleOrNull().obj()
        checkErrors(envelope)
        return envelope["data"].obj().also { check(it.isNotEmpty()) { "Palvelimen vastaus puuttuu." } }
    }

    private fun checkErrors(obj: Map<String, Any?>) {
        val errors = obj["errors"]
        check(errors == null || errors is List<*> && errors.isEmpty()) {
            (errors as? List<*>)?.joinToString("; ") { it.obj()["message"]?.toString() ?: "Palvelinvirhe" }
                ?: "Palvelin palautti virheen."
        }
    }

    private fun parse(obj: Map<String, Any?>): ApprovalPeriod {
        fun number(key: String) = (obj[key] as? Number)?.toLong() ?: error("Jakson $key puuttuu.")
        val state = obj["jaksotila"].obj()
        val totals = obj["kertyma"].obj()
        val values = listOf("tv_tot", "tv_luetut", "tase", "tyopaivat").mapNotNull { key ->
            val value = totals[key]
            // The official UI renders these scalars directly. Never assume seconds or sum punches.
            when (value) {
                is String -> value.takeIf { it.isNotBlank() }?.let { key to it }
                is Number -> key to if (value.toDouble() == value.toLong().toDouble()) value.toLong().toString() else value.toString()
                else -> null
            }
        }.toMap()
        return ApprovalPeriod(number("jaksoid").toInt(), number("alku"), number("loppu"),
            number("jaksotyyppiid").toInt(), obj["jakso"]?.toString().orEmpty(), values,
            state["hyvaksytty"] is Map<*, *>,
            listOf("tarkistettu", "valmistettu", "siirretty").any { state[it] != null } || state["jaksoEnabled"] == false,
            listOf("hyvaksytty", "tarkistettu", "valmistettu", "siirretty").all { state.containsKey(it) },
            (obj["henkiloid"] as? Number)?.toInt())
    }

    @Suppress("UNCHECKED_CAST")
    private fun Any?.obj(): Map<String, Any?> = this as? Map<String, Any?> ?: emptyMap()

    companion object {
        // Verified against Finago Mobiili's public main.9577daff7ecc85670913.js, 2026-09-10.
        private const val FIELDS = """jaksoid alku loppu jakso jaksotyyppiid henkiloid
            kertyma { tv_tot tv_luetut tase tyopaivat }
            jaksotila { hyvaksytty { muokattu muokkaaja } tarkistettu { muokattu }
                valmistettu { muokattu } siirretty { muokattu } jaksoEnabled }"""
        val LIST = """query kellokorttiTyovuorot(${'$'}from: Int!, ${'$'}to: Int!) {
            tyovuorot(from: ${'$'}from, to: ${'$'}to, skipRealtimeInterval: false) { jaksot { $FIELDS } errors { message } }
        }"""
        val READ = """query jaksoById(${'$'}jaksoid: Int!) { jaksoById(jaksoid: ${'$'}jaksoid) { $FIELDS } }"""
        val APPROVE = """mutation hyvaksyJakso(${'$'}jaksoid: Int!, ${'$'}value: Boolean!) {
            hyvaksyJakso(jaksoid: ${'$'}jaksoid, value: ${'$'}value) { jakso { $FIELDS } errors { message } }
        }"""
    }
}

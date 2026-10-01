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
    // Opening the review must not depend on whether every total needed for a write is available.
    val awaitingApproval: Boolean get() = stateKnown && !approved && !locked
    val canApprove: Boolean get() = stateKnown && employeeId != null && employeeId > 0 && !approved && !locked && totals.size == 4
}

interface ApprovalGateway {
    fun diagnosticSummary(): String = ""
    suspend fun load(month: String): List<ApprovalPeriod>
    suspend fun refresh(id: Int): ApprovalPeriod
    suspend fun refresh(period: ApprovalPeriod): ApprovalPeriod = refresh(period.id)
    suspend fun approve(period: ApprovalPeriod, month: String): ApprovalPeriod
}

/** Transport is injectable: tests never need credentials or a production connection. */
class ApprovalRepository(
    private val transport: suspend (payload: String, mutation: Boolean) -> String,
    private val currentMonth: () -> String = { ApprovalCalendar.previousMonth() },
) : ApprovalGateway {
    private val json = Moshi.Builder().build().adapter(Any::class.java)
    private val approvalMutex = Mutex()
    private var readDiagnostic = ""
    override fun diagnosticSummary(): String = readDiagnostic

    override suspend fun load(month: String): List<ApprovalPeriod> {
        readDiagnostic = "stage=period_list"
        check(month <= currentMonth()) { "Keskeneräisen kuukauden tunteja ei voi hyväksyä." }
        val range = ApprovalCalendar.monthRange(month)
        val data = request(LIST, mapOf("from" to range.first, "to" to range.last))
        val shifts = data["tyovuorot"].obj()
        checkErrors(shifts)
        val periods = (shifts["jaksot"] as? List<*>) ?: error("Jaksojen tiedot puuttuvat.")
        readDiagnostic += "\nreceived=${periods.size}"
        val parsed = periods.map { parse(it.obj()) }
        val selected = parsed.filter { eligible(it, month) }.distinctBy { it.id }.sortedByDescending { it.end }
        readDiagnostic += "\ntypes=" + parsed.groupingBy { it.type }.eachCount().toSortedMap()
            .entries.joinToString(",") { "${it.key}:${it.value}" }
        readDiagnostic += "\nexcluded_type=${parsed.count { it.type != 2 }}" +
            "\nexcluded_dates=${parsed.count { it.type == 2 && (it.start > it.end || it.end !in range) }}" +
            "\nmatched=${selected.size}"
        return selected
    }

    override suspend fun refresh(id: Int): ApprovalPeriod = readPeriod(id, null)

    override suspend fun refresh(period: ApprovalPeriod): ApprovalPeriod =
        readPeriod(period.id, period.employeeId).also {
            check(period.employeeId == null || it.employeeId == period.employeeId) {
                "Jakson henkilö muuttui. Päivitä tiedot."
            }
        }

    private suspend fun readPeriod(id: Int, employeeId: Int?): ApprovalPeriod =
        parse(request(READ, buildMap {
            put("jaksoid", id)
            employeeId?.let { put("henkiloid", it) }
        })["jaksoById"].obj()).also {
            check(it.id == id) { "Palvelin palautti eri jakson." }
        }

    override suspend fun approve(period: ApprovalPeriod, month: String): ApprovalPeriod = approvalMutex.withLock {
        check(eligible(period, month)) { "Jakso ei kuulu päättyneeseen kuukauteen." }
        val fresh = refresh(period)
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
        if (!(errors == null || errors is List<*> && errors.isEmpty())) {
            val message = (errors as? List<*>)?.joinToString("; ") { it.obj()["message"]?.toString() ?: "Palvelinvirhe" }
                ?: "Palvelin palautti virheen."
            val rejectedField = listOf("tyovuorot", "jaksot", "errors", "jaksoid", "alku", "loppu", "jakso", "jaksotyyppiid",
                "henkiloid", "jaksohenkilo", "kertyma", "tv_tot", "tv_luetut", "tase", "tyopaivat", "jaksotila", "hyvaksytty",
                "tarkistettu", "valmistettu", "siirretty", "jaksoEnabled", "muokattu", "muokkaaja")
                .firstOrNull { message.contains("Cannot query field \"$it\"") }
            throw ApprovalServiceException(message, rejectedField?.let { "GRAPHQL_FIELD_$it" } ?: "GRAPHQL_ERROR")
        }
    }

    private fun parse(obj: Map<String, Any?>): ApprovalPeriod {
        fun number(key: String) = (obj[key] as? Number)?.toLong() ?: error("Jakson $key puuttuu.")
        val state = obj["jaksotila"].obj()
        val totals = obj["kertyma"].obj()
        val directEmployee = (obj["henkiloid"] as? Number)?.toInt()?.takeIf { it > 0 }
        val periodEmployee = (obj["jaksohenkilo"].obj()["henkiloid"] as? Number)?.toInt()?.takeIf { it > 0 }
        check(directEmployee == null || periodEmployee == null || directEmployee == periodEmployee) {
            "Jakson henkilötunnisteet eivät täsmää."
        }
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
            periodEmployee ?: directEmployee)
    }

    @Suppress("UNCHECKED_CAST")
    private fun Any?.obj(): Map<String, Any?> = this as? Map<String, Any?> ?: emptyMap()

    companion object {
        // Verified against Finago Mobiili's public main.9577daff7ecc85670913.js, 2026-09-10.
        private const val FIELDS = """jaksoid alku loppu jakso jaksotyyppiid henkiloid
            jaksohenkilo { henkiloid }
            kertyma { tv_tot tv_luetut tase tyopaivat }
            jaksotila { hyvaksytty { muokattu muokkaaja } tarkistettu { muokattu }
                valmistettu { muokattu } siirretty { muokattu } jaksoEnabled }"""
        val LIST = """query kellokorttiTyovuorot(${'$'}from: Int!, ${'$'}to: Int!) {
            tyovuorot(from: ${'$'}from, to: ${'$'}to, skipRealtimeInterval: false) { jaksot { $FIELDS } errors { message } }
        }"""
        val READ = """query jaksoById(${'$'}jaksoid: Int!, ${'$'}henkiloid: Int) {
            jaksoById(jaksoid: ${'$'}jaksoid, henkiloid: ${'$'}henkiloid) { $FIELDS }
        }"""
        val APPROVE = """mutation hyvaksyJakso(${'$'}jaksoid: Int!, ${'$'}value: Boolean!) {
            hyvaksyJakso(jaksoid: ${'$'}jaksoid, value: ${'$'}value) { jakso { $FIELDS } errors { message } }
        }"""
    }
}

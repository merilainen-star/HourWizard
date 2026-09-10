package com.numbawang.leimaus.approval

import com.squareup.moshi.Moshi

internal val approvalJson = Moshi.Builder().build().adapter(Any::class.java).serializeNulls()

internal fun periodJson(
    id: Int = 101, month: String = "2026-08", approved: Boolean = false,
    locked: Boolean = false, total: Any? = "152:30", type: Int = 2,
): Map<String, Any?> {
    val range = ApprovalCalendar.monthRange(month)
    return mapOf("jaksoid" to id, "alku" to range.first, "loppu" to range.last,
        "jaksotyyppiid" to type, "henkiloid" to 202, "jakso" to "Testijakso",
        "kertyma" to mapOf("tv_tot" to total, "tv_luetut" to "151:45", "tase" to "-0:45", "tyopaivat" to 20),
        "jaksotila" to mapOf("hyvaksytty" to if (approved) mapOf("muokattu" to 1788300000) else null,
            "tarkistettu" to if (locked) mapOf("muokattu" to 1788300000) else null,
            "valmistettu" to null, "siirretty" to null, "jaksoEnabled" to true))
}

internal fun envelope(field: String, value: Any?) = approvalJson.toJson(listOf(mapOf("data" to mapOf(field to value))))

internal fun samplePeriod(): ApprovalPeriod {
    val range = ApprovalCalendar.monthRange("2026-08")
    return ApprovalPeriod(101, range.first, range.last, 2, "Testijakso",
        mapOf("tv_tot" to "152:30", "tv_luetut" to "151:45", "tase" to "-0:45", "tyopaivat" to "20"),
        false, false, true, 202)
}

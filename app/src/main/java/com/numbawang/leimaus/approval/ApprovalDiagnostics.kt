package com.numbawang.leimaus.approval

import java.io.IOException

data class ApprovalDiagnostic(
    val message: String = "Tilaa ei ole vielä tarkistettu.",
    val report: String = "", val needsAttention: Boolean = false,
)

class ApprovalServiceException(message: String, val diagnosticCode: String) : IllegalStateException(message)

/** Reports contain only fixed codes and aggregate counts, never raw server errors or responses. */
fun approvalReadFailureCode(error: Exception): String {
    if (error is ApprovalServiceException) return error.diagnosticCode
    val message = error.message.orEmpty()
    Regex("HTTP (\\d{3})").find(message)?.let { return "HTTP_${it.groupValues[1]}" }
    if (error is com.squareup.moshi.JsonDataException || error is com.squareup.moshi.JsonEncodingException) return "JSON_SHAPE"
    if (error is IOException) return "NETWORK"
    for (field in listOf("jaksoid", "alku", "loppu", "jaksotyyppiid")) {
        if (message == "Jakson $field puuttuu.") return "MISSING_$field"
    }
    return when (message) {
        "Tuntien hyväksyntä ei ole käytössä demotilassa." -> "DEMO_MODE"
        "Syötä tunnukset asetuksissa." -> "NO_CREDENTIALS"
        "Tili vaihtui. Päivitä tiedot." -> "ACCOUNT_CHANGED"
        "Jaksojen tiedot puuttuvat." -> "MISSING_PERIOD_LIST"
        "Palvelimen vastaus puuttuu." -> "MISSING_DATA"
        else -> "READ_FAILED"
    }
}

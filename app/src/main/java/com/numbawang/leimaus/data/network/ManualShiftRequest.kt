package com.numbawang.leimaus.data.network

/** Follow the native form's employee and work-quality default marker selection. */
fun manualShiftVariables(shift: ManualShift, context: GraphQLDataPayload): Map<String, Any> {
    val employee = requireNotNull(context.userProfile?.henkiloid) { "Työntekijän tunniste puuttuu. Työvuoroa ei lähetetty." }
    val defaults = requireNotNull(context.kellokortti?.selectiondefaults) { "Työvuoron oletustiedot puuttuvat." }
    val workplace = requireNotNull(defaults.tyopisteid) { "Työpiste puuttuu." }
    val quality = requireNotNull(defaults.talaatuid) { "Työn laatu puuttuu." }
    val marker = requireNotNull(context.talaadut?.find { it.value == quality }?.defaultType) {
        "Työn laadun työaikamerkintä puuttuu. Työvuoroa ei lähetetty."
    }
    require(context.tvmerkinnat?.any { it.value == marker } == true) { "Työaikamerkintä ei ole käytettävissä. Työvuoroa ei lähetetty." }
    return mapOf(
        "tyyppi" to "tot", "henkiloid" to employee, "tyopisteid" to workplace,
        "talaatuid" to quality, "tvmerkintaid" to marker,
        "alku" to shift.startSeconds, "loppu" to shift.endSeconds,
        // Native form initializes the break start at the workshift day's UTC midnight.
        "taukoalku" to Math.floorDiv(shift.startSeconds, 86400L) * 86400L,
        "taukokesto" to shift.breakMinutes * 60, "tyontekijalukumaara" to 1,
        "tietoja" to "Jälkikäteen kirjattu työvuoro"
    )
}

fun manualShiftError(messages: List<String>): String {
    val detail = messages.joinToString("; ")
    return if (messages.any { it.contains("Sentry\\") && it.contains("clientReport") }) {
        "Tuntivelhon palvelimen virheraportointi epäonnistui. Tallennuksen tulosta ei voitu varmistaa. " +
            "Tarkista vuoro verkkopalvelusta ennen uutta yritystä.\nTekninen virhe: $detail"
    } else detail
}

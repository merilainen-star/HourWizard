package com.numbawang.leimaus.achievements

data class Achievement(
    val id: String,
    val emoji: String,
    val title: String,
    val description: String
)

object Achievements {
    val ALL: List<Achievement> = listOf(
        Achievement(
            id = "first_stamp",
            emoji = "🥇",
            title = "Ensimmäinen leima",
            description = "Ensimmäinen leimaus kirjattu sovellukseen."
        ),
        Achievement(
            id = "on_a_roll",
            emoji = "🔥",
            title = "Putkessa",
            description = "5 työpäivää putkeen sisään- ja ulosleimattuna."
        ),
        Achievement(
            id = "early_bird",
            emoji = "🌅",
            title = "Aamuvirkku",
            description = "Sisäänleimattu ennen klo 07:30 viitenä päivänä putkeen."
        ),
        Achievement(
            id = "clockwork",
            emoji = "⏰",
            title = "Kelloseppä",
            description = "Sisäänleimaus samaan minuuttiin (±2 min) viitenä päivänä putkeen."
        ),
        Achievement(
            id = "right_on_the_dot",
            emoji = "🎯",
            title = "Napakymppi",
            description = "Päivän tunnit osuivat ±5 minuutin päähän tavoitteesta."
        ),
        Achievement(
            id = "dead_even",
            emoji = "⚖️",
            title = "Tasan tarkkaan",
            description = "Tuntitase osui tasan nollaan, 0:00."
        ),
        Achievement(
            id = "overtime_hero",
            emoji = "💪",
            title = "Ylityösankari",
            description = "Tuntitase nousi yli +10:00."
        ),
        Achievement(
            id = "comeback",
            emoji = "📈",
            title = "Takaisin plussalle",
            description = "Tuntitase kääntyi miinukselta plussan puolelle."
        ),
        Achievement(
            id = "night_owl",
            emoji = "🦉",
            title = "Yökyöpeli",
            description = "Ulosleimaus tehty klo 22:00 jälkeen."
        ),
        Achievement(
            id = "century_club",
            emoji = "💯",
            title = "Sadan klubi",
            description = "100. leimaus kirjattu sovellukseen."
        )
    )

    val byId: Map<String, Achievement> = ALL.associateBy { it.id }
}

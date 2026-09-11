package com.numbawang.leimaus.data.network

object GraphQLQueries {
    // Verified from Finago Mobiili main.9577daff7ecc85670913.js, 2026-09-11.
    // tot = actual workshift. This does not change the live clock-card direction.
    const val ADD_MANUAL_SHIFT = """
        mutation tyovuoroAdd(${'$'}tyyppi: TyovuoroEnumType!, ${'$'}tyopisteid: Int!,
          ${'$'}talaatuid: Int!, ${'$'}alku: Int!, ${'$'}loppu: Int!,
          ${'$'}taukokesto: Int, ${'$'}tietoja: String) {
          tyovuoroAdd(tyyppi: ${'$'}tyyppi, tyopisteid: ${'$'}tyopisteid,
            talaatuid: ${'$'}talaatuid, alku: ${'$'}alku, loppu: ${'$'}loppu,
            taukokesto: ${'$'}taukokesto, tietoja: ${'$'}tietoja) {
            tyovuoro { id alku loppu }
            errors { message }
          }
        }
    """
    
    // LOGIN_MUTATION
    const val LOGIN_MUTATION = """
        mutation login(${'$'}kayttajatunnus: String!, ${'$'}salasana: String!, ${'$'}subuser: Boolean, ${'$'}kieliid: Int) {
          login(kayttajatunnus: ${'$'}kayttajatunnus, salasana: ${'$'}salasana, subuser: ${'$'}subuser, kieliid: ${'$'}kieliid) {
            henkiloid
            success
            nimi
            token
            errors {
              message
            }
          }
        }
    """
    
    // KELLOKORTTI_QUERY (defaults + previousstamp)
    const val KELLOKORTTI_QUERY = """
        query kellokortti(${'$'}subuser: Int) {
          kellokortti(subuser: ${'$'}subuser) {
            previousstamp {
              tv_leimaid
              aika
              suuntaid
            }
            selectiondefaults {
              talaatuid
              tyopisteid
            }
            talaadut {
              talaatuid
            }
          }
        }
    """
    
    // PUNCH_MUTATION (leimaTallenna)
    const val PUNCH_MUTATION = """
        mutation leimaTallenna(${'$'}input: LeimaInput!, ${'$'}subuser: Int, ${'$'}withStamps: Boolean = true) {
          leimaTallenna(input: ${'$'}input, subuser: ${'$'}subuser) {
            previousstamp {
              tv_leimaid
              aika
              suuntaid
              talaatuid
              tyopisteid
              tyolajiid
            }
            previousstamps @include(if: ${'$'}withStamps) {
              henkiloid
              tv_leimaid
              aika
              suuntaid
            }
            selectiondefaults {
              talaatuid
              tyopisteid
              tyolajiid
            }
            errors {
              message
            }
          }
        }
    """
    
    // BALANCE_QUERY
    const val BALANCE_QUERY = """
        query kellokortti(${'$'}subuser: Int) {
          kellokortti(subuser: ${'$'}subuser) {
            tase {
              tase
            }
          }
        }
    """

    fun buildLoginPayload(username: String, passwordText: String): String {
        val cleanUser = username.replace("\\", "\\\\").replace("\"", "\\\"")
        val cleanPass = passwordText.replace("\\", "\\\\").replace("\"", "\\\"")
        return """
            [
              {
                "query": "mutation login(${'$'}kayttajatunnus: String!, ${'$'}salasana: String!, ${'$'}subuser: Boolean, ${'$'}kieliid: Int) { login(kayttajatunnus: ${'$'}kayttajatunnus, salasana: ${'$'}salasana, subuser: ${'$'}subuser, kieliid: ${'$'}kieliid) { henkiloid success nimi token errors { message } } }",
                "variables": {
                  "kayttajatunnus": "$cleanUser",
                  "salasana": "$cleanPass",
                  "subuser": false,
                  "kieliid": 1
                }
              }
            ]
        """.trimIndent()
    }

    fun buildKellokorttiDefaultsPayload(): String {
        return """
            [
              {
                "query": "query kellokortti(${'$'}subuser: Int) { kellokortti(subuser: ${'$'}subuser) { previousstamp { tv_leimaid aika suuntaid } selectiondefaults { talaatuid tyopisteid } talaadut { talaatuid } } }",
                "variables": {
                  "subuser": null
                }
              }
            ]
        """.trimIndent()
    }

    fun buildPunchPayload(
        type: String,
        talaatuid: Int,
        tyopisteid: Int,
        leimausaikaSec: Long = System.currentTimeMillis() / 1000
    ): String {
        val tapahtuma = when (type) {
            "in" -> "sisaan"
            "break_start" -> "tauolle"
            "break_end" -> "tauolta"
            else -> "ulos"
        }
        return """
            [
              {
                "query": "mutation leimaTallenna(${'$'}input: LeimaInput!, ${'$'}subuser: Int, ${'$'}withStamps: Boolean = true) { leimaTallenna(input: ${'$'}input, subuser: ${'$'}subuser) { previousstamp { tv_leimaid aika suuntaid talaatuid tyopisteid tyolajiid } previousstamps @include(if: ${'$'}withStamps) { henkiloid tv_leimaid aika suuntaid } selectiondefaults { talaatuid tyopisteid tyolajiid } errors { message } } }",
                "variables": {
                  "input": {
                    "tietoja": "",
                    "talaatuid": $talaatuid,
                    "tyopisteid": $tyopisteid,
                    "tyolajiid": null,
                    "polaatuid": null,
                    "leimausaika": $leimausaikaSec,
                    "tapahtuma": "$tapahtuma"
                  },
                  "subuser": null,
                  "withStamps": true
                }
              }
            ]
        """.trimIndent()
    }

    fun buildKellokorttiFullPayload(): String {
        return """
            [
              {
                "query": "query kellokortti(${'$'}subuser: Int) { kellokortti(subuser: ${'$'}subuser) { previousstamp { tv_leimaid aika suuntaid } selectiondefaults { talaatuid tyopisteid } tase { tase } } }",
                "variables": {
                  "subuser": null
                }
              }
            ]
        """.trimIndent()
    }

    fun buildBalancePayload(): String {
        return """
            [
              {
                "query": "query kellokortti(${'$'}subuser: Int) { kellokortti(subuser: ${'$'}subuser) { tase { tase } } }",
                "variables": {
                  "subuser": null
                }
              }
            ]
        """.trimIndent()
    }
}


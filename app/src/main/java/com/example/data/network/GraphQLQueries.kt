package com.example.data.network

object GraphQLQueries {
    
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
        val tapahtuma = if (type == "in") "sisaan" else "ulos"
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


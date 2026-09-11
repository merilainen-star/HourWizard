package com.numbawang.leimaus.data.network

import android.util.Log
import com.numbawang.leimaus.data.db.StampDao
import com.numbawang.leimaus.data.db.StampEntity
import com.numbawang.leimaus.data.preferences.DEFAULT_URL
import com.numbawang.leimaus.data.preferences.FALLBACK_GRAPHQL_URL
import com.numbawang.leimaus.data.preferences.UserPreferencesRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

sealed class StampResult {
    data class Success(val message: String, val timestamp: Long = System.currentTimeMillis(), val balanceStr: String = "") : StampResult()
    data class Error(val errorMessage: String) : StampResult()
}

class TimecardRepository(
    private val prefsRepository: UserPreferencesRepository,
    private val stampDao: StampDao
) {

    val rawApiResponse = kotlinx.coroutines.flow.MutableStateFlow<String>("Ei vielä hakuja tehty.")

    private val cookieMap = java.util.concurrent.ConcurrentHashMap<String, MutableMap<String, Cookie>>()

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    if (cookies.isNotEmpty()) {
                        val map = cookieMap.getOrPut(url.host) { java.util.concurrent.ConcurrentHashMap() }
                        for (cookie in cookies) {
                            map[cookie.name] = cookie
                        }
                    }
                }

                override fun loadForRequest(url: HttpUrl): List<Cookie> {
                    val map = cookieMap[url.host] ?: return emptyList()
                    val now = System.currentTimeMillis()
                    return map.values.filter { it.expiresAt > now }
                }
            })
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    suspend fun login(username: String, passwordText: String, persistCredentials: Boolean = true): StampResult = withContext(Dispatchers.IO) {
        val settings = prefsRepository.settings.value

        if (settings.isDemoMode) {
            return@withContext StampResult.Success(
                message = "Kirjautuminen onnistui (Demotila).",
                timestamp = System.currentTimeMillis()
            )
        }

        if (username.isBlank() || passwordText.isBlank()) {
            return@withContext StampResult.Error("Syötä käyttäjätunnus ja salasana.")
        }

        val baseClean = formatBaseUrl(settings.serverUrl)
        val candidateUrls = buildCandidateUrls(baseClean)

        var lastError = ""

        for (targetUrl in candidateUrls) {
            val payload = GraphQLQueries.buildLoginPayload(username, passwordText)
            val jsonMediaType = "application/json; charset=utf-8".toMediaType()

            try {
                val request = Request.Builder()
                    .url(targetUrl)
                    .post(payload.toRequestBody(jsonMediaType))
                    .addHeaders()
                    .build()

                val response = client.newCall(request).execute()
                val code = response.code
                val responseBody = response.body?.string() ?: ""
                response.close()

                if (code in 200..303) {
                    val gqlResp = parseGraphQLResponse(responseBody)
                    val loginData = gqlResp?.data?.login

                    if (loginData?.success == true) {
                        // A background approval-status read must not restore an account that
                        // the user changed while the request was in flight.
                        if (!persistCredentials) {
                            val current = prefsRepository.settings.value
                            if (current.username != username || current.serverUrl != settings.serverUrl ||
                                current.isDemoMode || prefsRepository.getPassword() != passwordText) {
                                return@withContext StampResult.Error("Tili vaihtui. Päivitä tiedot.")
                            }
                        }
                        val token = loginData.token ?: ""
                        if (token.isNotBlank()) {
                            prefsRepository.saveAuthToken(token)
                        }
                        if (persistCredentials) prefsRepository.saveCredentials(username, passwordText)

                        return@withContext StampResult.Success(
                            message = "Kirjautuminen Tuntivelhoon onnistui!",
                            timestamp = System.currentTimeMillis()
                        )
                    } else {
                        val errMsg = loginData?.errors?.joinToString("; ") { it.message }?.takeIf { it.isNotBlank() }
                            ?: gqlResp?.errors?.joinToString("; ") { it.message }?.takeIf { it.isNotBlank() }
                            ?: "Kirjautuminen epäonnistui: Käyttäjätunnus tai salasana on virheellinen."
                        return@withContext StampResult.Error(errMsg)
                    }
                } else if (code == 404) {
                    lastError = "Palvelin palautti koodin HTTP 404 osoitteessa $targetUrl"
                    continue // Try next candidate URL only on 404
                } else {
                    return@withContext StampResult.Error("Kirjautuminen epäonnistui osoitteessa $targetUrl (HTTP $code).")
                }
            } catch (e: IOException) {
                lastError = "Yhteysvirhe ($targetUrl): ${e.localizedMessage}"
                continue // Try next candidate URL on connection error
            } catch (e: Exception) {
                return@withContext StampResult.Error("Virhe kirjautumisessa ($targetUrl): ${e.localizedMessage}")
            }
        }

        return@withContext StampResult.Error(
            if (lastError.isNotBlank()) lastError else "Kirjautuminen epäonnistui kaikissa päätepisteissä."
        )
    }

    suspend fun testLogin(customServerUrl: String? = null): StampResult = withContext(Dispatchers.IO) {
        val settings = prefsRepository.settings.value
        val password = prefsRepository.getPassword()

        if (settings.isDemoMode) {
            return@withContext StampResult.Success(
                message = "Kirjautuminen testattu onnistuneesti (Demotila).",
                timestamp = System.currentTimeMillis()
            )
        }

        login(settings.username, password)
    }

    suspend fun clockIn(): StampResult = executePunch("in")

    private var approvalEndpoint: String? = null

    /** Approval writes are never retried or redirected after an uncertain network outcome. */
    suspend fun approvalRequest(payload: String, mutation: Boolean): String = withContext(Dispatchers.IO) {
        val settings = prefsRepository.settings.value
        check(!settings.isDemoMode) { "Tuntien hyväksyntä ei ole käytössä demotilassa." }
        check(settings.username.isNotBlank() && prefsRepository.getPassword().isNotBlank()) {
            "Syötä tunnukset asetuksissa."
        }
        if (!mutation) {
            val result = login(settings.username, prefsRepository.getPassword(), persistCredentials = false)
            check(result is StampResult.Success) { (result as StampResult.Error).errorMessage }
        }
        val token = prefsRepository.getAuthToken()
        check(token.isNotBlank()) { "Kirjaudu uudelleen." }
        val urls = if (mutation) listOf(checkNotNull(approvalEndpoint) { "Päivitä jakson tiedot." })
            else buildCandidateUrls(formatBaseUrl(settings.serverUrl))
        val approvalClient = client.newBuilder().retryOnConnectionFailure(false)
            .followRedirects(false).followSslRedirects(false).build()
        var lastError: IOException? = null
        for ((index, url) in urls.withIndex()) {
            try {
                val request = Request.Builder().url(url).addHeaders(token)
                    .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType())).build()
                approvalClient.newCall(request).execute().use { response ->
                    if (!mutation && response.code == 404 && index < urls.lastIndex) return@use
                    check(response.isSuccessful) { "Palvelinvirhe HTTP ${response.code}. Päivitä tiedot." }
                    val body = response.body?.string() ?: error("Palvelimen vastaus puuttuu.")
                    if (!mutation) approvalEndpoint = url
                    return@withContext body
                }
            } catch (e: IOException) {
                if (mutation) throw IOException("Hyväksynnän tulos jäi epäselväksi. Päivitä tiedot ennen uutta yritystä.", e)
                lastError = e
            }
        }
        throw IOException("Jakson haku epäonnistui. Tarkista verkkoyhteys.", lastError)
    }

    suspend fun clockOut(): StampResult = executePunch("out")

    suspend fun breakStart(): StampResult = executePunch("break_start")

    suspend fun breakEnd(): StampResult = executePunch("break_end")

    private suspend fun executePunch(type: String): StampResult = withContext(Dispatchers.IO) {
        val settings = prefsRepository.settings.value
        val password = prefsRepository.getPassword()
        val now = System.currentTimeMillis()
        val isClockIn = (type == "in")
        val isClockOut = (type == "out")
        val isBreakStart = (type == "break_start")
        val isBreakEnd = (type == "break_end")
        val actionTitle = when (type) {
            "in" -> "SISÄÄN"
            "break_start" -> "TAUOLLE"
            "break_end" -> "TAUOLTA"
            else -> "ULOS"
        }
        val helsinkiTz = TimeZone.getTimeZone("Europe/Helsinki")
        val utcTz = TimeZone.getTimeZone("UTC")
        val timeFormat = SimpleDateFormat("HH.mm", Locale.getDefault()).apply { timeZone = helsinkiTz }
        val fullTimeFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).apply { timeZone = helsinkiTz }
        val utcTimeFormat = SimpleDateFormat("HH.mm", Locale.getDefault()).apply { timeZone = utcTz }
        val utcFullTimeFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).apply { timeZone = utcTz }

        val timeStr = timeFormat.format(Date(now))
        val fullTimeStr = fullTimeFormat.format(Date(now))

        if (settings.isDemoMode || settings.username.isBlank()) {
            val demoMsg = when {
                isClockIn -> "Sisäänleimaus tehty klo $timeStr (Demotila)."
                isBreakStart -> "Tauolle klo $timeStr (Demotila)."
                isBreakEnd -> "Tauolta takaisin klo $timeStr (Demotila)."
                else -> {
                    val balanceStr = calculateBalance(settings.clockInTimestamp, now, settings.requiredElapsedMinutes(now))
                    "Ulosleimaus tehty klo $timeStr (Demotila). Saldo: $balanceStr"
                }
            }
            when {
                isClockIn -> prefsRepository.updateClockInStatus(isClockedIn = true, clockInTs = now)
                isClockOut -> prefsRepository.updateClockInStatus(isClockedIn = false, clockInTs = 0L)
                // Break stamps keep the running clock-in session intact
                else -> if (isBreakStart) prefsRepository.startBreak(now) else prefsRepository.endBreak(now)
            }
            stampDao.insertLog(
                StampEntity(
                    timestamp = now,
                    formattedTime = fullTimeStr,
                    actionType = actionTitle,
                    isSuccess = true,
                    message = demoMsg,
                    balance = "",
                    rawDetails = "DEMOTILA: Leimaus simuloitu offline-tilassa (ei verkkokutsua palvelimelle)."
                )
            )
            return@withContext StampResult.Success(demoMsg, now)
        }

        // Always log in first to establish PHP session cookies and fresh Bearer token (matching numbawang_api.py)
        if (settings.username.isNotBlank() && password.isNotBlank()) {
            val loginResult = login(settings.username, password)
            if (loginResult is StampResult.Error) {
                recordLog(now, fullTimeStr, actionTitle, isSuccess = false, message = loginResult.errorMessage)
                return@withContext StampResult.Error("Kirjautuminen epäonnistui: ${loginResult.errorMessage}")
            }
        }

        var token = prefsRepository.getAuthToken()
        if (token.isBlank()) {
            return@withContext StampResult.Error("Autentikointitokeni puuttuu. Kirjaudu uudelleen.")
        }

        val baseClean = formatBaseUrl(settings.serverUrl)
        val candidateUrls = buildCandidateUrls(baseClean)

        var lastError = ""

        for (targetUrl in candidateUrls) {
            // Step 1: Query KELLOKORTTI_QUERY for selectiondefaults (talaatuid, tyopisteid)
            val defaults = fetchKellokorttiDefaults(targetUrl, token)
            val talaatuid = defaults?.talaatuid ?: prefsRepository.getTalaatuid()
            val tyopisteid = defaults?.tyopisteid ?: prefsRepository.getTyopisteid()

            // Step 2: Build punch payload (leimaTallenna)
            val punchPayload = GraphQLQueries.buildPunchPayload(
                type = type,
                talaatuid = talaatuid,
                tyopisteid = tyopisteid,
                leimausaikaSec = now / 1000
            )

            if (com.numbawang.leimaus.BuildConfig.DEBUG) {
                Log.d("Numbawang", "Sending punch ($type) to $targetUrl with talaatuid=$talaatuid, tyopisteid=$tyopisteid")
            }

            try {
                val (code, responseBody) = executeGraphQLCall(targetUrl, token, punchPayload)

                if (com.numbawang.leimaus.BuildConfig.DEBUG) {
                    Log.d("Numbawang", "Punch Response ($code)")
                }

                if (code in 200..303) {
                    val gqlResp = parseGraphQLResponse(responseBody)
                    val leimaPayload = gqlResp?.data?.leimaTallenna

                    val leimaErrors = leimaPayload?.errors
                    val topErrors = gqlResp?.errors

                    if (leimaErrors?.isNotEmpty() == true) {
                        val errMsg = leimaErrors.joinToString("; ") { it.message }
                        Log.e("Numbawang", "Leima errors: $errMsg")
                        recordLog(now, fullTimeStr, actionTitle, isSuccess = false, message = errMsg)
                        return@withContext StampResult.Error(errMsg)
                    }

                    if (topErrors?.isNotEmpty() == true) {
                        val errMsg = topErrors.joinToString("; ") { it.message }
                        Log.e("Numbawang", "Top-level GraphQL errors: $errMsg")
                        recordLog(now, fullTimeStr, actionTitle, isSuccess = false, message = errMsg)
                        return@withContext StampResult.Error(errMsg)
                    }

                    if (leimaPayload == null) {
                        val errMsg = "Palvelin ei palauttanut leimaTallenna-tietoja. Vastaus: ${responseBody.take(150)}"
                        Log.e("Numbawang", errMsg)
                        recordLog(now, fullTimeStr, actionTitle, isSuccess = false, message = errMsg)
                        return@withContext StampResult.Error("Palvelin ei palauttanut leimauksen vahvistusta.")
                    }

                    if (com.numbawang.leimaus.BuildConfig.DEBUG) {
                        Log.d("Numbawang", "Stamp successful! PreviousStamp: ${leimaPayload.previousstamp}")
                    }

                    var displayTime = timeStr
                    var actualTimestamp = now
                    var actualFullTimeStr = fullTimeStr

                    val serverAikaTs = leimaPayload.previousstamp?.aika
                    if (serverAikaTs != null && serverAikaTs > 0L) {
                        val serverMillis = if (serverAikaTs < 10000000000L) serverAikaTs * 1000L else serverAikaTs
                        // Tuntivelho API returns local wall clock time encoded as a UTC epoch.
                        // Format directly in UTC to get the exact local time string.
                        displayTime = utcTimeFormat.format(Date(serverMillis))
                        actualFullTimeStr = utcFullTimeFormat.format(Date(serverMillis))
                        // Convert local-as-UTC millis to real UTC epoch millis
                        actualTimestamp = serverMillis - helsinkiTz.getOffset(serverMillis)
                    }

                    // On successful punch
                    var balanceStr = ""
                    val taseSeconds = fetchBalance(targetUrl, token)
                    if (taseSeconds != null) {
                        balanceStr = formatTaseSeconds(taseSeconds)
                        prefsRepository.saveServerBalance(balanceStr)
                    } else if (isClockOut) {
                        balanceStr = calculateBalance(settings.clockInTimestamp, actualTimestamp, settings.requiredElapsedMinutes(actualTimestamp))
                        prefsRepository.saveServerBalance(balanceStr)
                    }

                    val successMsg = when {
                        isClockIn -> "Sisäänleimaus klo $displayTime kirjattu Tuntivelhoon."
                        isBreakStart -> "Tauolle klo $displayTime kirjattu Tuntivelhoon."
                        isBreakEnd -> "Tauolta takaisin klo $displayTime kirjattu Tuntivelhoon."
                        else -> "Ulosleimaus klo $displayTime kirjattu Tuntivelhoon." + if (balanceStr.isNotBlank()) " Saldo: $balanceStr" else ""
                    }

                    when {
                        isClockIn -> prefsRepository.updateClockInStatus(isClockedIn = true, clockInTs = actualTimestamp)
                        isClockOut -> prefsRepository.updateClockInStatus(isClockedIn = false, clockInTs = 0L)
                        // Break stamps keep the running clock-in session intact
                        else -> if (isBreakStart) {
                            prefsRepository.startBreak(actualTimestamp)
                        } else {
                            prefsRepository.endBreak(actualTimestamp)
                        }
                    }

                    recordLog(actualTimestamp, actualFullTimeStr, actionTitle, isSuccess = true, message = successMsg, balance = balanceStr)
                    return@withContext StampResult.Success(successMsg, actualTimestamp, balanceStr)

                } else if (code == 404) {
                    lastError = "Leimauspyyntö osoitteessa $targetUrl palautti koodin HTTP 404."
                    Log.w("Numbawang", lastError)
                    continue // Try next candidate URL on 404
                } else if (code == 401) {
                    prefsRepository.clearAuthToken()
                    val errMsg = "Autentikointitokeni ei kelpaa. Kirjaudu uudelleen."
                    Log.e("Numbawang", errMsg)
                    recordLog(now, fullTimeStr, actionTitle, isSuccess = false, message = errMsg)
                    return@withContext StampResult.Error(errMsg)
                } else {
                    val errMsg = "Leimauspyyntö osoitteessa $targetUrl palautti koodin HTTP $code."
                    Log.e("Numbawang", errMsg)
                    recordLog(now, fullTimeStr, actionTitle, isSuccess = false, message = errMsg)
                    return@withContext StampResult.Error(errMsg)
                }

            } catch (e: IOException) {
                lastError = "Yhteysvirhe ($targetUrl): ${e.localizedMessage}"
                Log.e("Numbawang", lastError)
                continue // Try next candidate URL on connection error
            } catch (e: Exception) {
                val errMsg = "Leimausvirhe ($targetUrl): ${e.localizedMessage}"
                Log.e("Numbawang", errMsg)
                recordLog(now, fullTimeStr, actionTitle, isSuccess = false, message = errMsg)
                return@withContext StampResult.Error(errMsg)
            }
        }

        val finalError = if (lastError.isNotBlank()) lastError else "Leimaus epäonnistui kaikissa päätepisteissä."
        Log.e("Numbawang", finalError)
        recordLog(now, fullTimeStr, actionTitle, isSuccess = false, message = finalError)
        return@withContext StampResult.Error(finalError)
    }

    suspend fun fetchServerBalance(): String? {
        val res = fetchServerBalanceResult()
        return if (res is StampResult.Success) res.message else null
    }

    suspend fun fetchServerBalanceResult(): StampResult = withContext(Dispatchers.IO) {
        val settings = prefsRepository.settings.value
        val password = prefsRepository.getPassword()

        if (settings.isDemoMode) {
            val balance = if (settings.isClockedIn && settings.clockInTimestamp > 0L) {
                calculateBalance(settings.clockInTimestamp, System.currentTimeMillis(), settings.requiredElapsedMinutes(System.currentTimeMillis()))
            } else {
                settings.lastServerBalance.ifBlank { "+0:00" }
            }
            prefsRepository.saveServerBalance(balance)
            return@withContext StampResult.Success(balance)
        }

        if (settings.username.isBlank() || password.isBlank()) {
            return@withContext StampResult.Error("Käyttäjätunnus tai salasana puuttuu. Syötä tunnukset asetuksissa.")
        }

        val loginResult = login(settings.username, password)
        if (loginResult is StampResult.Error) {
            return@withContext StampResult.Error(loginResult.errorMessage)
        }

        val token = prefsRepository.getAuthToken()
        val candidateUrls = buildCandidateUrls(settings.serverUrl)

        var lastError = ""

        for (targetUrl in candidateUrls) {
            val result = fetchKellokorttiFullResult(targetUrl, token)
            if (result is StampResult.Success) {
                return@withContext result
            } else if (result is StampResult.Error) {
                lastError = result.errorMessage
            }
        }
        val finalError = if (lastError.isNotBlank()) lastError else "Tuntitaseen haku epäonnistui."
        return@withContext StampResult.Error(finalError)
    }

    private fun executeGraphQLCall(targetUrl: String, token: String, payload: String): Pair<Int, String> {
        val jsonMediaType = "application/json; charset=utf-8".toMediaType()
        val request = Request.Builder()
            .url(targetUrl)
            .post(payload.toRequestBody(jsonMediaType))
            .addHeaders(token)
            .build()

        val response = client.newCall(request).execute()
        val code = response.code
        val body = response.body?.string() ?: ""
        response.close()
        return Pair(code, body)
    }

    private fun fetchKellokorttiFullResult(targetUrl: String, token: String): StampResult {
        val payload = GraphQLQueries.buildKellokorttiFullPayload()

        return try {
            val (code, body) = executeGraphQLCall(targetUrl, token, payload)

            if (com.numbawang.leimaus.BuildConfig.DEBUG) {
                Log.d("Numbawang", "Kellokortti full response ($code)")
            }
            val newEntry = "HTTP $code\nURL: $targetUrl\nResponse Body:\n$body"
            if (rawApiResponse.value.startsWith("Ei vielä") || code in 200..303) {
                rawApiResponse.value = newEntry
            } else {
                rawApiResponse.value += "\n\n---\n\n$newEntry"
            }

            if (code in 200..303) {
                val gqlResp = parseGraphQLResponse(body)
                val kellokortti = gqlResp?.data?.kellokortti

                val defaults = kellokortti?.selectiondefaults
                if (defaults?.talaatuid != null && defaults.tyopisteid != null) {
                    prefsRepository.saveSelectionDefaults(defaults.talaatuid, defaults.tyopisteid)
                }

                val prevStamp = kellokortti?.previousstamp
                if (prevStamp != null && prevStamp.suuntaid != null) {
                    val helsinkiTz = java.util.TimeZone.getTimeZone("Europe/Helsinki")
                    if (com.numbawang.leimaus.BuildConfig.DEBUG) {
                        Log.d("Numbawang", "Kellokortti status update: suuntaid=${prevStamp.suuntaid}, aika=${prevStamp.aika}")
                    }
                    if (prevStamp.suuntaid == 0) { // 0 = Sisään (IN) in Tuntivelho API
                        var actualTs = System.currentTimeMillis()
                        val serverAikaTs = prevStamp.aika
                        if (serverAikaTs != null && serverAikaTs > 0L) {
                            val serverMillis = if (serverAikaTs < 10000000000L) serverAikaTs * 1000L else serverAikaTs
                            actualTs = serverMillis - helsinkiTz.getOffset(serverMillis)
                        }
                        prefsRepository.updateClockInStatus(isClockedIn = true, clockInTs = actualTs)
                    } else if (!prefsRepository.settings.value.isOnBreak) { // 1, etc. = Ulos (OUT); preserve session if user is on break
                        prefsRepository.updateClockInStatus(isClockedIn = false, clockInTs = 0L)
                    }
                }

                val taseSec = kellokortti?.tase?.tase
                if (taseSec != null) {
                    val formatted = formatTaseSeconds(taseSec)
                    prefsRepository.saveServerBalance(formatted)
                    StampResult.Success(formatted)
                } else {
                    StampResult.Success("")
                }
            } else if (code == 404) {
                StampResult.Error("Palvelin palautti virhekoodin HTTP 404 (Sivua tai rajapintaa ei löytynyt osoitteesta $targetUrl)")
            } else if (code == 401) {
                prefsRepository.clearAuthToken()
                StampResult.Error("Autentikointivirhe HTTP 401: Kirjaudu uudelleen osoitteessa $targetUrl")
            } else {
                StampResult.Error("Palvelinvirhe HTTP $code osoitteessa $targetUrl")
            }
        } catch (e: Exception) {
            Log.e("Numbawang", "Error fetching kellokortti full: ${e.localizedMessage}")
            StampResult.Error("Yhteysvirhe: ${e.localizedMessage}")
        }
    }

    private fun fetchKellokorttiDefaults(targetUrl: String, token: String): com.numbawang.leimaus.data.network.SelectionDefaults? {
        val payload = GraphQLQueries.buildKellokorttiDefaultsPayload()

        return try {
            val (code, body) = executeGraphQLCall(targetUrl, token, payload)

            if (com.numbawang.leimaus.BuildConfig.DEBUG) {
                Log.d("Numbawang", "Defaults response ($code)")
            }

            if (code in 200..303) {
                val gqlResp = parseGraphQLResponse(body)
                val defaults = gqlResp?.data?.kellokortti?.selectiondefaults
                if (defaults?.talaatuid != null && defaults.tyopisteid != null) {
                    prefsRepository.saveSelectionDefaults(defaults.talaatuid, defaults.tyopisteid)
                }
                defaults
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("Numbawang", "Error fetching defaults: ${e.localizedMessage}")
            null
        }
    }

    private fun fetchBalance(targetUrl: String, token: String): Long? {
        val payload = GraphQLQueries.buildBalancePayload()

        return try {
            val (code, body) = executeGraphQLCall(targetUrl, token, payload)

            if (com.numbawang.leimaus.BuildConfig.DEBUG) {
                Log.d("Numbawang", "Balance response ($code)")
            }

            if (code in 200..303) {
                val gqlResp = parseGraphQLResponse(body)
                gqlResp?.data?.kellokortti?.tase?.tase
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("Numbawang", "Error fetching balance: ${e.localizedMessage}")
            null
        }
    }

    private fun parseGraphQLResponse(jsonString: String): GraphQLResponse? {
        return try {
            val trimmed = jsonString.trim()
            if (trimmed.startsWith("[")) {
                val listType = Types.newParameterizedType(List::class.java, GraphQLResponse::class.java)
                val list = moshi.adapter<List<GraphQLResponse>>(listType).fromJson(trimmed)
                list?.firstOrNull()
            } else {
                moshi.adapter(GraphQLResponse::class.java).fromJson(trimmed)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun Request.Builder.addHeaders(token: String = ""): Request.Builder {
        header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
        header("Content-Type", "application/json")
        header("Accept", "application/json")
        header("Origin", "https://app.tuntivelho.com")
        if (token.isNotBlank()) {
            header("Authorization", "Bearer $token")
        }
        return this
    }

    private suspend fun recordLog(
        timestamp: Long,
        formattedTime: String,
        actionType: String,
        isSuccess: Boolean,
        message: String,
        balance: String = "",
        rawDetails: String = ""
    ) {
        val detailsToSave = if (rawDetails.isNotBlank()) rawDetails else rawApiResponse.value
        stampDao.insertLog(
            StampEntity(
                timestamp = timestamp,
                formattedTime = formattedTime,
                actionType = actionType,
                isSuccess = isSuccess,
                message = message,
                balance = balance,
                rawDetails = detailsToSave
            )
        )
    }

    private fun formatBaseUrl(url: String): String {
        var clean = url.trim()
        if (clean.isEmpty() || clean == "https://www.tuntivelho.fi" || clean == "https://app.tuntivelho.com/") {
            return DEFAULT_URL
        }
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "https://$clean"
        }
        while (clean.endsWith("/")) {
            clean = clean.dropLast(1)
        }
        return clean
    }

    private fun buildCandidateUrls(baseClean: String): List<String> {
        val list = mutableListOf<String>()

        if (baseClean.contains("graphql") && baseClean != DEFAULT_URL && baseClean != FALLBACK_GRAPHQL_URL) {
            list.add(baseClean)
        }
        list.add(DEFAULT_URL)
        list.add(FALLBACK_GRAPHQL_URL)

        return list.distinct()
    }

    companion object {
        fun formatTaseSeconds(taseSeconds: Long): String {
            val sign = if (taseSeconds < 0) "−" else "+"
            val absSec = Math.abs(taseSeconds)
            val hours = absSec / 3600
            val minutes = (absSec % 3600) / 60
            return String.format(Locale.getDefault(), "%s%d:%02d", sign, hours, minutes)
        }

        fun calculateBalance(clockInTs: Long, currentTs: Long, targetMinutesNeeded: Int): String {
            if (clockInTs <= 0L || currentTs < clockInTs) return "0:00"

            val elapsedMillis = currentTs - clockInTs
            val elapsedMinutes = (elapsedMillis / (1000 * 60)).toInt()

            val balanceMinutes = elapsedMinutes - targetMinutesNeeded
            val isNegative = balanceMinutes < 0
            val absMinutes = Math.abs(balanceMinutes)

            val hours = absMinutes / 60
            val mins = absMinutes % 60

            val sign = if (isNegative) "−" else "+"
            val minsFormatted = String.format(Locale.getDefault(), "%02d", mins)

            return "$sign$hours:$minsFormatted"
        }
    }
}


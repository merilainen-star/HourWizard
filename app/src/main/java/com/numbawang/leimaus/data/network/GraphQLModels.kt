package com.numbawang.leimaus.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GraphQLResponse(
    @Json(name = "data") val data: GraphQLDataPayload? = null,
    @Json(name = "errors") val errors: List<GraphQLError>? = null
)

@JsonClass(generateAdapter = true)
data class GraphQLDataPayload(
    @Json(name = "login") val login: LoginPayload? = null,
    @Json(name = "kellokortti") val kellokortti: KellokorttiPayload? = null,
    @Json(name = "leimaTallenna") val leimaTallenna: LeimaTallennaPayload? = null
)

@JsonClass(generateAdapter = true)
data class LoginPayload(
    @Json(name = "success") val success: Boolean = false,
    @Json(name = "token") val token: String? = null,
    @Json(name = "nimi") val nimi: String? = null,
    @Json(name = "henkiloid") val henkiloid: Int? = null,
    @Json(name = "errors") val errors: List<GraphQLError>? = null
)

@JsonClass(generateAdapter = true)
data class KellokorttiPayload(
    @Json(name = "previousstamp") val previousstamp: PreviousStamp? = null,
    @Json(name = "previousstamps") val previousstamps: List<PreviousStamp>? = null,
    @Json(name = "selectiondefaults") val selectiondefaults: SelectionDefaults? = null,
    @Json(name = "tase") val tase: TaseData? = null
)

@JsonClass(generateAdapter = true)
data class SelectionDefaults(
    @Json(name = "talaatuid") val talaatuid: Int? = null,
    @Json(name = "tyopisteid") val tyopisteid: Int? = null,
    @Json(name = "tyolajiid") val tyolajiid: Int? = null
)

@JsonClass(generateAdapter = true)
data class TaseData(
    @Json(name = "tase") val tase: Long? = null
)

@JsonClass(generateAdapter = true)
data class LeimaTallennaPayload(
    @Json(name = "previousstamp") val previousstamp: PreviousStamp? = null,
    @Json(name = "previousstamps") val previousstamps: List<PreviousStamp>? = null,
    @Json(name = "selectiondefaults") val selectiondefaults: SelectionDefaults? = null,
    @Json(name = "errors") val errors: List<GraphQLError>? = null
)

@JsonClass(generateAdapter = true)
data class PreviousStamp(
    @Json(name = "tv_leimaid") val tv_leimaid: String? = null,
    @Json(name = "henkiloid") val henkiloid: Int? = null,
    @Json(name = "aika") val aika: Long? = null,
    @Json(name = "suuntaid") val suuntaid: Int? = null,
    @Json(name = "talaatuid") val talaatuid: Int? = null,
    @Json(name = "tyopisteid") val tyopisteid: Int? = null,
    @Json(name = "tyolajiid") val tyolajiid: Int? = null
)

@JsonClass(generateAdapter = true)
data class GraphQLError(
    @Json(name = "message") val message: String = ""
)

data class LoginResponse(
    val success: Boolean,
    val token: String,
    val message: String,
    val henkiloid: Int? = null
)

data class StampResponse(
    val success: Boolean,
    val message: String,
    val timestamp: Long? = null,
    val previousStamps: List<PreviousStamp>? = null,
    val defaults: SelectionDefaults? = null
)

sealed class APIError {
    data class Authentication(val message: String) : APIError()
    data class Network(val message: String) : APIError()
    data class GraphQL(val message: String, val errors: List<String>) : APIError()
    data class Server(val code: Int, val message: String) : APIError()
    data class Unknown(val message: String) : APIError()
}


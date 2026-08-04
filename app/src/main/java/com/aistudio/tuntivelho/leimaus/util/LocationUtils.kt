package com.aistudio.tuntivelho.leimaus.util

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.regex.Pattern
import org.json.JSONArray

object LocationUtils {

    data class LocationResult(
        val latitude: Double,
        val longitude: Double,
        val formattedAddress: String
    )

    // Regex patterns for coordinates in shared links/text
    private val COORD_PATTERN_AT = Pattern.compile("@(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)")
    private val COORD_PATTERN_Q = Pattern.compile("[?&](?:q|ll)=(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)")
    private val COORD_PATTERN_RAW = Pattern.compile("(-?\\d{1,2}\\.\\d{4,}),\\s*(-?\\d{1,3}\\.\\d{4,})")

    fun extractCoordinatesFromText(text: String): Pair<Double, Double>? {
        // 1. Try @lat,lng
        val matcherAt = COORD_PATTERN_AT.matcher(text)
        if (matcherAt.find()) {
            val lat = matcherAt.group(1)?.toDoubleOrNull()
            val lng = matcherAt.group(2)?.toDoubleOrNull()
            if (lat != null && lng != null) return Pair(lat, lng)
        }

        // 2. Try q=lat,lng or ll=lat,lng
        val matcherQ = COORD_PATTERN_Q.matcher(text)
        if (matcherQ.find()) {
            val lat = matcherQ.group(1)?.toDoubleOrNull()
            val lng = matcherQ.group(2)?.toDoubleOrNull()
            if (lat != null && lng != null) return Pair(lat, lng)
        }

        // 3. Try raw lat,lng numbers in text
        val matcherRaw = COORD_PATTERN_RAW.matcher(text)
        if (matcherRaw.find()) {
            val lat = matcherRaw.group(1)?.toDoubleOrNull()
            val lng = matcherRaw.group(2)?.toDoubleOrNull()
            if (lat != null && lng != null) return Pair(lat, lng)
        }

        return null
    }

    suspend fun searchAddress(context: Context, addressQuery: String): LocationResult? = withContext(Dispatchers.IO) {
        val query = addressQuery.trim()
        if (query.isEmpty()) return@withContext null

        // First check if user or share text contains direct lat/lng coordinates
        val directCoords = extractCoordinatesFromText(query)
        if (directCoords != null) {
            val addressName = reverseGeocode(context, directCoords.first, directCoords.second) ?: "Koordinaatit (${directCoords.first}, ${directCoords.second})"
            return@withContext LocationResult(directCoords.first, directCoords.second, addressName)
        }

        // Try Android native Geocoder
        try {
            val geocoder = Geocoder(context, Locale("fi", "FI"))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                var syncResult: LocationResult? = null
                val lock = java.lang.Object()
                geocoder.getFromLocationName(query, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (addresses.isNotEmpty()) {
                            val addr = addresses[0]
                            val name = addr.getAddressLine(0) ?: addr.featureName ?: query
                            syncResult = LocationResult(addr.latitude, addr.longitude, name)
                        }
                        synchronized(lock) { lock.notifyAll() }
                    }

                    override fun onError(errorMessage: String?) {
                        synchronized(lock) { lock.notifyAll() }
                    }
                })
                synchronized(lock) { lock.wait(3000) }
                if (syncResult != null) return@withContext syncResult
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocationName(query, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    val name = addr.getAddressLine(0) ?: addr.featureName ?: query
                    return@withContext LocationResult(addr.latitude, addr.longitude, name)
                }
            }
        } catch (_: Exception) {
            // Fallback to web search API below
        }

        // Fallback: OpenStreetMap Nominatim Geocoding API
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val urlString = "https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=1"
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "TuntivelhoAndroidApp/1.0")
            connection.connectTimeout = 4000
            connection.readTimeout = 4000

            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.use { it.readText() }
                val jsonArray = JSONArray(response)
                if (jsonArray.length() > 0) {
                    val obj = jsonArray.getJSONObject(0)
                    val lat = obj.getDouble("lat")
                    val lon = obj.getDouble("lon")
                    val displayName = obj.optString("display_name", query)
                    return@withContext LocationResult(lat, lon, displayName)
                }
            }
        } catch (_: Exception) {
        }

        return@withContext null
    }

    private fun reverseGeocode(context: Context, lat: Double, lng: Double): String? {
        try {
            val geocoder = Geocoder(context, Locale("fi", "FI"))
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                return addr.getAddressLine(0) ?: addr.featureName
            }
        } catch (_: Exception) {
        }
        return null
    }
}

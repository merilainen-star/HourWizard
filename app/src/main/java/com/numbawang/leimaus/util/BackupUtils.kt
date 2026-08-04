package com.numbawang.leimaus.util

import android.content.Context
import android.net.Uri
import com.numbawang.leimaus.data.db.StampEntity
import com.numbawang.leimaus.data.preferences.AppSettings
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

object BackupUtils {

    fun exportBackupJson(logs: List<StampEntity>, settings: AppSettings): String {
        val root = JSONObject()
        root.put("app", "Numbawang")
        root.put("version", 1)
        root.put("created_at", System.currentTimeMillis())

        // Settings
        val settingsObj = JSONObject().apply {
            put("username", settings.username)
            put("morningReminderTime", settings.morningReminderTime)
            put("eveningReminderTime", settings.eveningReminderTime)
            put("workdayHours", settings.workdayHours)
            put("workdayMinutes", settings.workdayMinutes)
            put("lunchBreakMinutes", settings.lunchBreakMinutes)
            put("serverUrl", settings.serverUrl)
            put("isDemoMode", settings.isDemoMode)
            put("enabledDaysString", settings.enabledDaysString)
            put("isVacationEnabled", settings.isVacationEnabled)
            put("vacationStart", settings.vacationStart)
            put("vacationEnd", settings.vacationEnd)
            put("appTheme", settings.appTheme)
            put("isGeofenceEnabled", settings.isGeofenceEnabled)
            put("workplaceLat", settings.workplaceLat)
            put("workplaceLng", settings.workplaceLng)
            put("geofenceRadiusMeters", settings.geofenceRadiusMeters)
            put("arrivalWindowStart", settings.arrivalWindowStart)
            put("arrivalWindowEnd", settings.arrivalWindowEnd)
            put("departureWindowStart", settings.departureWindowStart)
            put("departureWindowEnd", settings.departureWindowEnd)
            put("targetMode", settings.targetMode)
            put("targetHoursDaily", settings.targetHoursDaily)
            put("targetHoursWeekly", settings.targetHoursWeekly)
            put("targetSoundAlertEnabled", settings.targetSoundAlertEnabled)
            put("targetVibrationAlertEnabled", settings.targetVibrationAlertEnabled)
        }
        root.put("settings", settingsObj)

        // Logs
        val logsArray = JSONArray()
        for (log in logs) {
            val logObj = JSONObject().apply {
                put("id", log.id)
                put("timestamp", log.timestamp)
                put("formattedTime", log.formattedTime)
                put("actionType", log.actionType)
                put("isSuccess", log.isSuccess)
                put("message", log.message)
                put("balance", log.balance)
                put("rawDetails", log.rawDetails)
            }
            logsArray.put(logObj)
        }
        root.put("logs", logsArray)

        return root.toString(2)
    }

    data class BackupImportData(
        val logs: List<StampEntity>,
        val settings: AppSettings?
    )

    fun importBackupJson(jsonString: String): BackupImportData {
        val root = JSONObject(jsonString)
        val logsList = mutableListOf<StampEntity>()

        if (root.has("logs")) {
            val logsArray = root.getJSONArray("logs")
            for (i in 0 until logsArray.length()) {
                val obj = logsArray.getJSONObject(i)
                val entity = StampEntity(
                    id = obj.optLong("id", 0L),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                    formattedTime = obj.optString("formattedTime", ""),
                    actionType = obj.optString("actionType", "SISÄÄN"),
                    isSuccess = obj.optBoolean("isSuccess", true),
                    message = obj.optString("message", ""),
                    balance = obj.optString("balance", ""),
                    rawDetails = obj.optString("rawDetails", "")
                )
                logsList.add(entity)
            }
        }

        var appSettings: AppSettings? = null
        if (root.has("settings")) {
            val sObj = root.getJSONObject("settings")

            appSettings = AppSettings(
                username = sObj.optString("username", ""),
                morningReminderTime = sObj.optString("morningReminderTime", "07:00"),
                eveningReminderTime = sObj.optString("eveningReminderTime", "16:00"),
                workdayHours = sObj.optInt("workdayHours", 7),
                workdayMinutes = sObj.optInt("workdayMinutes", 30),
                lunchBreakMinutes = sObj.optInt("lunchBreakMinutes", 30),
                serverUrl = sObj.optString("serverUrl", "https://app.tuntivelho.com/mobiili/backend/public/graphql"),
                isDemoMode = sObj.optBoolean("isDemoMode", false),
                enabledDaysString = sObj.optString("enabledDaysString", "1,2,3,4,5"),
                isVacationEnabled = sObj.optBoolean("isVacationEnabled", false),
                vacationStart = sObj.optString("vacationStart", ""),
                vacationEnd = sObj.optString("vacationEnd", ""),
                appTheme = sObj.optString("appTheme", "system"),
                isGeofenceEnabled = sObj.optBoolean("isGeofenceEnabled", false),
                workplaceLat = sObj.optDouble("workplaceLat", 60.1699),
                workplaceLng = sObj.optDouble("workplaceLng", 24.9384),
                geofenceRadiusMeters = sObj.optInt("geofenceRadiusMeters", 150),
                arrivalWindowStart = sObj.optString("arrivalWindowStart", "06:30"),
                arrivalWindowEnd = sObj.optString("arrivalWindowEnd", "10:30"),
                departureWindowStart = sObj.optString("departureWindowStart", "14:30"),
                departureWindowEnd = sObj.optString("departureWindowEnd", "18:30"),
                targetMode = sObj.optString("targetMode", "DAILY"),
                targetHoursDaily = sObj.optDouble("targetHoursDaily", 7.5),
                targetHoursWeekly = sObj.optDouble("targetHoursWeekly", 37.5),
                targetSoundAlertEnabled = sObj.optBoolean("targetSoundAlertEnabled", true),
                targetVibrationAlertEnabled = sObj.optBoolean("targetVibrationAlertEnabled", true)
            )
        }

        return BackupImportData(logsList, appSettings)
    }

    fun writeToUri(context: Context, uri: Uri, content: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(content)
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun readFromUri(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}

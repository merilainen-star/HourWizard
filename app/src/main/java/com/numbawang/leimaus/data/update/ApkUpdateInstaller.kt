package com.numbawang.leimaus.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Streams a published APK straight into Android's private PackageInstaller staging area.
 * Nothing is written to Downloads or other user-visible shared storage.
 */
class ApkUpdateInstaller(context: Context) {
    private val appContext = context.applicationContext
    private val packageInstaller = appContext.packageManager.packageInstaller

    suspend fun downloadAndCommit(
        update: UpdateStatus.Available,
        onProgress: (Int) -> Unit,
    ) = withContext(Dispatchers.IO) {
        require(update.apkSizeBytes > 0) { "APK:n koko puuttuu" }
        require(update.apkSha256.matches(Regex("[0-9a-fA-F]{64}"))) {
            "APK:n SHA-256-tarkistussumma puuttuu tai on virheellinen"
        }

        val url = URL(update.apkUrl)
        require(url.protocol == "https") { "Päivitysosoitteen on käytettävä HTTPS-yhteyttä" }

        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }

        var sessionId: Int? = null
        try {
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                error("APK-lataus epäonnistui: HTTP $responseCode")
            }

            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            ).apply {
                setAppPackageName(appContext.packageName)
                setSize(update.apkSizeBytes)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                }
            }

            val createdSessionId = packageInstaller.createSession(params)
            sessionId = createdSessionId
            packageInstaller.openSession(createdSessionId).use { session ->
                val digest = MessageDigest.getInstance("SHA-256")
                var downloadedBytes = 0L
                var lastProgress = -1

                connection.inputStream.buffered().use { input ->
                    session.openWrite("Numbawang-test.apk", 0, update.apkSizeBytes).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            downloadedBytes += count

                            val progress = ((downloadedBytes * 100) / update.apkSizeBytes)
                                .toInt()
                                .coerceIn(0, 99)
                            if (progress != lastProgress) {
                                lastProgress = progress
                                onProgress(progress)
                            }
                        }
                        session.fsync(output)
                    }
                }

                if (downloadedBytes != update.apkSizeBytes) {
                    error(
                        "APK-latauksen koko ei täsmää " +
                            "($downloadedBytes / ${update.apkSizeBytes} tavua)"
                    )
                }

                val actualSha256 = digest.digest().joinToString("") { byte ->
                    "%02x".format(byte.toInt() and 0xff)
                }
                if (!actualSha256.equals(update.apkSha256, ignoreCase = true)) {
                    error("APK:n SHA-256-tarkistus epäonnistui")
                }

                onProgress(100)
                val statusIntent = Intent(appContext, UpdateInstallReceiver::class.java).apply {
                    action = ACTION_INSTALL_STATUS
                    putExtra(EXTRA_UPDATE_VERSION, update.versionName)
                }
                val mutableFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE
                } else {
                    0
                }
                val statusReceiver = PendingIntent.getBroadcast(
                    appContext,
                    createdSessionId,
                    statusIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag,
                ).intentSender

                session.commit(statusReceiver)
                sessionId = null
            }
        } catch (error: Exception) {
            sessionId?.let { id -> runCatching { packageInstaller.abandonSession(id) } }
            throw error
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val ACTION_INSTALL_STATUS =
            "com.numbawang.leimaus.action.UPDATE_INSTALL_STATUS"
        const val EXTRA_UPDATE_VERSION = "update_version"
    }
}

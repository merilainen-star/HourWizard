package com.numbawang.leimaus.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.numbawang.leimaus.MainActivity

class NotificationHelper(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private val vibrationPattern = longArrayOf(0, 300, 150, 300, 150, 450)

    private fun triggerHapticFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager =
                    context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createWaveform(vibrationPattern, -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(vibrationPattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(vibrationPattern, -1)
                }
            }
        } catch (_: Exception) {
            // Ignore if device hardware lacks vibration motor or permission denied
        }
    }

    /**
     * Content intent for a reminder notification. When [punchAction] is given, tapping the body
     * of the notification opens the app *and* performs that punch — tapping the notification is
     * the same shortcut as its action button, not just an app launcher.
     */
    private fun buildContentPendingIntent(requestCode: Int, punchAction: String? = null): PendingIntent {
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (punchAction != null) {
                putExtra(EXTRA_PUNCH_ACTION, punchAction)
            }
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Tuntivelho Ilmoitukset",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Muistutukset ja leimauspainikkeet työpäivän leimauksille"
                enableVibration(true)
                vibrationPattern = this@NotificationHelper.vibrationPattern
            }
            notificationManager.createNotificationChannel(channel)

            // Its own channel at DEFAULT importance: an available test build is worth a line in
            // the shade, but it must not buzz like a punch reminder, and the user has to be able
            // to silence it without silencing the reminders.
            val updateChannel = NotificationChannel(
                UPDATE_CHANNEL_ID,
                "Sovelluspäivitykset",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Ilmoitus kun GitHubissa on uusi testiversio saatavilla"
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(updateChannel)
        }
    }

    /**
     * Tapping this opens the APK URL in the browser and hands off to Android's own installer, the
     * same route the Settings card takes — see [com.numbawang.leimaus.ui.components.UpdateCard]
     * for why the download is not performed in-app.
     */
    fun showUpdateAvailableNotification(versionName: String, sizeMb: Int, apkUrl: String) {
        val downloadIntent = Intent(Intent.ACTION_VIEW, apkUrl.toUri()).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val downloadPendingIntent = PendingIntent.getActivity(
            context,
            601,
            downloadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val body = "Versio $versionName ($sizeMb MB). Napauta ladataksesi — " +
            "leimaushistoria ja asetukset säilyvät."

        val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Numbawang-päivitys saatavilla")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)
            .setContentIntent(downloadPendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_UPDATE_ID, notification)
    }

    fun showMorningNotification() {
        triggerHapticFeedback()

        val contentPendingIntent =
            buildContentPendingIntent(101, NotificationActionReceiver.ACTION_CLOCK_IN)

        // Action button "SISÄÄN"
        val clockInIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_CLOCK_IN
        }
        val clockInPendingIntent = PendingIntent.getBroadcast(
            context,
            102,
            clockInIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Tuntivelho")
            .setContentText("Paina leimataksesi sisään")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVibrate(vibrationPattern)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(false)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_add,
                "SISÄÄN",
                clockInPendingIntent
            )
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun showEveningNotification(clockInTimeStr: String, balanceStr: String) {
        triggerHapticFeedback()

        val contentPendingIntent =
            buildContentPendingIntent(201, NotificationActionReceiver.ACTION_CLOCK_OUT)

        // Action button "ULOS"
        val clockOutIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_CLOCK_OUT
        }
        val clockOutPendingIntent = PendingIntent.getBroadcast(
            context,
            202,
            clockOutIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val bodyText = if (clockInTimeStr.isNotBlank()) {
            "Sisään $clockInTimeStr\nSaldo $balanceStr"
        } else if (balanceStr.isNotBlank()) {
            "Tarkista saldo ja leimaa ulos\nSaldo $balanceStr"
        } else {
            "Iltamuistutus: Muista tarkistaa työpäiväsi ja leimata ulos."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Tuntivelho")
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVibrate(vibrationPattern)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(false)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "ULOS",
                clockOutPendingIntent
            )
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun showLocationArrivalNotification() {
        triggerHapticFeedback()

        val contentPendingIntent =
            buildContentPendingIntent(301, NotificationActionReceiver.ACTION_CLOCK_IN)

        val clockInIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_CLOCK_IN
        }
        val clockInPendingIntent = PendingIntent.getBroadcast(
            context,
            302,
            clockInIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle("📍 Saavuit työpaikalle")
            .setContentText("Olet työpaikkasi alueella (Saapumisikkuna). Muista leimata sisään!")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Olet saapunut työpaikkasi alueelle (Saapumisikkuna). Muista leimata sisään Tuntivelhoon!"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVibrate(vibrationPattern)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(false)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_add,
                "LEIMAA SISÄÄN",
                clockInPendingIntent
            )
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun showLocationDepartureNotification() {
        triggerHapticFeedback()

        val contentPendingIntent =
            buildContentPendingIntent(401, NotificationActionReceiver.ACTION_CLOCK_OUT)

        val clockOutIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_CLOCK_OUT
        }
        val clockOutPendingIntent = PendingIntent.getBroadcast(
            context,
            402,
            clockOutIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle("📍 Poistut työpaikalta")
            .setContentText("Olet poistumassa työpaikaltasi (Poistumisikkuna). Muista leimata ulos!")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Olet poistumassa työpaikkasi alueelta (Poistumisikkuna). Muista leimata ulos Tuntivelhosta!"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVibrate(vibrationPattern)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(false)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "LEIMAA ULOS",
                clockOutPendingIntent
            )
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun showTargetReachedNotification(title: String, message: String, playSound: Boolean, vibrate: Boolean) {
        if (vibrate) {
            triggerHapticFeedback()
        }

        // Own request code: sharing one with the arrival notification would let
        // FLAG_UPDATE_CURRENT overwrite that notification's punch extra
        val contentPendingIntent = buildContentPendingIntent(501)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)

        if (playSound) {
            builder.setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            try {
                val soundUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                val ringtone = android.media.RingtoneManager.getRingtone(context, soundUri)
                ringtone?.play()
            } catch (_: Exception) {
            }
        }

        if (vibrate) {
            builder.setVibrate(vibrationPattern)
        }

        notificationManager.notify(NOTIFICATION_TARGET_ID, builder.build())
    }

    fun cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    companion object {
        /** Extra on the content intent naming the punch to run when the body is tapped. */
        const val EXTRA_PUNCH_ACTION = "com.numbawang.leimaus.EXTRA_PUNCH_ACTION"
        const val CHANNEL_ID = "numbawang_notifications"
        const val UPDATE_CHANNEL_ID = "numbawang_updates"
        const val NOTIFICATION_ID = 8881
        const val NOTIFICATION_TARGET_ID = 8882
        const val NOTIFICATION_UPDATE_ID = 8883
    }
}

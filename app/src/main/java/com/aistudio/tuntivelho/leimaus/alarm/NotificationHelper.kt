package com.aistudio.tuntivelho.leimaus.alarm

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
import com.aistudio.tuntivelho.leimaus.MainActivity

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
        }
    }

    fun showMorningNotification() {
        triggerHapticFeedback()

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            101,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

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

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            201,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

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

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            301,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

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

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            401,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

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

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            301,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

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
        const val CHANNEL_ID = "tuntivelho_notifications"
        const val NOTIFICATION_ID = 8881
        const val NOTIFICATION_TARGET_ID = 8882
    }
}

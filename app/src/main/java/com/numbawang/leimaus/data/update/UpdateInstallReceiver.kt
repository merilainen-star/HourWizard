package com.numbawang.leimaus.data.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.numbawang.leimaus.MainActivity

/** Receives trusted PackageInstaller callbacks; the manifest keeps this component non-exported. */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE,
        )

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirmationIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                intent.getParcelableExtra(Intent.EXTRA_INTENT)
            }
            if (confirmationIntent != null) {
                confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(confirmationIntent)
                return
            }
        }

        if (status != PackageInstaller.STATUS_SUCCESS) {
            // Bring the existing task back after cancellation/failure so its state and message
            // can be restored. No nested intent is forwarded to the exported activity.
            context.startActivity(
                Intent(context, MainActivity::class.java).apply {
                    action = ApkUpdateInstaller.ACTION_INSTALL_STATUS
                    putExtra(PackageInstaller.EXTRA_STATUS, status)
                    putExtra(
                        PackageInstaller.EXTRA_STATUS_MESSAGE,
                        intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE),
                    )
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            )
        }
    }
}

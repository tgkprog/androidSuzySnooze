package com.sel2in.suzysnooze

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object NotificationHelper {

    private const val CHANNEL_ID = "sel2in_snooze_channel"
    private const val ONGOING_NOTIFICATION_ID = 2014

    // Distinct request codes so the OS never reuses the wrong cached PendingIntent
    private const val REQ_ONGOING = 2
    private const val REQ_CANCEL = 3

    fun showOngoingSnoozeNotification(context: Context, endTimeString: String) {
        if (!areNotificationsAllowed(context)) return

        ensureChannel(context, NotificationManager.IMPORTANCE_LOW)

        val contentIntent = PendingIntent.getActivity(
            context,
            REQ_ONGOING,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = PendingIntent.getBroadcast(
            context,
            REQ_CANCEL,
            Intent(context, CancelSnoozeReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notification_ongoing_title))
            .setContentText(context.getString(R.string.notification_ongoing_text, endTimeString))
            .setSmallIcon(R.drawable.cancel_snooze)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(0, context.getString(R.string.action_cancel), cancelIntent)
            .build()

        NotificationManagerCompat.from(context).notify(ONGOING_NOTIFICATION_ID, notification)
    }

    fun cancelOngoingSnoozeNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(ONGOING_NOTIFICATION_ID)
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun ensureChannel(context: Context, importance: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // createNotificationChannel is idempotent; safe to call every time.
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                importance
            )
        )
    }

    private fun areNotificationsAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}

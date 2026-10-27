package com.gavaldon.go.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.app.PendingIntent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.gavaldon.go.R
import com.gavaldon.go.ui.main.MainActivity

/**
 * RF-21 (parcial): notificación LOCAL al aprobarse un pago. Las notificaciones push de promociones
 * requieren un proyecto Firebase (google-services.json) que GAVALDON debe crear; ver README.
 */
object NotificationHelper {
    private const val CHANNEL_PAYMENTS = "payments"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_PAYMENTS,
            context.getString(R.string.channel_payments_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = context.getString(R.string.channel_payments_desc) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun notifyPaymentApproved(context: Context, folio: String, stationName: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        // Intent explícito + FLAG_IMMUTABLE (OWASP M8).
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_PAYMENTS)
            .setSmallIcon(R.drawable.ic_pump)
            .setContentTitle(context.getString(R.string.notif_payment_approved, folio))
            .setContentText(context.getString(R.string.notif_payment_body, stationName))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        NotificationManagerCompat.from(context).notify(folio.hashCode(), notification)
    }
}

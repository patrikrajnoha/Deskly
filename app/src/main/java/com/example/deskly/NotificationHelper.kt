package com.example.deskly

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

object NotificationHelper {
    private const val CHANNEL_ID = "deskly_connection"
    private const val CHANNEL_NAME = "Deskly Connection"
    private const val CONNECTION_NOTIFICATION_ID = 1001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Connection status notifications for Deskly"
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean {
        if (!DesklyPrefs.getNotificationsEnabled(context)) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
    }

    fun showConnected(context: Context, pcName: String, connectionType: String) {
        show(
            context = context,
            title = "Connected to $pcName",
            text = "Connected via $connectionType",
            alert = false
        )
    }

    fun showDisconnected(context: Context, pcName: String) {
        show(
            context = context,
            title = "Disconnected from $pcName",
            text = "Connection lost",
            alert = true
        )
    }

    fun showConnectionFailed(context: Context, pcName: String, reason: String) {
        show(
            context = context,
            title = "Failed to connect to $pcName",
            text = reason.take(80).ifBlank { "Connection failed" },
            alert = true
        )
    }

    private fun show(context: Context, title: String, text: String, alert: Boolean) {
        if (!canNotify(context)) return

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(if (alert) android.R.drawable.ic_dialog_alert else android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(CONNECTION_NOTIFICATION_ID, notification)
    }
}

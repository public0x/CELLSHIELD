package com.cellshield.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class NotificationHelper(private val context: Context) {

    companion object {
        const val HIGH_PRIORITY_CHANNEL_ID = "cellshield_high_priority_alerts"
    }

    /**
     * Creates the notification channel. This should be called once when the app starts.
     */
    fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ (Android 8.0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "High Priority Alerts"
            val descriptionText = "Notifications for high-severity FBTS detections"
            val importance = NotificationManager.IMPORTANCE_HIGH // This makes it a "heads-up" notification

            val channel = NotificationChannel(HIGH_PRIORITY_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }

            // Register the channel with the system
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Builds and displays a high-priority system notification.
     */
    fun showHighPriorityAlert(title: String, message: String) {
        // Create an intent that will open the app when the notification is tapped
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(context, HIGH_PRIORITY_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher) // Uses your app icon. (Ideally, create a dedicated white notification icon)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // For "heads-up"
            .setCategory(NotificationCompat.CATEGORY_ALARM) // Treat as an alarm/urgent event
            .setContentIntent(pendingIntent) // Set the tap action
            .setAutoCancel(true) // Remove the notification when tapped

        // Show the notification
        try {
            with(NotificationManagerCompat.from(context)) {
                // notificationId is a unique int for this notification.
                // Using a constant (e.g., 1) will make new alerts replace old ones.
                notify(1, builder.build())
            }
        } catch (e: SecurityException) {
            // This will happen if the user has disabled notifications.
            // You can show a toast or log, but we'll ignore it for this test.
            e.printStackTrace()
        }
    }
}
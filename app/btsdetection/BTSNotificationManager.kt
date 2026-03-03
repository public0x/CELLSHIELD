// Create: app/src/main/java/com/cellshield/app/btsdetection/BTSNotificationManager.kt

package com.cellshield.app.btsdetection

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cellshield.app.MainActivity
import com.cellshield.app.R

class BTSNotificationManager(private val context: Context) {

    companion object {
        private const val CHANNEL_ID_HIGH = "bts_detection_high"
        private const val CHANNEL_ID_MEDIUM = "bts_detection_medium"
        private const val CHANNEL_ID_LOW = "bts_detection_low"

        private const val NOTIFICATION_ID_HIGH = 100
        private const val NOTIFICATION_ID_MEDIUM = 101
        private const val NOTIFICATION_ID_LOW = 102
    }

    private val notificationManager = NotificationManagerCompat.from(context)

    init {
        createNotificationChannels()
    }

    /**
     * Create notification channels for different severity levels
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // High priority channel for critical threats
            val highChannel = NotificationChannel(
                CHANNEL_ID_HIGH,
                "Critical Threats",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for high-confidence fake BTS detections"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500)
                setShowBadge(true)
            }

            // Medium priority channel for moderate threats
            val mediumChannel = NotificationChannel(
                CHANNEL_ID_MEDIUM,
                "Moderate Threats",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts for suspicious network activity"
                enableVibration(true)
                setShowBadge(true)
            }

            // Low priority channel for informational alerts
            val lowChannel = NotificationChannel(
                CHANNEL_ID_LOW,
                "Network Alerts",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "General network status alerts"
                setShowBadge(false)
            }

            // Register channels
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(highChannel)
            manager.createNotificationChannel(mediumChannel)
            manager.createNotificationChannel(lowChannel)
        }
    }

    /**
     * Send notification for detected threat
     */
    fun sendThreatNotification(result: DetectionResult) {
        val severity = when {
            result.riskLevel >= 8 || result.confidence == "HIGH" -> "High"
            result.riskLevel >= 5 || result.confidence == "MEDIUM" -> "Medium"
            else -> "Low"
        }

        when (severity) {
            "High" -> sendHighPriorityNotification(result)
            "Medium" -> sendMediumPriorityNotification(result)
            "Low" -> sendLowPriorityNotification(result)
        }
    }

    /**
     * Send high priority notification (critical threat)
     */
    private fun sendHighPriorityNotification(result: DetectionResult) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("open_alerts", true)
            putExtra("show_countermeasures", true) // ✅ Trigger countermeasures dialog
            putExtra("detection_result", result.riskLevel) // Pass risk level
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ✅ Add countermeasures action button
        val countermeasuresIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("show_countermeasures", true)
            putExtra("detection_result", result.riskLevel)
        }
        val countermeasuresPendingIntent = PendingIntent.getActivity(
            context,
            1,
            countermeasuresIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_HIGH)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🚨 CRITICAL: Fake BTS Detected!")
            .setContentText("High confidence threat detected. Risk level: ${result.riskLevel}/10")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(buildNotificationText(result)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 500, 250, 500))
            .setOnlyAlertOnce(false)
            .addAction(
                android.R.drawable.ic_menu_compass,
                "Countermeasures", // ✅ New action button
                countermeasuresPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Dismiss",
                pendingIntent
            )
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID_HIGH, notification)
        } catch (e: SecurityException) {
            android.util.Log.e("BTSNotificationManager", "Notification permission denied", e)
        }
    }

    /**
     * Send medium priority notification (suspicious activity)
     */
    private fun sendMediumPriorityNotification(result: DetectionResult) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_MEDIUM)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("⚠️ Suspicious Network Activity")
            .setContentText("Potential threat detected. Confidence: ${result.confidence}")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(buildNotificationText(result)))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true) // Don't spam for medium priority
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID_MEDIUM, notification)
        } catch (e: SecurityException) {
            android.util.Log.e("BTSNotificationManager", "Notification permission denied", e)
        }
    }

    /**
     * Send low priority notification (informational)
     */
    private fun sendLowPriorityNotification(result: DetectionResult) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_LOW)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Network Alert")
            .setContentText("Unusual network behavior detected")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(buildNotificationText(result)))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID_LOW, notification)
        } catch (e: SecurityException) {
            android.util.Log.e("BTSNotificationManager", "Notification permission denied", e)
        }
    }

    /**
     * Build detailed notification text
     */
    private fun buildNotificationText(result: DetectionResult): String {
        return buildString {
            appendLine("Risk Level: ${result.riskLevel}/10")
            appendLine("Confidence: ${result.confidence}")
            appendLine("Cells Detected: ${result.cellCount}")

            if (result.isDowngrade) {
                appendLine("\n⚠️ Network Downgrade Detected")
            }

            if (result.detectionReasons.isNotEmpty()) {
                appendLine("\nReasons:")
                result.detectionReasons.take(3).forEach { reason ->
                    appendLine("• $reason")
                }
            }

            appendLine("\nOperator: ${result.networkOperator}")
        }
    }

    /**
     * Send scanning status notification
     */
    fun sendScanningNotification() {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_LOW)
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentTitle("CellShield Active")
            .setContentText("Monitoring network for suspicious activity...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true) // Can't be dismissed
            .setContentIntent(pendingIntent)
            .build()

        try {
            notificationManager.notify(999, notification)
        } catch (e: SecurityException) {
            android.util.Log.e("BTSNotificationManager", "Notification permission denied", e)
        }
    }

    /**
     * Cancel scanning notification
     */
    fun cancelScanningNotification() {
        notificationManager.cancel(999)
    }

    /**
     * Cancel all notifications
     */
    fun cancelAllNotifications() {
        notificationManager.cancelAll()
    }

    /**
     * Check if notifications are enabled
     */
    fun areNotificationsEnabled(): Boolean {
        return notificationManager.areNotificationsEnabled()
    }
}
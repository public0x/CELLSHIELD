// Create: app/src/main/java/com/cellshield/app/service/BTSDetectionService.kt

package com.cellshield.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import com.cellshield.app.MainActivity
import com.cellshield.app.btsdetection.BTSDetector
import com.cellshield.app.btsdetection.BTSNotificationManager
import com.cellshield.app.data.Alert
import com.cellshield.app.data.DetectionEvent
import com.cellshield.app.MainApplication
import com.cellshield.app.auth.AuthManager
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class BTSDetectionService : Service() {

    companion object {
        private const val CHANNEL_ID = "bts_detection_service"
        private const val NOTIFICATION_ID = 1000

        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"

        private const val SCAN_INTERVAL_MS = 3000L // 3 seconds

        // Service state
        @Volatile
        private var isServiceRunning = false

        fun isRunning(): Boolean = isServiceRunning
    }

    private lateinit var btsDetector: BTSDetector
    private lateinit var telephonyManager: TelephonyManager
    private lateinit var notificationManager: BTSNotificationManager
    private lateinit var historyDao: com.cellshield.app.data.HistoryDao
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var serviceJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var detectionCount = 0
    private var threatCount = 0
    private var lastThreatTime = 0L

    // Store current location
    @Volatile
    private var currentLatitude: Double? = null
    @Volatile
    private var currentLongitude: Double? = null

    override fun onCreate() {
        super.onCreate()

        btsDetector = BTSDetector(applicationContext)
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        notificationManager = BTSNotificationManager(applicationContext)
        historyDao = (application as MainApplication).database.historyDao()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        createNotificationChannel()
        android.util.Log.d("BTSDetectionService", "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startDetection()
            ACTION_STOP -> stopDetection()
        }
        return START_STICKY // Restart service if killed
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Start continuous BTS detection in background
     */
    private fun startDetection() {
        if (isServiceRunning) {
            android.util.Log.d("BTSDetectionService", "Service already running")
            return
        }

        isServiceRunning = true
        android.util.Log.d("BTSDetectionService", "Starting detection service")

        // Start as foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                createForegroundNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, createForegroundNotification())
        }

        // Update initial location
        updateCurrentLocation()

        // Start detection loop
        serviceJob = serviceScope.launch {
            while (isActive && isServiceRunning) {
                try {
                    // Update location periodically
                    updateCurrentLocation()
                    performDetection()
                    delay(SCAN_INTERVAL_MS)
                } catch (e: Exception) {
                    android.util.Log.e("BTSDetectionService", "Detection error", e)
                    delay(SCAN_INTERVAL_MS)
                }
            }
        }
    }

    /**
     * Stop detection service
     */
    private fun stopDetection() {
        android.util.Log.d("BTSDetectionService", "Stopping detection service")

        isServiceRunning = false
        serviceJob?.cancel()
        serviceJob = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Perform single detection scan
     */
    private suspend fun performDetection() {
        withContext(Dispatchers.IO) {
            try {
                val result = btsDetector.detectFakeBTS(telephonyManager)

                detectionCount++

                // 🔥 Broadcast result to ViewModel/UI
                com.cellshield.app.btsdetection.DetectionBroadcaster.broadcast(result)

                // Update foreground notification with stats
                updateForegroundNotification()

                // Handle threats
                if (result.isThreats) {
                    handleThreatDetection(result)
                }

                android.util.Log.d(
                    "BTSDetectionService",
                    "Scan #$detectionCount - Threats: ${result.isThreats}, Risk: ${result.riskLevel}"
                )

            } catch (e: SecurityException) {
                android.util.Log.e("BTSDetectionService", "Permission denied", e)
            } catch (e: Exception) {
                android.util.Log.e("BTSDetectionService", "Detection failed", e)
            }
        }
    }

    /**
     * Handle detected threats
     */
    private suspend fun handleThreatDetection(result: com.cellshield.app.btsdetection.DetectionResult) {
        withContext(Dispatchers.IO) {
            try {
                threatCount++
                lastThreatTime = System.currentTimeMillis()

                // Determine severity
                val severity = when {
                    result.riskLevel >= 8 || result.confidence == "HIGH" -> "High"
                    result.riskLevel >= 5 || result.confidence == "MEDIUM" -> "Medium"
                    else -> "Low"
                }

                val timestamp = System.currentTimeMillis()

                // Create detection event for history
                val event = DetectionEvent(
                    title = if (result.isDowngrade) "Network Downgrade Detected" else "Suspicious Cell Tower Detected",
                    details = result.detectionReasons.joinToString(", "),
                    severity = severity,
                    towerId = result.suspiciousCells.firstOrNull() ?: "Unknown",
                    signalStrength = result.cellDetails.firstOrNull()?.signalStrength,
                    location = null
                )

                // ALWAYS save to Room database (no cooldown for logging)
                historyDao.insertEvent(event)

                // ALWAYS save to Firebase (no cooldown for logging)
                saveToFirebase(event, timestamp, result)

                // 🔥 Send notification (cooldown is handled inside BTSNotificationManager)
                notificationManager.sendThreatNotification(result)

                // Update foreground notification
                updateForegroundNotification()

                android.util.Log.d("BTSDetectionService", "Threat #$threatCount detected and saved")

            } catch (e: Exception) {
                android.util.Log.e("BTSDetectionService", "Error handling threat", e)
            }
        }
    }

    /**
     * Save detection to Firebase
     */
    private fun saveToFirebase(event: DetectionEvent, timestamp: Long, result: com.cellshield.app.btsdetection.DetectionResult) {
        try {
            // Format location as "lat,lng" for heatmap
            val locationString = if (currentLatitude != null && currentLongitude != null) {
                "$currentLatitude,$currentLongitude"
            } else {
                "" // Empty if location not available
            }

            // Get current user ID
            val currentUserId = AuthManager.getCurrentUserId() ?: "anonymous"

            // Save alert with both GPS location and operator info
            val alert = Alert(
                id = timestamp.toString(),
                userId = currentUserId,
                title = event.title,
                location = locationString,
                operator = result.networkOperator,
                severity = event.severity,
                timestamp = timestamp
            )

            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("alerts")
                .document(alert.id)
                .set(alert)
                .addOnSuccessListener {
                    android.util.Log.d("BTSDetectionService", "Alert saved to Firebase")
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("BTSDetectionService", "Failed to save alert", e)
                }

            // Save detection event
            val eventData = hashMapOf(
                "title" to event.title,
                "details" to event.details,
                "severity" to event.severity,
                "towerId" to event.towerId,
                "signalStrength" to event.signalStrength,
                "location" to event.location,
                "timestamp" to timestamp
            )

            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("detection_events")
                .document(timestamp.toString())
                .set(eventData)
                .addOnSuccessListener {
                    android.util.Log.d("BTSDetectionService", "Event saved to Firebase")
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("BTSDetectionService", "Failed to save event", e)
                }

        } catch (e: Exception) {
            android.util.Log.e("BTSDetectionService", "Firebase save error", e)
        }
    }

    /**
     * Create foreground notification
     */
    private fun createForegroundNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, BTSDetectionService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🛡️ CellShield Active")
            .setContentText("Monitoring network for suspicious activity...")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPendingIntent
            )
            .build()
    }

    /**
     * Update foreground notification with current stats
     */
    private fun updateForegroundNotification() {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, BTSDetectionService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val lastThreatText = if (threatCount > 0) {
            " | Last threat: ${timeFormatter.format(Date(lastThreatTime))}"
        } else {
            ""
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🛡️ CellShield Active")
            .setContentText("Scans: $detectionCount | Threats: $threatCount$lastThreatText")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPendingIntent
            )
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Create notification channel for service
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "BTS Detection Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing notification for background BTS detection"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Update current location
     */
    private fun updateCurrentLocation() {
        try {
            // Check if we have location permission
            val hasFineLocation = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            val hasCoarseLocation = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasFineLocation && !hasCoarseLocation) {
                android.util.Log.w("BTSDetectionService", "No location permission")
                return
            }

            // Get current location
            val cancellationTokenSource = CancellationTokenSource()
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.token
            ).addOnSuccessListener { location ->
                location?.let {
                    currentLatitude = it.latitude
                    currentLongitude = it.longitude
                    android.util.Log.d(
                        "BTSDetectionService",
                        "Location updated: ${it.latitude}, ${it.longitude}"
                    )
                }
            }.addOnFailureListener { e ->
                android.util.Log.e("BTSDetectionService", "Failed to get location", e)
            }
        } catch (e: SecurityException) {
            android.util.Log.e("BTSDetectionService", "Location permission error", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        serviceJob?.cancel()
        serviceScope.cancel()
        android.util.Log.d("BTSDetectionService", "Service destroyed")
    }
}
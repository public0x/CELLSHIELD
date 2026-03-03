// In app/src/main/java/com/cellshield/app/ui/DashboardViewModel.kt
package com.cellshield.app.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.TelephonyManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.cellshield.app.MainApplication
import com.cellshield.app.btsdetection.BTSDetector
import com.cellshield.app.btsdetection.DetectionResult
import com.cellshield.app.data.Alert
import com.cellshield.app.data.DailyTrend
import com.cellshield.app.data.DetectionEvent
import com.cellshield.app.data.Review
import com.cellshield.app.data.ReviewRepository
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.graphics.pdf.PdfDocument
import android.graphics.Paint
import android.graphics.Typeface
import java.io.FileOutputStream
import java.util.Calendar

class DashboardViewModel(private val application: Application) : ViewModel() {

    private val historyDao = (application as MainApplication).database.historyDao()

    // ============ BTS DETECTION INTEGRATION ============
    // Detection state flows
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _currentDetectionResult = MutableStateFlow<DetectionResult?>(null)
    val currentDetectionResult: StateFlow<DetectionResult?> = _currentDetectionResult

    private val _detectionStatus = MutableStateFlow("Idle")
    val detectionStatus: StateFlow<String> = _detectionStatus

    // 🔥 NEW: Cooldown management to prevent spam
    private var lastAlertShownTime = 0L
    private var lastCountermeasuresShownTime = 0L
    private val ALERT_COOLDOWN_MS = 60000L // 1 minute between alerts
    private val COUNTERMEASURES_COOLDOWN_MS = 120000L // 2 minutes between countermeasures dialogs

    private val _shouldShowCountermeasures = MutableStateFlow(false)
    val shouldShowCountermeasures: StateFlow<Boolean> = _shouldShowCountermeasures

    /**
     * Check if we should show countermeasures dialog (respects cooldown)
     */
    fun checkAndTriggerCountermeasures(result: DetectionResult): Boolean {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastShown = currentTime - lastCountermeasuresShownTime

        android.util.Log.d("DashboardViewModel", "checkAndTriggerCountermeasures called:")
        android.util.Log.d("DashboardViewModel", "  - isThreats: ${result.isThreats}")
        android.util.Log.d("DashboardViewModel", "  - riskLevel: ${result.riskLevel}")
        android.util.Log.d("DashboardViewModel", "  - confidence: ${result.confidence}")
        android.util.Log.d("DashboardViewModel", "  - timeSinceLastShown: ${timeSinceLastShown}ms")
        android.util.Log.d("DashboardViewModel", "  - cooldown: ${COUNTERMEASURES_COOLDOWN_MS}ms")

        if (result.isThreats &&
            (result.riskLevel >= 5 || result.confidence != "LOW") &&
            timeSinceLastShown > COUNTERMEASURES_COOLDOWN_MS) {

            lastCountermeasuresShownTime = currentTime
            _shouldShowCountermeasures.value = true
            android.util.Log.d("DashboardViewModel", "✅ Triggering countermeasures dialog!")
            return true
        }

        android.util.Log.d("DashboardViewModel", "❌ Countermeasures NOT triggered")
        return false
    }

    /**
     * User dismissed the countermeasures dialog
     */
    fun dismissCountermeasures() {
        _shouldShowCountermeasures.value = false
    }

    /**
     * Reset cooldowns (e.g., when user stops scanning)
     */
    fun resetCooldowns() {
        lastAlertShownTime = 0L
        lastCountermeasuresShownTime = 0L
        _shouldShowCountermeasures.value = false
    }

    // ============ EXISTING FLOWS ============
    val historyEvents = historyDao.getAllEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val detectionTrends = historyDao.getDetectionTrends()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<DailyTrend>())

    val latestEvent = historyDao.getLatestEvent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // --- Alerts (Firestore + Local) - User's alerts only ---
    private val _alerts = MutableStateFlow<List<Alert>>(emptyList())
    val alerts: StateFlow<List<Alert>> = _alerts

    // --- Map Alerts (Firestore) - All users' alerts for heatmap ---
    private val _mapAlerts = MutableStateFlow<List<Alert>>(emptyList())
    val mapAlerts: StateFlow<List<Alert>> = _mapAlerts

    // --- Reviews (Firestore) ---
    private val _reviews = MutableStateFlow<List<Review>>(emptyList())
    val reviews: StateFlow<List<Review>> = _reviews

    init {
        listenForAlerts()
        listenForMapAlerts()
        listenForReviews()

        // 🔥 Listen for detection results from background service
        listenToDetectionResults()
    }

    /**
     * Listen to detection results broadcast from the service
     */
    private fun listenToDetectionResults() {
        viewModelScope.launch {
            com.cellshield.app.btsdetection.DetectionBroadcaster.detectionResults.collect { result ->
                android.util.Log.d("DashboardViewModel", "Received detection result: Risk=${result.riskLevel}, Confidence=${result.confidence}")

                // Update current result for UI
                _currentDetectionResult.value = result

                // Update status
                _detectionStatus.value = when {
                    result.errorMessage != null -> "Error: ${result.errorMessage}"
                    result.isThreats -> "⚠️ Threat Detected"
                    else -> "✅ Secure"
                }

                // 🔥 Check if we should trigger countermeasures (respects cooldown)
                checkAndTriggerCountermeasures(result)
            }
        }
    }

    // ============ BTS DETECTION FUNCTIONS ============

    /**
     * Start continuous BTS detection scanning via background service
     */
    fun startScanning() {
        android.util.Log.d("DashboardViewModel", "startScanning() called")

        if (_isScanning.value) {
            android.util.Log.d("DashboardViewModel", "Already scanning, returning")
            return
        }

        _isScanning.value = true
        _detectionStatus.value = "Starting..."

        android.util.Log.d("DashboardViewModel", "About to start background service...")

        try {
            // 🔥 Start background service
            val serviceIntent = Intent(application, com.cellshield.app.service.BTSDetectionService::class.java).apply {
                action = com.cellshield.app.service.BTSDetectionService.ACTION_START
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.util.Log.d("DashboardViewModel", "Starting foreground service (Android O+)")
                application.startForegroundService(serviceIntent)
            } else {
                android.util.Log.d("DashboardViewModel", "Starting regular service")
                application.startService(serviceIntent)
            }

            android.util.Log.d("DashboardViewModel", "Background service start command sent")
        } catch (e: Exception) {
            android.util.Log.e("DashboardViewModel", "Error starting service", e)
            _detectionStatus.value = "Error: ${e.message}"
        }
    }

    /**
     * Stop BTS detection scanning
     */
    fun stopScanning() {
        android.util.Log.d("DashboardViewModel", "stopScanning() called")

        _isScanning.value = false
        _detectionStatus.value = "Idle"
        _currentDetectionResult.value = null

        // 🔥 Reset cooldowns when scanning stops
        resetCooldowns()

        // 🔥 Stop background service
        val serviceIntent = Intent(application, com.cellshield.app.service.BTSDetectionService::class.java).apply {
            action = com.cellshield.app.service.BTSDetectionService.ACTION_STOP
        }
        application.stopService(serviceIntent)

        android.util.Log.d("DashboardViewModel", "Background service stop command sent")
    }

    // ============ EXISTING FUNCTIONS ============

    suspend fun exportHistoryToTxt(startDate: Long, endDate: Long): File? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val events = historyDao.getEventsForExport(startDate, endDate)
                if (events.isEmpty()) {
                    return@withContext null
                }

                val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

                val content = buildString {
                    appendLine("CellShield History Export")
                    appendLine("Period: ${dateFormatter.format(Date(startDate))} to ${dateFormatter.format(Date(endDate))}")
                    appendLine("============================================\n")

                    events.forEach { event ->
                        appendLine("Date: ${dateFormatter.format(Date(event.timestamp))}")
                        appendLine("Title: ${event.title}")
                        appendLine("Severity: ${event.severity}")
                        appendLine("Details: ${event.details}")
                        event.towerId?.let { appendLine("Tower ID: $it") }
                        event.signalStrength?.let { appendLine("Signal: $it dBm") }
                        event.location?.let { appendLine("Location: $it") }
                        appendLine("--------------------")
                    }
                }

                val context: Context = application
                val fileName = "cellshield_export_${System.currentTimeMillis()}.txt"
                val file = File(context.cacheDir, fileName)

                file.writeText(content)

                return@withContext file

            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext null
            }
        }
    }

    suspend fun exportHistoryToPdf(startDate: Long, endDate: Long): File? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val events = historyDao.getEventsForExport(startDate, endDate)
                if (events.isEmpty()) {
                    return@withContext null
                }

                val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val pdfDocument = PdfDocument()

                // Page dimensions (A4: 595 x 842 points)
                val pageWidth = 595
                val pageHeight = 842
                val margin = 40
                val contentWidth = pageWidth - (2 * margin)

                // Paint configurations
                val titlePaint = Paint().apply {
                    textSize = 20f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    color = android.graphics.Color.BLACK
                }

                val headerPaint = Paint().apply {
                    textSize = 12f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    color = android.graphics.Color.BLACK
                }

                val bodyPaint = Paint().apply {
                    textSize = 10f
                    color = android.graphics.Color.DKGRAY
                }

                val severityPaint = Paint().apply {
                    textSize = 10f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }

                var pageNumber = 1
                var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                var page = pdfDocument.startPage(pageInfo)
                var canvas = page.canvas
                var yPosition = margin + 30f

                // Draw title
                canvas.drawText("CellShield History Export", margin.toFloat(), yPosition, titlePaint)
                yPosition += 30f

                // Draw date range
                val period = "Period: ${dateFormatter.format(Date(startDate))} to ${dateFormatter.format(Date(endDate))}"
                canvas.drawText(period, margin.toFloat(), yPosition, bodyPaint)
                yPosition += 30f

                // Draw separator line
                canvas.drawLine(margin.toFloat(), yPosition, (pageWidth - margin).toFloat(), yPosition, bodyPaint)
                yPosition += 20f

                // Draw events
                events.forEach { event ->
                    // Check if we need a new page
                    if (yPosition > pageHeight - 100) {
                        pdfDocument.finishPage(page)
                        pageNumber++
                        pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas
                        yPosition = margin + 30f
                    }

                    // Date
                    canvas.drawText("Date: ${dateFormatter.format(Date(event.timestamp))}", margin.toFloat(), yPosition, headerPaint)
                    yPosition += 18f

                    // Title
                    canvas.drawText("Title: ${event.title}", margin.toFloat(), yPosition, bodyPaint)
                    yPosition += 15f

                    // Severity with color
                    severityPaint.color = when (event.severity) {
                        "High" -> android.graphics.Color.RED
                        "Medium" -> android.graphics.Color.rgb(255, 165, 0) // Orange
                        else -> android.graphics.Color.rgb(0, 150, 200) // Cyan
                    }
                    canvas.drawText("Severity: ${event.severity}", margin.toFloat(), yPosition, severityPaint)
                    yPosition += 15f

                    // Details (wrap text if too long)
                    val details = "Details: ${event.details}"
                    if (details.length > 70) {
                        val lines = details.chunked(70)
                        lines.forEach { line ->
                            canvas.drawText(line, margin.toFloat(), yPosition, bodyPaint)
                            yPosition += 15f
                        }
                    } else {
                        canvas.drawText(details, margin.toFloat(), yPosition, bodyPaint)
                        yPosition += 15f
                    }

                    // Tower ID
                    event.towerId?.let {
                        canvas.drawText("Tower ID: $it", margin.toFloat(), yPosition, bodyPaint)
                        yPosition += 15f
                    }

                    // Signal Strength
                    event.signalStrength?.let {
                        canvas.drawText("Signal: $it dBm", margin.toFloat(), yPosition, bodyPaint)
                        yPosition += 15f
                    }

                    // Location
                    event.location?.let {
                        canvas.drawText("Location: $it", margin.toFloat(), yPosition, bodyPaint)
                        yPosition += 15f
                    }

                    // Separator
                    yPosition += 5f
                    canvas.drawLine(margin.toFloat(), yPosition, (pageWidth - margin).toFloat(), yPosition, bodyPaint)
                    yPosition += 15f
                }

                // Finish last page
                pdfDocument.finishPage(page)

                // Save PDF to file
                val context: Context = application
                val fileName = "cellshield_export_${System.currentTimeMillis()}.pdf"
                val file = File(context.cacheDir, fileName)

                FileOutputStream(file).use { outputStream ->
                    pdfDocument.writeTo(outputStream)
                }

                pdfDocument.close()

                return@withContext file

            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext null
            }
        }
    }

    private fun listenForAlerts() {
        val currentUserId = com.cellshield.app.auth.AuthManager.getCurrentUserId() ?: "anonymous"

        com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("alerts")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { return@addSnapshotListener }

                if (snapshot != null && !snapshot.isEmpty) {
                    val allAlerts = snapshot.toObjects(Alert::class.java)

                    // Calculate today's start time (midnight)
                    val todayStart = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis

                    // Filter to show only current user's alerts from today
                    val myTodayAlerts = allAlerts.filter { alert ->
                        alert.userId == currentUserId && alert.timestamp >= todayStart
                    }

                    _alerts.value = myTodayAlerts
                }
            }
    }

    private fun listenForMapAlerts() {
        com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("alerts")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { return@addSnapshotListener }

                if (snapshot != null && !snapshot.isEmpty) {
                    // All users' alerts for community heatmap
                    val allAlerts = snapshot.toObjects(Alert::class.java)
                    _mapAlerts.value = allAlerts
                }
            }
    }

    private fun listenForReviews() {
        ReviewRepository.listenForReviews { list ->
            _reviews.value = list
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopScanning()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                return DashboardViewModel(application) as T
            }
        }
    }
}
package com.cellshield.app.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.cellshield.app.BuildConfig
import com.cellshield.app.R
import com.cellshield.app.Screen
import com.cellshield.app.auth.AuthManager
import com.cellshield.app.data.Alert
import com.cellshield.app.data.DailyTrend
import com.cellshield.app.data.DetectionEvent
import com.cellshield.app.data.Review
import com.cellshield.app.data.ReviewRepository
import com.cellshield.app.ui.theme.Cyan
import com.cellshield.app.ui.theme.ErrorRed
import com.cellshield.app.ui.theme.LightBlue
import com.cellshield.app.ui.theme.SurfaceBlue
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.heatmaps.HeatmapTileProvider
import com.google.maps.android.heatmaps.WeightedLatLng
import com.google.android.gms.maps.model.TileOverlayOptions
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.cellshield.app.util.rememberBTSPermissionsState
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.clickable
import com.cellshield.app.btsdetection.DetectionResult
import com.cellshield.app.util.BTSPermissionsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    navController: NavController,
    viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory)
) {
    var selectedItem by remember { mutableStateOf(0) }
    val navItems = listOf("Home", "Alerts", "Map", "History", "Settings")
    val icons = listOf(
        Icons.Filled.Home,
        Icons.Filled.Notifications,
        Icons.Filled.Map,
        Icons.Filled.History,
        Icons.Filled.Settings
    )

    val userProfile by AuthManager.userProfileState.collectAsStateWithLifecycle()
    val userPhone = userProfile?.phone ?: "N/A"
    val userTelco = userProfile?.telco ?: "N/A"
    val userPhotoUrl = userProfile?.photoUrl ?: AuthManager.getCurrentUserPhoto()
    var scanning by remember { mutableStateOf(false) }
    val alerts by viewModel.alerts.collectAsStateWithLifecycle()
    val mapAlerts by viewModel.mapAlerts.collectAsStateWithLifecycle()
    val history by viewModel.historyEvents.collectAsStateWithLifecycle()
    val reviews by viewModel.reviews.collectAsStateWithLifecycle()
    val trends by viewModel.detectionTrends.collectAsStateWithLifecycle()
    val latestEvent by viewModel.latestEvent.collectAsStateWithLifecycle()

    // ✅ ADD PERMISSIONS HANDLING
    val context = LocalContext.current
    var showPermissionsDialog by remember { mutableStateOf(false) }
    val permissionsState = rememberBTSPermissionsState { granted: Boolean ->
        if (granted) {
            Toast.makeText(context, "Permissions granted. You can now start scanning.", Toast.LENGTH_SHORT).show()
        } else {
            // Check specifically what was denied
            val notificationsDenied = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            } else {
                false
            }

            if (notificationsDenied) {
                Toast.makeText(
                    context,
                    "Notifications denied - you'll only see in-app alerts",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(
                    context,
                    "Location/Phone permissions required for detection",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // Check permissions before scanning
    LaunchedEffect(scanning) {
        if (scanning && !permissionsState.hasPermissions) {
            scanning = false
            showPermissionsDialog = true
        }
    }

    Scaffold(
        modifier = Modifier.navigationBarsPadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(navItems[selectedItem], color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = SurfaceBlue) {
                navItems.forEachIndexed { index, item ->
                    NavigationBarItem(
                        icon = {
                            Icon(
                                icons[index],
                                contentDescription = item,
                                tint = if (selectedItem == index) Cyan else LightBlue
                            )
                        },
                        label = { Text(item, color = if (selectedItem == index) Cyan else LightBlue) },
                        selected = selectedItem == index,
                        onClick = { selectedItem = index }
                    )
                }
            }
        }
    ) { innerPadding ->
        Crossfade(
            targetState = selectedItem,
            modifier = Modifier.padding(innerPadding).fillMaxSize()
        ) { screenIndex ->
            when (screenIndex) {
                0 -> HomeScreen(

                    userPhone = userPhone,
                    permissionsState = permissionsState,
                    userTelco = userTelco,
                    userPhotoUrl = userPhotoUrl,
                    scanning = scanning,
                    onScanToggled = { scanning = it },
                    trends = trends,
                    latestEvent = latestEvent,
                    reviews = reviews,
                    onAddReview = { text, rating, done ->
                        val uid = AuthManager.getCurrentUserId()
                        val name = AuthManager.getCurrentUserName() ?: userProfile?.name ?: "Anonymous"
                        if (uid == null) {
                            done(false, "Not logged in"); return@HomeScreen
                        }
                        val review = Review(userId = uid, name = name, text = text, rating = rating)
                        ReviewRepository.addReview(review) { success, error -> done(success, error) }
                    },
                    viewModel = viewModel // ✅ Pass viewModel
                )
                1 -> AlertsScreen(alerts = alerts)
                2 -> MapScreen(alerts = mapAlerts)
                3 -> HistoryScreen(history = history)
                4 -> SettingsScreen(navController)
            }
        }
    }

    // ✅ Add permissions dialog
    if (showPermissionsDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionsDialog = false },
            title = { Text("Permissions Required") },
            text = {
                Text("BTS detection requires:\n\n• Location access\n• Phone state access\n• Notification permission (optional)\n\nThese permissions are needed to scan cellular networks and detect suspicious towers.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionsDialog = false
                    permissionsState.requestPermissions()
                }) {
                    Text("Grant Permissions")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}


/* ----------------------- HomeScreen ----------------------- */
// Add this updated HomeScreen to replace the existing one in DashboardScreen.kt

// Add this updated HomeScreen to replace the existing one in DashboardScreen.kt

@Composable
fun HomeScreen(
    permissionsState: BTSPermissionsState,
    userPhone: String,
    userTelco: String,
    userPhotoUrl: String?,
    scanning: Boolean,
    onScanToggled: (Boolean) -> Unit,
    trends: List<DailyTrend>,
    latestEvent: DetectionEvent?,
    reviews: List<Review>,
    onAddReview: (text: String, rating: Int, (Boolean, String?) -> Unit) -> Unit,
    viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory)
) {
    val context = LocalContext.current
    var showAddReviewDialog by remember { mutableStateOf(false) }
    var showBatteryWarningDialog by remember { mutableStateOf(false) }
    var showCountermeasuresDialog by remember { mutableStateOf(false) }

    // 🔥 Observe detection state
    val detectionResult by viewModel.currentDetectionResult.collectAsStateWithLifecycle()
    val detectionStatus by viewModel.detectionStatus.collectAsStateWithLifecycle()
    val shouldShowCountermeasures by viewModel.shouldShowCountermeasures.collectAsStateWithLifecycle()

    // 🔥 Capture in local variable
    val currentResult = detectionResult

    // 🔥 Start/Stop scanning
    LaunchedEffect(scanning) {
        if (scanning) {
            viewModel.startScanning()
        } else {
            viewModel.stopScanning()
        }
    }

    // 🔥 FIXED: Watch for countermeasures trigger from ViewModel
    LaunchedEffect(shouldShowCountermeasures) {
        android.util.Log.d("HomeScreen", "shouldShowCountermeasures changed: $shouldShowCountermeasures")
        if (shouldShowCountermeasures) {
            android.util.Log.d("HomeScreen", "Opening countermeasures dialog")
            showCountermeasuresDialog = true
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // Profile row
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceBlue)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (!userPhotoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = userPhotoUrl,
                        contentDescription = "User Profile",
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(LightBlue.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = "Default Profile",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(userPhone, style = MaterialTheme.typography.titleMedium.copy(color = Color.White))
                    Text(userTelco, style = MaterialTheme.typography.bodySmall.copy(color = LightBlue))
                }
                Spacer(Modifier.weight(1f))
            }
        }

        // Network scanning card
        item {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = SurfaceBlue)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Network Scanning", style = MaterialTheme.typography.titleMedium.copy(color = Color.White))
                    Switch(
                        checked = scanning,
                        onCheckedChange = { toggledOn ->
                            if (toggledOn) {
                                // Check permissions FIRST before showing battery dialog
                                if (permissionsState.hasPermissions) {
                                    showBatteryWarningDialog = true
                                } else {
                                    permissionsState.requestPermissions()
                                }
                            } else {
                                onScanToggled(false)
                                viewModel.stopScanning()
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.background,
                            checkedTrackColor = Cyan,
                            uncheckedThumbColor = MaterialTheme.colorScheme.background,
                            uncheckedTrackColor = LightBlue
                        )
                    )
                }
            }
        }


        item {
            val serviceRunning = com.cellshield.app.service.BTSDetectionService.isRunning()

            if (scanning && serviceRunning) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Cyan.copy(alpha = 0.1f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "Service Running",
                            tint = Cyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "Background Protection Active",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = Cyan,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                "Detection continues even when app is closed",
                                style = MaterialTheme.typography.bodySmall.copy(color = LightBlue)
                            )
                        }
                    }
                }
            }
        }

        // 🔥 Current Threat Level (REAL DATA from your detection)
        item {
            val (threatLevel, threatColor, threatDescription) = remember(scanning, currentResult) {
                val Orange = Color(0xFFFFA500)
                when {
                    scanning && currentResult == null -> Triple("Scanning...", Cyan, "Analyzing network...")
                    currentResult?.errorMessage != null -> Triple("Error", ErrorRed, currentResult.errorMessage ?: "Unknown error")
                    currentResult?.isThreats == true && currentResult.confidence == "HIGH" -> {
                        Triple("HIGH RISK", ErrorRed, "Fake BTS detected with high confidence")
                    }
                    currentResult?.isThreats == true && currentResult.confidence == "MEDIUM" -> {
                        Triple("MODERATE", Orange, "Suspicious activity detected")
                    }
                    currentResult?.isThreats == true -> {
                        Triple("LOW RISK", Orange, "Potential threat detected")
                    }
                    currentResult != null -> Triple("SAFE", Cyan, "No threats detected")
                    else -> Triple("IDLE", LightBlue, "Enable scanning to monitor network")
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceBlue)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Current Threat Level", style = MaterialTheme.typography.bodyMedium.copy(color = LightBlue))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = threatLevel,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                color = threatColor,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        if (scanning && currentResult == null) {
                            Spacer(Modifier.width(8.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Cyan,
                                strokeWidth = 2.dp
                            )
                        }
                    }
                    Text(threatDescription, style = MaterialTheme.typography.bodySmall.copy(color = LightBlue))

                    Spacer(Modifier.height(16.dp))
                    Divider(color = LightBlue.copy(alpha = 0.2f))
                    Spacer(Modifier.height(16.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        ThreatStat(
                            label = "Risk Level",
                            value = currentResult?.riskLevel?.toString() ?: "-"
                        )
                        ThreatStat(
                            label = "Severity",
                            value = currentResult?.confidence ?: "-"
                        )
                        ThreatStat(
                            label = "Cells",
                            value = currentResult?.cellCount?.toString() ?: "-"
                        )
                    }
                }
            }
        }

        // 🔥 Detection Details (when threats detected)
        if (currentResult?.isThreats == true) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            // 🔥 Manual trigger - show immediately without cooldown
                            showCountermeasuresDialog = true
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.1f))
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Warning,
                                    contentDescription = "Warning",
                                    tint = ErrorRed,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Threat Details",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = ErrorRed,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }

                            // Countermeasures button
                            TextButton(
                                onClick = {
                                    // 🔥 Manual trigger - show immediately
                                    showCountermeasuresDialog = true
                                }
                            ) {
                                Text("Countermeasures →", color = ErrorRed, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        currentResult.detectionReasons.forEach { reason ->
                            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text("• ", color = ErrorRed)
                                Text(reason, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        if (currentResult.isDowngrade) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "⚠️ Network Downgrade Detected - 2G Connection",
                                color = ErrorRed,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Operators: SIM: ${currentResult.simOperator} | Network: ${currentResult.networkOperator}",
                            color = LightBlue,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        // FBTS Detection Trends
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceBlue)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("FBTS Detection Trends", style = MaterialTheme.typography.bodyMedium.copy(color = LightBlue))
                    Text("Number of FBTS", style = MaterialTheme.typography.bodySmall.copy(color = LightBlue))
                    Spacer(Modifier.height(8.dp))
                    val trendData = remember(trends) {
                        if (trends.isEmpty()) return@remember emptyList()
                        val maxCount = trends.maxOfOrNull { it.count }?.toFloat() ?: 1f
                        trends.reversed().map { (it.count / maxCount).coerceAtLeast(0.1f) }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        if (trendData.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No trend data available", color = LightBlue.copy(alpha = 0.7f))
                            }
                        } else {
                            trendData.forEach { weight ->
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxHeight(weight)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(LightBlue)
                                )
                            }
                        }
                    }
                    Text(
                        "Time (Last 7 Days)",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall.copy(color = LightBlue)
                    )
                }
            }
        }

        // User Feedback
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("User Feedback", style = MaterialTheme.typography.titleMedium.copy(color = Color.White))
                    TextButton(onClick = { showAddReviewDialog = true }) {
                        Text("Add Review >", color = Cyan)
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (reviews.isEmpty()) {
                    UserFeedbackItem(name = "John Doe", review = "Very helpful in detecting unauthorized stations!", rating = 5)
                    Spacer(Modifier.height(8.dp))
                    UserFeedbackItem(name = "Jane Smith", review = "The alerts are timely and accurate.", rating = 5)
                } else {
                    reviews.forEach { r ->
                        UserFeedbackItem(name = r.name, review = r.text, rating = r.rating)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    // Add Review Dialog
    if (showAddReviewDialog) {
        var reviewText by remember { mutableStateOf("") }
        var rating by remember { mutableStateOf(5) }
        AlertDialog(
            onDismissRequest = { showAddReviewDialog = false },
            title = { Text("Add Review") },
            text = {
                Column {
                    OutlinedTextField(
                        value = reviewText,
                        onValueChange = { reviewText = it },
                        label = { Text("Your review") }
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Rating:")
                        Spacer(Modifier.width(8.dp))
                        RatingDropdown(selected = rating, onSelect = { rating = it })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (reviewText.isBlank()) {
                        Toast.makeText(context, "Please enter review text", Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    onAddReview(reviewText, rating) { success, error ->
                        if (!success) Toast.makeText(context, error ?: "Failed to add review", Toast.LENGTH_SHORT).show()
                    }
                    showAddReviewDialog = false
                }) { Text("Submit") }
            },
            dismissButton = {
                TextButton(onClick = { showAddReviewDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Battery Warning Dialog
    if (showBatteryWarningDialog) {
        AlertDialog(
            onDismissRequest = { showBatteryWarningDialog = false },
            title = { Text("Enable Background Scanning?") },
            text = {
                Text("Active network scanning runs in the background and may consume significant battery. \n\nAre you sure you want to proceed?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBatteryWarningDialog = false
                        onScanToggled(true)
                        Toast.makeText(context, "Scanning enabled", Toast.LENGTH_SHORT).show()
                    }
                ) { Text("Enable") }
            },
            dismissButton = {
                TextButton(onClick = { showBatteryWarningDialog = false }) { Text("Cancel") }
            }
        )
    }

    // 🔥 FIXED: Countermeasures Dialog
    if (showCountermeasuresDialog && currentResult != null) {
        android.util.Log.d("HomeScreen", "Showing countermeasures dialog")
        CountermeasuresDialog(
            detectionResult = currentResult,
            onDismiss = {
                android.util.Log.d("HomeScreen", "Countermeasures dialog dismissed")
                showCountermeasuresDialog = false
                viewModel.dismissCountermeasures()
            }
        )
    }
}

/* ----------------------- AlertsScreen ----------------------- */
@Composable
fun AlertsScreen(alerts: List<Alert>) {
    if (alerts.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.lottie_secure))
                LottieAnimation(
                    composition = composition,
                    iterations = LottieConstants.IterateForever,
                    modifier = Modifier.size(250.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "All Clear!",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
                Text(
                    "No active threats detected. We'll notify you if anything suspicious appears.",
                    color = LightBlue.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(alerts) { alert ->
                val (iconColor, bgColor) = when (alert.severity) {
                    "High" -> ErrorRed to ErrorRed.copy(alpha = 0.1f)
                    "Medium" -> Color(0xFFFFA500) to Color(0xFFFFA500).copy(alpha = 0.1f)
                    else -> Cyan to Cyan.copy(alpha = 0.1f)
                }
                AlertListItem(
                    icon = Icons.Filled.Warning,
                    iconColor = iconColor,
                    title = alert.title,
                    location = if (alert.operator.isNotEmpty()) "Operator: ${alert.operator}" else alert.location,
                    severity = alert.severity,
                    severityColor = iconColor,
                    severityBgColor = bgColor
                )
            }
        }
    }
}

@Composable
fun AlertListItem(icon: ImageVector, iconColor: Color, title: String, location: String, severity: String, severityColor: Color, severityBgColor: Color) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = SurfaceBlue)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = title, tint = iconColor, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge.copy(color = Color.White, fontWeight = FontWeight.Bold))
                Text(location, style = MaterialTheme.typography.bodySmall.copy(color = LightBlue))
            }
            Text(
                severity,
                color = severityColor,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier
                    .background(severityBgColor, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

/* ----------------------- MapScreen ----------------------- */
@Composable
fun MapScreen(alerts: List<Alert>) {
    var mapLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // Small delay to ensure smooth transition
        delay(100)
        mapLoaded = true
    }
    Box(modifier = Modifier.fillMaxSize()) {
        if (mapLoaded) {
            ActualMapContent(alerts = alerts)
        } else {
            // Show loading state while map initializes
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = SurfaceBlue
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = Cyan)
                    Spacer(Modifier.height(16.dp))
                    Text("Loading Map...", color = LightBlue)
                }
            }
        }
    }
}

@Composable
private fun ActualMapContent(alerts: List<Alert>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val kualaLumpur = LatLng(3.1390, 101.6869)

    var currentLocation by remember { mutableStateOf<LatLng?>(null) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(kualaLumpur, 10f)
    }

    // Check if location permission is granted
    val hasLocationPermission = remember {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    // Fetch current location
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            try {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                val cancellationTokenSource = CancellationTokenSource()

                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cancellationTokenSource.token
                ).addOnSuccessListener { location ->
                    location?.let {
                        val userLocation = LatLng(it.latitude, it.longitude)
                        currentLocation = userLocation
                        // Move camera to user's location
                        scope.launch {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngZoom(userLocation, 14f),
                                durationMs = 1000
                            )
                        }
                    }
                }
            } catch (e: SecurityException) {
                // Location permission not granted
                android.util.Log.e("MapScreen", "Location permission error", e)
            }
        }
    }

    // Parse alert locations for heatmap
    val heatmapData = remember(alerts, currentLocation) {
        val weightedPoints = mutableListOf<WeightedLatLng>()

        // Add current location as a reference point if available
        currentLocation?.let {
            weightedPoints.add(WeightedLatLng(it, 0.5))
        }

        // Add alerts with weight based on severity
        alerts.forEach { alert ->
            val parts = alert.location?.split(",")
            if (parts?.size == 2) {
                try {
                    val lat = parts[0].trim().toDouble()
                    val lng = parts[1].trim().toDouble()
                    val weight = when (alert.severity) {
                        "High" -> 10.0
                        "Medium" -> 5.0
                        else -> 2.0
                    }
                    weightedPoints.add(WeightedLatLng(LatLng(lat, lng), weight))
                } catch (e: NumberFormatException) {
                    android.util.Log.e("MapScreen", "Error parsing alert location", e)
                }
            }
        }

        weightedPoints
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(
            isMyLocationEnabled = hasLocationPermission
        ),
        uiSettings = MapUiSettings(
            myLocationButtonEnabled = true,
            zoomControlsEnabled = true
        )
    ) {
        // Add heatmap overlay
        if (heatmapData.isNotEmpty()) {
            MapEffect(heatmapData) { map ->
                val provider = HeatmapTileProvider.Builder()
                    .weightedData(heatmapData)
                    .radius(50) // Radius of influence for each point
                    .opacity(0.7) // Transparency of the heatmap
                    .build()

                map.addTileOverlay(
                    TileOverlayOptions().tileProvider(provider)
                )
            }
        }
    }
}

/* ----------------------- HistoryScreen ----------------------- */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(history: List<DetectionEvent>) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    var selectedExportFormat by remember { mutableStateOf("PDF") }
    val dateRangePickerState = rememberDateRangePickerState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    Box {
                        IconButton(onClick = { showExportMenu = true }) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = "Export History",
                                tint = LightBlue
                            )
                        }
                        DropdownMenu(
                            expanded = showExportMenu,
                            onDismissRequest = { showExportMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Export as PDF") },
                                onClick = {
                                    selectedExportFormat = "PDF"
                                    showExportMenu = false
                                    showDatePicker = true
                                },
                                leadingIcon = {
                                    Icon(Icons.Filled.Share, contentDescription = "PDF")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export as TXT") },
                                onClick = {
                                    selectedExportFormat = "TXT"
                                    showExportMenu = false
                                    showDatePicker = true
                                },
                                leadingIcon = {
                                    Icon(Icons.Filled.Share, contentDescription = "TXT")
                                }
                            )
                        }
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (history.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.lottie_empty))
                    LottieAnimation(
                        composition = composition,
                        iterations = LottieConstants.IterateForever,
                        modifier = Modifier.size(250.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No History Found",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )
                    Text(
                        "All detection events will be recorded here for your review.",
                        color = LightBlue.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(history) { event ->
                    HistoryListItem(event = event)
                }
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDatePicker = false
                        val startDate = dateRangePickerState.selectedStartDateMillis
                        val endDate = dateRangePickerState.selectedEndDateMillis
                        if (startDate != null && endDate != null) {
                            scope.launch {
                                val file = if (selectedExportFormat == "PDF") {
                                    viewModel.exportHistoryToPdf(startDate, endDate)
                                } else {
                                    viewModel.exportHistoryToTxt(startDate, endDate)
                                }

                                if (file != null) {
                                    shareFile(context, file)
                                    Toast.makeText(context, "Exported as $selectedExportFormat", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "No history found for this period", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            Toast.makeText(context, "Please select a start and end date", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = dateRangePickerState.selectedEndDateMillis != null
                ) {
                    Text("Export as $selectedExportFormat")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DateRangePicker(state = dateRangePickerState)
        }
    }
}

@Composable
fun HistoryListItem(event: DetectionEvent) {
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
    val formattedDate = remember(event.timestamp) { dateFormatter.format(Date(event.timestamp)) }
    var isExpanded by remember { mutableStateOf(false) }
    val (severityColor, severityBgColor) = when (event.severity) {
        "High" -> ErrorRed to ErrorRed.copy(alpha = 0.1f)
        "Medium" -> Color(0xFFFFA500) to Color(0xFFFFA500).copy(alpha = 0.1f)
        else -> Cyan to Cyan.copy(alpha = 0.1f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceBlue)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = "History Event",
                    tint = LightBlue,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(event.title, style = MaterialTheme.typography.bodyLarge.copy(color = Color.White, fontWeight = FontWeight.Bold))
                    Text(event.details, style = MaterialTheme.typography.bodySmall.copy(color = LightBlue))
                    Text(formattedDate, style = MaterialTheme.typography.labelSmall.copy(color = LightBlue.copy(alpha = 0.7f)))
                }
                Text(
                    event.severity,
                    color = severityColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .background(severityBgColor, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            AnimatedVisibility(visible = isExpanded) {
                Column {
                    Divider(color = LightBlue.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 12.dp))
                    HistoryDetailRow(label = "Tower ID", value = event.towerId)
                    HistoryDetailRow(label = "Signal Strength", value = event.signalStrength?.let { "$it dBm" })
                    HistoryDetailRow(label = "Location", value = event.location)
                }
            }
        }
    }
}

@Composable
private fun HistoryDetailRow(label: String, value: String?) {
    if (!value.isNullOrBlank()) {
        Row(modifier = Modifier.padding(top = 4.dp)) {
            Text(
                text = "$label: ",
                style = MaterialTheme.typography.bodySmall.copy(color = LightBlue, fontWeight = FontWeight.Bold)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(color = Color.White)
            )
        }
    }
}

@Composable
fun ThreatStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall.copy(color = Color.White, fontWeight = FontWeight.Bold))
        Text(label, style = MaterialTheme.typography.bodySmall.copy(color = LightBlue))
    }
}

@Composable
fun UserFeedbackItem(name: String, review: String, rating: Int) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = SurfaceBlue)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AccountCircle, contentDescription = "User", tint = LightBlue, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(name, fontWeight = FontWeight.Bold, color = Color.White)
                    Row {
                        repeat(5) { index ->
                            Icon(Icons.Filled.Star, contentDescription = "Star", tint = if (index < rating) Color(0xFFFFC107) else LightBlue.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(review, style = MaterialTheme.typography.bodyMedium.copy(color = LightBlue))
        }
    }
}

@Composable
private fun RatingDropdown(selected: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) { Text("$selected/5") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            (1..5).forEach { r ->
                DropdownMenuItem(text = { Text("$r") }, onClick = { onSelect(r); expanded = false })
            }
        }
    }
}

private fun shareFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.provider",
            file
        )

        // Detect MIME type based on file extension
        val mimeType = when (file.extension.lowercase()) {
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "CellShield History Export")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share History File"))
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Error: Could not share file.", Toast.LENGTH_SHORT).show()
    }
}

/**
 * A custom modifier that hides a composable by laying it out with zero size
 * when `isVisible` is false. This keeps the composable "alive" in the composition.
 */
/**fun Modifier.visible(isVisible: Boolean): Modifier = this.then(
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        if (isVisible) {
            // If visible, layout as normal
            layout(placeable.width, placeable.height) {
                placeable.placeRelative(0, 0)
            }
        } else {
            // If not visible, layout with 0 size and take no space
            layout(0, 0) {}
        }
    }
)*/
// Create: app/src/main/java/com/cellshield/app/ui/CountermeasuresDialog.kt

package com.cellshield.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.cellshield.app.btsdetection.DetectionResult
import com.cellshield.app.countermeasures.CountermeasuresManager
import com.cellshield.app.countermeasures.Priority
import com.cellshield.app.ui.theme.ErrorRed
import com.cellshield.app.ui.theme.LightBlue
import com.cellshield.app.ui.theme.SurfaceBlue

@Composable
fun CountermeasuresDialog(
    detectionResult: DetectionResult,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val countermeasuresManager = remember { CountermeasuresManager(context) }

    val actions = remember(detectionResult.isDowngrade) {
        countermeasuresManager.getCountermeasureActions(detectionResult.isDowngrade)
    }

    val recommendations = remember(detectionResult.riskLevel, detectionResult.isDowngrade, detectionResult.confidence) {
        countermeasuresManager.getSecurityRecommendations(
            detectionResult.riskLevel,
            detectionResult.isDowngrade,
            detectionResult.confidence
        )
    }

    var selectedTab by remember { mutableStateOf(0) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ErrorRed)
                        .padding(16.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = "Warning",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    "🚨 Security Countermeasures",
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    "Risk Level: ${detectionResult.riskLevel}/10 | ${detectionResult.confidence}",
                                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.9f))
                                )
                            }
                        }
                    }
                }

                // Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = SurfaceBlue
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Actions", color = if (selectedTab == 0) Color.White else LightBlue) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Recommendations", color = if (selectedTab == 1) Color.White else LightBlue) }
                    )
                }

                // Content
                when (selectedTab) {
                    0 -> ActionsTab(actions = actions)
                    1 -> RecommendationsTab(recommendations = recommendations)
                }

                // Close Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("Close", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ActionsTab(actions: List<com.cellshield.app.countermeasures.CountermeasureAction>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Take immediate action to protect yourself:",
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(Modifier.height(8.dp))
        }

        items(actions) { action ->
            CountermeasureActionCard(action = action)
        }
    }
}

@Composable
private fun CountermeasureActionCard(action: com.cellshield.app.countermeasures.CountermeasureAction) {
    val backgroundColor = when (action.priority) {
        Priority.CRITICAL -> ErrorRed.copy(alpha = 0.2f)
        Priority.HIGH -> Color(0xFFFFA500).copy(alpha = 0.2f)
        Priority.MEDIUM -> Color(0xFF00BCD4).copy(alpha = 0.2f)
        Priority.LOW -> LightBlue.copy(alpha = 0.1f)
    }

    val borderColor = when (action.priority) {
        Priority.CRITICAL -> ErrorRed
        Priority.HIGH -> Color(0xFFFFA500)
        Priority.MEDIUM -> Color(0xFF00BCD4)
        Priority.LOW -> LightBlue
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { action.action() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Text(
                action.icon,
                fontSize = 32.sp,
                modifier = Modifier.size(48.dp)
            )

            Spacer(Modifier.width(16.dp))

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    action.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    action.description,
                    style = MaterialTheme.typography.bodySmall.copy(color = LightBlue)
                )

                // Priority badge
                Text(
                    action.priority.name,
                    fontSize = 10.sp,
                    color = borderColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .background(borderColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            // Arrow
            Text("›", fontSize = 24.sp, color = LightBlue)
        }
    }
}

@Composable
private fun RecommendationsTab(recommendations: List<String>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "Security Recommendations:",
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(Modifier.height(8.dp))
        }

        items(recommendations) { recommendation ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceBlue)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        recommendation,
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                    )
                }
            }
        }
    }
}
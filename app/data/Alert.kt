package com.cellshield.app.data

data class Alert(
    val id: String = "",
    val userId: String = "", // User who created this alert
    val title: String = "",
    val location: String = "", // GPS coordinates in "lat,lng" format
    val operator: String = "", // Network operator name
    val severity: String = "Low",
    val timestamp: Long = 0L
)

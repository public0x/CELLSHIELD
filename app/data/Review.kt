package com.cellshield.app.data

// Review model stored in Firestore
data class Review(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val text: String = "",
    val rating: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

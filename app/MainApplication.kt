package com.cellshield.app

import android.app.Application
import com.cellshield.app.data.AppDatabase
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

class MainApplication : Application() {

    // A lazy-initialized database instance, as expected by your DashboardViewModel
    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(this)
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase
        FirebaseApp.initializeApp(this)

        // Initialize App Check Debug Provider
        // This is CRITICAL for debugging
        val appCheck = FirebaseAppCheck.getInstance()
        appCheck.installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )

        // Create the notification channel
        NotificationHelper(this).createNotificationChannel()
    }
}
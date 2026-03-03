// Create: app/src/main/java/com/cellshield/app/countermeasures/CountermeasuresManager.kt

package com.cellshield.app.countermeasures

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast

/**
 * Manages security countermeasures when fake BTS is detected
 */
class CountermeasuresManager(private val context: Context) {

    /**
     * Enable Airplane Mode
     * Note: On Android 4.2+ this requires system permissions, so we open settings
     */
    fun enableAirplaneMode(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                // Modern Android - can't toggle directly, open settings
                val intent = Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                Toast.makeText(
                    context,
                    "Please enable Airplane Mode manually for maximum security",
                    Toast.LENGTH_LONG
                ).show()
                true
            } else {
                // Legacy Android (unlikely to be used)
                val intent = Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                true
            }
        } catch (e: Exception) {
            android.util.Log.e("CountermeasuresManager", "Failed to open airplane mode settings", e)
            Toast.makeText(context, "Could not open airplane mode settings", Toast.LENGTH_SHORT).show()
            false
        }
    }

    /**
     * Open VPN settings to enable VPN
     */
    fun enableVPN(): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_VPN_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Toast.makeText(
                context,
                "Enable a VPN to encrypt your connection",
                Toast.LENGTH_LONG
            ).show()
            true
        } catch (e: Exception) {
            android.util.Log.e("CountermeasuresManager", "Failed to open VPN settings", e)
            Toast.makeText(context, "Could not open VPN settings", Toast.LENGTH_SHORT).show()
            false
        }
    }

    /**
     * Disable Mobile Data
     * Opens settings for user to disable manually
     */
    fun disableMobileData(): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_DATA_ROAMING_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Toast.makeText(
                context,
                "Disable mobile data to prevent fake BTS interception",
                Toast.LENGTH_LONG
            ).show()
            true
        } catch (e: Exception) {
            // Fallback to wireless settings
            try {
                val fallbackIntent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
                true
            } catch (e2: Exception) {
                android.util.Log.e("CountermeasuresManager", "Failed to open data settings", e2)
                Toast.makeText(context, "Could not open data settings", Toast.LENGTH_SHORT).show()
                false
            }
        }
    }

    /**
     * Disable 2G Network (Force 3G/4G/5G only)
     * Opens network settings
     */
    fun disable2GNetwork(): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Toast.makeText(
                context,
                "Set preferred network to 4G/5G only to avoid 2G downgrade attacks",
                Toast.LENGTH_LONG
            ).show()
            true
        } catch (e: Exception) {
            // Fallback to general settings
            try {
                val fallbackIntent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
                Toast.makeText(
                    context,
                    "Navigate to Mobile Networks → Preferred network type → Set to 4G/5G",
                    Toast.LENGTH_LONG
                ).show()
                true
            } catch (e2: Exception) {
                android.util.Log.e("CountermeasuresManager", "Failed to open network settings", e2)
                Toast.makeText(context, "Could not open network settings", Toast.LENGTH_SHORT).show()
                false
            }
        }
    }

    /**
     * Open location services to help user move to different location
     */
    fun openMapsForRelocation(): Boolean {
        return try {
            // Open Google Maps
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("geo:0,0?q=safe+location")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Toast.makeText(
                context,
                "Move to a different location away from the suspicious tower",
                Toast.LENGTH_LONG
            ).show()
            true
        } catch (e: Exception) {
            android.util.Log.e("CountermeasuresManager", "Failed to open maps", e)
            Toast.makeText(
                context,
                "Consider moving to a different location",
                Toast.LENGTH_SHORT
            ).show()
            false
        }
    }

    /**
     * Get security recommendations based on threat level
     */
    fun getSecurityRecommendations(
        riskLevel: Int,
        isDowngrade: Boolean,
        confidence: String
    ): List<String> {
        val recommendations = mutableListOf<String>()

        // Critical recommendations for high risk
        if (riskLevel >= 8 || confidence == "HIGH") {
            recommendations.add("🚨 CRITICAL: Enable Airplane Mode immediately")
            recommendations.add("🔒 Do NOT make calls or send SMS")
            recommendations.add("🚫 Avoid accessing banking or sensitive apps")
            recommendations.add("📍 Move to a different location urgently")
            recommendations.add("🔐 Enable VPN if you must use data")
        } else if (riskLevel >= 5 || confidence == "MEDIUM") {
            recommendations.add("⚠️ Enable Airplane Mode as precaution")
            recommendations.add("🔐 Use VPN for all internet activities")
            recommendations.add("🚫 Avoid sensitive transactions")
            recommendations.add("📍 Consider moving to different location")
        } else {
            recommendations.add("🛡️ Monitor network status regularly")
            recommendations.add("🔐 Consider enabling VPN")
            recommendations.add("📱 Stay alert for unusual behavior")
        }

        // 2G specific recommendations
        if (isDowngrade) {
            recommendations.add("📶 Disable 2G network in settings (Force 4G/5G only)")
            recommendations.add("⚠️ 2G has weak encryption - avoid all communications")
        }

        // General security tips
        recommendations.add("🔄 Restart your device after moving locations")
        recommendations.add("📧 Report suspicious activity to your carrier")

        return recommendations
    }

    /**
     * Generate countermeasures action list
     */
    fun getCountermeasureActions(isDowngrade: Boolean): List<CountermeasureAction> {
        val actions = mutableListOf<CountermeasureAction>()

        actions.add(
            CountermeasureAction(
                title = "Enable Airplane Mode",
                description = "Immediately disconnect from all networks",
                icon = "✈️",
                priority = Priority.CRITICAL,
                action = { enableAirplaneMode() }
            )
        )

        if (isDowngrade) {
            actions.add(
                CountermeasureAction(
                    title = "Disable 2G Network",
                    description = "Force device to use 4G/5G only",
                    icon = "📶",
                    priority = Priority.HIGH,
                    action = { disable2GNetwork() }
                )
            )
        }

        actions.add(
            CountermeasureAction(
                title = "Enable VPN",
                description = "Encrypt your connection with VPN",
                icon = "🔐",
                priority = Priority.HIGH,
                action = { enableVPN() }
            )
        )

        actions.add(
            CountermeasureAction(
                title = "Disable Mobile Data",
                description = "Prevent data interception",
                icon = "📵",
                priority = Priority.MEDIUM,
                action = { disableMobileData() }
            )
        )

        actions.add(
            CountermeasureAction(
                title = "Change Location",
                description = "Move away from suspicious tower",
                icon = "📍",
                priority = Priority.MEDIUM,
                action = { openMapsForRelocation() }
            )
        )

        return actions
    }
}

/**
 * Represents a countermeasure action
 */
data class CountermeasureAction(
    val title: String,
    val description: String,
    val icon: String,
    val priority: Priority,
    val action: () -> Boolean
)

/**
 * Priority levels for countermeasures
 */
enum class Priority {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW
}
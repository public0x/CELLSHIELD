// app/src/main/java/com/cellshield/app/util/PermissionsHandler.kt

package com.cellshield.app.util

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
fun rememberBTSPermissionsState(
    onPermissionsResult: (Boolean) -> Unit
): BTSPermissionsState {
    val context = LocalContext.current

    val requiredPermissions = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.READ_PHONE_STATE)
        }
    }

    val optionalPermissions = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Check if REQUIRED permissions are granted (location + phone state)
    val hasRequiredPermissions by remember {
        derivedStateOf {
            // Need at least ONE location permission
            val hasLocation = (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED) ||
                    (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED)

            // Need phone state permission
            val hasPhoneState = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED

            hasLocation && hasPhoneState
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        // Check if required permissions were granted
        val hasLocation = (permissionsMap[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
                (permissionsMap[Manifest.permission.ACCESS_COARSE_LOCATION] == true)

        val hasPhoneState = permissionsMap[Manifest.permission.READ_PHONE_STATE] == true

        val granted = hasLocation && hasPhoneState
        onPermissionsResult(granted)
    }

    return remember(hasRequiredPermissions) {
        BTSPermissionsState(
            hasPermissions = hasRequiredPermissions,
            requestPermissions = {
                launcher.launch((requiredPermissions + optionalPermissions).toTypedArray())
            }
        )
    }
}

data class BTSPermissionsState(
    val hasPermissions: Boolean,
    val requestPermissions: () -> Unit
)
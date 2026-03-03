package com.cellshield.app.ui

import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.cellshield.app.NotificationHelper // 👈 ADD THIS IMPORT
import com.cellshield.app.Screen
import com.cellshield.app.auth.AuthManager
import com.cellshield.app.data.Alert
import com.cellshield.app.ui.theme.Cyan
import com.cellshield.app.ui.theme.ErrorRed
import com.cellshield.app.ui.theme.LightBlue
import com.cellshield.app.ui.theme.SurfaceBlue
import com.google.firebase.FirebaseException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val userProfile by AuthManager.userProfileState.collectAsState()

    val userName = userProfile?.name ?: AuthManager.getCurrentUserName() ?: "Anonymous"
    val userPhone = userProfile?.phone ?: "N/A"
    val userTelco = userProfile?.telco ?: "N/A"
    val userEmail = AuthManager.getCurrentUserEmail() ?: "N/A"
    val userPhotoUrl = userProfile?.photoUrl ?: AuthManager.getCurrentUserPhoto()

    var showEditPhoneDialog by remember { mutableStateOf(false) }
    var showEditTelcoDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showGoogleResetInfoDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile Section ...
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceBlue)
                    .padding(16.dp)
            ) {
                if (!userPhotoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = userPhotoUrl,
                        contentDescription = "User Profile",
                        modifier = Modifier.size(64.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.size(64.dp).clip(CircleShape).background(SurfaceBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = "Default Profile",
                            tint = LightBlue,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(userName, style = MaterialTheme.typography.titleMedium.copy(color = Color.White))
                    Text(userEmail, style = MaterialTheme.typography.bodySmall.copy(color = LightBlue))
                }
            }

            // Settings Items ...
            SettingsItem(label = "Phone Number", value = userPhone, icon = Icons.Filled.Phone) {
                showEditPhoneDialog = true
            }
            SettingsItem(label = "Telco Provider", value = userTelco, icon = Icons.Filled.SignalCellularAlt) {
                showEditTelcoDialog = true
            }
            SettingsItem(label = "Email", value = userEmail, icon = Icons.Filled.Email)

            Divider(color = LightBlue.copy(alpha = 0.3f))

            FaqSection()

            Divider(color = LightBlue.copy(alpha = 0.3f))

            SettingsItem(label = "Reset Password", icon = Icons.Filled.LockReset) {
                val providerId = AuthManager.getCurrentUser()?.providerData?.find { it.email == userEmail }?.providerId
                if (providerId == GoogleAuthProvider.PROVIDER_ID) {
                    showGoogleResetInfoDialog = true
                } else {
                    showResetDialog = true
                }
            }

            SettingsItem(label = "Logout", icon = Icons.Filled.Logout) {
                AuthManager.logout {
                    navController.navigate(Screen.Welcome.route) {
                        popUpTo(Screen.Dashboard.route) { inclusive = true }
                    }
                }
            }

            SettingsItem(label = "Delete Account", icon = Icons.Filled.Delete, isDestructive = true) {
                showDeleteDialog = true
            }

            Divider(color = LightBlue.copy(alpha = 0.3f))

            // --- 👇 UPDATED TEST ALERT BUTTON ---
            SettingsItem(label = "Send Test Alert", icon = Icons.Filled.NotificationAdd, isDestructive = true) {
                // 1. Create the Alert object
                val alertTitle = "High Severity Threat Detected"
                val alertMessage = "A potential FBTS has been detected nearby."
                val newAlert = Alert(
                    id = "",
                    userId = AuthManager.getCurrentUserId() ?: "anonymous",
                    title = alertTitle,
                    location = "3.0160, 101.7110",
                    operator = "Test Operator",
                    severity = "High",
                    timestamp = System.currentTimeMillis()
                )

                // 2. Save it to Firestore (so it appears in the "Alerts" tab)
                FirebaseFirestore.getInstance().collection("alerts")
                    .add(newAlert)
                    .addOnSuccessListener {
                        Toast.makeText(context, "Test alert sent to database!", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "Failed to send alert: ${it.message}", Toast.LENGTH_SHORT).show()
                    }

                // 3. Show a REAL system notification
                val notificationHelper = NotificationHelper(context)
                notificationHelper.showHighPriorityAlert(alertTitle, alertMessage)
            }
            // --- 👆 END OF UPDATE ---
        }
    }

    // --- All dialogs (unchanged) ---
    if (showEditPhoneDialog) {
        UpdatePhoneDialog(
            currentPhone = userPhone,
            onDismiss = { showEditPhoneDialog = false },
            onSuccess = { newPhone ->
                AuthManager.updateUserAccount(userName, newPhone, userTelco) { success, error ->
                    if (success) {
                        Toast.makeText(context, "Phone number updated!", Toast.LENGTH_SHORT).show()
                        showEditPhoneDialog = false
                    } else {
                        Toast.makeText(context, "Auth updated, but profile save failed: $error", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }
    if (showEditTelcoDialog) {
        var newTelco by remember { mutableStateOf(userTelco) }
        AlertDialog(
            onDismissRequest = { showEditTelcoDialog = false },
            title = { Text("Edit Telco Provider") },
            text = {
                TelcoDropdown(
                    selectedTelco = newTelco,
                    onTelcoSelected = { newTelco = it }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = userProfile?.name ?: AuthManager.getCurrentUserName() ?: ""
                        AuthManager.updateUserAccount(name, userPhone, newTelco) { success, error ->
                            if (!success) {
                                Toast.makeText(context, error ?: "Failed to update profile", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Telco updated!", Toast.LENGTH_SHORT).show()
                                showEditTelcoDialog = false
                            }
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditTelcoDialog = false }) { Text("Cancel") }
            }
        )
    }
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Password?") },
            text = { Text("This will send a password reset link to:\n$userEmail\n\nDo you want to continue?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        AuthManager.getCurrentUserEmail()?.let { email ->
                            AuthManager.resetPassword(email) { success, error ->
                                val message = if (success) "Reset email sent." else error ?: "Failed to send email."
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                        }
                        showResetDialog = false
                    }
                ) { Text("Send Link") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            }
        )
    }
    if (showGoogleResetInfoDialog) {
        AlertDialog(
            onDismissRequest = { showGoogleResetInfoDialog = false },
            title = { Text("Google Account") },
            text = { Text("You are logged in with your Google account. To change your password, please change it through your Google account settings.") },
            confirmButton = {
                TextButton(onClick = { showGoogleResetInfoDialog = false }) { Text("OK") }
            }
        )
    }
    if (showDeleteDialog) {
        DeleteAccountDialog(
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                AuthManager.deleteAccount { success, error ->
                    if (success) {
                        Toast.makeText(context, "Account deleted", Toast.LENGTH_SHORT).show()
                        navController.navigate(Screen.Welcome.route) {
                            popUpTo(Screen.Dashboard.route) { inclusive = true }
                        }
                    } else {
                        val errorMsg = if (error != null && error.contains("RECENT_LOGIN_REQUIRED")) {
                            "This is a sensitive operation. Please log out and log in again before deleting your account."
                        } else {
                            error ?: "Failed to delete account."
                        }
                        Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }
}

// ... (All other helper composables: SettingsItem, DeleteAccountDialog, FaqSection, etc. remain unchanged) ...
// (Make sure they are still at the bottom of your SettingsScreen.kt file)
@Composable
private fun SettingsItem(
    label: String,
    value: String? = null,
    icon: ImageVector,
    isDestructive: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val textColor = if (isDestructive) MaterialTheme.colorScheme.error else Color.White
    val iconTint = if (isDestructive) MaterialTheme.colorScheme.error else Cyan
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceBlue)
            .padding(16.dp)
            .let { if (onClick != null) it.clickable { onClick() } else it },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = iconTint)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge.copy(color = textColor))
            if (!value.isNullOrEmpty()) {
                Text(value, style = MaterialTheme.typography.bodySmall.copy(color = LightBlue))
            }
        }
        if (onClick != null) {
            Icon(Icons.Filled.ArrowForward, contentDescription = "Edit", tint = LightBlue)
        }
    }
}

@Composable
private fun DeleteAccountDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var confirmText by remember { mutableStateOf("") }
    val isConfirmEnabled = confirmText == "DELETE"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Account?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("This action is permanent and cannot be undone. All your data, including profile and history, will be erased.")
                Text("To confirm, please type \"DELETE\" in the box below.")
                OutlinedTextField(
                    value = confirmText,
                    onValueChange = { confirmText = it },
                    label = { Text("Type DELETE to confirm") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = isConfirmEnabled
            ) {
                Text("DELETE", color = if (isConfirmEnabled) ErrorRed else Color.Gray)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private data class FaqItemData(val question: String, val answer: String)
@Composable
private fun FaqSection() {
    val faqItems = listOf(
        FaqItemData(
            question = "What is a Fake Base Station (FBTS)?",
            answer = "An FBTS, also known as an 'IMSI Catcher' or 'Stingray', is a device that mimics a legitimate cell tower to intercept mobile phone traffic and track users."
        ),
        FaqItemData(
            question = "Why does CellShield need root access?",
            answer = "Root access is required to perform deep network analysis and access low-level radio information that isn't available to standard Android apps. This is necessary for accurately detecting potential threats."
        ),
        FaqItemData(
            question = "Will this app drain my battery?",
            answer = "Active background scanning (when enabled) will consume additional battery. We recommend using it when you are in a location of concern rather than 24/7. The app is idle when scanning is turned off."
        )
    )

    Column {
        Text(
            text = "Frequently Asked Questions",
            style = MaterialTheme.typography.titleMedium.copy(color = Color.White),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        faqItems.forEach { item ->
            FaqItem(item = item)
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FaqItem(item: FaqItemData) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceBlue),
        onClick = { isExpanded = !isExpanded }
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = item.question,
                    style = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = "Expand",
                    tint = Cyan
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Text(
                    text = item.answer,
                    style = MaterialTheme.typography.bodyMedium.copy(color = LightBlue),
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TelcoDropdown(
    selectedTelco: String,
    onTelcoSelected: (String) -> Unit,
    enabled: Boolean = true
) {
    val telcoList = listOf("Maxis", "CelcomDigi", "U Mobile", "Yes 4G/5G", "unifi Mobile", "Other")
    var isExpanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = isExpanded && enabled,
        onExpandedChange = { if (enabled) isExpanded = it }
    ) {
        OutlinedTextField(
            value = selectedTelco,
            onValueChange = {},
            readOnly = true,
            label = { Text("Telco Provider", color = if (enabled) LightBlue else LightBlue.copy(alpha = 0.7f)) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded && enabled)
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Cyan,
                unfocusedBorderColor = LightBlue.copy(alpha = 0.5f),
                cursorColor = Cyan,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                disabledTextColor = Color.White.copy(alpha = 0.7f),
                disabledTrailingIconColor = LightBlue.copy(alpha = 0.7f),
                disabledLabelColor = LightBlue.copy(alpha = 0.7f),
                disabledBorderColor = LightBlue.copy(alpha = 0.5f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            shape = RoundedCornerShape(12.dp),
            enabled = enabled
        )

        ExposedDropdownMenu(
            expanded = isExpanded && enabled,
            onDismissRequest = { isExpanded = false }
        ) {
            telcoList.forEach { telcoName ->
                DropdownMenuItem(
                    text = { Text(telcoName) },
                    onClick = {
                        onTelcoSelected(telcoName)
                        isExpanded = false
                    }
                )
            }
        }
    }
}

private enum class UpdatePhoneStep {
    ENTER_PHONE,
    VERIFY_CODE,
}
@Composable
private fun UpdatePhoneDialog(
    currentPhone: String,
    onDismiss: () -> Unit,
    onSuccess: (newPhone: String) -> Unit
) {
    var step by remember { mutableStateOf(UpdatePhoneStep.ENTER_PHONE) }
    var newPhone by remember { mutableStateOf(currentPhone) }
    var smsCode by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()

    val authCallbacks = remember {
        object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                isSubmitting = false
                smsCode = credential.smsCode ?: ""
                errorMessage = "Phone auto-verified. Click 'Update' to confirm."
            }

            override fun onVerificationFailed(e: FirebaseException) {
                isSubmitting = false
                step = UpdatePhoneStep.ENTER_PHONE
                errorMessage = e.message ?: "Verification failed."
            }

            override fun onCodeSent(newVerificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                isSubmitting = false
                verificationId = newVerificationId
                step = UpdatePhoneStep.VERIFY_CODE
                errorMessage = null
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text("Update Phone Number") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (step == UpdatePhoneStep.ENTER_PHONE) {
                    Text("Enter your new phone number to receive a verification code.")
                    OutlinedTextField(
                        value = newPhone,
                        onValueChange = { newPhone = it },
                        label = { Text("Phone Number (e.g. +6012...)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true
                    )
                } else {
                    Text("Enter the 6-digit code sent to $newPhone.")
                    OutlinedTextField(
                        value = smsCode,
                        onValueChange = { if (it.length <= 6) smsCode = it },
                        label = { Text("6-Digit Code") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }

                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp))
                }

                errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (step) {
                        UpdatePhoneStep.ENTER_PHONE -> {
                            if (newPhone == currentPhone) {
                                errorMessage = "This is your current phone number."
                                return@TextButton
                            }
                            isSubmitting = true
                            errorMessage = null
                            try {
                                val options = PhoneAuthOptions.newBuilder(AuthManager.getAuthInstance())
                                    .setPhoneNumber(newPhone)
                                    .setTimeout(60L, TimeUnit.SECONDS)
                                    .setActivity(activity)
                                    .setCallbacks(authCallbacks)
                                    .build()
                                PhoneAuthProvider.verifyPhoneNumber(options)
                            } catch (e: Exception) {
                                isSubmitting = false
                                errorMessage = e.message ?: "Invalid phone number."
                            }
                        }
                        UpdatePhoneStep.VERIFY_CODE -> {
                            if (smsCode.length < 6 || verificationId == null) {
                                errorMessage = "Please enter the 6-digit code."
                                return@TextButton
                            }
                            isSubmitting = true
                            errorMessage = null

                            val credential = PhoneAuthProvider.getCredential(verificationId!!, smsCode)
                            AuthManager.updatePhoneNumber(credential) { success, error ->
                                if (success) {
                                    onSuccess(newPhone)
                                } else {
                                    isSubmitting = false
                                    val errorMsg = if (error != null && error.contains("RECENT_LOGIN_REQUIRED")) {
                                        "This is a sensitive operation. Please log out and log in again to update your phone number."
                                    } else {
                                        error ?: "Update failed."
                                    }

                                    errorMessage = errorMsg
                                }
                            }
                        }
                    }
                },
                enabled = !isSubmitting
            ) {
                Text(if (step == UpdatePhoneStep.ENTER_PHONE) "Send Code" else "Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("Cancel")
            }
        }
    )
}
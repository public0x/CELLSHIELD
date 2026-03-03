package com.cellshield.app.ui

import android.app.Activity
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.cellshield.app.R
import com.cellshield.app.Screen
import com.cellshield.app.auth.AuthManager
import com.cellshield.app.ui.common.AppBackground
import com.cellshield.app.ui.theme.*
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.time.Year
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ... (ThemedTextField composable remains unchanged) ...
@Composable
fun ThemedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    enabled: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = LightBlue) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Cyan,
            unfocusedBorderColor = LightBlue.copy(alpha = 0.5f),
            cursorColor = Cyan,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            disabledTextColor = Color.White.copy(alpha = 0.7f),
            disabledLabelColor = LightBlue.copy(alpha = 0.7f)
        ),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        singleLine = true,
        enabled = enabled
    )
}

// --- 👇 UPDATED ThemedButton composable ---
@Composable
fun ThemedButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false // 👈 NEW PARAMETER
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Cyan,
            contentColor = DarkBlue,
            disabledContainerColor = Cyan.copy(alpha = 0.5f),
            disabledContentColor = DarkBlue.copy(alpha = 0.7f)
        ),
        enabled = enabled && !isLoading // 👈 Button is disabled when loading
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = DarkBlue, // Color of the spinner
                strokeWidth = 2.dp
            )
        } else {
            Text(text, fontWeight = FontWeight.Bold)
        }
    }
}
// --- 👆 END OF UPDATE ---

@Composable
fun SplashScreen(navController: NavController) {
    // ... (SplashScreen composable remains unchanged) ...
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1500)
        if (AuthManager.isLoggedIn()) {
            AuthManager.fetchUserProfile {
                navController.navigate(Screen.Dashboard.route) {
                    popUpTo(Screen.Splash.route) { inclusive = true }
                }
            }
        } else {
            navController.navigate(Screen.Welcome.route) {
                popUpTo(Screen.Splash.route) { inclusive = true }
            }
        }
    }
    AppBackground {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_cellshield_logo),
                contentDescription = "CellShield Logo",
                modifier = Modifier.size(250.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "CellShield",
                style = MaterialTheme.typography.headlineLarge.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Securing Your Network",
                style = MaterialTheme.typography.bodyLarge.copy(color = LightBlue)
            )
        }
    }
}

@Composable
fun WelcomeScreen(navController: NavController) {
    // ... (WelcomeScreen composable remains unchanged) ...
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    AppBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.weight(1f))
            Image(
                painter = painterResource(id = R.drawable.ic_cellshield_logo),
                contentDescription = "CellShield Logo",
                modifier = Modifier.size(200.dp)
            )
            Text(
                "CellShield",
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold, color = Color.White)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Detect fake base stations • Alerts • Countermeasures",
                style = MaterialTheme.typography.bodyLarge.copy(color = LightBlue),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.weight(1.5f))
            ThemedButton(onClick = { navController.navigate("login") }, text = "Login with Email")
            Spacer(Modifier.height(16.dp))
            ThemedButton(onClick = {
                AuthManager.logout {}
                navController.navigate("register")
            }, text = "Register with Email")
            Spacer(Modifier.height(16.dp))
            Text("or", color = LightBlue)
            Spacer(Modifier.height(16.dp))
            ThemedButton(
                onClick = {
                    coroutineScope.launch {
                        try {
                            val credentialManager = CredentialManager.create(context)
                            val googleIdOption = GetGoogleIdOption.Builder()
                                .setFilterByAuthorizedAccounts(false)
                                .setServerClientId("746810109778-hntslngpd44246ts29l4tvm5mphsoj0g.apps.googleusercontent.com")
                                .build()
                            val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()
                            val result = credentialManager.getCredential(context, request)
                            val credential = result.credential
                            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data)
                                AuthManager.firebaseAuthWithGoogle(googleIdToken.idToken) { isNewUser, error ->
                                    if (error != null) {
                                        message = error
                                    } else if (isNewUser) {
                                        navController.navigate("register")
                                    } else {
                                        navController.navigate("dashboard") { popUpTo("welcome") { inclusive = true } }
                                    }
                                }
                            } else { message = "Unrecognized credential type." }
                        } catch (e: GetCredentialException) {
                            message = "Google Sign-In failed. Ensure you have a Google account."
                            Log.e("WelcomeScreen", "Google Sign-In failed", e)
                        }
                    }
                },
                text = "Continue with Google"
            )
            message?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.weight(0.5f))
            Text("© ${Year.now()} CellShield", style = MaterialTheme.typography.labelSmall, color = LightBlue.copy(alpha = 0.7f))
        }
    }
}

// --- 👇 UPDATED LoginScreen composable ---
@Composable
fun LoginScreen(navController: NavController) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) } // 👈 NEW

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        AppBackground {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(32.dp)
                    .statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Welcome Back", style = MaterialTheme.typography.headlineLarge.copy(color = Color.White))
                Spacer(Modifier.height(48.dp))
                ThemedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = "Email",
                    keyboardType = KeyboardType.Email,
                    enabled = !isLoading // 👈 NEW
                )
                Spacer(Modifier.height(16.dp))
                ThemedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Password",
                    isPassword = true,
                    enabled = !isLoading // 👈 NEW
                )
                Spacer(Modifier.height(32.dp))
                ThemedButton(
                    onClick = {
                        val emailTrimmed = email.trim()
                        val passwordTrimmed = password.trim()

                        if (emailTrimmed.isBlank() || passwordTrimmed.isBlank()) {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    message = "Email and password cannot be empty.",
                                    withDismissAction = true
                                )
                            }
                            return@ThemedButton
                        }

                        isLoading = true // 👈 NEW
                        AuthManager.loginWithEmail(emailTrimmed, passwordTrimmed) { success, error ->
                            isLoading = false // 👈 NEW
                            if (success) {
                                val profile = AuthManager.userProfileState.value
                                if (profile?.consentAccepted == true) {
                                    navController.navigate("dashboard") {
                                        popUpTo("welcome") { inclusive = true }
                                    }
                                } else {
                                    navController.navigate("consent") {
                                        popUpTo("welcome") { inclusive = true }
                                    }
                                }
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = error ?: "An unknown login error occurred.",
                                        withDismissAction = true
                                    )
                                }
                            }
                        }
                    },
                    text = "Login",
                    isLoading = isLoading // 👈 NEW
                )
                Spacer(Modifier.height(8.dp))

                TextButton(
                    onClick = {
                        scope.launch { snackbarHostState.showSnackbar("Sending...") }
                        AuthManager.resendVerificationEmail { ok, err ->
                            val message = if (ok) {
                                "Verification email sent again. Please check your inbox."
                            } else {
                                err ?: "Failed to resend verification email."
                            }
                            scope.launch { snackbarHostState.showSnackbar(message) }
                        }
                    },
                    enabled = !isLoading // 👈 NEW
                ) {
                    Text("Resend verification email", color = Cyan)
                }
            }
        }
    }
}
// --- 👆 END OF UPDATE ---


// --- 👇 UPDATED RegisterScreen composable ---
private enum class RegisterStep {
    ENTER_DETAILS,
    VERIFY_PHONE,
}

@Composable
fun RegisterScreen(navController: NavController) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("+60") }
    var telco by remember { mutableStateOf("") }
    var smsCode by remember { mutableStateOf("") }

    var step by remember { mutableStateOf(RegisterStep.ENTER_DETAILS) }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) } // 👈 RENAMED (was isSubmitting)
    val isGoogleUser = AuthManager.getCurrentUserEmail() != null

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val activity = context as Activity

    val authCallbacks = remember {
        object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                smsCode = credential.smsCode ?: ""
                scope.launch { snackbarHostState.showSnackbar("Phone verified automatically!") }
                isLoading = false
            }

            override fun onVerificationFailed(e: FirebaseException) {
                scope.launch { snackbarHostState.showSnackbar(e.message ?: "Verification failed.") }
                isLoading = false
                step = RegisterStep.ENTER_DETAILS
            }

            override fun onCodeSent(newVerificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                verificationId = newVerificationId
                isLoading = false
                step = RegisterStep.VERIFY_PHONE
                scope.launch { snackbarHostState.showSnackbar("Verification code sent!") }
            }
        }
    }

    val photoUrl = AuthManager.getCurrentUserPhoto()
    LaunchedEffect(isGoogleUser) {
        if (isGoogleUser) {
            email = AuthManager.getCurrentUserEmail() ?: ""
            name = AuthManager.getCurrentUserName() ?: ""
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        AppBackground {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(32.dp)
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "Complete Registration",
                    style = MaterialTheme.typography.headlineMedium.copy(color = Color.White),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))

                if (!photoUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = photoUrl,
                        contentDescription = "Profile",
                        modifier = Modifier.size(80.dp).clip(CircleShape)
                    )
                    Spacer(Modifier.height(16.dp))
                }

                ThemedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Full Name",
                    enabled = step == RegisterStep.ENTER_DETAILS && !isLoading
                )
                Spacer(Modifier.height(16.dp))

                ThemedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = "Email",
                    keyboardType = KeyboardType.Email,
                    enabled = !isGoogleUser && step == RegisterStep.ENTER_DETAILS && !isLoading
                )
                Spacer(Modifier.height(16.dp))

                if (!isGoogleUser) {
                    ThemedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = "Password",
                        isPassword = true,
                        enabled = step == RegisterStep.ENTER_DETAILS && !isLoading
                    )
                    Spacer(Modifier.height(16.dp))
                }

                ThemedTextField(
                    value = phone,
                    onValueChange = { if (it.startsWith("+")) phone = it },
                    label = "Phone Number (e.g. +6012...)",
                    keyboardType = KeyboardType.Phone,
                    enabled = step == RegisterStep.ENTER_DETAILS && !isLoading
                )
                Spacer(Modifier.height(16.dp))

                TelcoDropdown(
                    selectedTelco = telco,
                    onTelcoSelected = { telco = it },
                    enabled = step == RegisterStep.ENTER_DETAILS && !isLoading
                )
                Spacer(Modifier.height(16.dp))

                AnimatedVisibility(visible = step == RegisterStep.VERIFY_PHONE) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ThemedTextField(
                            value = smsCode,
                            onValueChange = { if (it.length <= 6) smsCode = it },
                            label = "6-Digit Code",
                            keyboardType = KeyboardType.Number,
                            enabled = !isLoading
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                step = RegisterStep.ENTER_DETAILS
                                scope.launch {
                                    snackbarHostState.showSnackbar("Requesting new code...")
                                    delay(100)
                                    sendVerificationCode(activity, phone, authCallbacks)
                                }
                            },
                            enabled = !isLoading
                        ) {
                            Text("Resend Code", color = Cyan)
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))

                ThemedButton(
                    onClick = {
                        when (step) {
                            RegisterStep.ENTER_DETAILS -> {
                                if (name.isBlank() || phone.isBlank() || telco.isBlank() || email.isBlank() || (!isGoogleUser && password.isBlank())) {
                                    scope.launch { snackbarHostState.showSnackbar("Please fill in all fields.") }
                                    return@ThemedButton
                                }
                                isLoading = true
                                sendVerificationCode(activity, phone, authCallbacks)
                            }

                            RegisterStep.VERIFY_PHONE -> {
                                if (smsCode.length < 6 || verificationId == null) {
                                    scope.launch { snackbarHostState.showSnackbar("Please enter the 6-digit code.") }
                                    return@ThemedButton
                                }
                                isLoading = true
                                val credential = PhoneAuthProvider.getCredential(verificationId!!, smsCode)

                                if (isGoogleUser) {
                                    handleGoogleUserRegistration(credential, name, phone, telco, navController,
                                        onError = { error ->
                                            isLoading = false
                                            scope.launch { snackbarHostState.showSnackbar(error) }
                                        }
                                    )
                                } else {
                                    handleEmailUserRegistration(email, password, credential, name, phone, telco, navController,
                                        onError = { error ->
                                            isLoading = false
                                            step = RegisterStep.ENTER_DETAILS
                                            scope.launch { snackbarHostState.showSnackbar(error) }
                                        }
                                    )
                                }
                            }
                        }
                    },
                    text = when (step) {
                        RegisterStep.ENTER_DETAILS -> "Send Verification Code"
                        RegisterStep.VERIFY_PHONE -> "Verify & Continue"
                    },
                    isLoading = isLoading // 👈 NEW
                )

                if (step == RegisterStep.VERIFY_PHONE && !isLoading) {
                    TextButton(onClick = { step = RegisterStep.ENTER_DETAILS }) {
                        Text("Change phone number", color = LightBlue)
                    }
                }
            }
        }
    }
}
// --- 👆 END OF UPDATE ---


// ... (ConsentScreen composable remains unchanged) ...
// ... (Helper functions: sendVerificationCode, handleGoogleUserRegistration, handleEmailUserRegistration, TelcoDropdown... all remain unchanged) ...
// (Make sure they are still at the bottom of your AuthScreen.kt file)
@Composable
fun ConsentScreen(navController: NavController) {
    var accepted by remember { mutableStateOf(false) }
    AppBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Final Step", style = MaterialTheme.typography.headlineLarge.copy(color = Color.White))
            Spacer(Modifier.height(16.dp))
            Text("Rules & Root Access", style = MaterialTheme.typography.titleMedium.copy(color = LightBlue))
            Spacer(Modifier.height(24.dp))
            Text(
                text = "1. This application is for demonstration and research purposes only.\n\n" +
                        "2. The app requires root access to perform comprehensive network scanning and activate countermeasures.\n\n" +
                        "3. You grant CellShield permission to use root access for its intended security functions.",
                style = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                textAlign = TextAlign.Start
            )
            Spacer(Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = accepted,
                    onCheckedChange = { accepted = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = Cyan,
                        uncheckedColor = LightBlue,
                        checkmarkColor = DarkBlue
                    )
                )
                Spacer(Modifier.width(8.dp))
                Text("I understand and agree to the terms.", color = Color.White)
            }
            Spacer(Modifier.height(24.dp))
            ThemedButton(
                onClick = {
                    AuthManager.acceptConsent { success ->
                        if (success) {
                            navController.navigate("dashboard") {
                                popUpTo("welcome") { inclusive = true }
                            }
                        }
                    }
                },
                text = "Finish Registration",
                enabled = accepted
            )
        }
    }
}


private fun sendVerificationCode(
    activity: Activity,
    phone: String,
    callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks
) {
    try {
        val options = PhoneAuthOptions.newBuilder(AuthManager.getAuthInstance())
            .setPhoneNumber(phone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    } catch (e: Exception) {
        callbacks.onVerificationFailed(FirebaseException(e.message ?: "Invalid phone number format."))
    }
}

private fun handleGoogleUserRegistration(
    credential: PhoneAuthCredential,
    name: String, phone: String, telco: String,
    navController: NavController,
    onError: (String) -> Unit
) {
    AuthManager.linkPhoneCredential(credential) { success, error ->
        if (success) {
            AuthManager.updateUserAccount(name, phone, telco) { updateSuccess, updateError ->
                if (updateSuccess) {
                    navController.navigate("consent")
                } else {
                    onError(updateError ?: "Failed to save profile.")
                }
            }
        } else {
            onError(error ?: "Failed to link phone. Is it in use by another account?")
        }
    }
}

private fun handleEmailUserRegistration(
    email: String, password: String,
    credential: PhoneAuthCredential,
    name: String, phone: String, telco: String,
    navController: NavController,
    onError: (String) -> Unit
) {
    AuthManager.registerWithEmail(email, password) { regSuccess, regError ->
        if (regSuccess) {
            AuthManager.linkPhoneCredential(credential) { linkSuccess, linkError ->
                if (linkSuccess) {
                    AuthManager.updateUserAccount(name, phone, telco) { updateSuccess, updateError ->
                        if (updateSuccess) {
                            AuthManager.logout { }
                            navController.navigate("login") {
                                popUpTo("register") { inclusive = true }
                            }
                        } else {
                            onError(updateError ?: "Failed to save profile.")
                        }
                    }
                } else {
                    onError(linkError ?: "Failed to link phone. Is it in use?")
                }
            }
        } else {
            onError(regError ?: "Failed to create account. Is the email already in use?")
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
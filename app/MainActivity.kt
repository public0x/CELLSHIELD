// In app/src/main/java/com/cellshield/app/MainActivity.kt

package com.cellshield.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cellshield.app.ui.ConsentScreen
import com.cellshield.app.ui.DashboardScreen
import com.cellshield.app.ui.LoginScreen
import com.cellshield.app.ui.RegisterScreen
import com.cellshield.app.ui.SplashScreen
import com.cellshield.app.ui.WelcomeScreen
import com.cellshield.app.ui.theme.CellShieldTheme



class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CellShieldTheme {
                AppNavigator()
            }
        }
    }
}

@Composable
fun AppNavigator() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Screen.Splash.route) {
        composable(Screen.Splash.route) { SplashScreen(navController) }
        composable(Screen.Welcome.route) { WelcomeScreen(navController) }
        composable(Screen.Login.route) { LoginScreen(navController) }
        composable(Screen.Register.route) { RegisterScreen(navController) }
        composable(Screen.Consent.route) { ConsentScreen(navController) }
        composable(Screen.Dashboard.route) { DashboardScreen(navController) }
    }
}

package com.cellshield.app

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Welcome : Screen("welcome")
    object Login : Screen("login")
    object Register : Screen("register")
    object Consent : Screen("consent")
    object Dashboard : Screen("dashboard")
}

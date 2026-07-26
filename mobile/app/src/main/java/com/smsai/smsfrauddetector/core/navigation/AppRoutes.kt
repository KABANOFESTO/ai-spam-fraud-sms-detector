package com.smsai.smsfrauddetector.core.navigation

sealed class AppRoute(val route: String) {
    data object Splash : AppRoute("splash")
    data object Login : AppRoute("login")
    data object Register : AppRoute("register")
    data object ResetPassword : AppRoute("reset-password/{uid}/{token}")
    data object Home : AppRoute("home")
    data object Analyze : AppRoute("analyze")
    data object History : AppRoute("history")
    data object Report : AppRoute("report")
    data object Profile : AppRoute("profile")
    data object Settings : AppRoute("settings")
    data object Dashboard : AppRoute("dashboard")
    data object AdminUsers : AppRoute("admin-users")

    companion object {
        fun resetPassword(uid: String, token: String): String = "reset-password/$uid/$token"
    }
}

object AppLaunchDestination {
    const val EXTRA_ROUTE = "launch_route"
}

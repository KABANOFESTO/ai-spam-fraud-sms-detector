package com.smsai.smsfrauddetector.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.smsai.smsfrauddetector.core.common.SimpleViewModelFactory
import com.smsai.smsfrauddetector.data.repository.AppRepository
import com.smsai.smsfrauddetector.features.analysis.AnalysisScreen
import com.smsai.smsfrauddetector.features.auth.AuthMode
import com.smsai.smsfrauddetector.features.auth.AuthScreen
import com.smsai.smsfrauddetector.features.dashboard.DashboardScreen
import com.smsai.smsfrauddetector.features.history.HistoryScreen
import com.smsai.smsfrauddetector.features.home.HomeScreen
import com.smsai.smsfrauddetector.features.profile.ProfileScreen
import com.smsai.smsfrauddetector.features.report.ReportScreen
import com.smsai.smsfrauddetector.features.settings.SettingsScreen
import com.smsai.smsfrauddetector.features.splash.SplashScreen
import com.smsai.smsfrauddetector.features.splash.SplashViewModel

@Composable
fun AppNavGraph(
    repository: AppRepository,
    launchRoute: String? = null,
    onLaunchRouteConsumed: (() -> Unit)? = null,
    navController: NavHostController = rememberNavController(),
) {
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    LaunchedEffect(launchRoute, currentRoute) {
        val targetRoute = launchRoute ?: return@LaunchedEffect
        if (currentRoute == AppRoute.Splash.route) return@LaunchedEffect
        if (!repository.isAuthenticated()) return@LaunchedEffect
        if (currentRoute == targetRoute) {
            onLaunchRouteConsumed?.invoke()
            return@LaunchedEffect
        }
        navController.navigate(targetRoute) {
            launchSingleTop = true
            popUpTo(AppRoute.Home.route) { inclusive = false }
        }
        onLaunchRouteConsumed?.invoke()
    }

    NavHost(
        navController = navController,
        startDestination = AppRoute.Splash.route,
    ) {
        composable(AppRoute.Splash.route) {
            val splashViewModel: SplashViewModel = viewModel(
                factory = SimpleViewModelFactory { SplashViewModel(repository) },
            )
            val state = splashViewModel.state.collectAsStateWithLifecycle().value
            LaunchedEffect(state.loading, state.authenticated) {
                if (!state.loading) {
                    val targetRoute = launchRoute?.takeIf { state.authenticated }
                    navController.navigate(
                        targetRoute ?: if (state.authenticated) AppRoute.Home.route else AppRoute.Login.route,
                    ) {
                        popUpTo(AppRoute.Splash.route) { inclusive = true }
                    }
                    if (targetRoute != null) {
                        onLaunchRouteConsumed?.invoke()
                    }
                }
            }
            SplashScreen(
                loading = state.loading,
                message = state.error ?: "Preparing secure SMS analysis...",
                onRetry = if (state.error != null) {
                    { splashViewModel.checkSession() }
                } else {
                    null
                },
            )
        }
        composable(AppRoute.Login.route) {
            AuthScreen(
                mode = AuthMode.Login,
                repository = repository,
                onAuthenticated = {
                    navController.navigate(launchRoute ?: AppRoute.Home.route) {
                        popUpTo(AppRoute.Login.route) { inclusive = true }
                    }
                    if (launchRoute != null) {
                        onLaunchRouteConsumed?.invoke()
                    }
                },
                onSwitchToLogin = {},
                onSwitchToRegister = {
                    navController.navigate(AppRoute.Register.route)
                },
            )
        }
        composable(AppRoute.Register.route) {
            AuthScreen(
                mode = AuthMode.Register,
                repository = repository,
                onAuthenticated = {
                    navController.navigate(launchRoute ?: AppRoute.Home.route) {
                        popUpTo(AppRoute.Register.route) { inclusive = true }
                    }
                    if (launchRoute != null) {
                        onLaunchRouteConsumed?.invoke()
                    }
                },
                onSwitchToLogin = {
                    navController.popBackStack(AppRoute.Login.route, inclusive = false)
                },
                onSwitchToRegister = {},
            )
        }
        composable(AppRoute.Home.route) {
            HomeScreen(
                repository = repository,
                onNavigate = { route -> navController.navigate(route) },
                onLogout = {
                    navController.navigate(AppRoute.Login.route) {
                        popUpTo(AppRoute.Home.route) { inclusive = true }
                    }
                },
            )
        }
        composable(AppRoute.Analyze.route) {
            AnalysisScreen(repository = repository)
        }
        composable(AppRoute.History.route) {
            HistoryScreen(repository = repository)
        }
        composable(AppRoute.Report.route) {
            ReportScreen(repository = repository)
        }
        composable(AppRoute.Profile.route) {
            ProfileScreen(
                repository = repository,
                onLogout = {
                    navController.navigate(AppRoute.Login.route) {
                        popUpTo(AppRoute.Home.route) { inclusive = true }
                    }
                },
            )
        }
        composable(AppRoute.Settings.route) {
            SettingsScreen(repository = repository)
        }
        composable(AppRoute.Dashboard.route) {
            DashboardScreen(repository = repository)
        }
    }
}

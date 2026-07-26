package com.smsai.smsfrauddetector.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.smsai.smsfrauddetector.core.common.SimpleViewModelFactory
import com.smsai.smsfrauddetector.data.repository.AppRepository
import com.smsai.smsfrauddetector.features.analysis.AnalysisScreen
import com.smsai.smsfrauddetector.features.auth.AuthMode
import com.smsai.smsfrauddetector.features.auth.AuthScreen
import com.smsai.smsfrauddetector.features.auth.reset.ResetPasswordScreen
import com.smsai.smsfrauddetector.features.admin.users.AdminUsersScreen
import com.smsai.smsfrauddetector.features.dashboard.DashboardScreen
import com.smsai.smsfrauddetector.features.history.HistoryScreen
import com.smsai.smsfrauddetector.features.home.HomeScreen
import com.smsai.smsfrauddetector.features.profile.ProfileScreen
import com.smsai.smsfrauddetector.features.report.ReportScreen
import com.smsai.smsfrauddetector.features.settings.SettingsScreen
import com.smsai.smsfrauddetector.features.splash.SplashScreen
import com.smsai.smsfrauddetector.features.splash.SplashViewModel

private val loggedInRoutes = setOf(
    AppRoute.Home.route,
    AppRoute.Analyze.route,
    AppRoute.History.route,
    AppRoute.Report.route,
    AppRoute.Profile.route,
    AppRoute.Settings.route,
    AppRoute.Dashboard.route,
    AppRoute.AdminUsers.route,
)

private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val bottomNavItems = listOf(
    BottomNavItem(AppRoute.Home.route, "Home", Icons.Rounded.Home),
    BottomNavItem(AppRoute.Analyze.route, "Scan", Icons.Rounded.EditNote),
    BottomNavItem(AppRoute.History.route, "Log", Icons.Rounded.History),
    BottomNavItem(AppRoute.Dashboard.route, "Dash", Icons.Rounded.Dashboard),
    BottomNavItem(AppRoute.AdminUsers.route, "Users", Icons.Rounded.People),
    BottomNavItem(AppRoute.Settings.route, "Set", Icons.Rounded.Settings),
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AppNavGraph(
    repository: AppRepository,
    currentUserRole: String? = null,
    launchRoute: String? = null,
    onLaunchRouteConsumed: (() -> Unit)? = null,
    navController: NavHostController = rememberNavController(),
) {
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val isAdmin = currentUserRole.equals("Admin", ignoreCase = true)
    val showChrome = currentRoute in loggedInRoutes
    val chromeItems = if (isAdmin) bottomNavItems else bottomNavItems.filterNot { it.route == AppRoute.AdminUsers.route }

    fun isResetRoute(route: String?): Boolean {
        return route?.startsWith("reset-password/") == true || route == AppRoute.ResetPassword.route
    }

    LaunchedEffect(launchRoute, currentRoute) {
        val targetRoute = launchRoute ?: return@LaunchedEffect
        if (currentRoute == AppRoute.Splash.route) return@LaunchedEffect
        if (!repository.isAuthenticated() && !isResetRoute(targetRoute)) return@LaunchedEffect
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

    Scaffold(
        topBar = {
            if (showChrome) {
                CenterAlignedTopAppBar(
                    title = { Text(text = routeTitle(currentRoute)) },
                    navigationIcon = {
                        if (currentRoute != AppRoute.Home.route) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(imageVector = Icons.Rounded.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (showChrome) {
                NavigationBar {
                    chromeItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    launchSingleTop = true
                                    restoreState = true
                                    popUpTo(AppRoute.Home.route) { saveState = true }
                                }
                            },
                            icon = { Icon(imageVector = item.icon, contentDescription = item.label) },
                            label = {},
                            alwaysShowLabel = false,
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = AppRoute.Splash.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            composable(AppRoute.Splash.route) {
                val splashViewModel: SplashViewModel = viewModel(
                    factory = SimpleViewModelFactory { SplashViewModel(repository) },
                )
                val state = splashViewModel.state.collectAsStateWithLifecycle().value
                LaunchedEffect(state.loading, state.authenticated) {
                    if (!state.loading) {
                        val targetRoute = launchRoute?.takeIf { state.authenticated || isResetRoute(it) }
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
            composable(
                route = AppRoute.ResetPassword.route,
                arguments = listOf(
                    navArgument("uid") { type = NavType.StringType },
                    navArgument("token") { type = NavType.StringType },
                ),
            ) { entry ->
                ResetPasswordScreen(
                    repository = repository,
                    uid = entry.arguments?.getString("uid"),
                    token = entry.arguments?.getString("token"),
                    onDone = {
                        navController.navigate(AppRoute.Login.route) {
                            popUpTo(AppRoute.ResetPassword.route) { inclusive = true }
                        }
                    },
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
                DashboardScreen(
                    repository = repository,
                    onNavigate = { route -> navController.navigate(route) },
                )
            }
            composable(AppRoute.AdminUsers.route) {
                AdminUsersScreen(repository = repository)
            }
        }
    }
}

private fun routeTitle(route: String?): String {
    return when (route) {
        AppRoute.Home.route -> "Home"
        AppRoute.Analyze.route -> "Analyze SMS"
        AppRoute.History.route -> "History"
        AppRoute.Report.route -> "Report"
        AppRoute.Profile.route -> "Profile"
        AppRoute.Settings.route -> "Settings"
        AppRoute.Dashboard.route -> "Dashboard"
        AppRoute.AdminUsers.route -> "Users"
        AppRoute.ResetPassword.route -> "Reset password"
        AppRoute.Login.route -> "Login"
        AppRoute.Register.route -> "Create account"
        else -> "SMS Fraud Detector"
    }
}

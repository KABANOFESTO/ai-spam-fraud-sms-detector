package com.smsai.smsfrauddetector.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material3.TopAppBarDefaults
import com.smsai.smsfrauddetector.core.common.SimpleViewModelFactory
import com.smsai.smsfrauddetector.core.common.ApiResult
import com.smsai.smsfrauddetector.data.repository.AppRepository
import com.smsai.smsfrauddetector.data.remote.dto.UserDto
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope

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

data class AppChromeUiState(
    val user: UserDto? = null,
    val notificationCount: Int = 0,
    val profileAttention: Boolean = false,
)

class AppChromeViewModel(private val repository: AppRepository) : androidx.lifecycle.ViewModel() {
    private val _state = MutableStateFlow(AppChromeUiState())
    val state: StateFlow<AppChromeUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            val session = repository.currentSession()
            val isAdmin = session.user?.role.equals("Admin", ignoreCase = true)
            val notificationCount = try {
                if (isAdmin) {
                    val dashboard = when (val result = repository.dashboard()) {
                        is ApiResult.Success -> result.data
                        else -> null
                    }
                    val reports = when (val result = repository.reportDashboard()) {
                        is ApiResult.Success -> result.data
                        else -> emptyMap()
                    }
                    val pendingReports = reports["pending_reports"] ?: 0
                    val reviewingReports = reports["reviewing_reports"] ?: 0
                    val modelMissing = if (dashboard?.activeModel == null) 1 else 0
                    (pendingReports + reviewingReports + modelMissing).coerceAtMost(9)
                } else {
                    val stats = when (val result = repository.stats()) {
                        is ApiResult.Success -> result.data
                        else -> null
                    }
                    val health = when (val result = repository.health()) {
                        is ApiResult.Success -> result.data
                        else -> null
                    }
                    val trackingDisabled = if (!session.smsMonitoringEnabled) 1 else 0
                    val modelPending = if (health?.modelReady == false) 1 else 0
                    ((stats?.suspiciousCount ?: 0) + trackingDisabled + modelPending).coerceAtMost(9)
                }
            } catch (_: Throwable) {
                0
            }

            val profileAttention = session.user?.let { user ->
                user.profilePictureUrl.isNullOrBlank() || user.firstName.isNullOrBlank() || user.lastName.isNullOrBlank()
            } == true

            _state.value = AppChromeUiState(
                user = session.user,
                notificationCount = notificationCount,
                profileAttention = profileAttention,
            )
        }
    }
}

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
    val chromeViewModel: AppChromeViewModel = viewModel(
        factory = remember(repository) { SimpleViewModelFactory { AppChromeViewModel(repository) } },
    )
    val chromeState by chromeViewModel.state.collectAsStateWithLifecycle()

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

    LaunchedEffect(showChrome, currentRoute, chromeState.user, chromeState.notificationCount) {
        if (showChrome) {
            chromeViewModel.load()
        }
    }

    Scaffold(
        topBar = {
            if (showChrome) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                    tonalElevation = 2.dp,
                    shadowElevation = 0.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
                ) {
                    CenterAlignedTopAppBar(
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.02f),
                        ),
                        title = { Text(text = routeTitle(currentRoute)) },
                        navigationIcon = {
                            if (currentRoute != AppRoute.Home.route) {
                                IconButton(onClick = { navController.popBackStack() }) {
                                    Icon(imageVector = Icons.Rounded.ArrowBack, contentDescription = "Back")
                                }
                            }
                        },
                        actions = {
                            BadgedBox(
                                badge = {
                                    if (chromeState.notificationCount > 0) {
                                        Badge { Text(text = chromeState.notificationCount.toString()) }
                                    }
                                },
                            ) {
                                IconButton(onClick = {
                                    navController.navigate(if (isAdmin) AppRoute.Report.route else AppRoute.History.route) {
                                        launchSingleTop = true
                                        restoreState = true
                                        popUpTo(AppRoute.Home.route) { saveState = true }
                                    }
                                }) {
                                    Icon(imageVector = Icons.Rounded.Notifications, contentDescription = "System notifications")
                                }
                            }

                            IconButton(onClick = {
                                if (currentRoute != AppRoute.Profile.route) {
                                    navController.navigate(AppRoute.Profile.route) {
                                        launchSingleTop = true
                                        restoreState = true
                                        popUpTo(AppRoute.Home.route) { saveState = true }
                                    }
                                }
                            }) {
                                ProfileChip(
                                    user = chromeState.user,
                                    hasAttention = chromeState.profileAttention,
                                )
                            }
                        },
                    )
                }
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

@Composable
private fun ProfileChip(
    user: UserDto?,
    hasAttention: Boolean,
) {
    val initials = remember(user) {
        val source = listOfNotNull(user?.firstName, user?.username).firstOrNull().orEmpty()
        source.firstOrNull()?.uppercaseChar()?.toString() ?: "U"
    }

    val displayName = remember(user) {
        listOfNotNull(user?.firstName, user?.lastName)
            .joinToString(" ")
            .trim()
            .ifBlank { user?.username ?: "Profile" }
    }

    val roleLabel = remember(user) {
        when (user?.role?.lowercase()) {
            "admin" -> "Admin"
            "user" -> "User"
            else -> user?.role?.takeIf { it.isNotBlank() } ?: "Member"
        }
    }

    val liveSubtitle = remember(user, hasAttention) {
        when {
            user == null -> "Syncing account"
            hasAttention -> "Profile needs attention"
            user.isActive -> "Live sync ready"
            else -> "Access limited"
        }
    }

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier.padding(end = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = initials, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                if (hasAttention) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 1.dp, end = 1.dp)
                            .size(7.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(MaterialTheme.colorScheme.error),
                    )
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                    ) {
                        Text(
                            text = roleLabel,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    text = liveSubtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

package com.smsai.smsfrauddetector

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smsai.smsfrauddetector.core.designsystem.theme.SmsFraudTheme
import com.smsai.smsfrauddetector.core.navigation.AppLaunchDestination
import com.smsai.smsfrauddetector.core.navigation.AppNavGraph
import com.smsai.smsfrauddetector.core.navigation.AppRoute
import com.smsai.smsfrauddetector.data.local.datastore.SessionSnapshot
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private var launchRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resolveLaunchRoute(intent)
        val app = application as SmsFraudApplication
        setContent {
            val sessionState = app.container.sessionStoreRef.sessionFlow.collectAsStateWithLifecycle(
                initialValue = SessionSnapshot(),
            )
                SmsFraudTheme(darkTheme = sessionState.value.darkMode, dynamicColor = true) {
                    AppNavGraph(
                        repository = app.container.repository,
                        currentUserRole = sessionState.value.user?.role,
                        launchRoute = launchRoute,
                        onLaunchRouteConsumed = { launchRoute = null },
                    )
                }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        resolveLaunchRoute(intent)
    }

    private fun resolveLaunchRoute(intent: Intent?) {
        val extraRoute = intent?.getStringExtra(AppLaunchDestination.EXTRA_ROUTE)
        val deepLinkRoute = intent?.data?.let { uri ->
            val segments = uri.pathSegments
            when {
                uri.host == "reset-password" && segments.size >= 2 -> {
                    AppRoute.resetPassword(segments[0], segments[1])
                }
                segments.size >= 3 && segments[0] == "reset-password" -> {
                    AppRoute.resetPassword(segments[1], segments[2])
                }
                else -> null
            }
        }
        val route = extraRoute ?: deepLinkRoute
        launchRoute = route?.takeIf {
            it == AppRoute.History.route || it == AppRoute.Notifications.route || it.startsWith("reset-password/")
        }
    }
}

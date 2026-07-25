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
        val route = intent?.getStringExtra(AppLaunchDestination.EXTRA_ROUTE)
        launchRoute = route?.takeIf { it == AppRoute.History.route }
    }
}

package com.smsai.smsfrauddetector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smsai.smsfrauddetector.core.designsystem.theme.SmsFraudTheme
import com.smsai.smsfrauddetector.core.navigation.AppNavGraph
import com.smsai.smsfrauddetector.data.local.datastore.SessionSnapshot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as SmsFraudApplication
        setContent {
            val sessionState = app.container.sessionStoreRef.sessionFlow.collectAsStateWithLifecycle(
                initialValue = SessionSnapshot(),
            )
            SmsFraudTheme(darkTheme = sessionState.value.darkMode, dynamicColor = true) {
                AppNavGraph(repository = app.container.repository)
            }
        }
    }
}

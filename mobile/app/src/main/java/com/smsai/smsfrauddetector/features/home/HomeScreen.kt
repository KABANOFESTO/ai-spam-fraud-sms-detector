package com.smsai.smsfrauddetector.features.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smsai.smsfrauddetector.core.common.SimpleViewModelFactory
import com.smsai.smsfrauddetector.core.designsystem.components.ActionCard
import com.smsai.smsfrauddetector.core.designsystem.components.ErrorStateCard
import com.smsai.smsfrauddetector.core.designsystem.components.MetricCard
import com.smsai.smsfrauddetector.core.designsystem.components.PrimaryButton
import com.smsai.smsfrauddetector.core.designsystem.components.StatusBadge
import com.smsai.smsfrauddetector.core.designsystem.components.SurfaceCard
import com.smsai.smsfrauddetector.core.navigation.AppRoute
import com.smsai.smsfrauddetector.data.repository.AppRepository
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    repository: AppRepository,
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit,
) {
    val viewModel: HomeViewModel = viewModel(
        factory = remember(repository) { SimpleViewModelFactory { HomeViewModel(repository) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    Scaffold { padding ->
        Column(
            modifier = androidx.compose.ui.Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SurfaceCard(modifier = androidx.compose.ui.Modifier.fillMaxWidth()) {
                Column(modifier = androidx.compose.ui.Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatusBadge(
                        text = if (state.health?.modelReady == true) "Model ready" else "Model loading",
                        color = if (state.health?.modelReady == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                    )
                    Text(
                        text = "Welcome${state.user?.firstName?.let { ", $it" } ?: ""}",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = "Monitor, classify, and act on suspicious SMS messages in real time.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PrimaryButton(
                            text = "Analyze",
                            modifier = androidx.compose.ui.Modifier.weight(1f),
                            onClick = { onNavigate(AppRoute.Analyze.route) },
                        )
                        PrimaryButton(
                            text = "Dashboard",
                            modifier = androidx.compose.ui.Modifier.weight(1f),
                            onClick = { onNavigate(AppRoute.Dashboard.route) },
                        )
                    }
                }
            }

            state.error?.let {
                ErrorStateCard(
                    message = it,
                    retryText = "Reload home",
                    onRetry = { viewModel.load() },
                )
            }

            if (state.loading) {
                CircularProgressIndicator()
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCard(
                        title = "Analyses",
                        value = (state.stats?.totalAnalyses ?: 0).toString(),
                        subtitle = "Total processed",
                        modifier = androidx.compose.ui.Modifier.weight(1f),
                    )
                    MetricCard(
                        title = "Suspicious",
                        value = (state.stats?.suspiciousCount ?: 0).toString(),
                        subtitle = "Flagged messages",
                        modifier = androidx.compose.ui.Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCard(
                        title = "Confidence",
                        value = "${((state.stats?.averageConfidence ?: 0.0) * 100).roundToInt()}%",
                        subtitle = "Average certainty",
                        modifier = androidx.compose.ui.Modifier.weight(1f),
                    )
                    MetricCard(
                        title = "Backend",
                        value = if (state.health?.status?.equals("ok", true) == true) "Online" else "Offline",
                        subtitle = state.health?.service ?: "Service status",
                        modifier = androidx.compose.ui.Modifier.weight(1f),
                    )
                }
            }

            Text(text = "Quick actions", style = MaterialTheme.typography.titleLarge)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionCard("Analyze SMS", "Classify a message immediately", modifier = androidx.compose.ui.Modifier.fillMaxWidth(), onClick = { onNavigate(AppRoute.Analyze.route) })
                ActionCard("History", "Review previous predictions", modifier = androidx.compose.ui.Modifier.fillMaxWidth(), onClick = { onNavigate(AppRoute.History.route) })
                ActionCard("Reports", "Escalate suspicious content", modifier = androidx.compose.ui.Modifier.fillMaxWidth(), onClick = { onNavigate(AppRoute.Report.route) })
                ActionCard("Profile", "Update your account details", modifier = androidx.compose.ui.Modifier.fillMaxWidth(), onClick = { onNavigate(AppRoute.Profile.route) })
                ActionCard("Settings", "Tune backend and monitoring", modifier = androidx.compose.ui.Modifier.fillMaxWidth(), onClick = { onNavigate(AppRoute.Settings.route) })
                ActionCard("Admin dashboard", "Monitor models and datasets", modifier = androidx.compose.ui.Modifier.fillMaxWidth(), onClick = { onNavigate(AppRoute.Dashboard.route) })
            }

            SurfaceCard(modifier = androidx.compose.ui.Modifier.fillMaxWidth()) {
                Column(modifier = androidx.compose.ui.Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = "Security posture", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "Active model: ${state.activeModel?.modelName ?: "Not loaded"} ${state.activeModel?.version?.let { "v$it" } ?: ""}",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    )
                    Text(
                        text = "Message monitoring is available when permissions are granted and the backend is reachable.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    )
                    StatusBadge(
                        text = if (state.smsMonitoringEnabled) "Automatic SMS tracking enabled" else "Automatic SMS tracking off",
                        color = if (state.smsMonitoringEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                    )
                    PrimaryButton(text = "Logout", onClick = { viewModel.logout(onLogout) })
                }
            }
        }
    }
}

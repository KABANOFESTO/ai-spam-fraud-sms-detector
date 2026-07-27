package com.smsai.smsfrauddetector.features.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smsai.smsfrauddetector.core.common.SimpleViewModelFactory
import com.smsai.smsfrauddetector.core.designsystem.components.ActionCard
import com.smsai.smsfrauddetector.core.designsystem.components.BannerTone
import com.smsai.smsfrauddetector.core.designsystem.components.ErrorStateCard
import com.smsai.smsfrauddetector.core.designsystem.components.FeedbackBanner
import com.smsai.smsfrauddetector.core.designsystem.components.MetricCard
import com.smsai.smsfrauddetector.core.designsystem.components.PrimaryButton
import com.smsai.smsfrauddetector.core.designsystem.components.StatusBadge
import com.smsai.smsfrauddetector.core.designsystem.components.SurfaceCard
import com.smsai.smsfrauddetector.core.navigation.AppRoute
import com.smsai.smsfrauddetector.core.utils.toSafePercent
import com.smsai.smsfrauddetector.data.repository.AppRepository

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
    val isAdmin = state.user?.role.equals("Admin", ignoreCase = true)
    var bannerMessage by remember { mutableStateOf<String?>(null) }
    var bannerTone by remember { mutableStateOf(BannerTone.Info) }

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    LaunchedEffect(state.loading, state.error, state.health, state.activeModel) {
        val health = state.health
        if (state.loading) {
            bannerMessage = null
            return@LaunchedEffect
        }

        bannerMessage = when {
            state.error != null && health == null -> state.error
            health?.status?.equals("ok", true) == true && health.modelReady -> "Secure backend connected and AI model ready."
            health?.status?.equals("ok", true) == true -> "Backend connected. Model status is loading."
            else -> "Backend is currently offline."
        }
        bannerTone = when {
            state.error != null && health == null -> BannerTone.Error
            health?.status?.equals("ok", true) == true && health.modelReady -> BannerTone.Success
            health?.status?.equals("ok", true) == true -> BannerTone.Info
            else -> BannerTone.Error
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        StatusBadge(
                            text = if (state.health?.modelReady == true) "Model ready" else "Checking model",
                            color = if (state.health?.modelReady == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                        )
                        Text(
                            text = "Welcome${state.user?.firstName?.let { ", $it" } ?: ""}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Monitor, classify, and act on suspicious SMS messages in real time.",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PrimaryButton(
                                text = "Analyze",
                                modifier = Modifier.weight(1f),
                                onClick = { onNavigate(AppRoute.Analyze.route) },
                            )
                            PrimaryButton(
                                text = "History",
                                modifier = Modifier.weight(1f),
                                onClick = { onNavigate(AppRoute.History.route) },
                            )
                        }
                    }
                }

                if (isAdmin && state.activeModel == null) {
                    ErrorStateCard(
                        message = "No active AI model is deployed yet. Open Admin dashboard to import a labeled CSV dataset and train the first model.",
                        retryText = "Open admin tools",
                        onRetry = { onNavigate(AppRoute.Dashboard.route) },
                    )
                }

                state.error?.takeIf { state.health == null }?.let {
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
                            modifier = Modifier.weight(1f),
                        )
                        MetricCard(
                            title = "Suspicious",
                            value = (state.stats?.suspiciousCount ?: 0).toString(),
                            subtitle = "Flagged messages",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricCard(
                            title = "Confidence",
                            value = (state.stats?.averageConfidence ?: 0.0).toSafePercent(),
                            subtitle = "Average certainty",
                            modifier = Modifier.weight(1f),
                        )
                        MetricCard(
                            title = "Backend",
                            value = if (state.health?.status?.equals("ok", true) == true) "Online" else "Offline",
                            subtitle = if (state.health?.modelReady == true) {
                                "Model ready"
                            } else {
                                "Model pending"
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Text(text = "Quick actions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionCard("Analyze SMS", "Classify a message immediately", modifier = Modifier.fillMaxWidth(), onClick = { onNavigate(AppRoute.Analyze.route) })
                    ActionCard("History", "Review previous predictions", modifier = Modifier.fillMaxWidth(), onClick = { onNavigate(AppRoute.History.route) })
                    ActionCard("Dashboard", if (isAdmin) "Open admin analytics and model tools" else "View your personal dashboard", modifier = Modifier.fillMaxWidth(), onClick = { onNavigate(AppRoute.Dashboard.route) })
                    if (isAdmin) {
                        ActionCard("Users", "Create, activate, or remove accounts", modifier = Modifier.fillMaxWidth(), onClick = { onNavigate(AppRoute.AdminUsers.route) })
                    }
                    ActionCard("Reports", "Escalate suspicious content", modifier = Modifier.fillMaxWidth(), onClick = { onNavigate(AppRoute.Report.route) })
                    ActionCard("Profile", "Update your account details", modifier = Modifier.fillMaxWidth(), onClick = { onNavigate(AppRoute.Profile.route) })
                    ActionCard("Settings", "Tune backend and monitoring", modifier = Modifier.fillMaxWidth(), onClick = { onNavigate(AppRoute.Settings.route) })
                }

                SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(text = "Security posture", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
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

        AnimatedVisibility(
            visible = bannerMessage != null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                .widthIn(max = 520.dp),
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        ) {
            bannerMessage?.let { FeedbackBanner(message = it, tone = bannerTone) }
        }
    }
}

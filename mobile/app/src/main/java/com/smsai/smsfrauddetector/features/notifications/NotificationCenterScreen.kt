package com.smsai.smsfrauddetector.features.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smsai.smsfrauddetector.core.common.ApiResult
import com.smsai.smsfrauddetector.core.common.SimpleViewModelFactory
import com.smsai.smsfrauddetector.core.designsystem.components.ErrorStateCard
import com.smsai.smsfrauddetector.core.designsystem.components.PrimaryButton
import com.smsai.smsfrauddetector.core.designsystem.components.StatusBadge
import com.smsai.smsfrauddetector.core.designsystem.components.SurfaceCard
import com.smsai.smsfrauddetector.core.navigation.AppRoute
import com.smsai.smsfrauddetector.core.utils.toSafePercent
import com.smsai.smsfrauddetector.data.remote.dto.AnalysisResultDto
import com.smsai.smsfrauddetector.data.remote.dto.FraudReportDto
import com.smsai.smsfrauddetector.data.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class NotificationCenterUiState(
    val loading: Boolean = true,
    val viewerRole: String? = null,
    val summary: Map<String, Int> = emptyMap(),
    val items: List<NotificationCenterItem> = emptyList(),
    val error: String? = null,
)

data class NotificationCenterItem(
    val id: String,
    val title: String,
    val message: String,
    val subtitle: String,
    val badge: String,
    val route: String? = null,
    val alert: Boolean = false,
)

class NotificationCenterViewModel(private val repository: AppRepository) : ViewModel() {
    private val _state = MutableStateFlow(NotificationCenterUiState())
    val state: StateFlow<NotificationCenterUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)

            val session = repository.currentSession()
            val isAdmin = session.user?.role.equals("Admin", ignoreCase = true)

            val historyResult = repository.history(page = 1, pageSize = 12)
            val reportsResult = if (isAdmin) repository.reports(page = 1, pageSize = 12) else null
            val statsResult = if (!isAdmin) repository.stats() else null
            val healthResult = repository.health()
            val dashboardResult = if (isAdmin) repository.dashboard() else null

            val history = when (historyResult) {
                is ApiResult.Success -> historyResult.data.results
                else -> emptyList()
            }
            val reports = when (reportsResult) {
                is ApiResult.Success -> reportsResult.data.results
                else -> emptyList()
            }
            val stats = when (statsResult) {
                is ApiResult.Success -> statsResult.data
                else -> null
            }
            val health = when (healthResult) {
                is ApiResult.Success -> healthResult.data
                else -> null
            }
            val dashboard = when (dashboardResult) {
                is ApiResult.Success -> dashboardResult.data
                else -> null
            }

            val items = buildList {
                if (health?.modelReady == false) {
                    add(
                        NotificationCenterItem(
                            id = "health-model",
                            title = "Model not ready",
                            message = "No active AI model is deployed yet. Predictions will stay limited until the admin publishes a trained model.",
                            subtitle = "System status",
                            badge = "Attention",
                            route = if (isAdmin) AppRoute.Dashboard.route else null,
                            alert = true,
                        ),
                    )
                }

                if (!session.smsMonitoringEnabled) {
                    add(
                        NotificationCenterItem(
                            id = "tracking-off",
                            title = "SMS tracking paused",
                            message = "Automatic SMS monitoring is currently disabled on this device.",
                            subtitle = "Device setup",
                            badge = "Setup",
                            route = AppRoute.Settings.route,
                            alert = true,
                        ),
                    )
                }

                if (isAdmin && dashboard?.activeModel == null) {
                    add(
                        NotificationCenterItem(
                            id = "admin-model",
                            title = "No active model published",
                            message = "Upload a labeled dataset and start retraining to activate the production SMS classifier.",
                            subtitle = "Admin action",
                            badge = "Admin",
                            route = AppRoute.Dashboard.route,
                            alert = true,
                        ),
                    )
                }

                if (isAdmin) {
                    reports.take(6).forEach { report ->
                        add(report.toNotificationItem())
                    }
                }

                history.take(6).forEach { analysis ->
                    add(analysis.toNotificationItem(isAdmin = isAdmin))
                }
            }

            val summary = if (isAdmin) {
                mapOf(
                    "alerts" to items.size,
                    "reports" to reports.size,
                    "pending" to reports.count { it.status.equals("PENDING", ignoreCase = true) },
                    "reviewing" to reports.count { it.status.equals("REVIEWING", ignoreCase = true) },
                    "predictions" to reports.count { it.analysis != null },
                )
            } else {
                mapOf(
                    "alerts" to items.size,
                    "analyses" to history.size,
                    "suspicious" to (stats?.suspiciousCount ?: history.count { it.isSuspicious }),
                    "confidence" to ((stats?.averageConfidence ?: 0.0) * 100).toInt(),
                )
            }

            val error = listOfNotNull(
                historyResult.takeIf { it is ApiResult.Error }?.let { (it as ApiResult.Error).message },
                reportsResult.takeIf { it is ApiResult.Error }?.let { (it as ApiResult.Error).message },
                statsResult.takeIf { it is ApiResult.Error }?.let { (it as ApiResult.Error).message },
                healthResult.takeIf { it is ApiResult.Error }?.let { (it as ApiResult.Error).message },
                dashboardResult.takeIf { it is ApiResult.Error }?.let { (it as ApiResult.Error).message },
            ).firstOrNull()

            _state.value = NotificationCenterUiState(
                loading = false,
                viewerRole = session.user?.role,
                summary = summary,
                items = items,
                error = error,
            )
        }
    }
}

@Composable
fun NotificationCenterScreen(
    repository: AppRepository,
    onNavigate: (String) -> Unit,
) {
    val viewModel: NotificationCenterViewModel = viewModel(
        factory = remember(repository) { SimpleViewModelFactory { NotificationCenterViewModel(repository) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isAdmin = state.viewerRole.equals("Admin", ignoreCase = true)

    LaunchedEffect(Unit) { viewModel.load() }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatusBadge(
                            text = if (isAdmin) "Admin notification center" else "Live notifications",
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        Text(
                            text = if (isAdmin) "Live model, report, and review alerts in one place." else "Recent SMS analysis and system alerts, updated from the backend.",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            state.summary.entries.take(3).forEach { entry ->
                                StatusBadge(
                                    text = "${entry.value} ${entry.key}",
                                    color = MaterialTheme.colorScheme.primary,
                                    compact = true,
                                )
                            }
                        }
                        PrimaryButton(
                            text = if (isAdmin) "Open report center" else "Open history",
                            onClick = { onNavigate(if (isAdmin) AppRoute.Report.route else AppRoute.History.route) },
                            trailingIcon = true,
                        )
                    }
                }
            }

            state.error?.let { error ->
                item {
                    ErrorStateCard(message = error, retryText = "Reload notifications", onRetry = { viewModel.load() })
                }
            }

            item {
                Text(
                    text = "Live feed",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (state.items.isEmpty() && !state.loading) {
                item {
                    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusBadge(text = "No alerts yet", color = MaterialTheme.colorScheme.tertiary)
                            Text(text = "Your notification center is ready and will populate automatically when new SMS activity or system alerts arrive.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
                        }
                    }
                }
            } else {
                items(state.items, key = { it.id }) { item ->
                    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(text = item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(text = item.subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                StatusBadge(
                                    text = item.badge,
                                    color = if (item.alert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(
                                text = item.message,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                if (item.route != null) {
                                    PrimaryButton(
                                        text = "Open",
                                        onClick = { onNavigate(item.route) },
                                        modifier = Modifier.weight(1f),
                                        trailingIcon = true,
                                    )
                                }
                                StatusBadge(
                                    text = item.id,
                                    color = MaterialTheme.colorScheme.secondary,
                                    compact = true,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (state.loading) {
            CircularProgressIndicator(modifier = Modifier.align(androidx.compose.ui.Alignment.Center))
        }
    }
}

private fun AnalysisResultDto.toNotificationItem(isAdmin: Boolean): NotificationCenterItem {
    val title = if (isSuspicious) "Suspicious SMS detected" else "SMS analyzed successfully"
    val badge = if (isSuspicious) "Alert" else "Analysis"
    val label = if (isAdmin) "Admin view" else "User view"
    return NotificationCenterItem(
        id = "analysis-$id",
        title = title,
        message = message,
        subtitle = "$label • ${prediction.uppercase(Locale.getDefault())} • Confidence ${confidence.toSafePercent()}",
        badge = badge,
        route = AppRoute.historyItem(id),
        alert = isSuspicious,
    )
}

private fun FraudReportDto.toNotificationItem(): NotificationCenterItem {
    return NotificationCenterItem(
        id = "report-$id",
        title = if (user.isNullOrBlank()) "Report #$id submitted" else "Report #$id by $user",
        message = smsMessage,
        subtitle = "${reportStatusLabel(status)} • ${formatNotificationTimestamp(reviewedAt ?: createdAt)}",
        badge = "Report",
        route = AppRoute.reportItem(id),
        alert = status.equals("PENDING", ignoreCase = true) || status.equals("REVIEWING", ignoreCase = true),
    )
}

private fun reportStatusLabel(status: String): String {
    return when {
        status.equals("PENDING", ignoreCase = true) -> "Pending review"
        status.equals("REVIEWING", ignoreCase = true) -> "Under review"
        status.equals("REVIEWED", ignoreCase = true) -> "Reviewed"
        status.equals("RESOLVED", ignoreCase = true) -> "Resolved"
        status.equals("REJECTED", ignoreCase = true) -> "Rejected"
        else -> status.ifBlank { "Activity" }
    }
}

private fun formatNotificationTimestamp(rawValue: String?): String {
    val raw = rawValue?.trim().orEmpty()
    if (raw.isBlank()) return "Recently"

    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSX",
        "yyyy-MM-dd'T'HH:mm:ssX",
        "yyyy-MM-dd HH:mm:ss",
    )

    for (pattern in patterns) {
        val parsed = runCatching {
            SimpleDateFormat(pattern, Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(raw)
        }.getOrNull()
        if (parsed != null) {
            val now = Calendar.getInstance()
            val whenCal = Calendar.getInstance().apply { time = Date(parsed.time) }
            val timeText = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(parsed.time))
            return when {
                now.get(Calendar.YEAR) == whenCal.get(Calendar.YEAR) &&
                    now.get(Calendar.DAY_OF_YEAR) == whenCal.get(Calendar.DAY_OF_YEAR) -> "Today, $timeText"
                else -> SimpleDateFormat("MMM d, yyyy - h:mm a", Locale.getDefault()).format(Date(parsed.time))
            }
        }
    }

    return raw
}

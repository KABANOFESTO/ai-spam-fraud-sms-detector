package com.smsai.smsfrauddetector.features.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
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
import com.smsai.smsfrauddetector.core.utils.toSafePercent
import com.smsai.smsfrauddetector.data.remote.dto.AnalysisResultDto
import com.smsai.smsfrauddetector.data.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HistoryUiState(
    val loading: Boolean = true,
    val items: List<AnalysisResultDto> = emptyList(),
    val error: String? = null,
    val status: String? = null,
)

class HistoryViewModel(
    private val repository: AppRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            when (val result = repository.history()) {
                is ApiResult.Success -> _state.value = HistoryUiState(loading = false, items = result.data.results)
                is ApiResult.Error -> _state.value = HistoryUiState(loading = false, items = emptyList(), error = result.message)
                else -> _state.value = _state.value.copy(loading = false)
            }
        }
    }

    fun delete(itemId: Int) {
        viewModelScope.launch {
            when (val result = repository.deleteHistory(itemId)) {
                is ApiResult.Success -> {
                    _state.value = _state.value.copy(status = result.data)
                    load()
                }
                is ApiResult.Error -> _state.value = _state.value.copy(status = result.message)
                else -> Unit
            }
        }
    }
}

@Composable
fun HistoryScreen(repository: AppRepository, highlightItemId: Int? = null) {
    val viewModel: HistoryViewModel = viewModel(
        factory = remember(repository) { SimpleViewModelFactory { HistoryViewModel(repository) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var pendingDeleteItem by remember { mutableStateOf<AnalysisResultDto?>(null) }
    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(state.items, highlightItemId) {
        val targetId = highlightItemId ?: return@LaunchedEffect
        val index = state.items.indexOfFirst { it.id == targetId }
        if (index >= 0) listState.scrollToItem(index)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusBadge(text = "Live analysis history", color = MaterialTheme.colorScheme.secondary)
                Text(text = "History", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    text = "Saved SMS predictions appear here automatically, with human-friendly dates and quick delete controls.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    StatusBadge(text = "Stored records", color = MaterialTheme.colorScheme.primary)
                    StatusBadge(text = "Backend synced", color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        PrimaryButton(text = "Refresh", onClick = { viewModel.load() })

        if (state.loading) {
            CircularProgressIndicator()
        }

        state.error?.let {
            ErrorStateCard(message = it, retryText = "Reload history", onRetry = { viewModel.load() })
        }

        if (state.items.isEmpty() && !state.loading) {
            SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusBadge(text = "No history yet", color = MaterialTheme.colorScheme.tertiary)
                    Text(text = "No analyses have been recorded yet.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(text = "When SMS messages are analyzed, the saved results will appear here automatically.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
                }
            }
        } else {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.items) { item ->
                    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                StatusBadge(
                                    text = item.prediction.uppercase(),
                                    color = if (item.isSuspicious) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                )
                                Text(text = "Confidence ${item.confidence.toSafePercent()}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            }
                            if (highlightItemId == item.id) {
                                StatusBadge(text = "Opened from notification", color = MaterialTheme.colorScheme.secondary, compact = true)
                            }
                            Text(text = item.message, maxLines = 3)
                            val display = formatHumanDateTime(item.analyzedAt)
                            val subtitle = formatHumanTimeSubtitle(item.analyzedAt)
                            Text(
                                text = display ?: "Recently analyzed",
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text(
                                text = "Delete removes this record from your local history view.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                            TextButton(onClick = { pendingDeleteItem = item }) {
                                Text(
                                    text = "Delete history",
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDeleteItem?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDeleteItem = null },
            title = { Text(text = "Delete history item?") },
            text = {
                Text(text = "This will permanently remove this analysis record and cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeleteItem = null
                        viewModel.delete(item.id)
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteItem = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

private fun formatHumanDateTime(rawValue: String?): String? {
    val raw = rawValue?.trim().orEmpty()
    if (raw.isBlank()) return null

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
            return SimpleDateFormat("MMM d, yyyy - h:mm a", Locale.getDefault()).format(Date(parsed.time))
        }
    }

    return raw
}

private fun formatHumanTimeSubtitle(rawValue: String?): String? {
    val parsed = parseDate(rawValue) ?: return null
    val now = java.util.Calendar.getInstance()
    val whenCal = java.util.Calendar.getInstance().apply { time = parsed }
    val timeText = SimpleDateFormat("h:mm a", Locale.getDefault()).format(parsed)

    return when {
        isSameDay(now, whenCal) -> "Today, $timeText"
        isYesterday(now, whenCal) -> "Yesterday, $timeText"
        else -> timeText
    }
}

private fun parseDate(rawValue: String?): Date? {
    val raw = rawValue?.trim().orEmpty()
    if (raw.isBlank()) return null

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
        if (parsed != null) return parsed
    }

    return null
}

private fun isSameDay(first: java.util.Calendar, second: java.util.Calendar): Boolean {
    return first.get(java.util.Calendar.YEAR) == second.get(java.util.Calendar.YEAR) &&
        first.get(java.util.Calendar.DAY_OF_YEAR) == second.get(java.util.Calendar.DAY_OF_YEAR)
}

private fun isYesterday(today: java.util.Calendar, candidate: java.util.Calendar): Boolean {
    val yesterday = (today.clone() as java.util.Calendar).apply {
        add(java.util.Calendar.DAY_OF_YEAR, -1)
    }
    return isSameDay(yesterday, candidate)
}

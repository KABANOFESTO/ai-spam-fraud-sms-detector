package com.smsai.smsfrauddetector.features.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.smsai.smsfrauddetector.data.remote.dto.AnalysisResultDto
import com.smsai.smsfrauddetector.data.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class HistoryUiState(
    val loading: Boolean = true,
    val items: List<AnalysisResultDto> = emptyList(),
    val error: String? = null,
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
}

@Composable
fun HistoryScreen(repository: AppRepository) {
    val viewModel: HistoryViewModel = viewModel(
        factory = remember(repository) { SimpleViewModelFactory { HistoryViewModel(repository) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(text = "History", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(text = "Recent predictions and classifications")
            }
            PrimaryButton(text = "Refresh", onClick = { viewModel.load() })
        }

        if (state.loading) {
            CircularProgressIndicator()
        }

        state.error?.let {
            ErrorStateCard(message = it, retryText = "Reload history", onRetry = { viewModel.load() })
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.items) { item ->
                SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusBadge(
                            text = item.prediction.uppercase(),
                            color = if (item.isSuspicious) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                        Text(text = item.message, maxLines = 3)
                        Text(text = "Confidence ${(item.confidence * 100).roundToInt()}%")
                        Text(text = item.analyzedAt ?: "Recently analyzed", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

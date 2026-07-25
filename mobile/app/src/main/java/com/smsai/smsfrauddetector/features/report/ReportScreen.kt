package com.smsai.smsfrauddetector.features.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smsai.smsfrauddetector.core.common.ApiResult
import com.smsai.smsfrauddetector.core.common.SimpleViewModelFactory
import com.smsai.smsfrauddetector.core.designsystem.components.BannerTone
import com.smsai.smsfrauddetector.core.designsystem.components.ErrorStateCard
import com.smsai.smsfrauddetector.core.designsystem.components.FeedbackBanner
import com.smsai.smsfrauddetector.core.designsystem.components.PrimaryButton
import com.smsai.smsfrauddetector.core.designsystem.components.StatusBadge
import com.smsai.smsfrauddetector.core.designsystem.components.SurfaceCard
import com.smsai.smsfrauddetector.data.remote.dto.FraudReportDto
import com.smsai.smsfrauddetector.data.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReportUiState(
    val loading: Boolean = true,
    val items: List<FraudReportDto> = emptyList(),
    val error: String? = null,
    val submitStatus: String? = null,
)

class ReportViewModel(
    private val repository: AppRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ReportUiState())
    val state: StateFlow<ReportUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            when (val result = repository.reports()) {
                is ApiResult.Success -> _state.value = ReportUiState(loading = false, items = result.data.results)
                is ApiResult.Error -> _state.value = ReportUiState(loading = false, error = result.message)
                else -> Unit
            }
        }
    }

    fun submit(message: String, notes: String, analysisId: Int? = null) {
        viewModelScope.launch {
            when (val result = repository.createReport(message = message, notes = notes, analysisId = analysisId)) {
                is ApiResult.Success -> {
                    _state.value = _state.value.copy(submitStatus = "Report created with status ${result.data.status}.")
                    load()
                }
                is ApiResult.Error -> _state.value = _state.value.copy(submitStatus = result.message)
                else -> Unit
            }
        }
    }
}

@Composable
fun ReportScreen(repository: AppRepository) {
    val viewModel: ReportViewModel = viewModel(
        factory = remember(repository) { SimpleViewModelFactory { ReportViewModel(repository) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var message by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var bannerMessage by remember { mutableStateOf<String?>(null) }
    var bannerTone by remember { mutableStateOf(BannerTone.Info) }

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(state.submitStatus) {
        val status = state.submitStatus ?: return@LaunchedEffect
        bannerMessage = status
        bannerTone = if (status.contains("status", ignoreCase = true) || status.contains("created", ignoreCase = true)) {
            BannerTone.Success
        } else {
            BannerTone.Error
        }
        kotlinx.coroutines.delay(2400)
        bannerMessage = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(text = "Reports", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(text = "Escalate suspicious messages and track admin review.")

            SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = message, onValueChange = { message = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Message") })
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Notes") })
                    PrimaryButton(
                        text = "Submit report",
                        onClick = { viewModel.submit(message, notes) },
                    )
                }
            }

            if (state.loading) {
                CircularProgressIndicator()
            }
            state.error?.let { ErrorStateCard(message = it, retryText = "Reload reports", onRetry = { viewModel.load() }) }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.items) { report ->
                    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusBadge(text = report.status.uppercase(), color = MaterialTheme.colorScheme.primary)
                            Text(text = report.smsMessage)
                            Text(text = report.notes.ifBlank { "No notes provided." })
                            Text(text = report.reviewedAt ?: report.createdAt ?: "Pending review", style = MaterialTheme.typography.labelMedium)
                        }
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

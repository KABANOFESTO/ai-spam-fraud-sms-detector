package com.smsai.smsfrauddetector.features.analysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.smsai.smsfrauddetector.data.remote.dto.AnalysisResultDto
import com.smsai.smsfrauddetector.data.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class AnalysisUiState(
    val loading: Boolean = false,
    val analyzing: Boolean = false,
    val result: AnalysisResultDto? = null,
    val reportStatus: String? = null,
    val error: String? = null,
    val lastMessage: String = "",
)

class AnalysisViewModel(
    private val repository: AppRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AnalysisUiState())
    val state: StateFlow<AnalysisUiState> = _state.asStateFlow()

    fun analyze(message: String) {
        if (message.isBlank()) {
            _state.value = _state.value.copy(error = "Enter an SMS message to analyze.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(analyzing = true, error = null, reportStatus = null, lastMessage = message)
            when (val result = repository.analyze(message)) {
                is ApiResult.Success -> _state.value = AnalysisUiState(result = result.data, lastMessage = message)
                is ApiResult.Error -> _state.value = _state.value.copy(analyzing = false, error = result.message)
                else -> _state.value = _state.value.copy(analyzing = false)
            }
        }
    }

    fun clear() {
        _state.value = AnalysisUiState()
    }

    fun retryLastAnalysis() {
        val message = _state.value.lastMessage
        if (message.isNotBlank()) {
            analyze(message)
        }
    }

    fun createReport(message: String, analysisId: Int?) {
        viewModelScope.launch {
            when (val result = repository.createReport(message = message, analysisId = analysisId)) {
                is ApiResult.Success -> _state.value = _state.value.copy(reportStatus = "Report submitted as ${result.data.status}.")
                is ApiResult.Error -> _state.value = _state.value.copy(reportStatus = result.message)
                else -> Unit
            }
        }
    }
}

@Composable
fun AnalysisScreen(
    repository: AppRepository,
) {
    val viewModel: AnalysisViewModel = viewModel(
        factory = remember(repository) { SimpleViewModelFactory { AnalysisViewModel(repository) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var message by rememberSaveable { mutableStateOf("") }
    var bannerMessage by remember { mutableStateOf<String?>(null) }
    var bannerTone by remember { mutableStateOf(BannerTone.Info) }

    LaunchedEffect(state.reportStatus) {
        val status = state.reportStatus ?: return@LaunchedEffect
        bannerMessage = status
        bannerTone = if (status.contains("created", ignoreCase = true) || status.contains("submitted", ignoreCase = true)) {
            BannerTone.Success
        } else {
            BannerTone.Error
        }
        kotlinx.coroutines.delay(2400)
        bannerMessage = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatusBadge(
                        text = "Live SMS analysis",
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Text(text = "Analyze SMS", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Paste or type a suspicious SMS and let the model classify it in real time.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        StatusBadge(text = "Dynamic prediction", color = MaterialTheme.colorScheme.primary)
                        StatusBadge(text = "Saved analysis", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(text = "Message input", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(text = "The model response updates from the backend and can be saved as a report when the message is suspicious.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        label = { Text("SMS message") },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PrimaryButton(
                            text = "Analyze",
                            modifier = Modifier.weight(1f),
                            enabled = !state.analyzing,
                            onClick = { viewModel.analyze(message) },
                            trailingIcon = true,
                        )
                        PrimaryButton(
                            text = "Clear",
                            modifier = Modifier.weight(1f),
                            enabled = !state.analyzing,
                            onClick = { message = ""; viewModel.clear() },
                        )
                    }
                    if (state.analyzing) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.width(24.dp).height(24.dp), strokeWidth = 2.dp)
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(12.dp))
                            Text("Analyzing message...")
                        }
                    }
                    state.error?.let {
                        ErrorStateCard(
                            message = it,
                            retryText = "Retry analysis",
                            onRetry = { viewModel.retryLastAnalysis() },
                        )
                    }
                }
            }

            state.result?.let { result ->
                SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatusBadge(
                            text = result.prediction.uppercase(),
                            color = if (result.isSuspicious) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                        Text(text = "Confidence ${(result.confidence * 100).roundToInt()}%", style = MaterialTheme.typography.headlineMedium)
                        Text(text = result.explanation ?: "Model analysis completed successfully.")
                        Text(text = "Matched signals", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (result.matchedSignals.isEmpty()) {
                                StatusBadge(text = "No specific signals matched", color = MaterialTheme.colorScheme.tertiary)
                            } else {
                                result.matchedSignals.take(4).forEach { signal ->
                                    FilterChip(selected = true, onClick = {}, label = { Text(signal) })
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PrimaryButton(
                                text = "Save report",
                                modifier = Modifier.weight(1f),
                                enabled = result.isSuspicious,
                                onClick = { viewModel.createReport(result.message, result.id) },
                            )
                            PrimaryButton(
                                text = "Reset",
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.clear(); message = "" },
                            )
                        }
                    }
                }
            }

            if (state.result == null && !state.analyzing) {
                SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = "What happens next", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(text = "Once you analyze a message, the confidence score, matched signals, and save-report action appear here.")
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

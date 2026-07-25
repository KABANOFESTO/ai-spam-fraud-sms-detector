package com.smsai.smsfrauddetector.features.dashboard

import android.content.Context
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Upload
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.smsai.smsfrauddetector.core.designsystem.components.MetricCard
import com.smsai.smsfrauddetector.core.designsystem.components.PrimaryButton
import com.smsai.smsfrauddetector.core.designsystem.components.StatusBadge
import com.smsai.smsfrauddetector.core.designsystem.components.SurfaceCard
import com.smsai.smsfrauddetector.data.remote.dto.DatasetDto
import com.smsai.smsfrauddetector.data.remote.dto.DashboardResponseDto
import com.smsai.smsfrauddetector.data.remote.dto.EvaluationReportDto
import com.smsai.smsfrauddetector.data.remote.dto.ModelDto
import com.smsai.smsfrauddetector.data.repository.AppRepository
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DashboardUiState(
    val loading: Boolean = true,
    val dashboard: DashboardResponseDto? = null,
    val evaluation: EvaluationReportDto? = null,
    val datasets: List<DatasetDto> = emptyList(),
    val models: List<ModelDto> = emptyList(),
    val error: String? = null,
    val status: String? = null,
)

class DashboardViewModel(private val repository: AppRepository) : ViewModel() {
    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val dashboardResult = repository.dashboard()
            val evaluationResult = repository.evaluation()
            val datasetsResult = repository.datasets()
            val modelsResult = repository.activeModels()

            val dashboard = when (dashboardResult) {
                is ApiResult.Success -> dashboardResult.data
                else -> null
            }
            val evaluation = when (evaluationResult) {
                is ApiResult.Success -> evaluationResult.data
                else -> null
            }
            val datasets = when (datasetsResult) {
                is ApiResult.Success -> datasetsResult.data.results
                else -> emptyList()
            }
            val models = when (modelsResult) {
                is ApiResult.Success -> modelsResult.data
                else -> emptyList()
            }
            val error = listOfNotNull(
                dashboardResult.takeIf { it is ApiResult.Error }?.let { (it as ApiResult.Error).message },
                evaluationResult.takeIf { it is ApiResult.Error }?.let { (it as ApiResult.Error).message },
                datasetsResult.takeIf { it is ApiResult.Error }?.let { (it as ApiResult.Error).message },
                modelsResult.takeIf { it is ApiResult.Error }?.let { (it as ApiResult.Error).message },
            ).firstOrNull()
            _state.value = DashboardUiState(
                loading = false,
                dashboard = dashboard,
                evaluation = evaluation,
                datasets = datasets,
                models = models,
                error = error,
            )
        }
    }

    fun retrain(datasetId: Int?, dataPath: String?, force: Boolean) {
        viewModelScope.launch {
            when (val result = repository.retrain(datasetId = datasetId, dataPath = dataPath, force = force)) {
                is ApiResult.Success -> _state.value = _state.value.copy(status = "Retraining started.")
                is ApiResult.Error -> _state.value = _state.value.copy(status = result.message)
                else -> Unit
            }
        }
    }

    fun importDataset(filePart: MultipartBody.Part, notes: String) {
        viewModelScope.launch {
            when (val result = repository.importDataset(filePart, notes)) {
                is ApiResult.Success -> {
                    _state.value = _state.value.copy(status = "Dataset imported: ${result.data.originalFilename}.")
                    load()
                }
                is ApiResult.Error -> _state.value = _state.value.copy(status = result.message)
                else -> Unit
            }
        }
    }
}

@Composable
fun DashboardScreen(repository: AppRepository) {
    val viewModel: DashboardViewModel = viewModel(
        factory = remember(repository) { SimpleViewModelFactory { DashboardViewModel(repository) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var datasetId by rememberSaveable { mutableStateOf("") }
    var dataPath by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var force by rememberSaveable { mutableStateOf(false) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var bannerMessage by remember { mutableStateOf<String?>(null) }
    var bannerTone by remember { mutableStateOf(BannerTone.Info) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        selectedUri = uri
    }

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(state.status) {
        val status = state.status ?: return@LaunchedEffect
        bannerMessage = status
        bannerTone = when {
            status.contains("failed", ignoreCase = true) ||
                status.contains("error", ignoreCase = true) ||
                status.contains("unable", ignoreCase = true) -> BannerTone.Error
            else -> BannerTone.Success
        }
        kotlinx.coroutines.delay(2500)
        bannerMessage = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(text = "Admin dashboard", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(text = "Track models, datasets, evaluation results, and retraining actions.")
            if (state.loading) CircularProgressIndicator()
            state.error?.let { ErrorStateCard(message = it, retryText = "Reload dashboard", onRetry = { viewModel.load() }) }

            state.dashboard?.let { dashboard ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricCard("Total", dashboard.totals.totalAnalyses.toString(), "Analyses", modifier = Modifier.weight(1f))
                    MetricCard("Suspicious", dashboard.totals.suspiciousCount.toString(), "Flagged", modifier = Modifier.weight(1f))
                }
            }
            state.evaluation?.let { evaluation ->
                SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusBadge(text = "Evaluation", color = MaterialTheme.colorScheme.secondary)
                        Text(text = "${evaluation.modelName} v${evaluation.version}", fontWeight = FontWeight.Bold)
                        Text(text = "Accuracy ${(evaluation.accuracy * 100).toInt()}% | F1 ${(evaluation.f1Score * 100).toInt()}%")
                        Text(text = "Train ${evaluation.trainingSamples} | Test ${evaluation.testSamples}")
                    }
                }
            }

            SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = "Import dataset", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Notes") })
                    PrimaryButton(text = "Pick file", onClick = { picker.launch(arrayOf("text/*", "application/*")) })
                    PrimaryButton(
                        text = "Upload dataset",
                        onClick = {
                            val uri = selectedUri ?: return@PrimaryButton
                            val part = uriToMultipart(context, uri)
                            viewModel.importDataset(part, notes)
                        },
                        enabled = selectedUri != null,
                    )
                }
            }

            SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = "Retrain model", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(value = datasetId, onValueChange = { datasetId = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Dataset ID (optional)") })
                    OutlinedTextField(value = dataPath, onValueChange = { dataPath = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Data path (optional)") })
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(text = "Force retrain", modifier = Modifier.weight(1f))
                        androidx.compose.material3.Switch(checked = force, onCheckedChange = { force = it })
                    }
                    PrimaryButton(text = "Start retraining", onClick = {
                        viewModel.retrain(
                            datasetId = datasetId.toIntOrNull(),
                            dataPath = dataPath.ifBlank { null },
                            force = force,
                        )
                    })
                }
            }

            Text(text = "Datasets", style = MaterialTheme.typography.titleLarge)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.datasets) { dataset ->
                    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(dataset.originalFilename, fontWeight = FontWeight.Bold)
                            Text("Rows: ${dataset.rowCount}")
                            Text(dataset.notes.ifBlank { "No notes" })
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
            bannerMessage?.let {
                FeedbackBanner(message = it, tone = bannerTone)
            }
        }
    }
}

private fun uriToMultipart(context: Context, uri: Uri): MultipartBody.Part {
    val file = File(context.cacheDir, "dataset_${System.currentTimeMillis()}.csv")
    context.contentResolver.openInputStream(uri)?.use { input ->
        file.outputStream().use { output -> input.copyTo(output) }
    } ?: error("Unable to read selected dataset file.")
    return MultipartBody.Part.createFormData(
        "file",
        file.name,
        file.asRequestBody("text/csv".toMediaType()),
    )
}

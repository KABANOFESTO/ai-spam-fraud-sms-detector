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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import com.smsai.smsfrauddetector.core.designsystem.components.ActionCard
import com.smsai.smsfrauddetector.core.designsystem.components.ErrorStateCard
import com.smsai.smsfrauddetector.core.designsystem.components.FeedbackBanner
import com.smsai.smsfrauddetector.core.designsystem.components.MetricCard
import com.smsai.smsfrauddetector.core.designsystem.components.PrimaryButton
import com.smsai.smsfrauddetector.core.designsystem.components.StatusBadge
import com.smsai.smsfrauddetector.core.designsystem.components.SurfaceCard
import com.smsai.smsfrauddetector.core.navigation.AppRoute
import com.smsai.smsfrauddetector.data.remote.dto.DatasetDto
import com.smsai.smsfrauddetector.data.remote.dto.DashboardResponseDto
import com.smsai.smsfrauddetector.data.remote.dto.EvaluationReportDto
import com.smsai.smsfrauddetector.data.remote.dto.ModelDto
import com.smsai.smsfrauddetector.data.remote.dto.StatsDto
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
    val viewerRole: String? = null,
    val stats: StatsDto? = null,
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
            val session = repository.currentSession()
            val isAdmin = session.user?.role.equals("Admin", ignoreCase = true)
            val statsResult = repository.stats()
            val dashboardResult = if (isAdmin) repository.dashboard() else null
            val evaluationResult = if (isAdmin) repository.evaluation() else null
            val datasetsResult = if (isAdmin) repository.datasets() else null
            val modelsResult = repository.activeModels()

            val dashboard = when (dashboardResult) {
                is ApiResult.Success -> dashboardResult.data
                else -> null
            }
            val stats = when (statsResult) {
                is ApiResult.Success -> statsResult.data
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
                statsResult.takeIf { it is ApiResult.Error }?.let { (it as ApiResult.Error).message },
                evaluationResult.takeIf { it is ApiResult.Error && it.code != 404 }?.let { (it as ApiResult.Error).message },
                datasetsResult.takeIf { it is ApiResult.Error }?.let { (it as ApiResult.Error).message },
                modelsResult.takeIf { it is ApiResult.Error }?.let { (it as ApiResult.Error).message },
            ).firstOrNull()
            _state.value = DashboardUiState(
                loading = false,
                viewerRole = session.user?.role,
                stats = stats,
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
fun DashboardScreen(
    repository: AppRepository,
    onNavigate: (String) -> Unit = {},
) {
    val viewModel: DashboardViewModel = viewModel(
        factory = remember(repository) { SimpleViewModelFactory { DashboardViewModel(repository) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isAdmin = state.viewerRole.equals("Admin", ignoreCase = true)
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
            Text(
                text = if (isAdmin) "Admin dashboard" else "User dashboard",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (isAdmin) {
                    "Track models, datasets, evaluation results, and retraining actions."
                } else {
                    "Review your SMS protection status, recent usage, and model readiness."
                },
            )

            if (isAdmin) {
                SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusBadge(text = "Admin workflow", color = MaterialTheme.colorScheme.primary)
                        Text(text = "1. Import a labeled CSV dataset")
                        Text(text = "2. Start retraining with that dataset")
                        Text(text = "3. Review evaluation metrics and activate the model")
                    }
                }
                ActionCard(
                    title = "Manage users",
                    subtitle = "Create, activate, deactivate, and delete accounts",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onNavigate(AppRoute.AdminUsers.route) },
                )
            } else {
                SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusBadge(text = "Personal view", color = MaterialTheme.colorScheme.secondary)
                        Text(text = "Your dashboard is ready to use for live SMS monitoring and analysis.")
                        Text(text = "Open Analyze or History to review suspicious messages and saved results.")
                    }
                }
            }

            if (state.loading) CircularProgressIndicator()
            state.error?.let { ErrorStateCard(message = it, retryText = "Reload dashboard", onRetry = { viewModel.load() }) }

            if (isAdmin) {
                state.dashboard?.let { dashboard ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        MetricCard("Total", dashboard.totals.totalAnalyses.toString(), "Analyses", modifier = Modifier.weight(1f))
                        MetricCard("Suspicious", dashboard.totals.suspiciousCount.toString(), "Flagged", modifier = Modifier.weight(1f))
                    }
                }
            } else {
                state.stats?.let { stats ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        MetricCard("Total", stats.totalAnalyses.toString(), "Analyses", modifier = Modifier.weight(1f))
                        MetricCard("Suspicious", stats.suspiciousCount.toString(), "Flagged", modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        MetricCard("Confidence", "${(stats.averageConfidence * 100).toInt()}%", "Avg certainty", modifier = Modifier.weight(1f))
                        MetricCard("Rate", "${(stats.suspiciousRate * 100).toInt()}%", "Suspicious rate", modifier = Modifier.weight(1f))
                    }
                }
            }

            if (isAdmin && state.dashboard?.activeModel == null && !state.loading) {
                NoActiveModelCard(
                    hasDatasets = state.datasets.isNotEmpty(),
                    hasModels = state.models.isNotEmpty(),
                    onReload = { viewModel.load() },
                )
            }
            if (isAdmin) {
                if (state.evaluation != null) {
                    PolishedEvaluationCard(evaluation = state.evaluation!!)
                } else {
                    EmptyCollectionCard(
                        title = "No evaluation report yet",
                        subtitle = "Train and activate a model to generate live evaluation metrics and confusion matrix data.",
                    )
                }
            }
            if (isAdmin) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Active models", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    StatusBadge(text = "${state.models.size} models", color = MaterialTheme.colorScheme.secondary)
                }
                if (state.models.isEmpty()) {
                    EmptyCollectionCard(
                        title = "No trained models published yet",
                        subtitle = "The first active model will appear here after dataset import and retraining.",
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        state.models.forEach { model ->
                            PolishedModelCard(model = model)
                        }
                    }
                }
            }

            if (isAdmin) {
                SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(text = "Import dataset", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Upload a labeled CSV with message/text and label/category columns. Valid labels: legitimate, spam, or fraud.",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                        )
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

                RetrainControlCard(
                    datasetId = datasetId,
                    onDatasetIdChange = { datasetId = it },
                    dataPath = dataPath,
                    onDataPathChange = { dataPath = it },
                    force = force,
                    onForceChange = { force = it },
                    onStart = {
                        viewModel.retrain(
                            datasetId = datasetId.toIntOrNull(),
                            dataPath = dataPath.ifBlank { null },
                            force = force,
                        )
                    },
                )

                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Datasets", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    StatusBadge(text = "${state.datasets.size} files", color = MaterialTheme.colorScheme.secondary)
                }
                if (state.datasets.isEmpty()) {
                    EmptyCollectionCard(
                        title = "No datasets uploaded yet",
                        subtitle = "Upload a labeled CSV to start training the first production model.",
                    )
                }
                if (state.datasets.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        state.datasets.forEach { dataset ->
                            PolishedDatasetCard(dataset = dataset)
                        }
                    }
                }
            } else {
                SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(text = "What you can do", style = MaterialTheme.typography.titleLarge)
                        Text(text = "Analyze SMS messages, review history, and keep automatic tracking enabled for real-time protection.")
                        StatusBadge(text = "Use Home for analysis and history", color = MaterialTheme.colorScheme.tertiary)
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

@Composable
private fun NoActiveModelCard(
    hasDatasets: Boolean,
    hasModels: Boolean,
    onReload: () -> Unit,
) {
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            StatusBadge(
                text = "Model not deployed yet",
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                text = "The dashboard is connected, but no active ML model has been published yet.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Once you import a labeled dataset and run retraining, the active model will appear here with accuracy, F1 score, and deployment details.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatusBadge(
                    text = if (hasDatasets) "Dataset uploaded" else "No dataset yet",
                    color = if (hasDatasets) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                )
                StatusBadge(
                    text = if (hasModels) "Training history found" else "No trained model",
                    color = if (hasModels) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Next steps", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(text = "1. Upload a labeled SMS dataset")
                Text(text = "2. Start retraining from the admin dashboard")
                Text(text = "3. Activate the best model and review evaluation metrics")
            }

            PrimaryButton(
                text = "Reload dashboard",
                onClick = onReload,
            )
        }
    }
}

@Composable
private fun PolishedModelCard(model: ModelDto) {
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    Text("${model.modelName} v${model.version}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = model.trainingDataPath ?: "Training data not recorded",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    )
                }
                StatusBadge(
                    text = if (model.isActive) "Active" else "Archived",
                    color = if (model.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatusBadge(text = "Acc ${(model.accuracy * 100).toInt()}%", color = MaterialTheme.colorScheme.secondary)
                StatusBadge(text = "F1 ${(model.f1Score * 100).toInt()}%", color = MaterialTheme.colorScheme.secondary)
                StatusBadge(text = "Trained ${model.trainedAt?.toString()?.take(10) ?: "recently"}", color = MaterialTheme.colorScheme.tertiary)
            }

            Text(
                text = "Precision ${(model.precision * 100).toInt()}% | Recall ${(model.recall * 100).toInt()}%",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
        }
    }
}

@Composable
private fun PolishedDatasetCard(dataset: DatasetDto) {
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    Text(dataset.originalFilename, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = dataset.notes.ifBlank { "No notes provided" },
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    )
                }
                StatusBadge(text = "${dataset.rowCount} rows", color = MaterialTheme.colorScheme.primary)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatusBadge(text = "Imported dataset", color = MaterialTheme.colorScheme.secondary)
                StatusBadge(text = "Training ready", color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
private fun EmptyCollectionCard(title: String, subtitle: String) {
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusBadge(text = "Nothing here yet", color = MaterialTheme.colorScheme.tertiary)
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(text = subtitle, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
        }
    }
}

@Composable
private fun PolishedEvaluationCard(evaluation: EvaluationReportDto) {
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    StatusBadge(text = "Evaluation", color = MaterialTheme.colorScheme.secondary)
                    Text(
                        text = "${evaluation.modelName} v${evaluation.version}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Live metrics for the currently active SMS fraud detector.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    )
                }
                StatusBadge(
                    text = "${(evaluation.accuracy * 100).toInt()}% accuracy",
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatusBadge(text = "F1 ${(evaluation.f1Score * 100).toInt()}%", color = MaterialTheme.colorScheme.secondary)
                StatusBadge(text = "Precision ${(evaluation.precision * 100).toInt()}%", color = MaterialTheme.colorScheme.secondary)
                StatusBadge(text = "Recall ${(evaluation.recall * 100).toInt()}%", color = MaterialTheme.colorScheme.secondary)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard("Train", evaluation.trainingSamples.toString(), "Samples", modifier = Modifier.weight(1f))
                MetricCard("Test", evaluation.testSamples.toString(), "Samples", modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RetrainControlCard(
    datasetId: String,
    onDatasetIdChange: (String) -> Unit,
    dataPath: String,
    onDataPathChange: (String) -> Unit,
    force: Boolean,
    onForceChange: (Boolean) -> Unit,
    onStart: () -> Unit,
) {
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Rounded.Upload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "Retrain model", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Choose a dataset ID or an explicit data path, then retrain the current model.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    )
                }
            }
            OutlinedTextField(
                value = datasetId,
                onValueChange = onDatasetIdChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Dataset ID (optional)") },
            )
            OutlinedTextField(
                value = dataPath,
                onValueChange = onDataPathChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Data path (optional)") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                StatusBadge(
                    text = if (force) "Force enabled" else "Force disabled",
                    color = if (force) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f),
                )
                androidx.compose.material3.Switch(checked = force, onCheckedChange = onForceChange)
            }
            PrimaryButton(
                text = "Start retraining",
                onClick = onStart,
            )
        }
    }
}

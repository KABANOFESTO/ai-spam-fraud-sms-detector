package com.smsai.smsfrauddetector.features.report

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
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
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReportUiState(
    val loading: Boolean = true,
    val viewerRole: String? = null,
    val reports: List<FraudReportDto> = emptyList(),
    val summary: Map<String, Int> = emptyMap(),
    val error: String? = null,
)

class ReportViewModel(private val repository: AppRepository) : ViewModel() {
    private val _state = MutableStateFlow(ReportUiState())
    val state: StateFlow<ReportUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val session = repository.currentSession()
            val isAdmin = session.user?.role.equals("Admin", ignoreCase = true)

            val reportsResult = repository.reports(page = 1, pageSize = 100)

            val reports = when (reportsResult) {
                is ApiResult.Success -> reportsResult.data.results
                else -> emptyList()
            }
            val summary = buildLiveSummary(reports)
            val error = listOfNotNull(
                reportsResult.takeIf { it is ApiResult.Error }?.let { (it as ApiResult.Error).message },
            ).firstOrNull()

            _state.value = ReportUiState(
                loading = false,
                viewerRole = session.user?.role,
                reports = reports,
                summary = summary,
                error = error,
            )
        }
    }

    private fun buildLiveSummary(reports: List<FraudReportDto>): Map<String, Int> {
        val pending = reports.count { it.status.equals("PENDING", ignoreCase = true) }
        val reviewing = reports.count { it.status.equals("REVIEWING", ignoreCase = true) }
        val reviewed = reports.count { it.status.equals("REVIEWED", ignoreCase = true) }
        val resolved = reports.count { it.status.equals("RESOLVED", ignoreCase = true) }
        val rejected = reports.count { it.status.equals("REJECTED", ignoreCase = true) }
        val predictionReports = reports.mapNotNull { it.analysis }
        val suspiciousPredictions = predictionReports.count {
            it.isSuspicious || it.prediction.contains("spam", ignoreCase = true) || it.prediction.contains("fraud", ignoreCase = true)
        }
        val legitimatePredictions = predictionReports.count {
            it.prediction.contains("legitimate", ignoreCase = true) ||
                it.prediction.contains("safe", ignoreCase = true) ||
                it.prediction.contains("benign", ignoreCase = true)
        }
        return mapOf(
            "total_reports" to reports.size,
            "sms_count" to reports.size,
            "pending_reports" to pending,
            "reviewing_reports" to reviewing,
            "reviewed_reports" to reviewed,
            "resolved_reports" to resolved,
            "rejected_reports" to rejected,
            "prediction_reports" to predictionReports.size,
            "suspicious_predictions" to suspiciousPredictions,
            "legitimate_predictions" to legitimatePredictions,
        )
    }
}

@Composable
fun ReportScreen(repository: AppRepository, highlightReportId: Int? = null) {
    val viewModel: ReportViewModel = viewModel(
        factory = remember(repository) { SimpleViewModelFactory { ReportViewModel(repository) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val isAdmin = state.viewerRole.equals("Admin", ignoreCase = true)
    var bannerMessage by remember { mutableStateOf<String?>(null) }
    var bannerTone by remember { mutableStateOf(BannerTone.Info) }
    var bannerToken by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(state.reports, highlightReportId) {
        val targetId = highlightReportId ?: return@LaunchedEffect
        val index = state.reports.indexOfFirst { it.id == targetId }
        if (index >= 0) listState.scrollToItem(index)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusBadge(
                        text = if (isAdmin) "Admin report center" else "Your report export",
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Text(
                        text = if (isAdmin) "Live review metrics, report backlog, and downloadable export." else "Your generated incident history, ready to export as PDF.",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricTile(
                        title = if (isAdmin) "SMS" else "Total",
                        value = if (isAdmin) state.summary["sms_count"].orZero().toString() else state.summary["total_reports"].orZero().toString(),
                        subtitle = if (isAdmin) "Live report messages" else "Your reports",
                        modifier = Modifier.weight(1f),
                    )
                    MetricTile(
                        title = if (isAdmin) "Predictions" else "Pending",
                        value = if (isAdmin) state.summary["prediction_reports"].orZero().toString() else state.summary["pending_reports"].orZero().toString(),
                        subtitle = if (isAdmin) "Linked AI outputs" else "Awaiting review",
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricTile(
                        title = if (isAdmin) "Suspicious" else "Reviewed",
                        value = if (isAdmin) state.summary["suspicious_predictions"].orZero().toString() else state.summary["reviewed_reports"].orZero().toString(),
                        subtitle = if (isAdmin) "High-risk outcomes" else "Completed",
                        modifier = Modifier.weight(1f),
                    )
                    MetricTile(
                        title = if (isAdmin) "Legitimate" else "Status",
                        value = if (isAdmin) state.summary["legitimate_predictions"].orZero().toString() else state.reports.count { it.status.equals("RESOLVED", ignoreCase = true) }.toString(),
                        subtitle = if (isAdmin) "Safe predictions" else "Live activity",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            PrimaryButton(
                                text = "Export PDF",
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    coroutineScope.launch {
                                        try {
                                            val uri = createReportPdf(context, state, isAdmin)
                                            sharePdf(context, uri, if (isAdmin) "Admin report" else "My report")
                                            bannerMessage = "PDF exported successfully."
                                            bannerTone = BannerTone.Success
                                            bannerToken += 1
                                        } catch (exc: Exception) {
                                            bannerMessage = exc.message ?: "Unable to export PDF."
                                            bannerTone = BannerTone.Error
                                            bannerToken += 1
                                        }
                                    }
                                },
                                trailingIcon = true,
                            )
                            PrimaryButton(
                                text = "Refresh",
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.load() },
                                trailingIcon = true,
                            )
                        }
                        Text(
                            text = if (isAdmin) {
                                "Admin exports include review totals, backlog counts, and the latest system activity."
                            } else {
                                "User exports include your activity history and current review outcomes."
                            },
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                        )
                    }
                }
            }

            item {
                SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatusBadge(
                            text = if (isAdmin) "Automatic system activity" else "Automatic personal activity",
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        Text(
                            text = if (isAdmin) "Live activity feed" else "Your activity feed",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        if (state.reports.isEmpty()) {
                            Text(
                                text = "No activity records are available yet.",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                            )
                        } else {
                            state.reports.take(3).forEach { report ->
                                ActivityPreviewRow(report = report, isAdmin = isAdmin)
                            }
                        }
                    }
                }
            }

            state.error?.let { error ->
                item {
                    ErrorStateCard(message = error, retryText = "Reload reports", onRetry = { viewModel.load() })
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (isAdmin) "System activity" else "My activity",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    StatusBadge(
                        text = "${state.reports.size} items",
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (state.reports.isEmpty()) {
                item {
                    EmptyReportCard(
                        title = if (isAdmin) "No system activity found yet" else "No personal activity found yet",
                        subtitle = if (isAdmin) {
                            "Admin-sourced SMS reports and prediction results will appear here as users interact with the app."
                        } else {
                            "Your saved report records will appear here automatically after analysis and review."
                        },
                    )
                }
            } else {
                items(state.reports, key = { it.id }) { report ->
                    ReportCard(
                        report = report,
                        isAdmin = isAdmin,
                        highlighted = highlightReportId == report.id,
                    )
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

    LaunchedEffect(bannerToken) {
        val messageSnapshot = bannerMessage ?: return@LaunchedEffect
        kotlinx.coroutines.delay(2400)
        if (bannerMessage == messageSnapshot) {
            bannerMessage = null
        }
    }
}

private suspend fun createReportPdf(context: Context, state: ReportUiState, isAdmin: Boolean): Uri = withContext(Dispatchers.IO) {
    val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
    val fileStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val outputDir = File(context.cacheDir, "reports").apply { mkdirs() }
    val outputFile = File(outputDir, "sms_report_${if (isAdmin) "admin" else "user"}_$fileStamp.pdf")

    val document = PdfDocument()
    val pageWidth = 1080f
    val pageHeight = 1600f
    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth.toInt(), pageHeight.toInt(), 1).create()
    val page = document.startPage(pageInfo)
    val canvas: Canvas = page.canvas
    canvas.drawColor(android.graphics.Color.WHITE)

    val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = android.graphics.Color.rgb(20, 88, 79)
    }
    val bandAccentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = android.graphics.Color.rgb(31, 118, 105)
    }
    val bandTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 18f
        isFakeBoldText = true
        color = android.graphics.Color.WHITE
    }
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 38f
        isFakeBoldText = true
        color = android.graphics.Color.BLACK
    }
    val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 22f
        color = android.graphics.Color.DKGRAY
    }
    val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 24f
        isFakeBoldText = true
        color = android.graphics.Color.rgb(34, 102, 92)
    }
    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 18f
        isFakeBoldText = true
        color = android.graphics.Color.GRAY
    }
    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 20f
        color = android.graphics.Color.DKGRAY
    }
    val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 18f
        color = android.graphics.Color.GRAY
    }
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = android.graphics.Color.rgb(225, 229, 232)
    }
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = android.graphics.Color.rgb(245, 248, 249)
    }
    val accentFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = android.graphics.Color.rgb(232, 243, 241)
    }

    canvas.drawRect(0f, 0f, pageWidth, 150f, bandPaint)
    canvas.drawCircle(92f, 75f, 34f, bandAccentPaint)
    canvas.drawText("SF", 68f, 82f, bandTextPaint)
    canvas.drawText("SMS Fraud Detector", 150f, 62f, bandTextPaint)
    canvas.drawText(if (isAdmin) "Admin Export" else "User Export", 150f, 104f, bandTextPaint)
    canvas.drawText("Live backend report", 780f, 62f, bandTextPaint)
    canvas.drawText(stamp, 780f, 104f, bandTextPaint)

    var y = 190f
    canvas.drawText(if (isAdmin) "Admin Report Export" else "User Report Export", 70f, y, titlePaint)
    y += 34f
    canvas.drawText(
        if (isAdmin) "A formal snapshot of system review activity and live report data." else "A formal snapshot of your submitted reports and current review outcomes.",
        70f,
        y,
        subtitlePaint,
    )

    y += 30f
    canvas.drawRect(70f, y, 1010f, y + 78f, accentFillPaint)
    canvas.drawRect(70f, y, 1010f, y + 78f, borderPaint)
    canvas.drawText("Generated", 92f, y + 30f, labelPaint)
    canvas.drawText(stamp, 92f, y + 58f, bodyPaint)
    canvas.drawText("Scope", 424f, y + 30f, labelPaint)
    canvas.drawText(if (isAdmin) "Admin dashboard" else "Personal report", 424f, y + 58f, bodyPaint)
    canvas.drawText("Records", 712f, y + 30f, labelPaint)
    canvas.drawText("${state.reports.size} items", 712f, y + 58f, bodyPaint)
    y += 112f

    canvas.drawText("Summary", 70f, y, sectionPaint)
    y += 18f
    canvas.drawLine(70f, y, 1010f, y, borderPaint)
    y += 38f

    val summaryRows = listOf(
        "Total reports" to state.summary["total_reports"].orZero(),
        "SMS items" to state.summary["sms_count"].orZero(),
        "Predictions" to state.summary["prediction_reports"].orZero(),
        "Suspicious" to state.summary["suspicious_predictions"].orZero(),
        "Legitimate" to state.summary["legitimate_predictions"].orZero(),
        "Pending" to state.summary["pending_reports"].orZero(),
        "Reviewing" to state.summary["reviewing_reports"].orZero(),
        "Reviewed" to state.summary["reviewed_reports"].orZero(),
        "Resolved" to state.summary["resolved_reports"].orZero(),
        "Rejected" to state.summary["rejected_reports"].orZero(),
    )

    summaryRows.chunked(2).forEach { pair ->
        val left = pair[0]
        val right = pair.getOrNull(1)
        canvas.drawRect(70f, y - 18f, 500f, y + 46f, fillPaint)
        canvas.drawRect(70f, y - 18f, 500f, y + 46f, borderPaint)
        canvas.drawText(left.first, 92f, y + 6f, labelPaint)
        canvas.drawText(left.second.toString(), 92f, y + 34f, bodyPaint)
        right?.let {
            canvas.drawRect(580f, y - 18f, 1010f, y + 46f, fillPaint)
            canvas.drawRect(580f, y - 18f, 1010f, y + 46f, borderPaint)
            canvas.drawText(it.first, 602f, y + 6f, labelPaint)
            canvas.drawText(it.second.toString(), 602f, y + 34f, bodyPaint)
        }
        y += 72f
    }

    y += 12f
    canvas.drawText("Recent reports", 70f, y, sectionPaint)
    y += 18f
    canvas.drawLine(70f, y, 1010f, y, borderPaint)
    y += 34f

    val maxItems = minOf(state.reports.size, 8)
    if (maxItems == 0) {
        canvas.drawText("No report records are available yet.", 92f, y + 18f, bodyPaint)
    } else {
        canvas.drawText("ID", 92f, y, labelPaint)
        canvas.drawText("Status", 188f, y, labelPaint)
        canvas.drawText("Message", 340f, y, labelPaint)
        canvas.drawText("Updated", 888f, y, labelPaint)
        y += 18f
        canvas.drawLine(90f, y, 1000f, y, borderPaint)
        y += 24f
        for (report in state.reports.take(maxItems)) {
            if (y > 1460f) break
            canvas.drawRect(70f, y - 16f, 1010f, y + 48f, fillPaint)
            canvas.drawRect(70f, y - 16f, 1010f, y + 48f, borderPaint)
            canvas.drawText("#${report.id}", 92f, y + 10f, bodyPaint)
            canvas.drawText(report.status.uppercase(Locale.getDefault()), 188f, y + 10f, bodyPaint)
            canvas.drawText(report.smsMessage.take(58), 340f, y + 10f, bodyPaint)
            canvas.drawText((report.reviewedAt ?: report.createdAt ?: "Pending"), 888f, y + 10f, bodyPaint)
            y += 64f
        }
    }

    if (state.reports.size > maxItems) {
        canvas.drawText(
            "Additional records are available inside the app (${state.reports.size - maxItems} more).",
            70f,
            1510f,
            mutedPaint,
        )
    }

    canvas.drawLine(70f, 1540f, 1010f, 1540f, borderPaint)
    canvas.drawText("Confidential report generated by SMS Fraud Detector", 70f, 1570f, mutedPaint)
    canvas.drawText("Page 1", 940f, 1570f, mutedPaint)

    document.finishPage(page)
    FileOutputStream(outputFile).use { out -> document.writeTo(out) }
    document.close()
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outputFile)
}

private fun sharePdf(context: Context, uri: Uri, title: String) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, title)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (context !is Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    context.startActivity(Intent.createChooser(shareIntent, title))
}

@Composable
private fun ReportCard(report: FraudReportDto, isAdmin: Boolean, highlighted: Boolean = false) {
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isAdmin && report.user != null) "Report #${report.id} by ${report.user}" else "Report #${report.id}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = report.smsMessage,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                        maxLines = 3,
                    )
                }
                StatusBadge(
                    text = report.status.uppercase(Locale.getDefault()),
                    color = reportStatusColor(report.status),
                )
            }
            if (highlighted) {
                StatusBadge(text = "Opened from notification", color = MaterialTheme.colorScheme.secondary, compact = true)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatusBadge(text = if (report.analysis != null) "Linked analysis" else "Standalone", color = MaterialTheme.colorScheme.primary)
                StatusBadge(text = report.analysis?.prediction ?: "No prediction", color = MaterialTheme.colorScheme.secondary)
            }

            Text(
                text = report.notes.ifBlank { "No notes provided." },
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
            report.adminNotes.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = "Admin notes: $it",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                )
            }

            Text(
                text = formatFriendlyTimestamp(report.reviewedAt ?: report.createdAt),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f),
            )
        }
    }
}

@Composable
private fun EmptyReportCard(title: String, subtitle: String) {
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StatusBadge(text = "No export data yet", color = MaterialTheme.colorScheme.tertiary)
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(text = subtitle, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
        }
    }
}

@Composable
private fun MetricTile(title: String, value: String, subtitle: String, modifier: Modifier = Modifier) {
    SurfaceCard(modifier = modifier) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
            Text(text = value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f))
        }
    }
}

@Composable
private fun reportStatusColor(status: String) = when (status.uppercase(Locale.getDefault())) {
    "PENDING" -> MaterialTheme.colorScheme.tertiary
    "REVIEWING" -> MaterialTheme.colorScheme.secondary
    "REVIEWED" -> MaterialTheme.colorScheme.primary
    "RESOLVED" -> MaterialTheme.colorScheme.primary
    "REJECTED" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.secondary
}

private fun Int?.orZero(): Int = this ?: 0

@Composable
private fun ActivityPreviewRow(report: FraudReportDto, isAdmin: Boolean) {
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (isAdmin && report.user != null) "Activity #${report.id} by ${report.user}" else "Activity #${report.id}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                StatusBadge(
                    text = report.status.uppercase(Locale.getDefault()),
                    color = reportStatusColor(report.status),
                    compact = true,
                )
            }
            Text(
                text = report.smsMessage,
                maxLines = 2,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusBadge(
                    text = report.analysis?.prediction ?: "Auto logged",
                    color = MaterialTheme.colorScheme.primary,
                    compact = true,
                )
                StatusBadge(
                    text = formatFriendlyTimestamp(report.reviewedAt ?: report.createdAt),
                    color = MaterialTheme.colorScheme.tertiary,
                    compact = true,
                )
            }
        }
    }
}

private fun formatFriendlyTimestamp(rawValue: String?): String {
    val raw = rawValue?.trim().orEmpty()
    if (raw.isBlank()) return "Pending review"

    val parsed = parseTimestamp(raw) ?: return raw
    val now = Calendar.getInstance()
    val whenCal = Calendar.getInstance().apply { time = parsed }
    val timeText = SimpleDateFormat("h:mm a", Locale.getDefault()).format(parsed)

    return when {
        isSameDay(now, whenCal) -> "Today, $timeText"
        isYesterday(now, whenCal) -> "Yesterday, $timeText"
        else -> SimpleDateFormat("MMM d, yyyy - h:mm a", Locale.getDefault()).format(parsed)
    }
}

private fun parseTimestamp(rawValue: String): Date? {
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSX",
        "yyyy-MM-dd'T'HH:mm:ssX",
        "yyyy-MM-dd HH:mm:ss",
    )
    for (pattern in patterns) {
        val parsed = runCatching {
            SimpleDateFormat(pattern, Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(rawValue)
        }.getOrNull()
        if (parsed != null) return parsed
    }
    return null
}

private fun isSameDay(first: Calendar, second: Calendar): Boolean {
    return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
        first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
}

private fun isYesterday(today: Calendar, candidate: Calendar): Boolean {
    val yesterday = (today.clone() as Calendar).apply {
        add(Calendar.DAY_OF_YEAR, -1)
    }
    return isSameDay(yesterday, candidate)
}

package com.smsai.smsfrauddetector.features.settings

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.smsai.smsfrauddetector.core.common.SimpleViewModelFactory
import com.smsai.smsfrauddetector.core.designsystem.components.BannerTone
import com.smsai.smsfrauddetector.core.designsystem.components.ErrorStateCard
import com.smsai.smsfrauddetector.core.designsystem.components.FeedbackBanner
import com.smsai.smsfrauddetector.core.designsystem.components.PrimaryButton
import com.smsai.smsfrauddetector.core.designsystem.components.StatusBadge
import com.smsai.smsfrauddetector.core.designsystem.components.SurfaceCard
import com.smsai.smsfrauddetector.core.permissions.SmsTrackingPermissions
import com.smsai.smsfrauddetector.data.repository.AppRepository
import kotlinx.coroutines.launch

data class SettingsUiState(
    val baseUrl: String = "",
    val darkMode: Boolean = true,
    val smsMonitoring: Boolean = false,
    val error: String? = null,
)

class SettingsViewModel(private val repository: AppRepository) : ViewModel() {
    private val _state = kotlinx.coroutines.flow.MutableStateFlow(SettingsUiState())
    val state: kotlinx.coroutines.flow.StateFlow<SettingsUiState> = _state

    fun load() {
        viewModelScope.launch {
            try {
                _state.value = SettingsUiState(
                    baseUrl = repository.currentBaseUrl(),
                    darkMode = repository.currentDarkMode(),
                    smsMonitoring = repository.currentSmsMonitoring(),
                )
            } catch (_: Throwable) {
                _state.value = _state.value.copy(error = "Unable to load settings.")
            }
        }
    }

    fun save(baseUrl: String, darkMode: Boolean) {
        viewModelScope.launch {
            try {
                repository.updateBaseUrl(baseUrl)
                repository.setDarkMode(darkMode)
                load()
            } catch (throwable: Throwable) {
                _state.value = _state.value.copy(error = throwable.message ?: "Unable to save settings.")
            }
        }
    }

    fun setSmsMonitoringEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                repository.setSmsMonitoringEnabled(enabled)
                load()
            } catch (throwable: Throwable) {
                _state.value = _state.value.copy(error = throwable.message ?: "Unable to update SMS tracking.")
            }
        }
    }
}

@Composable
fun SettingsScreen(repository: AppRepository) {
    val viewModel: SettingsViewModel = viewModel(
        factory = remember(repository) { SimpleViewModelFactory { SettingsViewModel(repository) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var baseUrl by rememberSaveable { mutableStateOf("") }
    var darkMode by rememberSaveable { mutableStateOf(true) }
    var trackingStatusMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var statusTone by remember { mutableStateOf(BannerTone.Info) }
    var statusRefresh by remember { mutableIntStateOf(0) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        statusRefresh += 1
        if (granted) {
            evaluateTrackingSetup(context, viewModel, onStatus = { trackingStatusMessage = it })
        } else {
            trackingStatusMessage = "Notification permission is required to show fraud alerts."
        }
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        statusRefresh += 1
        if (granted) {
            evaluateTrackingSetup(
                context,
                viewModel,
                onStatus = { trackingStatusMessage = it },
                notificationPermissionLauncher = notificationPermissionLauncher,
            )
        } else {
            trackingStatusMessage = "SMS permission is required for automatic message tracking."
        }
    }

    val defaultSmsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        statusRefresh += 1
        if (SmsTrackingPermissions.isDefaultSmsApp(context)) {
            evaluateTrackingSetup(
                context = context,
                viewModel = viewModel,
                onStatus = { trackingStatusMessage = it },
                smsPermissionLauncher = smsPermissionLauncher,
                notificationPermissionLauncher = notificationPermissionLauncher,
            )
        } else {
            trackingStatusMessage = "This app must be the default SMS handler to enable automatic tracking."
        }
    }

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    LaunchedEffect(state.baseUrl) {
        if (state.baseUrl.isNotBlank()) {
            baseUrl = state.baseUrl
            darkMode = state.darkMode
        }
    }

    LaunchedEffect(trackingStatusMessage) {
        val status = trackingStatusMessage ?: return@LaunchedEffect
        statusTone = when {
            status.contains("enabled", ignoreCase = true) ||
                status.contains("off", ignoreCase = true) ||
                status.contains("refreshed", ignoreCase = true) -> BannerTone.Success
            status.contains("required", ignoreCase = true) ||
                status.contains("missing", ignoreCase = true) ||
                status.contains("first", ignoreCase = true) ||
                status.contains("continue", ignoreCase = true) -> BannerTone.Error
            else -> BannerTone.Info
        }
        kotlinx.coroutines.delay(2400)
        trackingStatusMessage = null
    }

    val defaultSmsApp = remember(statusRefresh) { SmsTrackingPermissions.isDefaultSmsApp(context) }
    val smsPermissionGranted = remember(statusRefresh) { SmsTrackingPermissions.isSmsPermissionGranted(context) }
    val notificationPermissionGranted = remember(statusRefresh) { SmsTrackingPermissions.isNotificationPermissionGranted(context) }
    val trackingReady = remember(statusRefresh) { SmsTrackingPermissions.canTrackAutomatically(context) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatusBadge(text = "App control center", color = MaterialTheme.colorScheme.secondary)
                    Text(text = "Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Configure the backend, theme, and SMS tracking permissions from one secure place.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        StatusBadge(text = "Backend sync", color = MaterialTheme.colorScheme.primary)
                        StatusBadge(text = "Tracking setup", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            state.error?.let {
                ErrorStateCard(message = it, retryText = "Reload settings", onRetry = { viewModel.load() })
            }

            SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = "Core app settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "These values are saved locally and used by the app to reach the live backend.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    )
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Backend base URL") },
                    )
                    RowSetting("Dark mode", darkMode) { darkMode = it }
                    PrimaryButton(text = "Save settings", onClick = { viewModel.save(baseUrl, darkMode) })
                }
            }

            SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = "Automatic SMS tracking", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "For real-time fraud detection, the app must be the default SMS handler, and SMS + notification permissions must be granted.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusBadge(
                            text = if (defaultSmsApp) "Default SMS app" else "Not default",
                            color = if (defaultSmsApp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                        StatusBadge(
                            text = if (smsPermissionGranted) "SMS permission" else "SMS permission missing",
                            color = if (smsPermissionGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                        StatusBadge(
                            text = if (notificationPermissionGranted) "Alerts enabled" else "Alerts missing",
                            color = if (notificationPermissionGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                    }

                    Text(
                        text = when {
                            state.smsMonitoring && trackingReady -> "Automatic tracking is active."
                            state.smsMonitoring -> "Tracking is enabled, but the device is not currently ready."
                            else -> "Automatic tracking is disabled."
                        },
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        StatusBadge(
                            text = if (trackingReady) "Ready to monitor" else "Setup required",
                            color = if (trackingReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                        )
                        StatusBadge(
                            text = if (state.smsMonitoring) "Monitoring on" else "Monitoring off",
                            color = if (state.smsMonitoring) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                        )
                    }

                    PrimaryButton(
                        text = if (state.smsMonitoring) "Disable tracking" else "Enable tracking",
                        onClick = {
                            if (state.smsMonitoring) {
                                viewModel.setSmsMonitoringEnabled(false)
                                trackingStatusMessage = "Automatic SMS tracking has been turned off."
                            } else {
                                startAutomaticTrackingSetup(
                                    context = context,
                                    viewModel = viewModel,
                                    defaultSmsLauncher = defaultSmsLauncher,
                                    smsPermissionLauncher = smsPermissionLauncher,
                                    notificationPermissionLauncher = notificationPermissionLauncher,
                                    onStatus = { trackingStatusMessage = it },
                                )
                            }
                        },
                    )

                    PrimaryButton(
                        text = if (defaultSmsApp) "Refresh SMS status" else "Set as default SMS app",
                        onClick = {
                            if (defaultSmsApp) {
                                statusRefresh += 1
                                trackingStatusMessage = "SMS role status refreshed."
                            } else {
                                defaultSmsLauncher.launch(SmsTrackingPermissions.buildDefaultSmsRoleIntent(context))
                            }
                        },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = trackingStatusMessage != null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                .widthIn(max = 520.dp),
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        ) {
            trackingStatusMessage?.let { FeedbackBanner(message = it, tone = statusTone) }
        }
    }
}

private fun evaluateTrackingSetup(
    context: Context,
    viewModel: SettingsViewModel,
    onStatus: (String) -> Unit,
    smsPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>? = null,
    notificationPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>? = null,
) {
    when {
        !SmsTrackingPermissions.isDefaultSmsApp(context) -> {
            onStatus("Set this app as the default SMS handler to continue.")
        }
        !SmsTrackingPermissions.isSmsPermissionGranted(context) -> {
            smsPermissionLauncher?.launch(Manifest.permission.RECEIVE_SMS)
                ?: onStatus("SMS permission is missing.")
        }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !SmsTrackingPermissions.isNotificationPermissionGranted(context) -> {
            notificationPermissionLauncher?.launch(Manifest.permission.POST_NOTIFICATIONS)
                ?: onStatus("Notification permission is missing.")
        }
        else -> {
            viewModel.setSmsMonitoringEnabled(true)
            onStatus("Automatic SMS tracking is enabled and ready.")
        }
    }
}

private fun startAutomaticTrackingSetup(
    context: Context,
    viewModel: SettingsViewModel,
    defaultSmsLauncher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>,
    smsPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    notificationPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    onStatus: (String) -> Unit,
) {
    if (!SmsTrackingPermissions.isDefaultSmsApp(context)) {
        onStatus("Please set this app as the default SMS handler first.")
        defaultSmsLauncher.launch(SmsTrackingPermissions.buildDefaultSmsRoleIntent(context))
        return
    }

    if (!SmsTrackingPermissions.isSmsPermissionGranted(context)) {
        onStatus("Granting SMS permission...")
        smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
        return
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !SmsTrackingPermissions.isNotificationPermissionGranted(context)) {
        onStatus("Granting notification permission...")
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        return
    }

    viewModel.setSmsMonitoringEnabled(true)
    onStatus("Automatic SMS tracking is enabled and ready.")
}

@Composable
private fun RowSetting(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

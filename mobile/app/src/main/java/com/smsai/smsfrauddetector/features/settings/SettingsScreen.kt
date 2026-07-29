package com.smsai.smsfrauddetector.features.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    val isAdmin: Boolean = false,
    val error: String? = null,
)

class SettingsViewModel(private val repository: AppRepository) : ViewModel() {
    private val _state = kotlinx.coroutines.flow.MutableStateFlow(SettingsUiState())
    val state: kotlinx.coroutines.flow.StateFlow<SettingsUiState> = _state

    fun load() {
        viewModelScope.launch {
            try {
                val session = repository.currentSession()
                _state.value = SettingsUiState(
                    baseUrl = repository.currentBaseUrl(),
                    darkMode = repository.currentDarkMode(),
                    smsMonitoring = repository.currentSmsMonitoring(),
                    isAdmin = session.user?.role.equals("Admin", ignoreCase = true),
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
    var trackingToken by remember { mutableIntStateOf(0) }

    fun publishTrackingStatus(message: String) {
        trackingStatusMessage = message
        trackingToken += 1
    }

    fun openDefaultAppsSettings() {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure {
            publishTrackingStatus("Open Android Settings > Apps > Default apps > SMS app to finish setup.")
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        statusRefresh += 1
        if (granted) {
            evaluateTrackingSetup(context, viewModel, onStatus = { publishTrackingStatus(it) })
        } else {
            publishTrackingStatus("Notification permission is required to show fraud alerts.")
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
                onStatus = { publishTrackingStatus(it) },
                notificationPermissionLauncher = notificationPermissionLauncher,
            )
        } else {
            publishTrackingStatus("SMS permission is required for automatic message tracking.")
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
                onStatus = { publishTrackingStatus(it) },
                smsPermissionLauncher = smsPermissionLauncher,
                notificationPermissionLauncher = notificationPermissionLauncher,
            )
        } else {
            publishTrackingStatus("If prompted, choose this app as the default SMS handler, then come back here.")
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

    LaunchedEffect(trackingStatusMessage, trackingToken) {
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
        val currentToken = trackingToken
        kotlinx.coroutines.delay(2400)
        if (trackingToken == currentToken) {
            trackingStatusMessage = null
        }
    }

    val defaultSmsApp = remember(statusRefresh) { SmsTrackingPermissions.isDefaultSmsApp(context) }
    val smsPermissionGranted = remember(statusRefresh) { SmsTrackingPermissions.isSmsPermissionGranted(context) }
    val notificationPermissionGranted = remember(statusRefresh) { SmsTrackingPermissions.isNotificationPermissionGranted(context) }
    val trackingReady = remember(statusRefresh) { SmsTrackingPermissions.canTrackAutomatically(context) }
    val trackingStep = remember(
        defaultSmsApp,
        smsPermissionGranted,
        notificationPermissionGranted,
        trackingReady,
        state.smsMonitoring,
    ) {
        resolveTrackingStep(
            defaultSmsApp = defaultSmsApp,
            smsPermissionGranted = smsPermissionGranted,
            notificationPermissionGranted = notificationPermissionGranted,
            trackingReady = trackingReady,
            smsMonitoringEnabled = state.smsMonitoring,
        )
    }
    val setupButtonLabel = trackingStep.buttonLabel

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
                    StatusBadge(text = "App control center", color = MaterialTheme.colorScheme.secondary)
                    Text(text = "Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        StatusBadge(text = "Backend sync", color = MaterialTheme.colorScheme.primary)
                        StatusBadge(text = "Tracking setup", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            state.error?.let {
                ErrorStateCard(message = it, retryText = "Reload settings", onRetry = { viewModel.load() })
            }

            if (state.isAdmin) {
                SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(text = "Admin configuration", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
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
            } else {
                SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatusBadge(text = "User settings", color = MaterialTheme.colorScheme.primary)
                        Text(text = "Personal preferences", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Your app is already live. You can only adjust local preferences like appearance and tracking state here.",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                        )
                        RowSetting("Dark mode", darkMode) { darkMode = it }
                        PrimaryButton(text = "Save settings", onClick = { viewModel.save(baseUrl, darkMode) })
                    }
                }
            }

            SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = "Automatic SMS tracking", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "For real-time fraud detection, the app must be the default SMS handler, and SMS + notification permissions must be granted.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    )

                    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                StatusBadge(text = "Setup checklist", color = MaterialTheme.colorScheme.secondary, compact = true)
                                StatusBadge(
                                    text = if (trackingReady) "Ready" else "Needs setup",
                                    color = if (trackingReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                                    compact = true,
                                )
                            }
                            Text(
                                text = when {
                                    trackingReady -> "Everything required is in place. You can keep monitoring on or refresh status anytime."
                                    !defaultSmsApp -> "Start by making the app the default SMS handler. If Samsung hides it, open Default apps from the button below."
                                    !smsPermissionGranted -> "Next, let Android grant SMS read access."
                                    !notificationPermissionGranted -> "Then enable notifications for fraud alerts."
                                    else -> "Finish by turning on automatic monitoring."
                                },
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                            )
                            TrackingChecklistItem(
                                step = "1",
                                title = "Set as default SMS app",
                                done = defaultSmsApp,
                                active = trackingStep.currentStep == 0,
                                detail = if (defaultSmsApp) {
                                    "This device already trusts the app to receive SMS messages."
                                } else {
                                    "Tap below to request the SMS role from Android. If Samsung still hides the app, open Default apps and choose SMS app manually."
                                },
                            )
                            TrackingChecklistItem(
                                step = "2",
                                title = "Grant SMS permission",
                                done = smsPermissionGranted,
                                active = trackingStep.currentStep == 1,
                                detail = if (smsPermissionGranted) {
                                    "The receiver can now read incoming SMS bodies."
                                } else {
                                    "Android must allow RECEIVE_SMS before monitoring can start."
                                },
                            )
                            TrackingChecklistItem(
                                step = "3",
                                title = "Enable notifications",
                                done = notificationPermissionGranted,
                                active = trackingStep.currentStep == 2,
                                detail = if (notificationPermissionGranted) {
                                    "Fraud alerts can now appear instantly on the device."
                                } else {
                                    "Notification access is required for suspicious SMS alerts."
                                },
                            )
                            TrackingChecklistItem(
                                step = "4",
                                title = "Turn on automatic monitoring",
                                done = state.smsMonitoring && trackingReady,
                                active = trackingStep.currentStep == 3,
                                detail = when {
                                    state.smsMonitoring && trackingReady -> "Monitoring is active and ready for live analysis."
                                    state.smsMonitoring -> "The switch is on, but Android still has not accepted the app as the default SMS handler."
                                    else -> "Complete the steps above, then enable tracking from here."
                                },
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        PrimaryButton(
                            text = setupButtonLabel,
                            modifier = Modifier.weight(1f),
                            trailingIcon = true,
                            enabled = !trackingReady || !state.smsMonitoring,
                            onClick = {
                                startAutomaticTrackingSetup(
                                    context = context,
                                    viewModel = viewModel,
                                    defaultSmsLauncher = defaultSmsLauncher,
                                    smsPermissionLauncher = smsPermissionLauncher,
                                    notificationPermissionLauncher = notificationPermissionLauncher,
                                    onStatus = { trackingStatusMessage = it },
                                )
                            },
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusBadge(
                            text = if (defaultSmsApp) "Default SMS app" else "Not default",
                            color = if (defaultSmsApp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            compact = true,
                        )
                        StatusBadge(
                            text = if (smsPermissionGranted) "SMS permission" else "SMS permission missing",
                            color = if (smsPermissionGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            compact = true,
                        )
                        StatusBadge(
                            text = if (notificationPermissionGranted) "Alerts enabled" else "Alerts missing",
                            color = if (notificationPermissionGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            compact = true,
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
                                publishTrackingStatus("SMS role status refreshed.")
                            } else {
                                defaultSmsLauncher.launch(SmsTrackingPermissions.buildDefaultSmsRoleIntent(context))
                            }
                        },
                    )

                    PrimaryButton(
                        text = "Open default apps",
                        onClick = { openDefaultAppsSettings() },
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
            onStatus("Set this app as the default SMS handler to continue. If it is missing from the SMS list, use Open default apps and choose it manually.")
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
        onStatus("Please set this app as the default SMS handler first. If it is missing from the SMS list, open Default apps and choose it manually.")
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

@Composable
private fun TrackingChecklistItem(
    step: String,
    title: String,
    done: Boolean,
    active: Boolean,
    detail: String,
) {
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            StatusBadge(
                text = step,
                color = when {
                    done -> MaterialTheme.colorScheme.primary
                    active -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.tertiary
                },
                compact = true,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (active || done) FontWeight.Bold else FontWeight.SemiBold,
                )
                Text(
                    text = detail,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                )
            }
            StatusBadge(
                text = when {
                    done -> "Done"
                    active -> "Next"
                    else -> "Pending"
                },
                color = when {
                    done -> MaterialTheme.colorScheme.primary
                    active -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.error
                },
                compact = true,
            )
        }
    }
}

private data class TrackingStepState(
    val currentStep: Int,
    val buttonLabel: String,
)

private fun resolveTrackingStep(
    defaultSmsApp: Boolean,
    smsPermissionGranted: Boolean,
    notificationPermissionGranted: Boolean,
    trackingReady: Boolean,
    smsMonitoringEnabled: Boolean,
): TrackingStepState {
    return when {
        trackingReady && smsMonitoringEnabled -> TrackingStepState(currentStep = 3, buttonLabel = "Monitoring ready")
        trackingReady -> TrackingStepState(currentStep = 3, buttonLabel = "Enable monitoring")
        !defaultSmsApp -> TrackingStepState(currentStep = 0, buttonLabel = "Set default SMS app")
        !smsPermissionGranted -> TrackingStepState(currentStep = 1, buttonLabel = "Grant SMS access")
        !notificationPermissionGranted -> TrackingStepState(currentStep = 2, buttonLabel = "Enable notifications")
        else -> TrackingStepState(currentStep = 3, buttonLabel = "Continue setup")
    }
}

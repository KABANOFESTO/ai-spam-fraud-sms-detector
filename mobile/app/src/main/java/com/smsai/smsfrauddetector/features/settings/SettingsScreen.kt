package com.smsai.smsfrauddetector.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.smsai.smsfrauddetector.core.common.SimpleViewModelFactory
import com.smsai.smsfrauddetector.core.designsystem.components.ErrorStateCard
import com.smsai.smsfrauddetector.core.designsystem.components.PrimaryButton
import com.smsai.smsfrauddetector.core.designsystem.components.SurfaceCard
import com.smsai.smsfrauddetector.data.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val baseUrl: String = "",
    val darkMode: Boolean = true,
    val smsMonitoring: Boolean = false,
    val error: String? = null,
)

class SettingsViewModel(private val repository: AppRepository) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

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

    fun save(baseUrl: String, darkMode: Boolean, smsMonitoring: Boolean) {
        viewModelScope.launch {
            try {
                repository.updateBaseUrl(baseUrl)
                repository.setDarkMode(darkMode)
                repository.setSmsMonitoringEnabled(smsMonitoring)
                load()
            } catch (throwable: Throwable) {
                _state.value = _state.value.copy(error = throwable.message ?: "Unable to save settings.")
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
    var baseUrl by rememberSaveable { mutableStateOf("") }
    var darkMode by rememberSaveable { mutableStateOf(true) }
    var smsMonitoring by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(state.baseUrl) {
        if (state.baseUrl.isNotBlank()) {
            baseUrl = state.baseUrl
            darkMode = state.darkMode
            smsMonitoring = state.smsMonitoring
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text = "Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        state.error?.let { ErrorStateCard(message = it, retryText = "Reload settings", onRetry = { viewModel.load() }) }
        SurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Backend base URL") })
                RowSetting("Dark mode", darkMode) { darkMode = it }
                RowSetting("SMS monitoring", smsMonitoring) { smsMonitoring = it }
                PrimaryButton(text = "Save settings", onClick = { viewModel.save(baseUrl, darkMode, smsMonitoring) })
            }
        }
    }
}

@Composable
private fun RowSetting(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

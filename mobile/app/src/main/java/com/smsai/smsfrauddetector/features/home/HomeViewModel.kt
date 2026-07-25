package com.smsai.smsfrauddetector.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsai.smsfrauddetector.core.common.ApiResult
import com.smsai.smsfrauddetector.data.remote.dto.HealthDto
import com.smsai.smsfrauddetector.data.remote.dto.StatsDto
import com.smsai.smsfrauddetector.data.remote.dto.ModelDto
import com.smsai.smsfrauddetector.data.remote.dto.UserDto
import com.smsai.smsfrauddetector.data.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val user: UserDto? = null,
    val stats: StatsDto? = null,
    val health: HealthDto? = null,
    val activeModel: ModelDto? = null,
    val smsMonitoringEnabled: Boolean = false,
    val error: String? = null,
)

class HomeViewModel(
    private val repository: AppRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val session = repository.currentSession()
            val profile = session.user ?: when (val result = repository.fetchCurrentUser()) {
                is ApiResult.Success -> result.data
                is ApiResult.Error -> null
                else -> null
            }
            val stats = when (val result = repository.stats()) {
                is ApiResult.Success -> result.data
                is ApiResult.Error -> null
                else -> null
            }
            val health = when (val result = repository.health()) {
                is ApiResult.Success -> result.data
                is ApiResult.Error -> null
                else -> null
            }
            val activeModel = when (val result = repository.activeModels()) {
                is ApiResult.Success -> result.data.firstOrNull()
                is ApiResult.Error -> null
                else -> null
            }
            val smsMonitoringEnabled = session.smsMonitoringEnabled
            val error = when {
                profile == null || stats == null || health == null -> "Some dashboard data could not be loaded."
                else -> null
            }
            _state.value = HomeUiState(
                loading = false,
                user = profile,
                stats = stats,
                health = health,
                activeModel = activeModel,
                smsMonitoringEnabled = smsMonitoringEnabled,
                error = error,
            )
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.logout()
            onDone()
        }
    }
}

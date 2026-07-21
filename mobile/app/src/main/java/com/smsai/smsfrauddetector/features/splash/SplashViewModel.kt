package com.smsai.smsfrauddetector.features.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsai.smsfrauddetector.core.common.ApiResult
import com.smsai.smsfrauddetector.data.remote.dto.UserDto
import com.smsai.smsfrauddetector.data.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SplashUiState(
    val loading: Boolean = true,
    val authenticated: Boolean = false,
    val user: UserDto? = null,
    val error: String? = null,
)

class SplashViewModel(
    private val repository: AppRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SplashUiState())
    val state: StateFlow<SplashUiState> = _state.asStateFlow()

    init {
        checkSession()
    }

    fun checkSession() {
        viewModelScope.launch {
            try {
                val session = repository.currentSession()
                if (session.accessToken.isNullOrBlank()) {
                    _state.value = SplashUiState(loading = false, authenticated = false)
                    return@launch
                }

                when (val result = repository.fetchCurrentUser()) {
                    is ApiResult.Success -> {
                        _state.value = SplashUiState(
                            loading = false,
                            authenticated = true,
                            user = result.data,
                        )
                    }
                    is ApiResult.Error -> {
                        repository.logout()
                        _state.value = SplashUiState(
                            loading = false,
                            authenticated = false,
                            error = "Session expired. Please log in again.",
                        )
                    }
                    else -> Unit
                }
            } catch (_: Throwable) {
                _state.value = SplashUiState(
                    loading = false,
                    authenticated = false,
                    error = "Unable to verify session. Check your connection and try again.",
                )
            }
        }
    }
}

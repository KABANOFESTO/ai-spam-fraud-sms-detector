package com.smsai.smsfrauddetector.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsai.smsfrauddetector.core.common.ApiResult
import com.smsai.smsfrauddetector.core.common.UiState
import com.smsai.smsfrauddetector.data.remote.dto.UserDto
import com.smsai.smsfrauddetector.data.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val repository: AppRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<UiState<UserDto>>(UiState.Idle)
    val state: StateFlow<UiState<UserDto>> = _state.asStateFlow()

    fun clear() {
        _state.value = UiState.Idle
    }

    fun login(email: String, password: String) {
        _state.value = UiState.Loading
        viewModelScope.launch {
            when (val result = repository.login(email, password)) {
                is ApiResult.Success -> _state.value = UiState.Success(result.data.user)
                is ApiResult.Error -> _state.value = UiState.Error(result.message)
                else -> Unit
            }
        }
    }

    fun register(
        username: String,
        email: String,
        password: String,
        firstName: String,
        lastName: String,
    ) {
        _state.value = UiState.Loading
        viewModelScope.launch {
            when (val result = repository.register(username, email, password, firstName, lastName)) {
                is ApiResult.Success -> _state.value = UiState.Success(result.data.user)
                is ApiResult.Error -> _state.value = UiState.Error(result.message)
                else -> Unit
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
        }
    }
}


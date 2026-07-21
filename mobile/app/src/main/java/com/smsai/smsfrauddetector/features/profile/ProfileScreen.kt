package com.smsai.smsfrauddetector.features.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Logout
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smsai.smsfrauddetector.core.common.ApiResult
import com.smsai.smsfrauddetector.core.common.SimpleViewModelFactory
import com.smsai.smsfrauddetector.core.designsystem.components.ErrorStateCard
import com.smsai.smsfrauddetector.core.designsystem.components.PrimaryButton
import com.smsai.smsfrauddetector.core.designsystem.components.SurfaceCard
import com.smsai.smsfrauddetector.data.remote.dto.UserDto
import com.smsai.smsfrauddetector.data.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val loading: Boolean = true,
    val user: UserDto? = null,
    val error: String? = null,
    val status: String? = null,
)

class ProfileViewModel(private val repository: AppRepository) : androidx.lifecycle.ViewModel() {
    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            when (val result = repository.fetchCurrentUser()) {
                is ApiResult.Success -> _state.value = ProfileUiState(loading = false, user = result.data)
                is ApiResult.Error -> _state.value = ProfileUiState(loading = false, error = result.message)
                else -> Unit
            }
        }
    }

    fun save(
        username: String,
        firstName: String,
        lastName: String,
        currentPassword: String,
        newPassword: String,
    ) {
        viewModelScope.launch {
            when (val result = repository.updateProfile(
                username = username.ifBlank { null },
                firstName = firstName.ifBlank { null },
                lastName = lastName.ifBlank { null },
                currentPassword = currentPassword.ifBlank { null },
                newPassword = newPassword.ifBlank { null },
            )) {
                is ApiResult.Success -> _state.value = _state.value.copy(user = result.data, status = "Profile updated successfully.")
                is ApiResult.Error -> _state.value = _state.value.copy(status = result.message)
                else -> Unit
            }
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.logout()
            onDone()
        }
    }
}

@Composable
fun ProfileScreen(repository: AppRepository, onLogout: () -> Unit) {
    val viewModel: ProfileViewModel = viewModel(
        factory = remember(repository) { SimpleViewModelFactory { ProfileViewModel(repository) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var username by rememberSaveable { mutableStateOf("") }
    var firstName by rememberSaveable { mutableStateOf("") }
    var lastName by rememberSaveable { mutableStateOf("") }
    var currentPassword by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(state.user) {
        state.user?.let {
            username = it.username
            firstName = it.firstName.orEmpty()
            lastName = it.lastName.orEmpty()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text = "Profile", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (state.loading) {
            CircularProgressIndicator()
        }
        state.error?.let { ErrorStateCard(message = it, retryText = "Reload profile", onRetry = { viewModel.load() }) }
        SurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = username, onValueChange = { username = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Username") })
                OutlinedTextField(value = firstName, onValueChange = { firstName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("First name") })
                OutlinedTextField(value = lastName, onValueChange = { lastName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Last name") })
                OutlinedTextField(value = currentPassword, onValueChange = { currentPassword = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Current password") })
                OutlinedTextField(value = newPassword, onValueChange = { newPassword = it }, modifier = Modifier.fillMaxWidth(), label = { Text("New password") })
                PrimaryButton(text = "Save profile", onClick = { viewModel.save(username, firstName, lastName, currentPassword, newPassword) })
                state.status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            }
        }
        PrimaryButton(text = "Logout", onClick = {
            viewModel.logout(onLogout)
        })
    }
}

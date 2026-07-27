package com.smsai.smsfrauddetector.features.profile

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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
import com.smsai.smsfrauddetector.core.designsystem.components.PrimaryButton
import com.smsai.smsfrauddetector.core.designsystem.components.StatusBadge
import com.smsai.smsfrauddetector.core.designsystem.components.SurfaceCard
import com.smsai.smsfrauddetector.data.remote.dto.UserDto
import com.smsai.smsfrauddetector.data.repository.AppRepository
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
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
    var currentPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var newPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var bannerMessage by remember { mutableStateOf<String?>(null) }
    var bannerTone by remember { mutableStateOf(BannerTone.Info) }

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(state.user) {
        state.user?.let {
            username = it.username
            firstName = it.firstName.orEmpty()
            lastName = it.lastName.orEmpty()
        }
    }
    LaunchedEffect(state.status) {
        val status = state.status ?: return@LaunchedEffect
        bannerMessage = status
        bannerTone = if (status.contains("updated", ignoreCase = true)) BannerTone.Success else BannerTone.Error
        kotlinx.coroutines.delay(2400)
        bannerMessage = null
    }

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
                    StatusBadge(text = "Secure account", color = MaterialTheme.colorScheme.secondary)
                    Text(text = "Profile", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Update your account details and password using the live backend profile service.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        StatusBadge(text = "Live sync", color = MaterialTheme.colorScheme.primary)
                        StatusBadge(text = "Password change", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            if (state.loading) {
                CircularProgressIndicator()
            }
            state.error?.let { ErrorStateCard(message = it, retryText = "Reload profile", onRetry = { viewModel.load() }) }
            SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = "Account details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(value = username, onValueChange = { username = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Username") })
                    OutlinedTextField(value = firstName, onValueChange = { firstName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("First name") })
                    OutlinedTextField(value = lastName, onValueChange = { lastName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Last name") })
                    Text(
                        text = "For security, the password fields are sent only when you save changes.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    )
                    Text(text = "Password update", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Current password") },
                        leadingIcon = { Icon(imageVector = Icons.Rounded.Lock, contentDescription = null) },
                        visualTransformation = if (currentPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { currentPasswordVisible = !currentPasswordVisible }) {
                                Icon(
                                    imageVector = if (currentPasswordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (currentPasswordVisible) "Hide current password" else "Show current password",
                                )
                            }
                        },
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("New password") },
                        leadingIcon = { Icon(imageVector = Icons.Rounded.Lock, contentDescription = null) },
                        visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                                Icon(
                                    imageVector = if (newPasswordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (newPasswordVisible) "Hide new password" else "Show new password",
                                )
                            }
                        },
                    )
                    PrimaryButton(text = "Save profile", onClick = { viewModel.save(username, firstName, lastName, currentPassword, newPassword) })
                }
            }
            SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatusBadge(text = "Session", color = MaterialTheme.colorScheme.tertiary)
                    Text(text = "Logout", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "Use this to safely end the current session on this device.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    )
                    PrimaryButton(text = "Logout", onClick = { viewModel.logout(onLogout) })
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
}

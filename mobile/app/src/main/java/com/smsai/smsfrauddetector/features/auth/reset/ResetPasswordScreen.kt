package com.smsai.smsfrauddetector.features.auth.reset

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smsai.smsfrauddetector.core.common.ApiResult
import com.smsai.smsfrauddetector.core.common.SimpleViewModelFactory
import com.smsai.smsfrauddetector.core.designsystem.components.ErrorStateCard
import com.smsai.smsfrauddetector.core.designsystem.components.PrimaryButton
import com.smsai.smsfrauddetector.core.designsystem.components.SurfaceCard
import com.smsai.smsfrauddetector.core.designsystem.theme.SmsGradientBackground
import com.smsai.smsfrauddetector.data.repository.AppRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.saveable.rememberSaveable

data class ResetPasswordUiState(
    val loading: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val success: Boolean = false,
)

class ResetPasswordViewModel(private val repository: AppRepository) : ViewModel() {
    private val _state = MutableStateFlow(ResetPasswordUiState())
    val state: StateFlow<ResetPasswordUiState> = _state.asStateFlow()

    fun resetPassword(uid: String, token: String, newPassword: String, confirmPassword: String) {
        if (uid.isBlank() || token.isBlank()) {
            _state.value = _state.value.copy(error = "Missing reset token details.")
            return
        }
        if (newPassword != confirmPassword) {
            _state.value = _state.value.copy(error = "Passwords do not match.")
            return
        }

        _state.value = _state.value.copy(loading = true, error = null, message = null, success = false)
        viewModelScope.launch {
            when (val result = repository.resetPassword(uid, token, newPassword, confirmPassword)) {
                is ApiResult.Success -> _state.value = ResetPasswordUiState(
                    loading = false,
                    message = result.data,
                    success = true,
                )
                is ApiResult.Error -> _state.value = ResetPasswordUiState(
                    loading = false,
                    error = result.message,
                )
                else -> Unit
            }
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }
}

@Composable
fun ResetPasswordScreen(
    repository: AppRepository,
    uid: String? = null,
    token: String? = null,
    onDone: () -> Unit,
) {
    val viewModel: ResetPasswordViewModel = viewModel(
        factory = remember(repository) { SimpleViewModelFactory { ResetPasswordViewModel(repository) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    var uidField by rememberSaveable { mutableStateOf(uid.orEmpty()) }
    var tokenField by rememberSaveable { mutableStateOf(token.orEmpty()) }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var bannerMessage by remember { mutableStateOf<String?>(null) }
    var bannerToken by remember { mutableIntStateOf(0) }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        bannerMessage = message
        viewModel.clearMessage()
        onDone()
        bannerToken += 1
    }

    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        bannerMessage = error
        bannerToken += 1
    }

    LaunchedEffect(bannerToken) {
        val messageSnapshot = bannerMessage ?: return@LaunchedEffect
        delay(2200)
        if (bannerMessage == messageSnapshot) {
            bannerMessage = null
        }
    }

    SmsGradientBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(20.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Reset password",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Enter the token from your email to create a new secure password.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )

                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(22.dp))

                SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(imageVector = Icons.Rounded.LockReset, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Password reset token",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        OutlinedTextField(
                            value = uidField,
                            onValueChange = { uidField = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("UID") },
                            leadingIcon = { Icon(Icons.Rounded.Security, contentDescription = null) },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = tokenField,
                            onValueChange = { tokenField = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Reset token") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("New password") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Confirm password") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                        )

                        state.error?.let {
                            ErrorStateCard(message = it, retryText = "Try again", onRetry = null)
                        }

                        PrimaryButton(
                            text = "Reset password",
                            enabled = !state.loading,
                            onClick = {
                                viewModel.resetPassword(
                                    uid = uidField,
                                    token = tokenField,
                                    newPassword = newPassword,
                                    confirmPassword = confirmPassword,
                                )
                            },
                        )

                        if (state.loading) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                CircularProgressIndicator()
                            }
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
                    Snackbar(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = it, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

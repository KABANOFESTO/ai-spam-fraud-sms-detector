package com.smsai.smsfrauddetector.features.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smsai.smsfrauddetector.core.common.SimpleViewModelFactory
import com.smsai.smsfrauddetector.core.designsystem.components.ErrorStateCard
import com.smsai.smsfrauddetector.core.designsystem.components.PrimaryButton
import com.smsai.smsfrauddetector.core.designsystem.components.SurfaceCard
import com.smsai.smsfrauddetector.core.designsystem.theme.SmsGradientBackground
import com.smsai.smsfrauddetector.data.remote.dto.UserDto
import com.smsai.smsfrauddetector.data.repository.AppRepository
import kotlinx.coroutines.delay

enum class AuthMode { Login, Register }

private sealed interface AuthBanner {
    data class Success(val message: String) : AuthBanner
    data class Error(val message: String) : AuthBanner
}

@Composable
fun AuthScreen(
    mode: AuthMode,
    repository: AppRepository,
    onAuthenticated: (UserDto) -> Unit,
    onSwitchToLogin: () -> Unit,
    onSwitchToRegister: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: AuthViewModel = viewModel(
        factory = remember(repository) { SimpleViewModelFactory { AuthViewModel(repository) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    val scrollState = rememberScrollState()
    var username by rememberSaveable { mutableStateOf("") }
    var firstName by rememberSaveable { mutableStateOf("") }
    var lastName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var authBanner by remember { mutableStateOf<AuthBanner?>(null) }

    LaunchedEffect(state) {
        when (state) {
            is com.smsai.smsfrauddetector.core.common.UiState.Success -> {
                val message = if (mode == AuthMode.Register) {
                    "Account created successfully."
                } else {
                    "Login successful."
                }
                authBanner = AuthBanner.Success(message)
                delay(900)
                onAuthenticated((state as com.smsai.smsfrauddetector.core.common.UiState.Success<UserDto>).data)
                viewModel.clear()
                authBanner = null
            }
            is com.smsai.smsfrauddetector.core.common.UiState.Error -> {
                authBanner = AuthBanner.Error((state as com.smsai.smsfrauddetector.core.common.UiState.Error).message)
                delay(2600)
                authBanner = null
            }
            else -> Unit
        }
    }

    SmsGradientBackground(modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(innerPadding)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "SMS Fraud Detector",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "Secure every message before it is trusted.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                    )

                    Spacer(modifier = Modifier.height(22.dp))

                    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                PrimaryButton(
                                    text = "Login",
                                    modifier = Modifier.weight(1f),
                                    enabled = mode != AuthMode.Login,
                                    onClick = onSwitchToLogin,
                                )
                                PrimaryButton(
                                    text = "Register",
                                    modifier = Modifier.weight(1f),
                                    enabled = mode != AuthMode.Register,
                                    onClick = onSwitchToRegister,
                                )
                            }

                            if (mode == AuthMode.Register) {
                                OutlinedTextField(
                                    value = username,
                                    onValueChange = { username = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Username") },
                                    leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                                    singleLine = true,
                                )
                                OutlinedTextField(
                                    value = firstName,
                                    onValueChange = { firstName = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("First name") },
                                    leadingIcon = { Icon(Icons.Rounded.Badge, contentDescription = null) },
                                    singleLine = true,
                                )
                                OutlinedTextField(
                                    value = lastName,
                                    onValueChange = { lastName = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Last name") },
                                    leadingIcon = { Icon(Icons.Rounded.Badge, contentDescription = null) },
                                    singleLine = true,
                                )
                            }

                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Email") },
                                leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null) },
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Password") },
                                leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                            )

                            state.takeIf { it is com.smsai.smsfrauddetector.core.common.UiState.Error }
                                ?.let {
                                    ErrorStateCard(
                                        message = (it as com.smsai.smsfrauddetector.core.common.UiState.Error).message,
                                        retryText = if (mode == AuthMode.Login) "Retry login" else "Retry registration",
                                        onRetry = {
                                            if (mode == AuthMode.Login) {
                                                viewModel.login(email, password)
                                            } else {
                                                viewModel.register(username, email, password, firstName, lastName)
                                            }
                                        },
                                    )
                                }

                            PrimaryButton(
                                text = if (mode == AuthMode.Login) "Login" else "Create account",
                                enabled = state !is com.smsai.smsfrauddetector.core.common.UiState.Loading,
                                onClick = {
                                    if (mode == AuthMode.Login) {
                                        viewModel.login(email, password)
                                    } else {
                                        viewModel.register(username, email, password, firstName, lastName)
                                    }
                                },
                                trailingIcon = true,
                            )

                            if (state is com.smsai.smsfrauddetector.core.common.UiState.Loading) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                }
            }
            AnimatedVisibility(
                visible = authBanner != null,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 14.dp, start = 16.dp, end = 16.dp)
                    .widthIn(max = 520.dp),
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            ) {
                when (val banner = authBanner) {
                    is AuthBanner.Success -> PremiumAuthSnackbar(
                        message = banner.message,
                        icon = Icons.Rounded.CheckCircle,
                        containerColor = Color(0xFF0F5132),
                        contentColor = Color(0xFFE9F8EF),
                    )
                    is AuthBanner.Error -> PremiumAuthSnackbar(
                        message = banner.message,
                        icon = Icons.Rounded.Error,
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    null -> Unit
                }
            }
        }
    }
}

@Composable
private fun PremiumAuthSnackbar(
    message: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    contentColor: Color,
) {
    Snackbar(
        containerColor = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = icon, contentDescription = null)
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

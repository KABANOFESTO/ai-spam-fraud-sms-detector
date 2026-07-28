package com.smsai.smsfrauddetector.features.admin.users

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable

data class AdminUsersUiState(
    val loading: Boolean = true,
    val viewerEmail: String? = null,
    val users: List<UserDto> = emptyList(),
    val error: String? = null,
    val status: String? = null,
)

class AdminUsersViewModel(private val repository: AppRepository) : ViewModel() {
    private val _state = MutableStateFlow(AdminUsersUiState())
    val state: StateFlow<AdminUsersUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            val status = _state.value.status
            _state.value = _state.value.copy(loading = true, error = null)
            val session = repository.currentSession()
            when (val result = repository.users()) {
                is ApiResult.Success -> _state.value = AdminUsersUiState(
                    loading = false,
                    viewerEmail = session.user?.email,
                    users = result.data,
                    status = status,
                )
                is ApiResult.Error -> _state.value = AdminUsersUiState(
                    loading = false,
                    viewerEmail = session.user?.email,
                    error = result.message,
                    users = emptyList(),
                    status = status,
                )
                else -> Unit
            }
        }
    }

    fun createUser(
        username: String,
        email: String,
        firstName: String,
        lastName: String,
        role: String,
        status: String,
    ) {
        viewModelScope.launch {
            when (val result = repository.adminCreateUser(
                username = username,
                email = email,
                role = role,
                firstName = firstName,
                lastName = lastName,
                status = status,
            )) {
                is ApiResult.Success -> {
                    val message = buildString {
                        append(result.data.message ?: "User created successfully.")
                        result.data.temporaryPassword?.let { append(" Temporary password: $it") }
                    }
                    _state.value = _state.value.copy(status = message)
                    load()
                }
                is ApiResult.Error -> _state.value = _state.value.copy(status = result.message)
                else -> Unit
            }
        }
    }

    fun updateUser(
        userId: Int,
        username: String,
        firstName: String,
        lastName: String,
        role: String,
        status: String,
    ) {
        viewModelScope.launch {
            when (val result = repository.adminUpdateUser(
                userId = userId,
                username = username,
                firstName = firstName,
                lastName = lastName,
                role = role,
                status = status,
            )) {
                is ApiResult.Success -> {
                    _state.value = _state.value.copy(status = result.data.message ?: "User updated successfully.")
                    load()
                }
                is ApiResult.Error -> _state.value = _state.value.copy(status = result.message)
                else -> Unit
            }
        }
    }

    fun toggleUser(userId: Int) {
        viewModelScope.launch {
            when (val result = repository.adminToggleUser(userId)) {
                is ApiResult.Success -> {
                    _state.value = _state.value.copy(status = result.data.message ?: "User status updated.")
                    load()
                }
                is ApiResult.Error -> _state.value = _state.value.copy(status = result.message)
                else -> Unit
            }
        }
    }

    fun deleteUser(userId: Int) {
        viewModelScope.launch {
            when (val result = repository.adminDeleteUser(userId)) {
                is ApiResult.Success -> {
                    _state.value = _state.value.copy(status = result.data)
                    load()
                }
                is ApiResult.Error -> _state.value = _state.value.copy(status = result.message)
                else -> Unit
            }
        }
    }

    fun clearStatus() {
        _state.value = _state.value.copy(status = null)
    }
}

@Composable
fun AdminUsersScreen(repository: AppRepository) {
    val viewModel: AdminUsersViewModel = viewModel(
        factory = remember(repository) { SimpleViewModelFactory { AdminUsersViewModel(repository) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    var selectedUser by remember { mutableStateOf<UserDto?>(null) }
    var username by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var firstName by rememberSaveable { mutableStateOf("") }
    var lastName by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf("User") }
    var status by rememberSaveable { mutableStateOf("Active") }
    var bannerMessage by remember { mutableStateOf<String?>(null) }
    var bannerTone by remember { mutableStateOf(BannerTone.Info) }
    var pendingDeleteUser by remember { mutableStateOf<UserDto?>(null) }
    var pendingToggleUser by remember { mutableStateOf<UserDto?>(null) }

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(selectedUser) {
        selectedUser?.let { user ->
            username = user.username
            email = user.email
            firstName = user.firstName.orEmpty()
            lastName = user.lastName.orEmpty()
            role = user.role
            status = user.status
        } ?: run {
            username = ""
            email = ""
            firstName = ""
            lastName = ""
            role = "User"
            status = "Active"
        }
    }
    LaunchedEffect(state.status) {
        val message = state.status ?: return@LaunchedEffect
        bannerMessage = message
        bannerTone = when {
            message.contains("delete", ignoreCase = true) ||
                message.contains("deactivated", ignoreCase = true) ||
                message.contains("failed", ignoreCase = true) ||
                message.contains("error", ignoreCase = true) -> BannerTone.Error
            else -> BannerTone.Success
        }
        kotlinx.coroutines.delay(2400)
        bannerMessage = null
        viewModel.clearStatus()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatusBadge(text = "Admin control center", color = MaterialTheme.colorScheme.secondary)
                        Text(
                            text = "User management",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Create temporary accounts, change access, activate or deactivate users, and remove accounts safely.",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            StatusBadge(text = "Live sync", color = MaterialTheme.colorScheme.primary)
                            StatusBadge(text = "Temporary passwords", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricCardLite("Users", state.users.size.toString(), "Total accounts", Modifier.weight(1f))
                    MetricCardLite("Admins", state.users.count { it.role.equals("Admin", true) }.toString(), "Admin accounts", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricCardLite("Active", state.users.count { it.isActive }.toString(), "Enabled accounts", Modifier.weight(1f))
                    MetricCardLite("Disabled", state.users.count { !it.isActive }.toString(), "Inactive accounts", Modifier.weight(1f))
                }
            }

            item {
                SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatusBadge(text = if (selectedUser == null) "Create user" else "Edit user", color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "Temporary passwords are emailed automatically when a user is created.",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                        )
                        OutlinedTextField(value = username, onValueChange = { username = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Username") })
                        OutlinedTextField(value = email, onValueChange = { email = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Email") })
                        OutlinedTextField(value = firstName, onValueChange = { firstName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("First name") })
                        OutlinedTextField(value = lastName, onValueChange = { lastName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Last name") })
                        Text(
                            text = "Role",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            SelectableChip(
                                label = "User",
                                selected = role.equals("User", ignoreCase = true),
                                onClick = { role = "User" },
                                modifier = Modifier.weight(1f),
                            )
                            SelectableChip(
                                label = "Admin",
                                selected = role.equals("Admin", ignoreCase = true),
                                onClick = { role = "Admin" },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Text(
                            text = "Account status",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            SelectableChip(
                                label = "Active",
                                selected = status.equals("Active", ignoreCase = true),
                                onClick = { status = "Active" },
                                modifier = Modifier.weight(1f),
                            )
                            SelectableChip(
                                label = "Inactive",
                                selected = status.equals("Inactive", ignoreCase = true),
                                onClick = { status = "Inactive" },
                                modifier = Modifier.weight(1f),
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            PrimaryButton(
                                text = if (selectedUser == null) "Create user" else "Save changes",
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (selectedUser == null) {
                                        viewModel.createUser(username, email, firstName, lastName, role, status)
                                    } else {
                                        viewModel.updateUser(
                                            userId = selectedUser!!.id,
                                            username = username,
                                            firstName = firstName,
                                            lastName = lastName,
                                            role = role,
                                            status = status,
                                        )
                                    }
                                },
                            )
                            if (selectedUser != null) {
                                PrimaryButton(
                                    text = "Cancel",
                                    modifier = Modifier.weight(1f),
                                    onClick = { selectedUser = null },
                                )
                            }
                        }
                    }
                }
            }

            item {
                if (state.loading) {
                    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatusBadge(text = "Loading users", color = MaterialTheme.colorScheme.tertiary)
                            CircularProgressIndicator()
                            Text(
                                text = "Fetching the latest accounts from the backend...",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                            )
                        }
                    }
                }
            }

            state.error?.let { message ->
                item {
                    ErrorStateCard(message = message, retryText = "Reload users", onRetry = { viewModel.load() })
                }
            }

            item {
                Text(text = "Accounts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }

            if (!state.loading && state.users.isEmpty() && state.error == null) {
                item {
                    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatusBadge(text = "No users yet", color = MaterialTheme.colorScheme.tertiary)
                            Text(
                                text = "Your user directory is empty right now.",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            PrimaryButton(text = "Reload users", onClick = { viewModel.load() })
                        }
                    }
                }
            }

            items(state.users, key = { it.id }) { user ->
                val isSelf = user.email.equals(state.viewerEmail, ignoreCase = true)
                SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            StatusBadge(
                                text = if (user.isActive) "Active" else "Inactive",
                                color = if (user.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                            )
                            StatusBadge(
                                text = user.role,
                                color = if (user.role.equals("Admin", ignoreCase = true)) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                            )
                            if (isSelf) {
                                StatusBadge(text = "Your account", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Text(text = user.username, fontWeight = FontWeight.Bold)
                        Text(text = user.email, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
                        Text(
                            text = "${user.firstName.orEmpty()} ${user.lastName.orEmpty()}".trim().ifBlank { "No profile name set" },
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            PrimaryButton(
                                text = "Edit",
                                modifier = Modifier.weight(1f),
                                onClick = { selectedUser = user },
                                trailingIcon = true,
                                enabled = !isSelf,
                            )
                            PrimaryButton(
                                text = if (user.isActive) "Deactivate" else "Activate",
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (user.isActive) {
                                        pendingToggleUser = user
                                    } else {
                                        viewModel.toggleUser(user.id)
                                    }
                                },
                                enabled = !isSelf,
                            )
                        }
                        PrimaryButton(
                            text = "Delete user",
                            onClick = { pendingDeleteUser = user },
                            enabled = !isSelf,
                        )
                    }
                }
            }
        }

        pendingToggleUser?.let { user ->
            val isDeactivate = user.isActive
            AlertDialog(
                onDismissRequest = { pendingToggleUser = null },
                title = { Text(text = if (isDeactivate) "Deactivate user?" else "Activate user?") },
                text = {
                    Text(
                        text = if (isDeactivate) {
                            "This will disable ${user.username}'s access until you activate the account again."
                        } else {
                            "This will restore ${user.username}'s access to the platform."
                        },
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingToggleUser = null
                            viewModel.toggleUser(user.id)
                        },
                    ) {
                        Text(text = if (isDeactivate) "Deactivate" else "Activate")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingToggleUser = null }) {
                        Text("Cancel")
                    }
                },
            )
        }

        pendingDeleteUser?.let { user ->
            AlertDialog(
                onDismissRequest = { pendingDeleteUser = null },
                title = { Text(text = "Delete user?") },
                text = {
                    Text(
                        text = "This permanently removes ${user.username} and cannot be undone.",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingDeleteUser = null
                            viewModel.deleteUser(user.id)
                        },
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteUser = null }) {
                        Text("Cancel")
                    }
                },
            )
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

@Composable
private fun SelectableChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier,
    )
}

@Composable
private fun MetricCardLite(title: String, value: String, subtitle: String, modifier: Modifier = Modifier) {
    SurfaceCard(modifier = modifier) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
            Text(text = value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f))
        }
    }
}
